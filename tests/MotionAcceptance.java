import com.asterion.foundry.Glb;
import com.asterion.foundry.Mesh;
import com.asterion.foundry.Recipes;
import com.asterion.foundry.SkeletalMotion;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.imageio.ImageIO;

public final class MotionAcceptance {
    private static final String CLIP = """
            clip Turn and move
            rotate body 1 0 0 1 90
            move body 1 1 0 0
            move prop 1 -1 0 0
            contact hand prop 1 1 0.001
            """;

    public static void main(String[] args) throws Exception {
        Mesh rig = rig();
        SkeletalMotion motion = SkeletalMotion.parse(rig, CLIP);
        SkeletalMotion.Validation evidence = motion.validate(rig);
        near(1, evidence.durationSeconds());
        if (evidence.samples() != 31 || evidence.triangles() != 2 || evidence.contacts() != 1) {
            throw new AssertionError("Unexpected sampled validation evidence " + evidence);
        }
        near(0, evidence.maxContactGapMetres());

        float[][] half = preview(motion, rig, 0.5f);
        float[] vertex = half[0];
        near(0.5 + Math.sqrt(2), vertex[0]);
        near(Math.sqrt(2), vertex[1]);
        near(Math.sqrt(0.5), vertex[3]);
        near(-Math.sqrt(0.5), vertex[4]);
        near(1.5, half[3][0]);
        near(1, length(half[0], half[1]));
        near(2, rig.vertices.get(0)[0]);
        near(0, rig.vertices.get(0)[1]);
        near(rig.vertices.get(0)[6], vertex[6]);
        SkeletalMotion shortest = SkeletalMotion.parse(rig, """
                clip Short turn
                rotate body 0 0 0 1 170
                rotate body 1 0 0 1 -170
                """);
        near(-2, preview(shortest, rig, 0.5f)[0][0]);

        File dir = new File("test-output");
        dir.mkdirs();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        if (!ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", png)) {
            throw new AssertionError("PNG encoder missing");
        }
        File glb = new File(dir, "motion.glb");
        Glb.write(rig, glb, png.toByteArray(), motion);
        ByteBuffer bytes = ByteBuffer.wrap(Files.readAllBytes(glb.toPath())).order(ByteOrder.LITTLE_ENDIAN);
        int jsonSize = bytes.getInt(12);
        String json = new String(bytes.array(), 20, jsonSize, StandardCharsets.UTF_8);
        if (!json.contains("\"name\":\"Turn and move\"") || !json.contains("\"node\":1,\"path\":\"rotation\"")
                || !json.contains("\"node\":3,\"path\":\"translation\"")) {
            throw new AssertionError("Export omitted clip or animated joint channels");
        }
        int animationOffset = 28 + jsonSize + 6 * 72 + 3 * 64 + ((png.size() + 3) & ~3);
        near(0, bytes.getFloat(animationOffset));
        near(1, bytes.getFloat(animationOffset + 4));
        near(1, bytes.getFloat(animationOffset + 20));
        near(Math.sqrt(0.5), bytes.getFloat(animationOffset + 32));
        near(Math.sqrt(0.5), bytes.getFloat(animationOffset + 36));
        near(2, bytes.getFloat(bytes.limit() - 24));
        near(1, bytes.getFloat(bytes.limit() - 12));
        Glb.write(rig, new File(dir, "motion_untextured.glb"), null, motion);

        expectFailure(() -> SkeletalMotion.parse(rig, CLIP + "rotate body 1 0 0 1 60\n"));
        expectFailure(() -> SkeletalMotion.parse(rig, CLIP.replace("1 0 0 1 90", "1 0 0 0 90")));
        expectFailure(() -> SkeletalMotion.parse(rig, CLIP.replace("move prop", "move missing")));
        expectFailure(() -> SkeletalMotion.parse(rig, CLIP.replace("-1 0 0", "NaN 0 0")));
        expectFailure(() -> SkeletalMotion.parse(rig, CLIP
                + "contact hand prop 0 2 0.001\n"));
        expectFailure(() -> SkeletalMotion.parse(rig, CLIP.replace(" 1 1 0.001", " 0 1 0.001")).validate(rig));
        expectFailure(() -> SkeletalMotion.parse(rig, """
                clip Separate between keys
                rotate body 1 0 0 1 180
                move prop 0 -1 -1 0
                move prop 1 -3 -1 0
                contact hand prop 0 1 0.01
                """).validate(rig));
        expectFailure(() -> SkeletalMotion.parse(rig, """
                clip Missed mid-clip contact
                rotate body 0.015 0 0 1 90
                rotate body 0.03 0 0 1 0
                move prop 0 -1 -1 0
                contact hand prop 0 0.03 0.01
                """).validate(rig));
        Mesh changed = rig();
        changed.bones.get(1).x += 0.1f;
        expectFailure(() -> motion.validate(changed));
        Mesh mixed = rig();
        mixed.vertices.get(1)[12] = 2;
        expectFailure(() -> motion.validate(mixed));
        Mesh fractional = rig();
        fractional.vertices.get(1)[12] = 1.5f;
        expectFailure(() -> motion.validate(fractional));
        Mesh humanoid = Recipes.create("Humanoid template", 1, 4);
        SkeletalMotion walk = SkeletalMotion.parse(humanoid, SkeletalMotion.compose(humanoid, "Walk in place."));
        SkeletalMotion.Validation walkEvidence = walk.validate(humanoid);
        near(1.2, walk.durationSeconds());
        if (walkEvidence.tracks() < 6) throw new AssertionError("Walk needs coordinated limb channels");
        float[][] start = preview(walk, humanoid, 0), end = preview(walk, humanoid, 1.2f);
        float[][] middle = preview(walk, humanoid, 0.6f);
        boolean movedLeg = false, movedArm = false;
        for (int i = 0; i < start.length; i++) {
            near(0, length(start[i], end[i]));
            int joint = (int) humanoid.vertices.get(i)[12];
            if (length(start[i], middle[i]) < 0.03) continue;
            movedLeg |= humanoid.bones.get(joint).name.startsWith("lower_leg");
            movedArm |= humanoid.bones.get(joint).name.startsWith("forearm");
        }
        if (!movedLeg || !movedArm) throw new AssertionError("Walk failed to articulate both arms and legs");
        Mesh creature = Recipes.create("Quadruped template", 1, 4);
        SkeletalMotion wag = SkeletalMotion.parse(creature, SkeletalMotion.compose(creature, "wag its tail"));
        near(1.2, wag.validate(creature).durationSeconds());
        float[][] wagFrame = preview(wag, creature, 0.3f);
        boolean movedTail = false;
        for (int i = 0; i < wagFrame.length; i++) {
            float[] rest = creature.vertices.get(i);
            if (creature.bones.get((int) rest[12]).name.equals("tail")) {
                movedTail |= length(rest, wagFrame[i]) > 0.03;
            } else near(0, length(rest, wagFrame[i]));
        }
        if (!movedTail) throw new AssertionError("Tail wag left the tail static");
        SkeletalMotion.parse(creature, SkeletalMotion.compose(creature, "breathing idle")).validate(creature);
        expectFailure(() -> SkeletalMotion.compose(creature, "walk in place"));
        expectFailure(() -> SkeletalMotion.compose(humanoid, "walk and punch"));
        System.out.println("PASS skeletal authoring, playback, contact, binding and animated GLB checks.");
    }

    private static float[][] preview(SkeletalMotion motion, Mesh mesh, float time) {
        float[][] result = new float[mesh.vertices.size()][12];
        FloatBuffer buffer = FloatBuffer.allocate(12 * result.length);
        motion.playback(mesh).write(time, buffer);
        buffer.flip();
        for (float[] vertex : result) buffer.get(vertex);
        return result;
    }

    private static Mesh rig() {
        Mesh mesh = new Mesh();
        int body = mesh.bone("body", -1, 0, 0, 0);
        int hand = mesh.bone("hand", body, 1, 0, 0);
        int prop = mesh.bone("prop", -1, 2, 1, 0);
        float[] color = {1, 1, 1};
        mesh.tri(new float[]{2, 0, 0}, new float[]{3, 0, 0}, new float[]{2, 0, 1}, color, hand);
        mesh.tri(new float[]{2, 1, 0}, new float[]{3, 1, 0}, new float[]{2, 1, 1}, color, prop);
        for (float[] vertex : mesh.vertices) vertex[6] = vertex[0] - 2;
        return mesh;
    }

    private static double length(float[] a, float[] b) {
        return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2));
    }

    private static void near(double expected, double actual) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.00001) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Invalid motion was accepted");
    }
}
