package com.asterion.foundry;

/** Project revisions transform a copy and preserve source mesh topology and rig order. */
public final class MeshRevision {
    private MeshRevision() {}

    public static Mesh apply(Mesh source, float sx, float sy, float sz, Integer color) {
        for (float scale : new float[]{sx, sy, sz}) {
            if (!Float.isFinite(scale) || scale < .05f || scale > 20f) {
                throw new IllegalArgumentException("Each axis multiplier must be between 0.05 and 20");
            }
        }
        source.validate();
        Mesh result = new Mesh();
        for (Mesh.Bone bone : source.bones) {
            result.bone(bone.name, bone.parent, bone.x * sx, bone.y * sy, bone.z * sz);
        }
        for (float[] input : source.vertices) {
            float[] vertex = input.clone();
            vertex[0] *= sx;
            vertex[1] *= sy;
            vertex[2] *= sz;
            // Inverse transpose of the diagonal transform.
            float nx = vertex[3] / sx, ny = vertex[4] / sy, nz = vertex[5] / sz;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            vertex[3] = nx / length;
            vertex[4] = ny / length;
            vertex[5] = nz / length;
            if (color != null) {
                vertex[8] = linear(color >>> 16);
                vertex[9] = linear(color >>> 8);
                vertex[10] = linear(color);
            }
            result.vertices.add(vertex);
        }
        result.validate();
        return result;
    }

    private static float linear(int channel) {
        float value = (channel & 255) / 255f;
        return value <= .04045f ? value / 12.92f
                : (float) Math.pow((value + .055f) / 1.055f, 2.4);
    }
}
