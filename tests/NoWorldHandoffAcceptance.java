package com.asterion.foundry;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;

public final class NoWorldHandoffAcceptance {
    private static final String PROFILE_SCHEMA = "asterion.foundry.noworld.glb-handoff.v1";
    private static final String NOWORLD_REVISION = "77fd363eb9d5f94d82a2f334f12b91d122da594b";

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("foundry-noworld-test-");
        File source = directory.resolve("body.glb").toFile();
        File profile = directory.resolve("body.json").toFile();
        try {
            JSONObject document = sourceDocument();
            byte[] original = glb(document);
            Files.write(source.toPath(), original);
            JSONObject authored = profile(FilesUtil.hash(source));
            byte[] originalProfile = authored.toString(2).getBytes(StandardCharsets.UTF_8);
            Files.write(profile.toPath(), originalProfile);
            JSONObject report = NoWorldHandoff.validate(source, profile);
            if (report.getBoolean("native_acceptance") || report.getBoolean("unreal_import_executed")
                    || report.getBoolean("canonical_binding_verified")
                    || !"character:fixture".equals(report.getString("identity_id"))
                    || !"character:fixture:original".equals(report.getString("version_id"))
                    || !"fixture-manifestation".equals(report.getString("manifestation_id"))
                    || !FilesUtil.hash(source).equals(report.getString("source_sha256"))
                    || !FilesUtil.hash(profile).equals(report.getString("profile_sha256"))
                    || !Arrays.equals(original, Files.readAllBytes(source.toPath()))
                    || !Arrays.equals(originalProfile, Files.readAllBytes(profile.toPath()))) {
                throw new AssertionError("Local handoff proof or original bytes changed");
            }

            rejectsProfile(source, profile, authored, "source_sha256", "0".repeat(64));
            rejectsProfile(source, profile, authored, "noworld_revision", "0".repeat(40));
            rejectsProfile(source, profile, authored, "identity_id", "fixture");
            rejectsProfile(source, profile, authored, "native_skeleton", "/Game/Fixture/A.B");
            rejectsProfile(source, profile, authored, "shared_motion_library", "AUTO");
            rejectsProfile(source, profile, authored, "bone_names", new JSONArray("[\"Root\",\"Spare\"]"));
            rejectsProfile(source, profile, authored, "bone_parent_indices", new JSONArray("[-1,1]"));
            rejectsProfile(source, profile, authored, "morph_targets", new JSONArray("[\"Blink\"]"));
            rejectsProfile(source, profile, authored, "material_bindings", new JSONArray("[\"Hair\"]"));

            JSONArray wrongRest = new JSONArray(authored.getJSONArray("reference_local_transforms").toString());
            wrongRest.getJSONObject(1).getJSONArray("translation").put(1, 0.25);
            rejectsProfile(source, profile, authored, "reference_local_transforms", wrongRest);
            JSONArray missingRest = new JSONArray().put(authored.getJSONArray("reference_local_transforms").getJSONObject(0));
            rejectsProfile(source, profile, authored, "reference_local_transforms", missingRest);

            JSONObject differentGraph = new JSONObject(document.toString());
            differentGraph.getJSONArray("nodes").getJSONObject(0).put("children", new JSONArray("[2]"));
            rejectsSource(source, profile, differentGraph, authored, "parent");

            JSONObject unnamedMorph = new JSONObject(document.toString());
            unnamedMorph.getJSONArray("meshes").getJSONObject(0).remove("extras");
            rejectsSource(source, profile, unnamedMorph, authored, "morph");

            JSONObject unskinned = new JSONObject(document.toString());
            unskinned.getJSONArray("nodes").getJSONObject(2).remove("skin");
            rejectsSource(source, profile, unskinned, authored, "skin");

            JSONObject noWeights = new JSONObject(document.toString());
            primitive(noWeights).getJSONObject("attributes").remove("WEIGHTS_0");
            rejectsSource(source, profile, noWeights, authored, "weights");

            JSONObject wrongWeights = new JSONObject(document.toString());
            primitive(wrongWeights).getJSONObject("attributes").put("WEIGHTS_0", 1);
            rejectsSource(source, profile, wrongWeights, authored, "weights");

            JSONObject outOfRangeMorph = new JSONObject(document.toString());
            primitive(outOfRangeMorph).getJSONArray("targets").getJSONObject(0).put("POSITION", 12);
            rejectsSource(source, profile, outOfRangeMorph, authored, "morph");

            JSONObject noMorph = new JSONObject(document.toString());
            noMorph.getJSONArray("meshes").getJSONObject(0).remove("extras");
            primitive(noMorph).remove("targets");
            JSONArray rootMatrix = new JSONArray("[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]");
            noMorph.getJSONArray("nodes").getJSONObject(0).put("matrix", rootMatrix);
            Files.write(source.toPath(), glb(noMorph));
            JSONObject unowned = new JSONObject(authored.toString())
                    .put("source_sha256", FilesUtil.hash(source)).put("manifestation_id", JSONObject.NULL)
                    .put("morph_targets", new JSONArray());
            unowned.getJSONArray("reference_local_transforms").put(0, new JSONObject().put("matrix", rootMatrix));
            Files.writeString(profile.toPath(), unowned.toString());
            if (!NoWorldHandoff.validate(source, profile).isNull("manifestation_id")) {
                throw new AssertionError("Unowned source acquired a Manifestation");
            }

            System.out.println("NoWorldHandoffAcceptance passed");
        } finally {
            Files.deleteIfExists(profile.toPath());
            Files.deleteIfExists(source.toPath());
            Files.deleteIfExists(directory);
        }
    }

    private static void rejectsProfile(File source, File file, JSONObject authored, String key, Object value)
            throws Exception {
        JSONObject changed = new JSONObject(authored.toString()).put(key, value);
        Files.writeString(file.toPath(), changed.toString());
        reject(source, file, key);
    }

    private static void rejectsSource(File source, File file, JSONObject changed, JSONObject authored,
                                      String expectedField) throws Exception {
        Files.write(source.toPath(), glb(changed));
        JSONObject updatedHash = new JSONObject(authored.toString()).put("source_sha256", FilesUtil.hash(source));
        Files.writeString(file.toPath(), updatedHash.toString());
        reject(source, file, expectedField);
    }

    private static void reject(File source, File file, String expectedField) throws Exception {
        try {
            NoWorldHandoff.validate(source, file);
            throw new AssertionError("Local handoff passed a mismatch in " + expectedField);
        } catch (IOException error) {
            if (!error.getMessage().toLowerCase().contains(expectedField.toLowerCase())) {
                throw new AssertionError("Wrong rejection for " + expectedField, error);
            }
        }
    }

    private static JSONObject profile(String sourceSha256) throws Exception {
        return new JSONObject("""
                {
                    "identity_id":"character:fixture",
                    "version_id":"character:fixture:original",
                    "manifestation_id":"fixture-manifestation",
                    "wardrobe_id":"OriginalWardrobe",
                    "part_id":"Body",
                    "native_mesh":"/Game/Fixture/SK_Body.SK_Body",
                    "native_skeleton":"/Game/Fixture/Original_Skeleton.Original_Skeleton",
                    "shared_motion_library":"/Game/Fixture/SharedMotion.SharedMotion",
                    "bone_names":["Root","Jaw"],
                    "bone_parent_indices":[-1,0],
                    "reference_local_transforms":[
                        {"translation":[0,0,0],"rotation":[0,0,0,1],"scale":[1,1,1]},
                        {"translation":[0,0.125,0],"rotation":[0,0,0,1],"scale":[1,1,1]}
                    ],
                    "morph_targets":["Smile"],
                    "material_bindings":["Skin"]
                }
                """).put("schema", PROFILE_SCHEMA).put("noworld_revision", NOWORLD_REVISION)
                .put("source_sha256", sourceSha256);
    }

    private static JSONObject sourceDocument() throws Exception {
        return new JSONObject("""
                {
                    "asset":{"version":"2.0"},
                    "buffers":[{"byteLength":260}],
                    "bufferViews":[
                        {"buffer":0,"byteOffset":0,"byteLength":36},
                        {"buffer":0,"byteOffset":36,"byteLength":12},
                        {"buffer":0,"byteOffset":48,"byteLength":48},
                        {"buffer":0,"byteOffset":96,"byteLength":36},
                        {"buffer":0,"byteOffset":132,"byteLength":128}
                    ],
                    "accessors":[
                        {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3",
                         "min":[0,0,0],"max":[1,1,0]},
                        {"bufferView":1,"componentType":5121,"count":3,"type":"VEC4"},
                        {"bufferView":2,"componentType":5126,"count":3,"type":"VEC4"},
                        {"bufferView":3,"componentType":5126,"count":3,"type":"VEC3",
                         "min":[0,0,0],"max":[0,0.125,0]},
                        {"bufferView":4,"componentType":5126,"count":2,"type":"MAT4"}
                    ],
                    "nodes":[
                        {"name":"Root","children":[1]},
                        {"name":"Jaw","translation":[0,0.125,0]},
                        {"name":"OriginalBody","mesh":0,"skin":0}
                    ],
                    "skins":[{"skeleton":0,"joints":[0,1],"inverseBindMatrices":4}],
                    "materials":[{"name":"Skin"}],
                    "meshes":[{
                        "extras":{"targetNames":["Smile"]},
                        "primitives":[{
                            "attributes":{"POSITION":0,"JOINTS_0":1,"WEIGHTS_0":2},
                            "targets":[{"POSITION":3}],"material":0
                        }]
                    }],
                    "scene":0,"scenes":[{"nodes":[0,2]}]
                }
                """);
    }

    private static JSONObject primitive(JSONObject document) throws Exception {
        return document.getJSONArray("meshes").getJSONObject(0).getJSONArray("primitives").getJSONObject(0);
    }

    private static byte[] glb(JSONObject document) {
        byte[] text = document.toString().getBytes(StandardCharsets.UTF_8);
        int padded = (text.length + 3) & ~3;
        ByteBuffer file = ByteBuffer.allocate(28 + padded + 260).order(ByteOrder.LITTLE_ENDIAN);
        file.putInt(0x46546c67).putInt(2).putInt(file.capacity());
        file.putInt(padded).putInt(0x4e4f534a).put(text);
        for (int i = text.length; i < padded; i++) file.put((byte) ' ');
        file.putInt(260).putInt(0x004e4942);
        for (float coordinate : new float[]{0,0,0,1,0,0,0,1,0}) file.putFloat(coordinate);
        file.put(new byte[12]);
        for (int i = 0; i < 3; i++) file.putFloat(1).putFloat(0).putFloat(0).putFloat(0);
        for (float delta : new float[]{0,0,0,0,0.125f,0,0,0,0}) file.putFloat(delta);
        for (int bone = 0; bone < 2; bone++) {
            for (int i = 0; i < 16; i++) {
                file.putFloat(i % 5 == 0 ? 1 : bone == 1 && i == 13 ? -0.125f : 0);
            }
        }
        return file.array();
    }
}
