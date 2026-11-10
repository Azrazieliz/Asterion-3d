package com.asterion.foundry;

import java.nio.ByteBuffer;

public final class PoseEvidenceAcceptance {
    public static void main(String[] args) {
        PoseEvidence.ImageFit portrait = PoseEvidence.ImageFit.of(96, 192);
        float[][] output = new float[17][3];
        output[5] = new float[]{.25f, .375f, .9f};
        output[7] = new float[]{.55f, .05f, .91f};
        output[9] = new float[]{.62f, .64f, .17f};
        output[11] = new float[]{.75f, .625f, .3f};
        PoseEvidence evidence = PoseEvidence.decode(portrait, output);

        PoseEvidence.Point shoulder = evidence.point(PoseEvidence.Joint.LEFT_SHOULDER);
        require(close(shoulder.x(), .25f) && close(shoulder.y(), .25f) && shoulder.usable(),
                "A portrait point must be mapped back from the padded square in x/y order");
        require(evidence.hasTorsoSupport(), "A shoulder and a hip at the threshold must anchor the torso");
        PoseEvidence.Point elbow = evidence.point(PoseEvidence.Joint.LEFT_ELBOW);
        require(close(elbow.x(), -.4f) && !elbow.inFrame() && !elbow.usable(),
                "High confidence in the padding must not turn into an image-edge landmark");
        PoseEvidence.Point wrist = evidence.point(PoseEvidence.Joint.LEFT_WRIST);
        require(wrist.inFrame() && !wrist.usable() && close(wrist.confidence(), .17f),
                "An uncertain in-frame landmark must retain its raw confidence");

        output[11][2] = .29f;
        require(!PoseEvidence.decode(portrait, output).hasTorsoSupport(),
                "A weak hip must not anchor the torso");

        PoseEvidence.ImageFit landscape = PoseEvidence.ImageFit.of(2, 1);
        ByteBuffer input = landscape.rgb(new int[]{0xffff0000, 0x800000ff});
        require(input.remaining() == 192 * 192 * 3 && input.get(0) == 0,
                "Model input must be a full RGB tensor with black letterbox padding");
        int firstContent = (48 * 192) * 3;
        require(Byte.toUnsignedInt(input.get(firstContent)) == 255 && input.get(firstContent + 2) == 0,
                "Opaque RGB pixels must preserve their order and magnitude");
        int lastContent = firstContent + 191 * 3;
        require(input.get(lastContent) == 0 && Byte.toUnsignedInt(input.get(lastContent + 2)) == 128,
                "Transparent RGB must be composited over the letterbox color");
        require(PoseEvidence.ImageFit.of(192, 1).rgb(new int[192]).remaining() == 192 * 192 * 3,
                "A one-pixel source axis must remain representable");

        output[0][0] = Float.NaN;
        try {
            PoseEvidence.decode(portrait, output);
            throw new AssertionError("Invalid tensor values must fail before they can be exported");
        } catch (IllegalArgumentException expected) {
            // This is an invalid tensor, not missing pose evidence.
        }
        System.out.println("MoveNet evidence contract passed");
    }

    private static boolean close(float actual, float expected) {
        return Math.abs(actual - expected) < .00001f;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
