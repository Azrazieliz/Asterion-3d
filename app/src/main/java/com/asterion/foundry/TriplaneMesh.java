package com.asterion.foundry;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.io.*;
import java.nio.FloatBuffer;
import java.util.Map;
import java.util.Set;

/** Local ORT CPU encoder and decoder, with reusable triplane and density checkpoints. */
final class TriplaneMesh {
    private static final int SIDE = 64, CHANNELS = 40, INPUT = 512, MASK = 1024;
    private static final int PLANES = 3 * CHANNELS * SIDE * SIDE;
    private static final int BATCH = 4096;
    private static final float RADIUS = .87f, DENSITY = 25f;

    interface Progress { void update(String message); }
    record Result(Mesh mesh, boolean reusedPlanes, boolean reusedField, int cells, long millis) {}
    private record Cutout(int width, int height, int[] pixels, float[] alpha,
                          int left, int top, int right, int bottom) {}

    static Result reconstruct(File models, File job, int[] pixels, int width, int height,
                              int cells, float metres, VisualHull.Budget budget,
                              Progress progress) throws Exception {
        if (cells < 48 || cells > 128 || pixels.length != width * height || width < 4 || height < 4
                || !Float.isFinite(metres) || metres < .01f || metres > 100) {
            throw new IllegalArgumentException("Invalid reconstruction parameters");
        }
        long start = System.nanoTime();
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        env.setTelemetry(false);
        File planesFile = new File(job,"triplane.f32");
        File fieldFile = new File(job,"density-" + cells + ".f32");
        float[] planes = read(planesFile,PLANES);
        boolean reusedPlanes = planes != null;
        if (planes == null) {
            budget.check();
            progress.update("Isolating subject");
            Cutout cutout = cutout(env, models, pixels, width, height, budget);
            budget.check();
            progress.update("Encoding isolated subject into a 3D field");
            float[] rgb = frame(cutout);
            try (OrtSession session = open(env,models,TripoModels.ENCODER);
                 OnnxTensor input = OnnxTensor.createTensor(env,FloatBuffer.wrap(rgb),new long[]{1,3,INPUT,INPUT});
                 OrtSession.Result prediction = session.run(Map.of("image",input),Set.of("triplane"))) {
                planes = floats(prediction,"triplane",PLANES);
            }
            write(planesFile,planes);
        }
        budget.check();
        try (OrtSession session = open(env,models,TripoModels.DECODER)) {
            float[] field = read(fieldFile,cells*cells*cells);
            boolean reusedField = field != null;
            if (field == null) {
                progress.update("Sampling volumetric density");
                field = new float[cells*cells*cells];
                float[] points = new float[BATCH*3];
                float[] features = new float[BATCH*CHANNELS*3];
                for (int offset=0; offset<field.length; offset+=BATCH) {
                    budget.check();
                    int count = Math.min(BATCH,field.length-offset);
                    for (int i=0; i<count; i++) {
                        int position = offset+i;
                        points[3*i] = coordinate(position/(cells*cells),cells);
                        points[3*i+1] = coordinate(position/cells%cells,cells);
                        points[3*i+2] = coordinate(position%cells,cells);
                    }
                    sample(planes,points,count,features);
                    try (OnnxTensor input = tensor(env,features,count);
                         OrtSession.Result output = session.run(Map.of("features",input),Set.of("density"))) {
                        floats(output,"density",count,field,offset);
                    }
                }
                write(fieldFile,field);
            }
            progress.update("Extracting a closed surface from the field");
            FieldSurface surface = FieldSurface.extract(field,cells,RADIUS,DENSITY,budget);
            progress.update("Decoding surface colours");
            float[] colors = new float[surface.positions.size()*3];
            float[] points = new float[BATCH*3];
            float[] features = new float[BATCH*CHANNELS*3];
            for (int offset=0; offset<surface.positions.size(); offset+=BATCH) {
                budget.check();
                int count = Math.min(BATCH,surface.positions.size()-offset);
                for (int i=0; i<count; i++) System.arraycopy(surface.positions.get(offset+i),0,points,i*3,3);
                sample(planes,points,count,features);
                try (OnnxTensor input = tensor(env,features,count);
                     OrtSession.Result output = session.run(Map.of("features",input),Set.of("color"))) {
                    floats(output,"color",count*3,colors,offset*3);
                }
            }
            return new Result(surface.mesh(colors,metres,budget),reusedPlanes,reusedField,cells,
                    (System.nanoTime()-start)/1_000_000);
        }
    }

    private static float coordinate(int position,int cells) {
        return -RADIUS+2*RADIUS*position/(cells-1);
    }

    private static OrtSession open(OrtEnvironment env,File models,String name) throws Exception {
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors()/2)));
            options.setInterOpNumThreads(1);
            options.setMemoryPatternOptimization(false);
            options.setCPUArenaAllocator(false);
            return env.createSession(new File(models,name).getAbsolutePath(),options);
        }
    }

    private static OnnxTensor tensor(OrtEnvironment env,float[] features,int count) throws Exception {
        return OnnxTensor.createTensor(env,FloatBuffer.wrap(features,0,count*120),new long[]{count,120});
    }

    private static float[] floats(OrtSession.Result output,String name,int count) throws Exception {
        float[] result = new float[count];
        floats(output,name,count,result,0);
        return result;
    }

    private static void floats(OrtSession.Result output,String name,int count,float[] result,int offset)
            throws Exception {
        if (!(output.get(name).orElseThrow() instanceof OnnxTensor tensor)) {
            throw new IOException("Invalid model tensor " + name);
        }
        FloatBuffer buffer = tensor.getFloatBuffer();
        if (buffer == null || buffer.remaining()!=count) throw new IOException("Unexpected tensor size: " + name);
        buffer.get(result,offset,count);
        for (int i=offset; i<offset+count; i++) {
            if (!Float.isFinite(result[i])) throw new IOException("Non-finite tensor value: " + name);
        }
    }

    static void sample(float[] plane,float[] points,int count,float[] features) {
        for (int i=0; i<count; i++) {
            for (int p=0; p<3; p++) {
                float x=((points[3*i+(p==2?1:0)]/RADIUS+1)*SIDE-1)/2;
                float y=((points[3*i+(p==0?1:2)]/RADIUS+1)*SIDE-1)/2;
                int left=(int)Math.floor(x), top=(int)Math.floor(y);
                float dx=x-left,dy=y-top;
                for (int channel=0; channel<CHANNELS; channel++) {
                    int start=(p*CHANNELS+channel)*SIDE*SIDE;
                    features[i*120+p*CHANNELS+channel] =
                            (1-dx)*(1-dy)*read(plane,start,left,top)
                            +dx*(1-dy)*read(plane,start,left+1,top)
                            +(1-dx)*dy*read(plane,start,left,top+1)
                            +dx*dy*read(plane,start,left+1,top+1);
                }
            }
        }
    }

    private static float read(float[] plane,int start,int x,int y) {
        return x<0||x>=SIDE||y<0||y>=SIDE ? 0 : plane[start+y*SIDE+x];
    }

    private static Cutout cutout(OrtEnvironment env,File models,int[] pixels,int width,int height,
                                 VisualHull.Budget budget) throws Exception {
        boolean transparent=false;
        for (int pixel:pixels) if ((pixel>>>24)<32) { transparent=true; break; }
        float[] alpha = new float[pixels.length];
        if (transparent) {
            for (int i=0; i<pixels.length; i++) alpha[i]=(pixels[i]>>>24)/255f;
        } else {
            int w=Math.max(1,width*MASK/Math.max(width,height));
            int h=Math.max(1,height*MASK/Math.max(width,height));
            int left=(MASK-w)/2,top=(MASK-h)/2;
            float[] image = new float[MASK*MASK*3];
            for (int y=0; y<h; y++) {
                if ((y&63)==0) budget.check();
                for (int x=0; x<w; x++) {
                    int color = pixels[(y*height/h)*width+x*width/w];
                    int index=(top+y)*MASK+left+x;
                    for (int c=0; c<3; c++) image[c*MASK*MASK+index]=channel(color,c);
                }
            }
            try (OrtSession session = open(env,models,TripoModels.SEGMENTER);
                 OnnxTensor input=OnnxTensor.createTensor(env,FloatBuffer.wrap(image),new long[]{1,3,MASK,MASK});
                 OrtSession.Result prediction=session.run(Map.of("img",input),Set.of("mask"))) {
                float[] mask=floats(prediction,"mask",MASK*MASK);
                for(int y=0; y<height; y++) for(int x=0; x<width; x++) {
                    alpha[y*width+x]=Math.min(1,Math.max(0,mask[(top+y*h/height)*MASK+left+x*w/width]));
                }
            }
        }
        int left=width,top=height,right=-1,bottom=-1,foreground=0;
        for(int y=0; y<height; y++) for(int x=0; x<width; x++) {
            if (alpha[y*width+x]<.5f) continue;
            left=Math.min(left,x);
            top=Math.min(top,y);
            right=Math.max(right,x);
            bottom=Math.max(bottom,y);
            foreground++;
        }
        if (foreground<64 || foreground>.95*pixels.length || right-left<3 || bottom-top<3) {
            throw new IOException("An isolated subject could not be established");
        }
        return new Cutout(width,height,pixels,alpha,left,top,right,bottom);
    }

    private static float[] frame(Cutout cutout) {
        float side = Math.max(cutout.right-cutout.left+1,cutout.bottom-cutout.top+1)/.85f;
        float left = (cutout.left+cutout.right+1-side)/2;
        float top = (cutout.top+cutout.bottom+1-side)/2;
        float[] rgb = new float[3*INPUT*INPUT];
        for(int y=0; y<INPUT; y++) for(int x=0; x<INPUT; x++) {
            int px=(int)Math.floor(left+(x+.5)*side/INPUT);
            int py=(int)Math.floor(top+(y+.5)*side/INPUT);
            int color=0;
            float alpha=0;
            if(px>=cutout.left&&px<=cutout.right&&py>=cutout.top&&py<=cutout.bottom) {
                int i=py*cutout.width+px;
                color=cutout.pixels[i];
                alpha=cutout.alpha[i];
            }
            for (int c=0; c<3; c++) rgb[c*INPUT*INPUT+y*INPUT+x]=alpha*channel(color,c)+(1-alpha)*.5f;
        }
        return rgb;
    }

    private static float channel(int color,int index) {
        return ((color >>> (16-index*8))&255)/255f;
    }

    private static float[] read(File file,int count) throws IOException {
        if (!file.isFile() || file.length()!=4L*count) return null;
        float[] values=new float[count];
        try (DataInputStream stream=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            for (int i=0; i<count; i++) {
                values[i]=stream.readFloat();
                if (!Float.isFinite(values[i])) return null;
            }
        }
        return values;
    }

    private static void write(File file,float[] values) throws IOException {
        File temporary=new File(file+".tmp");
        try (FileOutputStream output=new FileOutputStream(temporary);
             DataOutputStream stream=new DataOutputStream(new BufferedOutputStream(output))) {
            for (float value:values) stream.writeFloat(value);
            stream.flush();
            output.getFD().sync();
        }
        FilesUtil.move(temporary,file);
    }
}
