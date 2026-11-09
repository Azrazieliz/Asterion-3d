package com.asterion.foundry;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import javax.imageio.ImageIO;

/** Host proof for the same mesher, runtime interface and checkpoints used by Android. */
public final class VolumetricAcceptance {
    public static void main(String[] args) throws Exception {
        int side = 25;
        float[] grid = new float[side*side*side];
        for (int x=0; x<side; x++) for (int y=0; y<side; y++) for (int z=0; z<side; z++) {
            double a = x/(side-1d)*2-1, b = y/(side-1d)*2-1, c = z/(side-1d)*2-1;
            grid[(x*side+y)*side+z] = (float) (.6417 - Math.sqrt(a*a+b*b+c*c));
        }
        FieldSurface surface = FieldSurface.extract(grid,side,1,0,() -> {});
        float[] colors = new float[3*surface.positions.size()];
        java.util.Arrays.fill(colors,.6f);
        Mesh sphere = surface.mesh(colors,1,() -> {});
        GeometryAudit.Result audit = GeometryAudit.inspect(sphere, () -> {});
        if (audit.boundaryEdges()!=0 || audit.nonManifoldEdges()!=0 || audit.windingConflicts()!=0) {
            throw new AssertionError("Analytic sphere topology: " + audit);
        }
        System.out.println("Analytic sphere passed: " + sphere.vertices.size()/3 + " triangles");
        if (args.length == 0) return;
        if (args.length != 3) throw new IllegalArgumentException("models image job-directory");
        File job = new File(args[2]);
        Files.createDirectories(job.toPath());
        BufferedImage image = ImageIO.read(new File(args[1]));
        double resize = Math.min(1d, 1024d / Math.max(image.getWidth(),image.getHeight()));
        int width = (int)Math.round(image.getWidth()*resize), height = (int)Math.round(image.getHeight()*resize);
        BufferedImage resized = new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        var graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(image,0,0,width,height,null);
        graphics.dispose();
        int[] pixels = resized.getRGB(0,0,width,height,null,0,width);
        // Interrupt after the encoder and before field sampling, then resume.
        try {
            TriplaneMesh.reconstruct(new File(args[0]),job,pixels,width,height,112,1,
                    () -> {
                        if (new File(job,"triplane.f32").isFile()) {
                            throw new InterruptedException("fixture checkpoint");
                        }
                    },System.out::println);
            throw new AssertionError("Interrupt did not stop the job");
        } catch (InterruptedException expected) {
            System.out.println("Interrupted after triplane checkpoint");
        }
        TriplaneMesh.Result result = TriplaneMesh.reconstruct(new File(args[0]),job,pixels,width,height,112,1,
                () -> {},System.out::println);
        if (!result.reusedPlanes() || result.reusedField()) {
            throw new AssertionError("Resume failed to reuse triplanes");
        }
        Glb.write(result.mesh(),new File(job,"candidate.glb"));
        System.out.println(result.mesh().vertices.size()/3 + " triangles from actual model, "
                + result.millis() + " ms during resume");
        GeometryAudit.Result trial = GeometryAudit.inspect(result.mesh(),() -> {});
        System.out.println("Candidate topology: " + trial);
    }
}
