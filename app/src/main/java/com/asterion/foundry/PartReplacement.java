package com.asterion.foundry;

import java.util.Arrays;

/**
 * Exact part-local geometry transfer. No retargeting or visual fit is inferred.
 * The donor and target must share an identical named skeleton and rest pose;
 * the caller must separately prove the colour atlas is identical or absent.
 */
final class PartReplacement {
    static final String VERSION = "part-geometry-transfer-exact-rig-v1";
    private PartReplacement() {}

    static boolean sameRig(Mesh a, Mesh b) {
        if (a == null || b == null || a.bones.size() != b.bones.size()) return false;
        for (int i = 0; i < a.bones.size(); i++) {
            Mesh.Bone x = a.bones.get(i), y = b.bones.get(i);
            if (!x.name.equals(y.name) || x.parent != y.parent
                    || Float.floatToIntBits(x.x) != Float.floatToIntBits(y.x)
                    || Float.floatToIntBits(x.y) != Float.floatToIntBits(y.y)
                    || Float.floatToIntBits(x.z) != Float.floatToIntBits(y.z)) return false;
        }
        return true;
    }

    static Mesh replace(Mesh target, String targetId, Mesh donor, String donorId) {
        if (target == null || donor == null) throw new IllegalArgumentException("Both editable meshes are required");
        target.validate();
        donor.validate();
        if (!sameRig(target, donor))
            throw new IllegalArgumentException("Part donor rig or rest pose differs; explicit retargeting required");
        int targetIndex = index(target, targetId), donorIndex = index(donor, donorId);
        Mesh.Part selected = target.parts().get(targetIndex);
        Mesh.Part incoming = donor.parts().get(donorIndex);
        if (!selected.role().equals(incoming.role()))
            throw new IllegalArgumentException("Part roles differ; replacement requires a matching role");
        long expected = (long) target.vertices.size()
                - (target.partEnd(targetIndex) - selected.firstVertex())
                + donor.partEnd(donorIndex) - incoming.firstVertex();
        if (expected > Mesh.LIMIT || expected % 3 != 0)
            throw new IllegalArgumentException("Replacement exceeds editable mesh limits");
        Mesh result = new Mesh();
        for (Mesh.Bone bone : target.bones)
            result.bone(bone.name, bone.parent, bone.x, bone.y, bone.z);
        for (int i = 0; i < target.parts().size(); i++) {
            Mesh.Part part = target.parts().get(i);
            result.beginPart(part.id(), part.name(), part.role());
            Mesh source = i == targetIndex ? donor : target;
            int start = i == targetIndex ? incoming.firstVertex() : part.firstVertex();
            int end = i == targetIndex ? donor.partEnd(donorIndex) : target.partEnd(i);
            for (int v = start; v < end; v++)
                result.vertices.add(Arrays.copyOf(source.vertices.get(v), source.vertices.get(v).length));
        }
        result.validate();
        return result;
    }

    private static int index(Mesh mesh, String id) {
        for (int i = 0; i < mesh.parts().size(); i++)
            if (mesh.parts().get(i).id().equals(id)) return i;
        throw new IllegalArgumentException("Exact part ID was not found in its editable mesh");
    }
}
