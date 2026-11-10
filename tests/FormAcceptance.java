package com.asterion.foundry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class FormAcceptance {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("foundry-forms-");
        try {
            FormLibrary library = new FormLibrary(directory.toFile());
            String firstCharacter = FilesUtil.hash("first character");
            String secondCharacter = FilesUtil.hash("second character");
            FormLibrary.Rule general = new FormLibrary.Rule(1, 1.1f, 1, 0x302040, .55f, true);
            FormLibrary.Rule override = new FormLibrary.Rule(1.12f, 1, 1, 0x602020, .4f, true);
            FormLibrary.Profile original = library.save("Alter", "", general);
            library.save("Alter", firstCharacter, override);
            FormLibrary.Profile revised = library.save("Alter", "", override);
            FormLibrary reloaded = new FormLibrary(directory.toFile());
            if (!reloaded.available(firstCharacter).get(0).baseMeshSha().equals(firstCharacter)
                    || !reloaded.available(secondCharacter).get(0).equals(revised)
                    || !reloaded.find("Alter", "", 1).equals(original)) {
                throw new AssertionError("Profile precedence, revision persistence or history failed");
            }

            Mesh source = Recipes.create("Humanoid template", 1, 6);
            float[] before = source.vertices.get(0).clone();
            Mesh result = original.rule().apply(source);
            if (!Arrays.equals(source.vertices.get(0), before) || result.bones.size() != source.bones.size()) {
                throw new AssertionError("Source changed or rig was lost");
            }
            boolean tinted = false, headRetained = false;
            for (int i = 0; i < source.vertices.size(); i++) {
                float[] base = source.vertices.get(i), form = result.vertices.get(i);
                if (form[6] != base[6] || form[7] != base[7] || form[12] != base[12]) {
                    throw new AssertionError("Form changed UVs or skin binding");
                }
                if (source.bones.get((int) base[12]).name.equals("head")) {
                    if (form[8] != base[8] || form[9] != base[9] || form[10] != base[10]) {
                        throw new AssertionError("Form changed protected facial colours");
                    }
                    headRetained = true;
                } else if (form[8] < base[8]) {
                    tinted = true;
                }
            }
            if (!headRetained || !tinted || result.bounds()[4] <= source.bounds()[4]) {
                throw new AssertionError("Saved form rules were not applied");
            }
            if (source.bones.get(3).y == result.bones.get(3).y) throw new AssertionError("Rig was not scaled with the mesh");
            System.out.println("Forms passed revision history, base overrides and source-preserving execution");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
