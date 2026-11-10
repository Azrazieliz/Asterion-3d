package com.asterion.foundry;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class OriginalMaterialVariantAcceptance {
    private static final String FACTOR = "[0.5, 0.4, 0.3, 0.43210000000000000000001]";
    private static final String JSON = """
            {
              "asset": {"version": "2.0", "extras":{"exactDecimal":0.123456789123456789123456789}},
              "buffers": [{"byteLength":129}],
              "bufferViews": [{"buffer":0,"byteOffset":0,"byteLength":129}],
              "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"}],
              "skins":[{"joints":[0]}],
              "nodes":[{"name":"Root"},{"mesh":0,"skin":0}],
              "meshes":[{"primitives":[{"attributes":{"POSITION":0},"material":0,"targets":[{"POSITION":0}]}]}],
              "extensionsUsed":["KHR_animation_pointer","KHR_materials_unlit"],
              "animations":[{"samplers":[{"input":0,"output":0}],"channels":[{"sampler":0,"target":{"path":"pointer","extensions":{"KHR_animation_pointer":{"pointer":"/materials/2/pbrMetallicRoughness/baseColorFactor/1"}}}}]}],
              "materi\\u0061ls": [
                {"name":"Hair A", "extensions":{"KHR_materials_unlit":{}}, "alphaMode":"MASK", "alphaCutoff":0.7,
                 "pbrMetallicRoughness":{"metallicFactor":0.17,"baseColorFactor":%s,"baseColorTexture":{"index":0}}},
                {"name":"Default colour", "extras":{"keep":"end bracket } inside a string"}},
                {"name":"Animated colour"},
                {"name":"Extension material","extensions":{"OTHER_tint":{}}},
                {"name":"Duplicate"},{"name":"Duplicate"},{"extras":{"unnamed":true}}
              ],
              "textures":[{"source":0}],"images":[{"mimeType":"image/png","bufferView":0}]
            }
            """.formatted(FACTOR);

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("foundry-material-test-");
        File source = temp.resolve("source.glb").toFile();
        File output = temp.resolve("tinted.glb").toFile();
        File alias = temp.resolve("alias.glb").toFile();
        byte[] binary = new byte[132];
        for (int i = 0; i < 129; i++) binary[i] = (byte) (i * 13 + 79);
        byte[] original = glb(JSON, binary);
        try {
            Files.write(source.toPath(), original);
            if (!OriginalMaterialVariant.editableNames(source).equals(List.of("Hair A", "Default colour"))) {
                throw new AssertionError("Material list included ambiguous or unsupported selections");
            }
            OriginalMaterialVariant.Result result = OriginalMaterialVariant.write(source, output, "Hair A", 0.25, 0.5, 1);
            if (result.materialIndex() != 0 || !result.materialName().equals("Hair A")
                    || result.red() != 0.125 || result.green() != 0.2 || result.blue() != 0.3 || result.alpha() != 0.4321) {
                throw new AssertionError("Tint did not multiply linear RGB and retain alpha: " + result);
            }
            byte[] tinted = Files.readAllBytes(output.toPath());
            String expected = JSON.replace(FACTOR, "[0.125, 0.2, 0.3, 0.43210000000000000000001]");
            if (!json(tinted).stripTrailing().equals(expected.stripTrailing())
                    || !Arrays.equals(binaryChunk(tinted), binaryChunk(original))) {
                throw new AssertionError("Rewrite changed untouched JSON or the embedded BIN chunk");
            }
            OriginalGlb.document(output);
            reject(() -> OriginalMaterialVariant.write(source, output, "hair a", 1, 1, 1));
            reject(() -> OriginalMaterialVariant.write(source, output, "Duplicate", 1, 1, 1));
            reject(() -> OriginalMaterialVariant.write(source, output, "Animated colour", 1, 1, 1));
            reject(() -> OriginalMaterialVariant.write(source, output, "Extension material", 1, 1, 1));
            reject(() -> OriginalMaterialVariant.write(source, output, "Hair A", Double.NaN, 1, 1));
            reject(() -> OriginalMaterialVariant.write(source, output, "Hair A", 1, 1.1, 1));
            reject(() -> OriginalMaterialVariant.write(source, source, "Hair A", 1, 1, 1));
            Files.createLink(alias.toPath(), source.toPath());
            reject(() -> OriginalMaterialVariant.write(source, alias, "Hair A", 1, 1, 1));
            if (!Arrays.equals(tinted, Files.readAllBytes(output.toPath()))
                    || !Arrays.equals(original, Files.readAllBytes(source.toPath()))) {
                throw new AssertionError("A failed variant changed an existing file");
            }
            OriginalMaterialVariant.Result defaults = OriginalMaterialVariant.write(source, output, "Default colour", 1, 0.75, 0);
            if (defaults.materialIndex() != 1 || defaults.alpha() != 1
                    || !json(Files.readAllBytes(output.toPath())).contains("\"baseColorFactor\":[1,0.75,0,1]")) {
                throw new AssertionError("Absent PBR did not use the glTF default factor");
            }
            System.out.println("OriginalMaterialVariantAcceptance passed exact JSON, BIN, alpha and source preservation");
        } finally {
            Files.deleteIfExists(alias.toPath());
            Files.deleteIfExists(output.toPath());
            Files.deleteIfExists(source.toPath());
            Files.deleteIfExists(temp);
        }
    }

    private static byte[] glb(String json, byte[] binary) {
        byte[] text = json.getBytes(StandardCharsets.UTF_8);
        int padding = (4 - text.length % 4) % 4;
        ByteBuffer result = ByteBuffer.allocate(28 + text.length + padding + binary.length).order(ByteOrder.LITTLE_ENDIAN);
        result.putInt(0x46546c67).putInt(2).putInt(result.capacity()).putInt(text.length + padding).putInt(0x4e4f534a).put(text);
        for (int i = 0; i < padding; i++) result.put((byte) 32);
        return result.putInt(binary.length).putInt(0x004e4942).put(binary).array();
    }

    private static String json(byte[] bytes) {
        return new String(bytes, 20, jsonLength(bytes), StandardCharsets.UTF_8);
    }

    private static byte[] binaryChunk(byte[] bytes) {
        return Arrays.copyOfRange(bytes, 20 + jsonLength(bytes), bytes.length);
    }

    private static int jsonLength(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
    }

    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }

    private static void reject(CheckedAction action) throws Exception {
        try {
            action.run();
        } catch (IOException | IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Unsupported material variant was accepted");
    }
}
