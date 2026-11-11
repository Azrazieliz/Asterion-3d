package com.asterion.foundry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/** One named base-colour edit with the source GLB and its embedded assets preserved. */
final class OriginalMaterialVariant {
    static final String VERSION = "original-material-tint-v1";
    private static final String PBR = "pbrMetallicRoughness", FACTOR = "baseColorFactor";
    private static final int MAX_JSON = 4 * 1024 * 1024;

    record Result(int materialIndex, String materialName, double red, double green, double blue, double alpha) {}

    private OriginalMaterialVariant() {}

    static List<String> editableNames(File source) throws Exception {
        JSONObject document = OriginalGlb.document(source);
        JSONArray materials = document.optJSONArray("materials");
        List<String> names = new ArrayList<>();
        if (materials == null) return names;
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < materials.length(); i++) {
            String name = name(materials.optJSONObject(i));
            if (name != null) counts.merge(name, 1, Integer::sum);
        }
        Set<Integer> animated = animatedFactors(document);
        for (int i = 0; i < materials.length(); i++) {
            JSONObject material = materials.optJSONObject(i);
            String name = name(material);
            if (name != null && counts.get(name) == 1 && supported(material, i, animated)) names.add(name);
        }
        return names;
    }

    static Result write(File source, File output, String materialName, double red, double green, double blue)
            throws Exception {
        double[] tint = {red, green, blue};
        for (double amount : tint) {
            if (!unit(amount)) throw new IllegalArgumentException("Linear tint multipliers must be between 0 and 1");
        }
        if (materialName == null || materialName.trim().isEmpty()) {
            throw new IllegalArgumentException("An exact material name is required");
        }
        Path destination = output.getAbsoluteFile().toPath();
        if (source.getCanonicalFile().equals(output.getCanonicalFile())
                || (Files.exists(destination) && Files.isSameFile(source.toPath(), destination))) {
            throw new IOException("Material variants require a separate output file");
        }
        try (RandomAccessFile input = new RandomAccessFile(source, "r")) {
            JSONObject document = OriginalGlb.document(source);
            int index = find(document.optJSONArray("materials"), materialName);
            if (!supported(document.getJSONArray("materials").getJSONObject(index), index, animatedFactors(document))) {
                throw new IOException("This material has an unsupported base colour, material extension, or animated base colour");
            }
            input.seek(12);
            int size = Integer.reverseBytes(input.readInt());
            if (size < 2 || size > MAX_JSON) throw new IOException("Invalid source JSON size");
            input.seek(20);
            byte[] original = new byte[size];
            input.readFully(original);
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(original)).toString();
            JsonSpans json = new JsonSpans(text);
            Span materials = json.member(json.value(0), "materials");
            Span material = json.element(materials, index);
            if (!materialName.equals(json.string(json.member(material, "name")))) {
                throw new IOException("Source material changed during the read");
            }
            Span pbr = json.member(material, PBR);
            Span factor = pbr == null ? null : json.member(pbr, FACTOR);
            StringBuilder changed = new StringBuilder(text);
            double[] values = {1, 1, 1, 1};
            if (factor == null) {
                String value = "[" + decimal(red) + "," + decimal(green) + "," + decimal(blue) + ",1]";
                if (pbr == null) json.insert(changed, material, "\"" + PBR + "\":{\"" + FACTOR + "\":" + value + "}");
                else json.insert(changed, pbr, "\"" + FACTOR + "\":" + value);
                System.arraycopy(tint, 0, values, 0, tint.length);
            } else {
                for (int i = 3; i >= 0; i--) {
                    Span part = json.element(factor, i);
                    BigDecimal base = new BigDecimal(text.substring(part.start, part.end));
                    if (base.signum() < 0 || base.compareTo(BigDecimal.ONE) > 0) {
                        throw new IOException("Base colour components must be between 0 and 1");
                    }
                    BigDecimal value = i == 3 ? base : base.multiply(BigDecimal.valueOf(tint[i]));
                    values[i] = value.doubleValue();
                    if (i != 3 && tint[i] != 1) changed.replace(part.start, part.end, value.stripTrailingZeros().toString());
                }
            }
            publish(input, changed.toString().getBytes(StandardCharsets.UTF_8), destination);
            return new Result(index, materialName, values[0], values[1], values[2], values[3]);
        }
    }


    /** Non-destructive numeric PBR edit of a uniquely named source material. */
    record FactorResult(int materialIndex,String materialName,double metallic,double roughness) {}

    static FactorResult factors(File source,String materialName) throws Exception {
        JSONObject document=OriginalGlb.document(source);
        int index=find(document.optJSONArray("materials"),materialName);
        JSONObject material=document.getJSONArray("materials").getJSONObject(index);
        JSONObject pbr=material.optJSONObject(PBR);
        return new FactorResult(index,materialName,pbr==null?1:pbr.optDouble("metallicFactor",1),
                pbr==null?1:pbr.optDouble("roughnessFactor",1));
    }

    static FactorResult writePbrFactors(File source,File output,String materialName,
                                        double metallic,double roughness) throws Exception {
        if(!unit(metallic)||!unit(roughness))
            throw new IllegalArgumentException("PBR factors must be finite and from 0 to 1");
        if(materialName==null||materialName.isBlank())
            throw new IllegalArgumentException("Choose a named source material");
        if(source.getCanonicalFile().equals(output.getCanonicalFile())
                || (output.isFile()&&Files.isSameFile(source.toPath(),output.toPath())))
            throw new IOException("Source materials can only be revised in a separate file");
        JSONObject document=OriginalGlb.document(source);
        int index=find(document.optJSONArray("materials"),materialName);
        JSONObject material=document.getJSONArray("materials").getJSONObject(index);
        if(!supported(material,index,animatedFactors(document))||animatedPbr(document,index))
            throw new IOException("This material has unsupported extensions or animated PBR parameters");
        try(RandomAccessFile input=new RandomAccessFile(source,"r")){
            input.seek(12);
            int size=Integer.reverseBytes(input.readInt());
            if(size<2||size>MAX_JSON)throw new IOException("Invalid source GLB JSON chunk");
            input.seek(20);
            byte[] original=new byte[size];
            input.readFully(original);
            String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(original)).toString();
            JsonSpans json=new JsonSpans(text);
            Span materials=json.member(json.value(0),"materials");
            Span selected=json.element(materials,index);
            if(!materialName.equals(json.string(json.member(selected,"name"))))
                throw new IOException("Material selector changed during revision");
            Span pbr=json.member(selected,PBR);
            StringBuilder changed=new StringBuilder(text);
            if(pbr==null){
                json.insert(changed,selected,"\""+PBR+"\":{\"metallicFactor\":"+
                        decimal(metallic)+",\"roughnessFactor\":"+decimal(roughness)+"}");
            }else{
                Span metal=json.member(pbr,"metallicFactor");
                Span rough=json.member(pbr,"roughnessFactor");
                StringBuilder additions=new StringBuilder();
                if(metal==null)additions.append("\"metallicFactor\":").append(decimal(metallic));
                if(rough==null){
                    if(!additions.isEmpty())additions.append(',');
                    additions.append("\"roughnessFactor\":").append(decimal(roughness));
                }
                // Insert at the PBR object's closing brace first so earlier token offsets
                // for existing properties remain valid; then edit in descending order.
                if(!additions.isEmpty())json.insert(changed,pbr,additions.toString());
                if(metal!=null&&rough!=null){
                    if(metal.start()>rough.start()){
                        replaceNumber(changed,metal,metallic);
                        replaceNumber(changed,rough,roughness);
                    }else{
                        replaceNumber(changed,rough,roughness);
                        replaceNumber(changed,metal,metallic);
                    }
                }else if(metal!=null)replaceNumber(changed,metal,metallic);
                else if(rough!=null)replaceNumber(changed,rough,roughness);
            }
            publish(input,changed.toString().getBytes(StandardCharsets.UTF_8),
                    output.getAbsoluteFile().toPath());
        }
        return new FactorResult(index,materialName,metallic,roughness);
    }

    private static void replaceNumber(StringBuilder text,Span value,double desired) throws IOException {
        String before=text.substring(value.start(),value.end());
        try{
            if(!unit(new BigDecimal(before).doubleValue()))
                throw new IOException("Existing PBR factor exceeds the supported numeric range");
        }catch(NumberFormatException invalid){
            throw new IOException("Existing source PBR factor is not numeric",invalid);
        }
        text.replace(value.start(),value.end(),decimal(desired));
    }

    private static boolean animatedPbr(JSONObject document,int index) {
        JSONArray animations=document.optJSONArray("animations");
        if(animations==null)return false;
        for(int i=0;i<animations.length();i++){
            JSONObject motion=animations.optJSONObject(i);
            JSONArray channels=motion==null?null:motion.optJSONArray("channels");
            if(channels==null)continue;
            for(int j=0;j<channels.length();j++){
                JSONObject channel=channels.optJSONObject(j);
                JSONObject target=channel==null?null:channel.optJSONObject("target");
                JSONObject extensions=target==null?null:target.optJSONObject("extensions");
                JSONObject pointer=extensions==null?null:extensions.optJSONObject("KHR_animation_pointer");
                String path=pointer==null?"":pointer.optString("pointer");
                if(path.equals("/materials")||path.equals("/materials/"+index))return true;
                if(path.startsWith("/materials/"+index+"/pbrMetallicRoughness/")){
                    String affected=path.substring(("/materials/"+index+"/pbrMetallicRoughness/").length());
                    if(affected.startsWith("metallicFactor")||affected.startsWith("roughnessFactor"))
                        return true;
                }
            }
        }
        return false;
    }

    private static void publish(RandomAccessFile source, byte[] json, Path destination) throws Exception {
        int padding = (4 - json.length % 4) % 4;
        long remaining = source.length() - source.getFilePointer();
        long total = 20L + json.length + padding + remaining;
        if (json.length + padding > MAX_JSON || total > 0xffffffffL || remaining < 0) {
            throw new IOException("Material variant exceeds GLB size limits");
        }
        byte[] header = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).putInt(0x46546c67)
                .putInt(2).putInt((int) total).putInt(json.length + padding).putInt(0x4e4f534a).array();
        Path temporary = Files.createTempFile(destination.getParent(), ".material-", ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                output.write(header);
                output.write(json);
                for (int i = 0; i < padding; i++) output.write(32);
                byte[] block = new byte[65536];
                while (remaining > 0) {
                    int bytes = (int) Math.min(remaining, block.length);
                    source.readFully(block, 0, bytes);
                    output.write(block, 0, bytes);
                    remaining -= bytes;
                }
                output.getFD().sync();
            }
            OriginalGlb.document(temporary.toFile());
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String decimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toString();
    }

    private static String name(JSONObject material) {
        Object name = material == null ? null : material.opt("name");
        return name instanceof String text && !text.trim().isEmpty() ? text : null;
    }

    private static int find(JSONArray materials, String name) throws IOException {
        int index = -1;
        if (materials != null) for (int i = 0; i < materials.length(); i++) {
            if (!name.equals(name(materials.optJSONObject(i)))) continue;
            if (index != -1) throw new IOException("Ambiguous exact material name " + name);
            index = i;
        }
        if (index == -1) throw new IOException("No material named exactly " + name);
        return index;
    }

    private static boolean supported(JSONObject material, int index, Set<Integer> animated) {
        if (animated.contains(index) || animated.contains(-1) || !extensionsSupported(material, "KHR_materials_unlit")) {
            return false;
        }
        if (!material.has(PBR)) return true;
        JSONObject pbr = material.optJSONObject(PBR);
        if (pbr == null || !extensionsSupported(pbr, null)) return false;
        if (!pbr.has(FACTOR)) return true;
        JSONArray factor = pbr.optJSONArray(FACTOR);
        if (factor == null || factor.length() != 4) return false;
        for (int i = 0; i < 4; i++) {
            if (!(factor.opt(i) instanceof Number value) || !unit(value.doubleValue())) return false;
        }
        return true;
    }

    private static boolean extensionsSupported(JSONObject object, String allowed) {
        if (!object.has("extensions")) return true;
        JSONObject extensions = object.optJSONObject("extensions");
        return extensions != null && (extensions.length() == 0
                || (extensions.length() == 1 && allowed != null && extensions.optJSONObject(allowed) != null));
    }

    private static boolean unit(double amount) { return amount >= 0 && amount <= 1; }

    private static Set<Integer> animatedFactors(JSONObject document) {
        Set<Integer> indices = new HashSet<>();
        JSONArray animations = document.optJSONArray("animations");
        if (animations == null) return indices;
        for (int i = 0; i < animations.length(); i++) {
            JSONObject animation = animations.optJSONObject(i);
            JSONArray channels = animation == null ? null : animation.optJSONArray("channels");
            if (channels == null) continue;
            for (int j = 0; j < channels.length(); j++) {
                JSONObject channel = channels.optJSONObject(j);
                JSONObject target = channel == null ? null : channel.optJSONObject("target");
                JSONObject extensions = target == null ? null : target.optJSONObject("extensions");
                if (extensions == null || !extensions.has("KHR_animation_pointer")) continue;
                JSONObject pointer = extensions.optJSONObject("KHR_animation_pointer");
                Object path = pointer == null ? null : pointer.opt("pointer");
                if (!(path instanceof String text) || "/materials".equals(text)) {
                    indices.add(-1);
                    continue;
                }
                if (!text.startsWith("/materials/")) continue;
                String[] parts = text.split("/", 6);
                if (parts.length > 3 && (!parts[3].equals(PBR) || (parts.length > 4 && !parts[4].equals(FACTOR)))) continue;
                try {
                    indices.add(Integer.parseInt(parts[2]));
                } catch (NumberFormatException failure) {
                    indices.add(-1);
                }
            }
        }
        return indices;
    }

    private record Span(int start, int end) {}

    /** Locate the editable JSON tokens without normalizing other numbers, strings or extensions. */
    private static final class JsonSpans {
        private final String text;

        JsonSpans(String text) { this.text = text; }

        Span member(Span object, String name) throws Exception {
            require(object, '{');
            Span match = null;
            int p = whitespace(object.start + 1);
            while (p < object.end - 1) {
                Span key = value(p);
                String decoded = string(key);
                int colon = whitespace(key.end);
                if (text.charAt(colon) != ':') throw malformed();
                Span member = value(colon + 1);
                if (name.equals(decoded)) {
                    if (match != null) throw new IOException("Duplicate structural key " + name);
                    match = member;
                }
                p = whitespace(member.end);
                if (p == object.end - 1) break;
                if (text.charAt(p) != ',') throw malformed();
                p = whitespace(p + 1);
            }
            return match;
        }

        Span element(Span array, int index) throws IOException {
            require(array, '[');
            int p = whitespace(array.start + 1);
            for (int i = 0; p < array.end - 1; i++) {
                Span entry = value(p);
                if (i == index) return entry;
                p = whitespace(entry.end);
                if (p == array.end - 1) break;
                if (text.charAt(p) != ',') throw malformed();
                p = whitespace(p + 1);
            }
            throw malformed();
        }

        void insert(StringBuilder result, Span object, String entry) throws IOException {
            require(object, '{');
            result.insert(object.end - 1, whitespace(object.start + 1) == object.end - 1 ? entry : "," + entry);
        }

        String string(Span value) throws Exception {
            require(value, '"');
            return (String) new JSONTokener(text.substring(value.start, value.end)).nextValue();
        }

        Span value(int offset) throws IOException {
            int p = whitespace(offset);
            if (p >= text.length()) throw malformed();
            int start = p;
            char first = text.charAt(p);
            if (first == '"') return new Span(start, stringEnd(p));
            if (first == '[' || first == '{') {
                int depth = 1;
                while (++p < text.length()) {
                    char c = text.charAt(p);
                    if (c == '"') p = stringEnd(p) - 1;
                    else if (c == '[' || c == '{') depth++;
                    else if ((c == ']' || c == '}') && --depth == 0) return new Span(start, p + 1);
                }
                throw malformed();
            }
            while (p < text.length() && " \t\r\n,]}".indexOf(text.charAt(p)) < 0) p++;
            if (p == start) throw malformed();
            return new Span(start, p);
        }

        private int stringEnd(int p) throws IOException {
            while (++p < text.length()) {
                char c = text.charAt(p);
                if (c == '\\') p++;
                else if (c == '"') return p + 1;
            }
            throw malformed();
        }

        private int whitespace(int p) {
            while (p < text.length() && " \t\r\n".indexOf(text.charAt(p)) >= 0) p++;
            return p;
        }

        private void require(Span value, char first) throws IOException {
            if (value == null || text.charAt(value.start) != first) throw malformed();
        }

        private IOException malformed() { return new IOException("Invalid JSON structure in source GLB"); }
    }
}
