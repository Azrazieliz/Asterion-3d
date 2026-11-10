package com.asterion.foundry;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Matches a preserved GLB part to a caller-authored source profile for NoWorld review. */
final class NoWorldHandoff {
    static final String PROFILE_SCHEMA = "asterion.foundry.noworld.glb-handoff.v1";
    static final String NOWORLD_REVISION = "77fd363eb9d5f94d82a2f334f12b91d122da594b";
    private static final String[] BINDING_FIELDS = {
            "identity_id", "version_id", "manifestation_id", "wardrobe_id", "part_id",
            "native_mesh", "native_skeleton", "shared_motion_library"
    };

    private NoWorldHandoff() {}

    static JSONObject validate(File source, File authoredProfile) throws Exception {
        byte[] profileBytes = readProfile(authoredProfile);
        try {
            JSONTokener json = new JSONTokener(StandardCharsets.UTF_8.newDecoder()
                    .decode(ByteBuffer.wrap(profileBytes)).toString());
            JSONObject profile = new JSONObject(json);
            require(json.nextClean() == 0, "Trailing data after the NoWorld profile");
            require(PROFILE_SCHEMA.equals(profile.get("schema")), "Wrong NoWorld profile schema");
            require(NOWORLD_REVISION.equals(profile.get("noworld_revision")), "Unknown noworld_revision");
            String sourceHash = text(profile.get("source_sha256"), "source_sha256");
            require(sourceHash.matches("[0-9a-f]{64}") && sourceHash.equals(FilesUtil.hash(source)),
                    "source_sha256 does not match the exact GLB bytes");

            String identity = text(profile.get("identity_id"), "identity_id");
            String version = text(profile.get("version_id"), "version_id");
            require(canonicalCharacterId(identity), "identity_id must be an authored character ID");
            require(canonicalCharacterId(version), "version_id must be an authored character ID");
            if (profile.get("manifestation_id") != JSONObject.NULL) {
                text(profile.get("manifestation_id"), "manifestation_id");
            }
            text(profile.get("wardrobe_id"), "wardrobe_id");
            text(profile.get("part_id"), "part_id");
            Set<String> paths = new HashSet<>();
            for (String field : new String[]{"native_mesh", "native_skeleton", "shared_motion_library"}) {
                String path = text(profile.get(field), field);
                require(nativePath(path) && paths.add(path.toLowerCase(Locale.ROOT)),
                        field + " must be a distinct top-level /Game object path");
            }

            JSONArray boneNames = profile.getJSONArray("bone_names");
            JSONArray boneParents = profile.getJSONArray("bone_parent_indices");
            JSONArray rest = profile.getJSONArray("reference_local_transforms");
            JSONArray morphNames = profile.getJSONArray("morph_targets");
            JSONArray materials = profile.getJSONArray("material_bindings");
            require(profile.length() == BINDING_FIELDS.length + 8, "Unknown fields in NoWorld profile");
            uniqueNames(boneNames, "bone_names");
            uniqueNames(morphNames, "morph_targets");

            JSONObject document = OriginalGlb.document(source);
            checkRig(document, boneNames, boneParents, rest);
            checkPrimitives(document, morphNames, materials);

            JSONObject result = new JSONObject()
                    .put("schema", "asterion.foundry.noworld.glb-handoff-receipt.v1")
                    .put("status", "SOURCE_PROFILE_MATCHED")
                    .put("profile_schema", PROFILE_SCHEMA)
                    .put("profile_provenance", "caller_supplied")
                    .put("noworld_revision", NOWORLD_REVISION)
                    .put("source_sha256", sourceHash)
                    .put("profile_sha256", FilesUtil.hex(MessageDigest.getInstance("SHA-256").digest(profileBytes)))
                    .put("bone_count", boneNames.length()).put("morph_count", morphNames.length())
                    .put("primitive_count", materials.length())
                    .put("canonical_binding_verified", false)
                    .put("upstream_admission_executed", false)
                    .put("unreal_import_executed", false).put("native_acceptance", false);
            for (String field : BINDING_FIELDS) result.put(field, profile.get(field));
            return result;
        } catch (JSONException error) {
            throw new IOException("Malformed NoWorld profile or GLB declaration: " + error.getMessage(), error);
        }
    }

    private static void checkRig(JSONObject document, JSONArray names, JSONArray expectedParents,
                                 JSONArray rest) throws Exception {
        JSONArray skins = document.getJSONArray("skins");
        require(skins.length() == 1 && document.getJSONArray("meshes").length() == 1,
                "NoWorld source profile requires one mesh and one skin");
        JSONObject skin = skins.getJSONObject(0);
        JSONArray joints = skin.getJSONArray("joints");
        int count = joints.length();
        require(count > 0 && names.length() == count, "bone_names count does not match the GLB skin");
        require(expectedParents.length() == count, "bone_parent_indices count does not match the GLB skin");
        require(rest.length() == count, "reference_local_transforms must include every bone");

        JSONArray nodes = document.getJSONArray("nodes");
        int[] parents = new int[nodes.length()];
        int[] order = new int[nodes.length()];
        Arrays.fill(parents, -1);
        Arrays.fill(order, -1);
        int skinnedNodes = 0;
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (node.has("mesh") || node.has("skin")) {
                require(node.has("mesh") && node.has("skin")
                        && integer(node.get("mesh"), "mesh") == 0 && integer(node.get("skin"), "skin") == 0,
                        "The GLB mesh node must use the declared skin");
                skinnedNodes++;
            }
            JSONArray children = array(node, "children");
            for (int j = 0; j < children.length(); j++) {
                int child = index(children.get(j), nodes, "node child");
                require(child != i && parents[child] == -1, "GLB node parent is ambiguous");
                parents[child] = i;
            }
        }
        require(skinnedNodes == 1, "NoWorld source profile requires one skinned mesh node");
        for (int i = 0; i < count; i++) {
            int node = index(joints.get(i), nodes, "skin joint");
            require(order[node] == -1, "GLB skin repeats a joint");
            order[node] = i;
        }
        int root = index(joints.get(0), nodes, "skin root");
        int declaredRoot = skin.has("skeleton") ? index(skin.get("skeleton"), nodes, "skin skeleton") : -1;
        boolean foundDeclaredRoot = declaredRoot == -1 || declaredRoot == root;
        for (int ancestor = parents[root], traversed = 0; ancestor >= 0; ancestor = parents[ancestor]) {
            require(traversed++ < nodes.length() && order[ancestor] == -1, "GLB skin root has a cyclic or joint parent");
            foundDeclaredRoot |= ancestor == declaredRoot;
        }
        require(foundDeclaredRoot, "GLB skin skeleton is outside its joint hierarchy");

        for (int i = 0; i < count; i++) {
            int nodeIndex = index(joints.get(i), nodes, "skin joint");
            JSONObject node = nodes.getJSONObject(nodeIndex);
            require(names.get(i).equals(node.get("name")), "bone_names differ at joint " + i);
            int parent = parents[nodeIndex] < 0 ? -1 : order[parents[nodeIndex]];
            int expected = integer(expectedParents.get(i), "bone_parent_indices");
            require(parent == expected && (i == 0 ? parent == -1 : parent >= 0 && parent < i),
                    "bone_parent_indices differ or are not root-first at joint " + i);
            checkRest(node, rest.getJSONObject(i), "reference_local_transforms[" + i + "]");
        }
        if (skin.has("inverseBindMatrices")) {
            JSONObject accessor = item(document.getJSONArray("accessors"), skin.get("inverseBindMatrices"),
                    "inverse bind accessor");
            require("MAT4".equals(accessor.get("type"))
                    && integer(accessor.get("componentType"), "inverse bind component") == 5126
                    && integer(accessor.get("count"), "inverse bind count") >= count,
                    "GLB inverse bind accessor does not match the skin");
        }
    }

    private static void checkRest(JSONObject node, JSONObject expected, String field) throws Exception {
        if (expected.has("matrix")) {
            require(expected.length() == 1 && node.has("matrix") && !node.has("translation")
                    && !node.has("rotation") && !node.has("scale"), field + " matrix representation differs");
            compareVector(expected.getJSONArray("matrix"), node.getJSONArray("matrix"), 16, field);
            return;
        }
        require(expected.length() == 3 && !node.has("matrix"), field + " TRS representation differs");
        compareVector(expected.getJSONArray("translation"), node.has("translation")
                ? node.getJSONArray("translation") : new JSONArray("[0,0,0]"), 3, field + " translation");
        JSONArray rotation = node.has("rotation") ? node.getJSONArray("rotation") : new JSONArray("[0,0,0,1]");
        compareVector(expected.getJSONArray("rotation"), rotation, 4, field + " rotation");
        double norm = 0;
        for (int i = 0; i < 4; i++) {
            double component = finite(rotation.get(i), field);
            norm += component * component;
        }
        require(Math.abs(norm - 1) <= 0.0001, field + " rotation must be normalized");
        JSONArray scale = node.has("scale") ? node.getJSONArray("scale") : new JSONArray("[1,1,1]");
        compareVector(expected.getJSONArray("scale"), scale, 3, field + " scale");
        for (int i = 0; i < 3; i++) {
            require(finite(scale.get(i), field) != 0, field + " scale cannot be zero");
        }
    }

    private static void compareVector(JSONArray expected, JSONArray source, int size, String field)
            throws Exception {
        require(expected.length() == size && source.length() == size, field + " vector size differs");
        for (int i = 0; i < size; i++) {
            require(finite(expected.get(i), field) == finite(source.get(i), field),
                    field + " differs at component " + i);
        }
    }

    private static void checkPrimitives(JSONObject document, JSONArray names, JSONArray bindings) throws Exception {
        JSONObject mesh = document.getJSONArray("meshes").getJSONObject(0);
        JSONObject extras = mesh.optJSONObject("extras");
        JSONArray targetNames = extras == null ? new JSONArray() : array(extras, "targetNames");
        require(targetNames.length() == names.length(), "morph_targets count does not match named GLB targets");
        for (int i = 0; i < names.length(); i++) {
            require(names.get(i).equals(targetNames.get(i)), "morph_targets name differs at target " + i);
        }
        JSONArray primitives = mesh.getJSONArray("primitives");
        JSONArray materials = document.getJSONArray("materials");
        JSONArray accessors = document.getJSONArray("accessors");
        require(primitives.length() > 0 && bindings.length() == primitives.length(),
                "material_bindings must name every primitive material");
        for (int i = 0; i < primitives.length(); i++) {
            JSONObject primitive = primitives.getJSONObject(i);
            JSONObject material = item(materials, primitive.get("material"), "material_bindings");
            require(text(bindings.get(i), "material_bindings").equals(material.get("name")),
                    "material_bindings name differs at primitive " + i);
            JSONObject attributes = primitive.getJSONObject("attributes");
            JSONObject position = item(accessors, attributes.get("POSITION"), "position accessor");
            int vertices = integer(position.get("count"), "position count");
            require(vertices > 0 && "VEC3".equals(position.get("type")), "GLB position accessor is invalid");
            JSONObject joints = checkAttribute(accessors, attributes, "JOINTS_0", "VEC4", vertices, "joints");
            int jointType = integer(joints.get("componentType"), "joints component type");
            require((jointType == 5121 || jointType == 5123) && !normalized(joints),
                    "GLB joints require unsigned unnormalized components");
            JSONObject weights = checkAttribute(accessors, attributes, "WEIGHTS_0", "VEC4", vertices, "weights");
            int weightType = integer(weights.get("componentType"), "weights component type");
            require(normalized(weights) ? weightType == 5121 || weightType == 5123 : weightType == 5126,
                    "GLB weights require float or normalized unsigned components");
            JSONArray targets = array(primitive, "targets");
            require(targets.length() == names.length(), "morph_targets are incomplete at primitive " + i);
            for (int target = 0; target < targets.length(); target++) {
                JSONObject deltas = targets.getJSONObject(target);
                require(deltas.length() > 0, "GLB morph target has no declared deltas");
                var semantics = deltas.keys();
                while (semantics.hasNext()) {
                    String semantic = (String) semantics.next();
                    require(semantic.equals("POSITION") || semantic.equals("NORMAL") || semantic.equals("TANGENT"),
                            "Unsupported GLB morph target attribute " + semantic);
                    require(attributes.has(semantic), "GLB morph target has no matching base " + semantic);
                    checkAttribute(accessors, deltas, semantic, "VEC3", vertices, "morph");
                }
            }
        }
    }

    private static JSONObject checkAttribute(JSONArray accessors, JSONObject attributes, String key, String type,
                                            int count, String field) throws Exception {
        require(attributes.has(key), "Missing GLB " + field + " accessor");
        JSONObject accessor = item(accessors, attributes.get(key), field + " accessor");
        require(type.equals(accessor.get("type")) && integer(accessor.get("count"), field + " count") == count,
                "GLB " + field + " accessor does not match the primitive");
        return accessor;
    }

    private static boolean normalized(JSONObject accessor) throws Exception {
        Object normalized = accessor.opt("normalized");
        require(normalized == null || normalized instanceof Boolean, "GLB accessor normalized must be boolean");
        return Boolean.TRUE.equals(normalized);
    }

    private static JSONArray array(JSONObject object, String key) throws JSONException {
        return object.has(key) ? object.getJSONArray(key) : new JSONArray();
    }

    private static JSONObject item(JSONArray array, Object value, String field) throws Exception {
        return array.getJSONObject(index(value, array, field));
    }

    private static int index(Object value, JSONArray array, String field) throws IOException {
        int index = integer(value, field);
        require(index >= 0 && index < array.length(), "GLB " + field + " index is out of range");
        return index;
    }

    private static int integer(Object value, String field) throws IOException {
        double number = finite(value, field);
        int integer = (int) number;
        require(integer == number, field + " must be an integer");
        return integer;
    }

    private static double finite(Object value, String field) throws IOException {
        require(value instanceof Number && Double.isFinite(((Number) value).doubleValue()),
                field + " must be a finite number");
        return ((Number) value).doubleValue();
    }

    private static void uniqueNames(JSONArray names, String field) throws Exception {
        Set<String> folded = new HashSet<>();
        for (int i = 0; i < names.length(); i++) {
            require(folded.add(text(names.get(i), field).toLowerCase(Locale.ROOT)),
                    field + " contains a duplicate name");
        }
    }

    private static String text(Object value, String field) throws IOException {
        require(value instanceof String, field + " must be explicit text");
        String text = (String) value;
        require(!text.isEmpty() && text.equals(text.trim()) && text.codePoints().noneMatch(Character::isISOControl),
                field + " must be explicit text without surrounding whitespace or control characters");
        return text;
    }

    private static boolean canonicalCharacterId(String id) {
        return id.startsWith("character:") && id.length() > "character:".length()
                && id.codePoints().noneMatch(Character::isWhitespace);
    }

    private static boolean nativePath(String path) {
        return path.matches("/Game(?:/[A-Za-z0-9_]+)+\\.[A-Za-z0-9_]+")
                && path.substring(path.lastIndexOf('/') + 1, path.indexOf('.'))
                        .equals(path.substring(path.indexOf('.') + 1));
    }

    private static byte[] readProfile(File file) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long bytes = input.length();
            require(bytes > 0 && bytes <= 4L * 1024 * 1024, "Invalid or oversized NoWorld profile");
            byte[] profile = new byte[(int) bytes];
            input.readFully(profile);
            require(input.read() == -1, "NoWorld profile changed while reading");
            return profile;
        }
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
