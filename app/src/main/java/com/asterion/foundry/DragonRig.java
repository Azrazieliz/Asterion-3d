package com.asterion.foundry;

/** Articulated, Y-up dragon with authored proportions, skinning and metalwork. */
final class DragonRig {
    private static final float[] HIDE = {.24f, .43f, .48f};
    private static final float[] RIB = {.40f, .62f, .64f};
    private static final float[] EDGE = {.56f, .75f, .77f};
    private static final float[] SILVER = {.78f, .88f, .90f};
    private static final float[] IVORY = {.86f, .92f, .89f};
    private static final float[] MOUTH = {.11f, .12f, .21f};
    private static final float[] IRIS = {.34f, .16f, .58f};
    private static final float[] AMETHYST = {.24f, .12f, .44f};
    private static final float[] WING_SHADE = {.31f, .43f, .53f};
    private static final float[] WING_LIGHT = {.70f, .80f, .84f};
    private static final float[][] PLATE_OUTLINE = {{0, .60f}, {.43f, .28f}, {.50f, -.08f},
            {.28f, -.39f}, {0, -.53f}, {-.28f, -.39f}, {-.50f, -.08f}, {-.43f, .28f}};
    private static final Vec RIGHT = new Vec(1, 0, 0);
    private static final Vec UP = new Vec(0, 1, 0);
    private static final Vec FORWARD = new Vec(0, 0, 1);

    private final Mesh mesh = new Mesh();
    private final int sides;
    private final int subdivisions;
    private final int membraneRows;
    private final int membraneColumns;

    private DragonRig(int detail) {
        int level = Math.max(4, Math.min(24, detail));
        sides = 8 + level / 2;
        subdivisions = level < 12 ? 2 : 3;
        membraneRows = 8 + level / 3 * 2;
        membraneColumns = 4 + level / 4;
    }

    static Mesh create(int detail) {
        return new DragonRig(detail).build();
    }

    private Mesh build() {
        int root = bone("root", -1, p(0, 0, 0));
        int pelvis = bone("pelvis", root, p(0, 1.13, -.58));
        int spine = bone("spine", pelvis, p(0, 1.20, 0));
        int chest = bone("chest", spine, p(0, 1.28, .60));
        int neckBase = bone("neck_base", chest, p(0, 1.42, .92));
        int neckMid = bone("neck_mid", neckBase, p(0, 1.97, 1.20));
        int head = bone("head", neckMid, p(0, 2.39, 1.50));
        int jaw = bone("jaw", head, p(0, 2.28, 1.52));

        Vec[] tailPath = {p(0, 1.12, -.86), p(.02, 1.04, -1.15), p(.12, .87, -1.54),
                p(.29, .69, -1.97), p(.53, .55, -2.41), p(.78, .47, -2.85),
                p(.95, .49, -3.26), p(.97, .62, -3.65), p(.84, .82, -3.95), p(.58, 1.03, -4.14)};
        int[] tail = new int[tailPath.length - 2];
        for (int i = 0; i < tail.length; i++) {
            tail[i] = bone("tail_0" + (i + 1), i == 0 ? pelvis : tail[i - 1], tailPath[i + 1]);
        }
        Leg[] legs = {leg(-1, true, chest), leg(1, true, chest),
                leg(-1, false, pelvis), leg(1, false, pelvis)};
        Wing left = wing(-1, chest, pelvis), right = wing(1, chest, pelvis);

        mesh.beginPart("Dragon body", "body");
        tube(HIDE, sides, subdivisions,
                section(0, 1.10, -1.01, .16, .16, pelvis), section(0, 1.13, -.82, .35, .31, pelvis),
                section(0, 1.16, -.46, .46, .35, between(pelvis, spine, .24f)),
                section(0, 1.20, .01, .49, .38, spine), section(0, 1.25, .48, .57, .46, chest),
                section(0, 1.35, .80, .45, .39, chest), section(0, 1.44, 1.03, .22, .24, neckBase));
        tube(RIB, sides, subdivisions,
                section(0, 1.39, .72, .32, .28, chest), section(0, 1.55, .93, .32, .25, neckBase),
                section(0, 1.77, 1.05, .26, .23, between(neckBase, neckMid, .48f)),
                section(0, 1.97, 1.20, .21, .20, neckMid), section(0, 2.20, 1.36, .19, .18, between(neckMid, head, .38f)),
                section(0, 2.42, 1.52, .23, .18, head));
        tube(EDGE, sides, subdivisions,
                section(0, 2.40, 1.33, .09, .12, head), section(0, 2.44, 1.53, .27, .24, head),
                section(0, 2.43, 1.70, .31, .20, head), section(0, 2.37, 1.89, .22, .12, head),
                section(0, 2.32, 2.09, .171, .088, head), section(0, 2.315, 2.16, .114, .056, head),
                section(0, 2.289, 2.211, .037, .024, head));
        tube(EDGE, sides - 2, subdivisions,
                section(0, 2.25, 1.50, .21, .086, jaw), section(0, 2.20, 1.72, .238, .079, jaw),
                section(0, 2.19, 1.94, .178, .054, jaw), section(0, 2.215, 2.10, .095, .042, jaw),
                section(0, 2.235, 2.15, .038, .022, jaw));
        tube(MOUTH, sides - 2, 1, section(0, 2.24, 1.62, .16, .027, jaw),
                section(0, 2.225, 1.90, .14, .020, jaw), section(0, 2.241, 2.10, .080, .014, jaw));
        float[] radii = {.28f, .24f, .20f, .16f, .128f, .103f, .079f, .054f, .034f, .004f};
        Section[] tailSections = new Section[tailPath.length];
        for (int i = 0; i < tailSections.length; i++) {
            int joint = i == 0 ? pelvis : tail[Math.min(i - 1, tail.length - 1)];
            tailSections[i] = new Section(tailPath[i], radii[i], radii[i] * .85f, Bind.of(joint));
        }
        tube(HIDE, sides - 2, subdivisions, tailSections);
        for (Leg leg : legs) drawLeg(leg);
        drawWing(left);
        drawWing(right);

        mesh.beginPart("Dragon armor", "armor");
        plate(p(0, 1.63, .88), p(0, .55, .83), p(0, 1, -.2), .66f, .58f, .12f, SILVER, Bind.of(chest));
        for (int i = 0; i < 5; i++) {
            float t = i / 4f;
            Vec center = p(0, 1.37 + .78 * t, 1.15 + .30 * t);
            Bind bind = t <= .5f ? between(neckBase, neckMid, t * 2) : between(neckMid, head, (t - .5f) * 1.4f);
            plate(center, p(0, -.34, .94), UP, .24f, .42f - .15f * t, .045f, SILVER, bind);
        }
        plate(p(0, 2.58, 1.66), p(0, 1, .33), FORWARD, .72f, .37f, .14f, SILVER, Bind.of(head));
        plate(p(0, 2.36, 2.035), p(0, .89, .45), FORWARD, .34f, .27f, .06f, SILVER, Bind.of(head));
        for (int i = 0; i < 7; i++) {
            float t = i / 6f;
            Vec center = p(0, 1.45 + .24 * t, -.84 + 1.54 * t);
            Bind bind = t < .55f ? between(pelvis, spine, t / .55f) : between(spine, chest, (t - .55f) / .45f);
            plate(center, UP, FORWARD, .35f, .43f + .14f * t, .10f, SILVER, bind);
            horn(center.add(p(0, .05, 0)), center.add(p(0, .23 + t * .16, -.13)),
                    center.add(p(0, .22 + t * .19, -.31)), .108f + t * .025f, EDGE, bind);
        }
        for (int i = 1; i < tailPath.length - 1; i++) {
            Vec center = tailPath[i].add(UP.scale(radii[i] * .64f));
            Bind bind = Bind.of(tail[i - 1]);
            plate(center, UP, tailPath[i - 1].subtract(center), radii[i] * 2.5f,
                    radii[i] * 2.6f, radii[i] * .50f, SILVER, bind);
        }
        Vec tip = tailPath[tailPath.length - 1];
        plate(tip.add(p(.035, -.025, .06)), p(.13, .86, -.50), p(-.68, .25, -.80), .47f, .22f,
                .052f, SILVER, Bind.of(tail[tail.length - 1]));
        for (int side : new int[]{-1, 1}) {
            headArmor(side, head, jaw);
            bodyArmor(side, pelvis, spine, chest, neckBase, neckMid, head);
        }
        for (Leg leg : legs) legArmor(leg);
        wingArmor(left);
        wingArmor(right);

        mesh.beginPart("Eyes and crystals", "accessory");
        for (int side : new int[]{-1, 1}) {
            ellipsoid(p(side * .278, 2.478, 1.752), .051f, .042f, .105f, MOUTH, head, 7);
            ellipsoid(p(side * .305, 2.486, 1.774), .029f, .030f, .070f, IRIS, head, 6);
            ellipsoid(p(side * .326, 2.486, 1.793), .015f, .023f, .018f, AMETHYST, head, 5);
            ellipsoid(p(side * .339, 2.496, 1.805), .006f, .007f, .009f, IVORY, head, 4);
            plate(p(side * .371, 1.57, .79), p(side * .55, .36, .73), UP,
                    .23f, .17f, .072f, IRIS, Bind.of(chest));
        }
        plate(p(0, 1.66, .965), p(0, .4, .91), UP, .31f, .23f, .09f, AMETHYST, Bind.of(chest));
        return mesh;
    }

    private void headArmor(int side, int head, int jaw) {
        Vec outward = p(side, .20, .16);
        Bind skull = Bind.of(head);
        plate(p(side * .244, 2.37, 1.66), outward, FORWARD, .58f, .23f, .095f, SILVER, skull);
        plate(p(side * .225, 2.54, 1.69), p(side * .6, .8, .05), FORWARD, .39f, .22f, .061f, SILVER, skull);
        plate(p(side * .18, 2.20, 1.80), p(side, -.24, .12), FORWARD, .47f, .12f, .044f, SILVER, Bind.of(jaw));
        horn(p(side * .191, 2.57, 1.53), p(side * .30, 2.89, 1.30), p(side * .47, 3.37, 1.08), .14f, SILVER, skull);
        horn(p(side * .208, 2.48, 1.43), p(side * .35, 2.60, 1.20), p(side * .55, 2.82, .98), .10f, EDGE, skull);
        horn(p(side * .238, 2.36, 1.46), p(side * .39, 2.34, 1.28), p(side * .52, 2.47, 1.10), .088f, SILVER, skull);
        for (int i = 0; i < 5; i++) {
            double z = 1.76 + i * .076;
            double x = side * (.195 - i * .017);
            double length = i == 0 ? .102 : .061;
            horn(p(x, 2.30 - i * .010, z), p(x, 2.267 - i * .007, z + .009),
                    p(x * .97, 2.30 - i * .010 - length, z - .013), .021f, IVORY, skull);
        }
        ellipsoid(p(side * .131, 2.350, 2.105), .033f, .017f, .029f, MOUTH, head, 5);
    }

    private void bodyArmor(int side, int pelvis, int spine, int chest, int neckBase, int neckMid, int head) {
        for (int i = 0; i < 5; i++) {
            float t = i / 4f;
            float x = .36f + .135f * (float) Math.sin(t * Math.PI * .73);
            Vec center = p(side * x, 1.36 + .11 * t, -.65 + 1.37 * t);
            Bind bind = t <= .5f ? between(pelvis, spine, t * 2) : between(spine, chest, (t - .5f) * 2);
            plate(center, p(side, .29, .03), p(0, .83, -.55), .43f + .12f * t, .28f, .056f,
                    i % 2 == 0 ? SILVER : EDGE, bind);
        }
        for (int i = 0; i < 4; i++) {
            float t = i / 3f;
            Vec center = p(side * (.258 - .083 * t), 1.64 + .65 * t, 1.00 + .37 * t);
            Bind bind = t <= .5f ? between(neckBase, neckMid, t * 2) : between(neckMid, head, (t - .5f) * 2);
            plate(center, p(side, .25, -.16), p(0, .88, -.43), .31f - .085f * t, .19f - .05f * t,
                    .045f, EDGE, bind);
        }
    }

    private record Leg(int side, boolean front, int upper, int lower, int foot,
                       Vec hip, Vec knee, Vec ankle, int trunk) {}

    private Leg leg(int side, boolean front, int trunk) {
        String limb = front ? "front" : "hind", suffix = side < 0 ? "_L" : "_R";
        Vec hip = front ? p(side * .44, 1.23, .57) : p(side * .44, 1.12, -.55);
        Vec knee = front ? p(side * .65, .69, .75) : p(side * .68, .62, -.48);
        Vec ankle = front ? p(side * .54, .13, 1.16) : p(side * .58, .12, -.82);
        int upper = bone(limb + "_upper" + suffix, trunk, hip);
        int lower = bone(limb + "_lower" + suffix, upper, knee);
        int foot = bone(limb + "_foot" + suffix, lower, ankle);
        return new Leg(side, front, upper, lower, foot, hip, knee, ankle, trunk);
    }

    private void drawLeg(Leg leg) {
        float thigh = leg.front ? .214f : .285f;
        tube(HIDE, sides - 2, subdivisions,
                new Section(leg.hip.add(p(-.18 * leg.side, .13, -.035)), thigh * .72f, thigh * .78f, Bind.of(leg.trunk)),
                new Section(leg.hip, thigh, thigh * .95f, Bind.of(leg.upper)),
                new Section(leg.hip.lerp(leg.knee, .53f), thigh * .83f, thigh * .74f, Bind.of(leg.upper)),
                new Section(leg.knee, .143f, .134f, between(leg.upper, leg.lower, .55f)),
                new Section(leg.knee.lerp(leg.ankle, .48f), .111f, .102f, Bind.of(leg.lower)),
                new Section(leg.ankle.add(p(0, .075, -.018)), .08f, .083f, between(leg.lower, leg.foot, .40f)),
                new Section(leg.ankle, .098f, .084f, Bind.of(leg.foot)));
        ellipsoid(leg.ankle.add(p(0, -.015, .068)), .125f, .103f, .179f, RIB, leg.foot, Math.max(5, sides / 2));
        for (int digit = -1; digit <= 1; digit++) {
            Vec toe = leg.ankle.add(p(digit * .089, -.056, .174));
            tube(RIB, 7, 1, new Section(toe.add(p(-digit * .027, .020, -.065)), .047f, .042f, Bind.of(leg.foot)),
                    new Section(toe, .041f, .037f, Bind.of(leg.foot)),
                    new Section(toe.add(p(digit * .030, -.008, .098)), .027f, .025f, Bind.of(leg.foot)));
        }
    }

    private void legArmor(Leg leg) {
        Vec outward = p(leg.side, .15, .15);
        plate(leg.hip.lerp(leg.knee, .27f).add(p(leg.side * (leg.front ? .157 : .22), .038, .035)),
                outward, leg.hip.subtract(leg.knee), leg.front ? .49f : .59f, leg.front ? .30f : .39f,
                .085f, SILVER, Bind.of(leg.upper));
        plate(leg.knee.lerp(leg.ankle, .38f).add(p(leg.side * .076, .01, .089)),
                p(leg.side * .63, .08, .78), leg.knee.subtract(leg.ankle), .41f, .18f, .049f, EDGE, Bind.of(leg.lower));
        plate(leg.ankle.add(p(0, .067, .086)), UP, FORWARD, .24f, .20f, .052f, SILVER, Bind.of(leg.foot));
        for (int digit = -1; digit <= 1; digit++) {
            Vec toe = leg.ankle.add(p(digit * .111, -.058, .242));
            horn(toe, toe.add(p(digit * .018, -.008, .066)), toe.add(p(digit * .023, -.042, .115)),
                    .035f, IVORY, Bind.of(leg.foot));
        }
    }

    private record Wing(int side, String name, Rail leading, Rail inner, Rail[] digits) {}
    private record Rail(Vec[] points, int[] joints) {
        Vertex sample(float t) {
            float scaled = t * (points.length - 1);
            int i = Math.min((int) scaled, points.length - 2);
            float fraction = scaled - i;
            return new Vertex(points[i].lerp(points[i + 1], fraction), null, between(joints[i], joints[i + 1], fraction));
        }
    }

    private Wing wing(int side, int chest, int pelvis) {
        String name = side < 0 ? "Left" : "Right", suffix = side < 0 ? "_L" : "_R";
        Vec shoulder = p(side * .42, 1.57, .40), elbow = p(side * 1.18, 2.30, .25), wrist = p(side * 2.16, 2.81, .05);
        int upper = bone("wing_shoulder" + suffix, chest, shoulder);
        int lower = bone("wing_elbow" + suffix, upper, elbow);
        int palm = bone("wing_wrist" + suffix, lower, wrist);
        Rail leading = new Rail(new Vec[]{p(side * .30, 1.45, .45), shoulder, elbow, wrist}, new int[]{chest, upper, lower, palm});
        Rail inner = new Rail(new Vec[]{wrist, elbow, shoulder, p(side * .42, 1.10, -.84)}, new int[]{palm, lower, upper, pelvis});
        Vec[] knuckles = {p(side * 3.03, 2.54, -.42), p(side * 3.13, 2.10, -.75),
                p(side * 2.82, 1.81, -1.00), p(side * 2.33, 1.57, -1.21)};
        Vec[] ends = {p(side * 3.86, 1.76, -.89), p(side * 3.95, .77, -1.70),
                p(side * 3.42, .31, -2.45), p(side * 2.26, .58, -2.69)};
        Rail[] digits = new Rail[ends.length];
        for (int i = 0; i < digits.length; i++) {
            String digitName = "wing_digit_0" + (i + 1);
            int proximal = bone(digitName + suffix, palm, knuckles[i]);
            Vec distal = knuckles[i].lerp(ends[i], .65f);
            int tip = bone(digitName + "_tip" + suffix, proximal, distal);
            digits[i] = new Rail(new Vec[]{wrist, knuckles[i], distal, ends[i]}, new int[]{palm, proximal, tip, tip});
        }
        return new Wing(side, name, leading, inner, digits);
    }

    private void drawWing(Wing wing) {
        mesh.beginPart(wing.name + " wing frame", "body");
        spar(wing.leading, RIB, .168f, .140f, .104f, .087f);
        for (int i = 0; i < wing.digits.length; i++) {
            float thickness = .105f - .012f * i;
            spar(wing.digits[i], EDGE, thickness, thickness * .72f, thickness * .43f, .006f);
        }
        mesh.beginPart(wing.name + " wing membrane", "wing_membrane");
        for (int i = 0; i < wing.digits.length; i++) {
            Rail next = i == wing.digits.length - 1 ? wing.inner : wing.digits[i + 1];
            membrane(wing.digits[i], next, wing.side);
        }
    }

    private void spar(Rail rail, float[] color, float... radii) {
        Section[] sections = new Section[rail.points.length];
        for (int i = 0; i < sections.length; i++) {
            sections[i] = new Section(rail.points[i], radii[i], radii[i] * .75f, Bind.of(rail.joints[i]));
        }
        tube(color, sides - 2, subdivisions, sections);
    }

    private void membrane(Rail a, Rail b, int side) {
        int rows = (membraneRows + 2) / 3 * 3;
        Vertex[][] grid = new Vertex[rows + 1][membraneColumns + 1];
        for (int row = 0; row <= rows; row++) {
            float t = (float) row / rows;
            Vertex left = a.sample(t), right = b.sample(t);
            for (int col = 0; col <= membraneColumns; col++) {
                float u = (float) col / membraneColumns;
                float belly = 4 * u * (1 - u);
                double fold = .029 * belly * t * Math.sin(u * 4 * Math.PI);
                Vec shape = left.position.lerp(right.position, u)
                        .add(p(0, belly * (.27 * t * t * t - .12 * t) + fold,
                                belly * (.27 * t * t - .10 * t) + fold));
                grid[row][col] = new Vertex(shape, null, Bind.mix(left.bind, right.bind, u));
            }
        }
        // Derive normals from the membrane, retaining identical positions and weights along the spars.
        for (int row = 0; row <= rows; row++) {
            for (int col = 0; col <= membraneColumns; col++) {
                int across = Math.max(1, row);
                Vec du = grid[across][Math.min(membraneColumns, col + 1)].position
                        .subtract(grid[across][Math.max(0, col - 1)].position);
                Vec dt = grid[Math.min(rows, row + 1)][col].position.subtract(grid[Math.max(0, row - 1)][col].position);
                Vec normal = du.cross(dt).scale(side);
                Vertex original = grid[row][col];
                if (normal.dot(normal) > 1e-18) grid[row][col] = new Vertex(original.position, normal.unit(), original.bind);
            }
        }
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < membraneColumns; col++) {
                float u = (col + .5f) / membraneColumns, t = (row + .5f) / rows;
                float pleat = (float) (.11 * Math.cos(u * 4 * Math.PI));
                float[] color = tint(WING_SHADE, WING_LIGHT, .24f + .45f * t + .38f * (1 - 4 * u * (1 - u)) + pleat);
                Vertex v0 = grid[row][col], v1 = grid[row][col + 1], v2 = grid[row + 1][col + 1], v3 = grid[row + 1][col];
                if (side > 0) {
                    triangle(v0, v1, v2, color);
                    triangle(v0, v2, v3, color);
                } else {
                    triangle(v0, v2, v1, color);
                    triangle(v0, v3, v2, color);
                }
            }
        }
    }

    private void wingArmor(Wing wing) {
        Vec[] points = wing.leading.points;
        for (int i = 1; i < points.length; i++) {
            Vec center = points[i];
            Bind bind = Bind.of(wing.leading.joints[i]);
            Vec towards = center.subtract(points[i - 1]);
            plate(center.add(p(0, .045, .085)), p(0, .25, 1), towards, .47f - i * .032f,
                    .32f - i * .025f, .084f, SILVER, bind);
            horn(center.add(p(0, .039, -.011)), center.add(p(-wing.side * .018, .16, -.041)),
                    center.add(p(-wing.side * .055, .27 + .058 * i, .087)), .095f, SILVER, bind);
        }
        for (Rail digit : wing.digits) {
            Vec center = digit.points[1];
            plate(center.add(p(0, .019, .045)), p(0, .20, 1), digit.points[2].subtract(center),
                    .22f, .135f, .041f, SILVER, Bind.of(digit.joints[1]));
        }
    }

    private void horn(Vec start, Vec bend, Vec end, float width, float[] color, Bind bind) {
        tube(color, 6, 1, new Section(start, width, width * .77f, bind),
                new Section(bend, width * .54f, width * .42f, bind), new Section(end, .002f, .0015f, bind));
    }

    private void plate(Vec center, Vec normal, Vec along, float length, float width, float lift, float[] color, Bind bind) {
        Vec n = normal.unit(), axis = along.subtract(n.scale(along.dot(n))).unit();
        Vec side = n.cross(axis);
        Vertex ridge = new Vertex(center.add(n.scale(lift)).add(axis.scale(length * .12f)), null, bind);
        Vertex underside = new Vertex(center.subtract(n.scale(lift * .23f)), null, bind);
        Vertex[] edge = new Vertex[PLATE_OUTLINE.length];
        for (int i = 0; i < edge.length; i++) {
            Vec point = center.add(side.scale(PLATE_OUTLINE[i][0] * width)).add(axis.scale(PLATE_OUTLINE[i][1] * length));
            edge[i] = new Vertex(point, null, bind);
        }
        for (int i = 0; i < edge.length; i++) {
            int next = (i + 1) % edge.length;
            triangle(edge[i], edge[next], ridge, shade(color, .77f + .085f * (i % 3)));
            triangle(edge[next], edge[i], underside, shade(color, .70f));
        }
    }

    private record Section(Vec center, float width, float height, Bind bind) {}
    private record Vertex(Vec position, Vec normal, Bind bind) {}

    private void tube(float[] color, int segments, int steps, Section... controls) {
        int rings = (controls.length - 1) * steps + 1;
        Section[] sections = new Section[rings];
        for (int i = 0; i < rings; i++) {
            int index = Math.min(i / steps, controls.length - 2);
            float t = (float) (i - index * steps) / steps;
            Section a = controls[index], b = controls[index + 1];
            sections[i] = new Section(a.center.lerp(b.center, t), mix(a.width, b.width, t),
                    mix(a.height, b.height, t), Bind.mix(a.bind, b.bind, t));
        }
        Vec chord = sections[rings - 1].center.subtract(sections[0].center).unit();
        Vec reference = Math.abs(chord.x) > .77 ? UP : RIGHT;
        Vertex[][] grid = new Vertex[rings][segments];
        for (int row = 0; row < rings; row++) {
            Section section = sections[row];
            Vec tangent = sections[Math.min(rings - 1, row + 1)].center.subtract(sections[Math.max(0, row - 1)].center).unit();
            Vec radialAxis = Math.abs(reference.dot(tangent)) < .97 ? reference : FORWARD;
            Vec right = radialAxis.subtract(tangent.scale(radialAxis.dot(tangent))).unit(), up = tangent.cross(right);
            for (int i = 0; i < segments; i++) {
                double angle = 2 * Math.PI * i / segments;
                Vec a = right.scale(Math.cos(angle)), b = up.scale(Math.sin(angle));
                Vec point = section.center.add(a.scale(section.width)).add(b.scale(section.height));
                Vec normal = a.scale(1.0 / section.width).add(b.scale(1.0 / section.height)).unit();
                grid[row][i] = new Vertex(point, normal, section.bind);
            }
        }
        for (int i = 0; i < segments; i++) {
            int next = (i + 1) % segments;
            float[] stripe = shade(color, .83f + .16f * (float) Math.abs(Math.sin((i + .5) * Math.PI / segments)));
            for (int row = 0; row < rings - 1; row++) {
                triangle(grid[row][i], grid[row][next], grid[row + 1][next], stripe);
                triangle(grid[row][i], grid[row + 1][next], grid[row + 1][i], stripe);
            }
            cap(sections[0], grid[0][next], grid[0][i], color);
            cap(sections[rings - 1], grid[rings - 1][i], grid[rings - 1][next], color);
        }
    }

    private void cap(Section section, Vertex a, Vertex b, float[] color) {
        triangle(new Vertex(section.center, null, section.bind), new Vertex(a.position, null, a.bind),
                new Vertex(b.position, null, b.bind), color);
    }

    private void triangle(Vertex a, Vertex b, Vertex c, float[] color) {
        int start = mesh.vertices.size();
        mesh.tri(a.position.array(), b.position.array(), c.position.array(), color, a.bind.joints[0]);
        if (mesh.vertices.size() == start) return;
        writeVertex(start, a);
        writeVertex(start + 1, b);
        writeVertex(start + 2, c);
    }

    private void writeVertex(int index, Vertex source) {
        float[] vertex = mesh.vertices.get(index);
        if (source.normal != null) {
            vertex[3] = (float) source.normal.x;
            vertex[4] = (float) source.normal.y;
            vertex[5] = (float) source.normal.z;
        }
        Bind bind = source.bind;
        vertex[12] = bind.joints[0];
        if (bind.joints.length == 1) return;
        float[] weighted = Mesh.bindPair(vertex, bind.joints[0], bind.joints[1], bind.weights[1]);
        for (int i = 0; i < bind.joints.length; i++) {
            weighted[12 + i * 2] = bind.joints[i];
            weighted[13 + i * 2] = bind.weights[i];
        }
        mesh.vertices.set(index, weighted);
    }

    private record Bind(int[] joints, float[] weights) {
        static Bind of(int joint) { return new Bind(new int[]{joint}, new float[]{1}); }

        static Bind mix(Bind a, Bind b, float t) {
            if (t <= 0) return a;
            if (t >= 1) return b;
            if (a.joints.length == 1 && b.joints.length == 1) return between(a.joints[0], b.joints[0], t);
            int[] joints = new int[Mesh.INFLUENCES];
            float[] weights = new float[Mesh.INFLUENCES];
            int count = a.joints.length;
            for (int i = 0; i < count; i++) {
                joints[i] = a.joints[i];
                weights[i] = a.weights[i] * (1 - t);
            }
            for (int i = 0; i < b.joints.length; i++) {
                int slot = 0;
                while (slot < count && joints[slot] != b.joints[i]) slot++;
                if (slot == count) joints[count++] = b.joints[i];
                weights[slot] += b.weights[i] * t;
            }
            return new Bind(java.util.Arrays.copyOf(joints, count), java.util.Arrays.copyOf(weights, count));
        }
    }

    private static Bind between(int first, int second, float t) {
        if (t <= 0 || first == second) return Bind.of(first);
        if (t >= 1) return Bind.of(second);
        return new Bind(new int[]{first, second}, new float[]{1 - t, t});
    }

    private void ellipsoid(Vec center, float x, float y, float z, float[] color, int joint, int detail) {
        mesh.ellipsoid((float) center.x, (float) center.y, (float) center.z, x, y, z, color, joint, detail);
    }

    private static Section section(double x, double y, double z, double width, double height, int joint) {
        return section(x, y, z, width, height, Bind.of(joint));
    }

    private static Section section(double x, double y, double z, double width, double height, Bind bind) {
        return new Section(p(x, y, z), (float) width, (float) height, bind);
    }

    private int bone(String name, int parent, Vec point) {
        return mesh.bone(name, parent, (float) point.x, (float) point.y, (float) point.z);
    }

    private static Vec p(double x, double y, double z) { return new Vec(x, y, z); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

    private static float[] shade(float[] color, float shade) {
        return new float[]{color[0] * shade, color[1] * shade, color[2] * shade};
    }

    private static float[] tint(float[] a, float[] b, float blend) {
        float t = Math.max(0, Math.min(1, blend));
        return new float[]{mix(a[0], b[0], t), mix(a[1], b[1], t), mix(a[2], b[2], t)};
    }

    private record Vec(double x, double y, double z) {
        Vec add(Vec b) { return p(x + b.x, y + b.y, z + b.z); }
        Vec subtract(Vec b) { return p(x - b.x, y - b.y, z - b.z); }
        Vec scale(double value) { return p(x * value, y * value, z * value); }
        Vec lerp(Vec b, double t) { return t == 0 ? this : t == 1 ? b : add(b.subtract(this).scale(t)); }
        Vec cross(Vec b) { return p(y * b.z - z * b.y, z * b.x - x * b.z, x * b.y - y * b.x); }
        Vec unit() { return scale(1 / Math.sqrt(dot(this))); }
        double dot(Vec b) { return x * b.x + y * b.y + z * b.z; }
        float[] array() { return new float[]{(float) x, (float) y, (float) z}; }
    }
}
