package com.asterion.foundry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class DragonRigAcceptance {
    public static void main(String[] args) {
        Mesh mesh = create(12);
        mesh.validate();
        Map<String, Integer> joints = new HashMap<>();
        for (int i = 0; i < mesh.bones.size(); i++) {
            require(joints.put(mesh.bones.get(i).name, i) == null, "Repeated joint name");
        }
        parent(mesh, joints, "jaw", "head");
        parent(mesh, joints, "tail_08", "tail_07");
        for (String side : new String[]{"L", "R"}) {
            parent(mesh, joints, "front_foot_" + side, "front_lower_" + side);
            parent(mesh, joints, "hind_foot_" + side, "hind_lower_" + side);
            parent(mesh, joints, "wing_wrist_" + side, "wing_elbow_" + side);
            for (int digit = 1; digit <= 4; digit++) {
                String name = "wing_digit_0" + digit;
                parent(mesh, joints, name + "_" + side, "wing_wrist_" + side);
                parent(mesh, joints, name + "_tip_" + side, name + "_" + side);
            }
            require(hasSharedMembrane(mesh, joints, side), "Membrane does not bridge finger bindings on " + side);
        }
        boolean[] used = new boolean[mesh.bones.size()];
        for (float[] vertex : mesh.vertices) {
            for (int slot = 0; slot < Mesh.INFLUENCES; slot++) {
                if (Mesh.weight(vertex, slot) > 0) used[Mesh.joint(vertex, slot)] = true;
            }
        }
        for (int joint = 1; joint < used.length; joint++) {
            require(used[joint], "Joint has no visible geometry: " + mesh.bones.get(joint).name);
        }
        float[] bounds = mesh.bounds();
        require(bounds[3] - bounds[0] > 7 && bounds[5] - bounds[2] > 5,
                "Wings and tail do not extend beyond the torso");
        require(bounds[1] >= -0.02f && bounds[1] <= 0.08f, "Feet are not grounded in the rest pose");
        Mesh low = create(4), high = create(24), repeat = create(12);
        require(low.vertices.size() < mesh.vertices.size() && mesh.vertices.size() < high.vertices.size()
                && high.vertices.size() <= 60_000, "Detail levels exceed the mobile triangle budget");
        require(mesh.vertices.size() == repeat.vertices.size(), "Non-deterministic vertex count");
        for (int i = 0; i < mesh.vertices.size(); i++) {
            require(Arrays.equals(mesh.vertices.get(i), repeat.vertices.get(i)), "Non-deterministic vertex data");
        }
        System.out.println("Dragon rig acceptance passed (" + mesh.bones.size() + " joints, "
                + mesh.vertices.size() / 3 + " triangles at standard detail)");
    }

    private static Mesh create(int detail) {
        return Recipes.create("Articulated dragon", 1, detail);
    }

    private static boolean hasSharedMembrane(Mesh mesh, Map<String, Integer> joints, String side) {
        int first = joints.get("wing_digit_02_" + side);
        int second = joints.get("wing_digit_03_" + side);
        for (int part = 0; part < mesh.parts().size(); part++) {
            Mesh.Part membrane = mesh.parts().get(part);
            if (!membrane.role().equals("wing_membrane")) continue;
            for (int i = membrane.firstVertex(); i < mesh.partEnd(part); i++) {
                float[] vertex = mesh.vertices.get(i);
                boolean usesFirst = false, usesSecond = false;
                for (int slot = 0; slot < Mesh.INFLUENCES; slot++) {
                    if (Mesh.weight(vertex, slot) <= .05f) continue;
                    int joint = Mesh.joint(vertex, slot);
                    usesFirst |= joint == first;
                    usesSecond |= joint == second;
                }
                if (usesFirst && usesSecond) return true;
            }
        }
        return false;
    }

    private static void parent(Mesh mesh, Map<String, Integer> joints, String child, String parent) {
        require(joints.containsKey(child) && joints.containsKey(parent)
                && mesh.bones.get(joints.get(child)).parent == joints.get(parent),
                child + " is not attached to " + parent);
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
