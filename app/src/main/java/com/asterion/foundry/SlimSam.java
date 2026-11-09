package com.asterion.foundry;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/** Refines a coarse foreground prior using locally inferred, spatially distributed prompts. */
final class SlimSam {
    static final String ENCODER = "vision_encoder.onnx";
    static final String DECODER = "prompt_encoder_mask_decoder.onnx";
    private static final int INPUT = 1024;
    private static final int MASK = 256;
    private static final int EMBEDDING = 256 * 64 * 64;
    private static final float[] MEAN = {.485f,.456f,.406f};
    private static final float[] STD = {.229f,.224f,.225f};
    record Result(float[] alpha, float score, double priorOverlap, int retainedPixels) {}

    static Result refine(OrtEnvironment env, File models, int[] pixels, int width, int height,
                         float[] prior, VisualHull.Budget budget) throws Exception {
        int[] seeds = seedPoints(prior,width,height);
        int w = Math.max(1, Math.round(INPUT * (float) width / Math.max(width,height)));
        int h = Math.max(1, Math.round(INPUT * (float) height / Math.max(width,height)));
        float[] image = new float[3*INPUT*INPUT];
        for (int y = 0; y < h; y++) {
            if ((y & 63) == 0) budget.check();
            for (int x = 0; x < w; x++) {
                int pixel = pixels[(y*height/h)*width+x*width/w];
                for (int c = 0; c < 3; c++) {
                    float channel = ((pixel >>> (16 - 8*c)) & 255)/255f;
                    image[c*INPUT*INPUT + y*INPUT + x] = (channel-MEAN[c])/STD[c];
                }
            }
        }
        float[] embeddings, positions;
        try (OrtSession encoder = TriplaneMesh.open(env,models,ENCODER);
             OnnxTensor tensor = OnnxTensor.createTensor(env,FloatBuffer.wrap(image),new long[]{1,3,INPUT,INPUT});
             OrtSession.Result result = encoder.run(Map.of("pixel_values",tensor),
                     Set.of("image_embeddings","image_positional_embeddings"))) {
            embeddings = TriplaneMesh.floats(result,"image_embeddings",EMBEDDING);
            positions = TriplaneMesh.floats(result,"image_positional_embeddings",EMBEDDING);
        }
        budget.check();

        float[] coordinates = new float[2*seeds.length];
        long[] labels = new long[seeds.length];
        Arrays.fill(labels,1);
        for (int i = 0; i < seeds.length; i++) {
            coordinates[2*i] = (seeds[i]%width+.5f)*w/width;
            coordinates[2*i+1] = (seeds[i]/width+.5f)*h/height;
        }
        float[] scores, masks;
        try (OrtSession decoder = TriplaneMesh.open(env,models,DECODER);
             OnnxTensor points = OnnxTensor.createTensor(env,FloatBuffer.wrap(coordinates),
                     new long[]{1,1,seeds.length,2});
             OnnxTensor pointLabels = OnnxTensor.createTensor(env,LongBuffer.wrap(labels),
                     new long[]{1,1,seeds.length});
             OnnxTensor imageEmbedding = OnnxTensor.createTensor(env,FloatBuffer.wrap(embeddings),
                     new long[]{1,256,64,64});
             OnnxTensor positionEmbedding = OnnxTensor.createTensor(env,FloatBuffer.wrap(positions),
                     new long[]{1,256,64,64});
             OrtSession.Result result = decoder.run(Map.of(
                     "input_points",points, "input_labels",pointLabels,
                     "image_embeddings",imageEmbedding, "image_positional_embeddings",positionEmbedding),
                     Set.of("iou_scores","pred_masks"))) {
            scores = TriplaneMesh.floats(result,"iou_scores",3);
            masks = TriplaneMesh.floats(result,"pred_masks",3*MASK*MASK);
        }

        Result best = null;
        double rank = Double.NEGATIVE_INFINITY;
        int priorSize = 0;
        for (float p : prior) if (p >= .5f) priorSize++;
        for (int k = 0; k < 3; k++) {
            budget.check();
            boolean[] foreground = new boolean[prior.length];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                float gx = (x+.5f)*w/width*MASK/INPUT-.5f;
                float gy = (y+.5f)*h/height*MASK/INPUT-.5f;
                foreground[y*width+x] = bilinear(masks,k*MASK*MASK,gx,gy)>0;
            }
            int[] component = largestComponent(foreground,width,height);
            int common = 0;
            for (int index : component) if (prior[index] >= .5f) common++;
            double overlap = (double) common / (component.length + priorSize - common);
            // Reject missing limbs/parts when the two segmenters disagree strongly.
            if (component.length < 64 || overlap < .60 || common < .72*priorSize) continue;
            double candidateRank = scores[k] + .25*overlap;
            if (candidateRank <= rank) continue;
            float[] alpha = new float[prior.length];
            for (int index : component) alpha[index] = 1;
            best = new Result(alpha,scores[k],overlap,component.length);
            rank = candidateRank;
        }
        if (best == null) throw new IOException("The foreground models disagree; no reliable subject mask");
        return best;
    }

    private static int[] seedPoints(float[] mask,int width,int height) throws IOException {
        int[] distance = new int[mask.length];
        int left = width, right = -1, top = height, bottom = -1;
        for (int i = 0; i < mask.length; i++) {
            if (mask[i] < .75f) continue;
            distance[i] = 100000;
            int x = i%width, y = i/width;
            left = Math.min(left,x);
            right = Math.max(right,x);
            top = Math.min(top,y);
            bottom = Math.max(bottom,y);
        }
        if (right-left < 3 || bottom-top < 3) throw new IOException("Foreground prior is too small for prompts");
        // Two-pass chamfer distance selects seeds well within the coarse subject.
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int i = y*width+x;
            if (x > 0) distance[i] = Math.min(distance[i],distance[i-1]+3);
            if (y > 0) {
                distance[i] = Math.min(distance[i],distance[i-width]+3);
                if (x > 0) distance[i] = Math.min(distance[i],distance[i-width-1]+4);
                if (x+1 < width) distance[i] = Math.min(distance[i],distance[i-width+1]+4);
            }
        }
        for (int y = height-1; y >= 0; y--) for (int x = width-1; x >= 0; x--) {
            int i = y*width+x;
            if (x+1 < width) distance[i] = Math.min(distance[i],distance[i+1]+3);
            if (y+1 < height) {
                distance[i] = Math.min(distance[i],distance[i+width]+3);
                if (x > 0) distance[i] = Math.min(distance[i],distance[i+width-1]+4);
                if (x+1 < width) distance[i] = Math.min(distance[i],distance[i+width+1]+4);
            }
        }
        boolean vertical = bottom-top >= right-left;
        int first = vertical ? top : left;
        int span = vertical ? bottom-top : right-left;
        float[][] ranges = {{.09f,.29f},{.30f,.50f},{.53f,.72f},{.77f,.97f}};
        ArrayList<Integer> seeds = new ArrayList<>();
        for (float[] range : ranges) {
            int begin = first+(int)(range[0]*span), end = first+(int)(range[1]*span);
            int seed = -1, best = 0;
            for (int i = 0; i < distance.length; i++) {
                int coordinate = vertical ? i/width : i%width;
                if (coordinate >= begin && coordinate < end && distance[i] > best) {
                    best = distance[i];
                    seed = i;
                }
            }
            if (seed >= 0) seeds.add(seed);
        }
        if (seeds.size() < 2) throw new IOException("Foreground prior lacks spatially distinct prompts");
        return seeds.stream().mapToInt(Integer::intValue).toArray();
    }

    private static float bilinear(float[] values,int offset,float x,float y) {
        x = Math.max(0,Math.min(MASK-1,x));
        y = Math.max(0,Math.min(MASK-1,y));
        int a = (int)x, b = (int)y;
        int nextX = Math.min(a+1,MASK-1), nextY = Math.min(b+1,MASK-1);
        float dx=x-a, dy=y-b;
        return (1-dy)*((1-dx)*values[offset+b*MASK+a] + dx*values[offset+b*MASK+nextX])
                +dy*((1-dx)*values[offset+nextY*MASK+a] + dx*values[offset+nextY*MASK+nextX]);
    }

    private static int[] largestComponent(boolean[] remaining,int width,int height) {
        int[] queue = new int[remaining.length];
        int[] best = new int[0];
        for (int start = 0; start < remaining.length; start++) {
            if (!remaining[start]) continue;
            int head=0, tail=1;
            queue[0]=start;
            remaining[start]=false;
            while (head<tail) {
                int i=queue[head++], x=i%width, y=i/width;
                if (x>0) tail=add(i-1,remaining,queue,tail);
                if (x+1<width) tail=add(i+1,remaining,queue,tail);
                if (y>0) tail=add(i-width,remaining,queue,tail);
                if (y+1<height) tail=add(i+width,remaining,queue,tail);
            }
            if (tail>best.length) best=Arrays.copyOf(queue,tail);
        }
        return best;
    }

    private static int add(int index,boolean[] remaining,int[] queue,int tail) {
        if (!remaining[index]) return tail;
        remaining[index]=false;
        queue[tail]=index;
        return tail+1;
    }
}
