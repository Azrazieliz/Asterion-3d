package com.asterion.foundry;

/**
 * Independent, non-destructive vertex-colour editing for semantic mesh parts.
 *
 * A new Mesh is returned. Geometry, UVs, normals, topology, bones, skin
 * influences, alpha and every other part remain byte-for-byte equivalent
 * at the float level. Existing base-colour textures are retained by the
 * caller and still modulate the recoloured vertex attributes in glTF.
 */
final class PartSurface {
    static final String VERSION = "part-surface-linear-rgb-v1";

    private PartSurface() {}

    static Mesh recolour(Mesh source, String id, int rgb, float strength) {
        if (source == null) throw new IllegalArgumentException("Missing editable mesh");
        if (rgb < 0 || rgb > 0xffffff)
            throw new IllegalArgumentException("RGB must contain exactly 24 bits");
        if (!Float.isFinite(strength) || strength < 0 || strength > 1)
            throw new IllegalArgumentException("Blend strength must be finite and in 0..1");
        source.validate();
        int index = -1;
        for (int i = 0; i < source.parts().size(); i++) {
            if (source.parts().get(i).id().equals(id)) {
                index = i;
                break;
            }
        }
        if (index < 0) throw new IllegalArgumentException("Selected part does not exist");
        int first = source.parts().get(index).firstVertex();
        int end = source.partEnd(index);
        float[] target = {
                linear((rgb >>> 16) & 255),
                linear((rgb >>> 8) & 255),
                linear(rgb & 255)
        };
        Mesh result = new Mesh();
        source.copyPartsTo(result);
        for (Mesh.Bone bone : source.bones) {
            result.bone(bone.name, bone.parent, bone.x, bone.y, bone.z);
        }
        for (int i = 0; i < source.vertices.size(); i++) {
            float[] vertex = source.vertices.get(i).clone();
            if (i >= first && i < end) {
                for (int channel = 0; channel < 3; channel++) {
                    int pos = 8 + channel;
                    vertex[pos] = vertex[pos] * (1f - strength) + target[channel] * strength;
                }
            }
            result.vertices.add(vertex);
        }
        result.validate();
        return result;
    }

    private static float linear(int channel) {
        float srgb = channel / 255f;
        return srgb <= 0.04045f ? srgb / 12.92f
                : (float) Math.pow((srgb + 0.055f) / 1.055f, 2.4);
    }
}
