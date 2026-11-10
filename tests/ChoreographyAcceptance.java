package com.asterion.foundry;

import java.nio.FloatBuffer;

public final class ChoreographyAcceptance {
    public static void main(String[] args) {
        Mesh mesh = pair();
        scopedActorsAndDelayedStages(mesh);
        ballisticRootArc(mesh);
        restStageHoldsBindPose(mesh);
        rejectConflictingAndUnboundedChoreography(mesh);
        System.out.println("PASS long actor-scoped stages, contact, bounded validation and ballistic root motion.");
    }

    private static void scopedActorsAndDelayedStages(Mesh mesh) {
        SkeletalMotion clip = SkeletalMotion.parse(mesh, """
                clip Long duet
                actor first @0
                actor second @3
                rotate first/hand 120 0 0 1 90
                move second/body 120 -4 0 0
                contact first/hand second/hand 120 120 0.001
                stage second 120 121.4 tail-wag
                stage second 150 151.4 tail-wag
                """);
        SkeletalMotion.Validation evidence = clip.validate(mesh);
        near(151.4, evidence.durationSeconds());
        near(0, evidence.maxContactGapMetres());
        if (evidence.contacts() != 1 || evidence.samples() < 4543) {
            throw new AssertionError("Long timeline lost contact or uniform sampling " + evidence);
        }
        near(0, pose(clip, mesh, 119, 9, 2));
        near(0, pose(clip, mesh, 120, 9, 2));
        if (Math.abs(pose(clip, mesh, 120.5f, 9, 2)) < 0.1) {
            throw new AssertionError("Second actor did not wag its tail during its stage");
        }
        near(0, pose(clip, mesh, 120.5f, 3, 2));
        near(0, pose(clip, mesh, 149, 9, 2));
        near(1, pose(clip, mesh, 120, 0, 0));
        near(1, pose(clip, mesh, 120, 0, 1));
        near(2, pose(clip, mesh, 120, 6, 0));
    }

    private static void ballisticRootArc(Mesh mesh) {
        SkeletalMotion clip = SkeletalMotion.parse(mesh, """
                clip Arc with a held landing
                actor jumper @0
                move jumper/body 75 0 0 0
                ballistic jumper/body 75 77 2 9.81 0 9.81
                stage jumper 77 85 idle
                """);
        clip.validate(mesh);
        near(2, pose(clip, mesh, 74.99f, 0, 0));
        near(4, pose(clip, mesh, 76, 0, 0));
        near(4.905, pose(clip, mesh, 76, 0, 1));
        near(6, pose(clip, mesh, 77, 0, 0));
        near(0, pose(clip, mesh, 77, 0, 1));
        near(6, pose(clip, mesh, 85, 0, 0));
        SkeletalMotion.Track translation = clip.tracks().stream().filter(track -> !track.rotation()).findFirst().orElseThrow();
        if (translation.times().length != 62) throw new AssertionError("Arc missing baked export keys");
    }

    private static void restStageHoldsBindPose(Mesh mesh) {
        SkeletalMotion clip = SkeletalMotion.parse(mesh, """
                clip Turn and settle
                actor first @0
                move first/body 0 1 0 0
                rotate first/hand 0 0 0 1 90
                stage first 2 5 rest
                """);
        clip.validate(mesh);
        near(1, pose(clip, mesh, 1, 0, 1));
        near(1, pose(clip, mesh, 2, 0, 1));
        near(0, pose(clip, mesh, 3, 0, 1));
        near(3, pose(clip, mesh, 5, 0, 0));
        near(6, pose(clip, mesh, 5, 6, 0));
    }

    private static void rejectConflictingAndUnboundedChoreography(Mesh mesh) {
        reject(mesh, "actor actor @1\nmove body 1 0 0 0", "root");
        reject(mesh, "actor actor @0\nmove actor/@3 1 0 0 0", "actor");
        reject(mesh, "actor actor @0\nstage actor 0 10 tail-wag\nstage actor 9 11 idle", "overlap");
        reject(mesh, "actor actor @0\nstage actor 1 10 tail-wag\nrotate actor/tail 2 0 1 0 0", "rotation key");
        reject(mesh, "actor actor @0\nmove actor/body 1 0 0 0\nballistic actor/body 0 1 0 1 0 1", "move key");
        reject(mesh, "actor actor @0\nmove actor/body 0 0 0 0\nballistic actor/body 0 600 0 1 0 1", "offset");
        reject(mesh, "move @0 600.01 0 0 0", "600");
        reject(mesh, "actor actor @0\nstage actor 0 1 dragon-flight", "dragon");
    }

    private static Mesh pair() {
        Mesh mesh = new Mesh();
        for (int actor = 0; actor < 2; actor++) {
            float x = actor * 4;
            int body = mesh.bone("body", -1, x, 0, 0);
            triangle(mesh, x, mesh.bone("hand", body, x + 1, 0, 0));
            triangle(mesh, x, mesh.bone("tail", body, x + 1, 0, 0));
        }
        return mesh;
    }

    private static void triangle(Mesh mesh, float x, int joint) {
        mesh.tri(new float[]{x + 2, 0, 0}, new float[]{x + 3, 0, 0}, new float[]{x + 2, 0, 1},
                new float[]{1, 1, 1}, joint);
    }

    private static float pose(SkeletalMotion clip, Mesh mesh, float time, int vertex, int component) {
        FloatBuffer data = FloatBuffer.allocate(mesh.vertices.size() * 12);
        clip.playback(mesh).write(time, data);
        return data.get(vertex * 12 + component);
    }

    private static void near(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.0001 || !Double.isFinite(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void reject(Mesh mesh, String body, String explanation) {
        try {
            SkeletalMotion.parse(mesh, "clip Invalid\n" + body);
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().toLowerCase(java.util.Locale.ROOT).contains(explanation)) throw expected;
            return;
        }
        throw new AssertionError("Accepted invalid choreography " + body);
    }
}
