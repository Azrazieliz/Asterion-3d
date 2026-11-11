package com.asterion.foundry;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;

/**
 * Separate named-part PBR texture maps. Each revision owns its files and SHA-pinned
 * manifest; sources, mesh, skin and sibling materials are never modified.
 * The four glTF 2.0 core channels are base_color (RGBA alpha), normal,
 * metallic_roughness (green=roughness, blue=metallic) and emissive.
 */
final class PartTextureMaps {
    static final String VERSION = "foundry-part-pbr-textures-v1";
    static final String MANIFEST = "part-textures.foundry";
    static final String FOLDER = "part-textures";
    static final String[] CHANNELS = {"base_color", "normal", "metallic_roughness", "emissive"};
    private static final int MAX_MAP_BYTES = 12 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 96L * 1024 * 1024;
    private static final byte[] PNG = {(byte)137,80,78,71,13,10,26,10};
    private final TreeMap<String,byte[]> maps;

    private PartTextureMaps(Map<String,byte[]> source) {
        maps = new TreeMap<>();
        for (var row : source.entrySet()) maps.put(row.getKey(),row.getValue().clone());
    }
    static PartTextureMaps empty() { return new PartTextureMaps(Map.of()); }
    boolean isEmpty() { return maps.isEmpty(); }
    int size() { return maps.size(); }

    static String filename(String id,String channel) {
        requireId(id);
        requireChannel(channel);
        return FOLDER + "/" + id + "-" + channel + ".png";
    }
    private static String key(String id,String channel) {
        requireId(id);requireChannel(channel);
        return id + "\t" + channel;
    }
    static void requireId(String id) {
        if (id==null || !id.matches("[a-z][a-z0-9_-]{0,63}"))
            throw new IllegalArgumentException("Invalid stable mesh part ID");
    }
    static void requireChannel(String channel) {
        if (!Arrays.asList(CHANNELS).contains(channel))
            throw new IllegalArgumentException("Unsupported glTF 2.0 PBR texture channel");
    }
    byte[] get(String id,String channel) {
        byte[] result=maps.get(key(id,channel));
        return result==null ? null : result.clone();
    }
    PartTextureMaps only(String id) {
        requireId(id);
        TreeMap<String,byte[]> selected=new TreeMap<>();
        for(var entry:maps.entrySet())
            if(entry.getKey().startsWith(id+"\t"))selected.put(entry.getKey(),entry.getValue());
        return new PartTextureMaps(selected);
    }
    PartTextureMaps change(Mesh mesh,String id,String channel,byte[] png) throws IOException {
        requireMesh(mesh);
        if(mesh.parts().stream().noneMatch(p -> p.id().equals(id)))
            throw new IllegalArgumentException("Selected mesh part does not exist");
        validatePng(png);
        TreeMap<String,byte[]> next=new TreeMap<>(maps);
        next.put(key(id,channel),png.clone());
        PartTextureMaps result=new PartTextureMaps(next);
        result.requireCompatible(mesh);
        return result;
    }
    void requireCompatible(Mesh mesh) {
        requireMesh(mesh);
        Set<String> ids=new HashSet<>();
        for(Mesh.Part p:mesh.parts())ids.add(p.id());
        for(String key:maps.keySet())
            if(!ids.contains(key.substring(0,key.indexOf('\t'))))
                throw new IllegalArgumentException("Texture map part missing from target mesh");
    }
    static void validatePng(byte[] image) throws IOException {
        if(image==null || image.length<57 || image.length>MAX_MAP_BYTES)
            throw new IOException("PNG texture is absent, truncated or exceeds 12 MiB");
        for(int i=0;i<PNG.length;i++)if(image[i]!=PNG[i])
            throw new IOException("Texture is not a PNG");
        int off=8;boolean header=false,pixels=false,end=false;
        while(off<image.length) {
            if(off+12>image.length)throw new IOException("Truncated PNG chunk");
            int length=ByteBuffer.wrap(image,off,4).order(ByteOrder.BIG_ENDIAN).getInt();
            if(length<0 || (long)off+12+length>image.length)
                throw new IOException("Invalid PNG chunk size");
            String type=new String(image,off+4,4,java.nio.charset.StandardCharsets.US_ASCII);
            CRC32 checksum=new CRC32();
            checksum.update(image,off+4,length+4);
            long expected=ByteBuffer.wrap(image,off+8+length,4)
                    .order(ByteOrder.BIG_ENDIAN).getInt() & 0xffffffffL;
            if(expected!=checksum.getValue())throw new IOException("PNG CRC mismatch: "+type);
            if(!header) {
                if(!type.equals("IHDR")||length!=13)throw new IOException("PNG missing IHDR");
                int width=ByteBuffer.wrap(image,off+8,4).getInt();
                int height=ByteBuffer.wrap(image,off+12,4).getInt();
                int depth=image[off+16]&255, colour=image[off+17]&255;
                if(width<1||height<1||width>4096||height>4096||depth!=8
                        || !(colour==2||colour==3||colour==4||colour==6))
                    throw new IOException("Only 8-bit PNG textures up to 4096 px are supported");
                header=true;
            }
            if(type.equals("IDAT"))pixels=true;
            if(type.equals("IEND")){
                if(length!=0||!pixels||off+12!=image.length)
                    throw new IOException("PNG has missing pixels or trailing data");
                end=true;
            }
            off+=12+length;
            if(end)break;
        }
        if(!end)throw new IOException("PNG image is incomplete");
    }
    static boolean potentiallyTransparent(byte[] png) {
        return png!=null && png.length>=26
                && ((png[25]&255)==4 || (png[25]&255)==6);
    }
    void save(File project,Mesh mesh) throws Exception {
        if(isEmpty())throw new IOException("Empty texture layer needs no manifest");
        requireCompatible(mesh);
        File folder=new File(project,FOLDER);
        Files.createDirectories(folder.toPath());
        StringBuilder manifest=new StringBuilder(VERSION).append('\n');
        long sum=0;
        for(var row:maps.entrySet()){
            String[] parts=row.getKey().split("\t",-1);
            byte[] bytes=row.getValue();
            validatePng(bytes);
            sum+=bytes.length;
            if(sum>MAX_TOTAL_BYTES)throw new IOException("Part texture stack exceeds 96 MiB budget");
            String name=filename(parts[0],parts[1]);
            File target=new File(project,name);
            FilesUtil.write(target,bytes);
            manifest.append(parts[0]).append('\t').append(parts[1]).append('\t')
                    .append(FilesUtil.hash(target)).append('\n');
        }
        FilesUtil.write(new File(project,MANIFEST),manifest.toString());
    }
    static PartTextureMaps load(File project,Mesh mesh,String expectedSha) throws Exception {
        if(expectedSha==null || expectedSha.isEmpty())return empty();
        File manifest=new File(project,MANIFEST);
        if(!manifest.isFile()||!expectedSha.equals(FilesUtil.hash(manifest)))
            throw new IOException("Part texture manifest checksum mismatch");
        String[] lines=FilesUtil.readString(manifest.toPath()).split("\n",-1);
        if(lines.length<3||!VERSION.equals(lines[0])||lines.length>258)
            throw new IOException("Part texture manifest schema or count invalid");
        TreeMap<String,byte[]> restored=new TreeMap<>();
        long total=0;
        for(int i=1;i<lines.length;i++){
            if(i==lines.length-1&&lines[i].isEmpty())continue;
            String[] fields=lines[i].split("\t",-1);
            if(fields.length!=3 || !fields[2].matches("[a-f0-9]{64}"))
                throw new IOException("Malformed part texture manifest entry");
            String key=key(fields[0],fields[1]);
            if(restored.containsKey(key))throw new IOException("Duplicate material texture map");
            File file=new File(project,filename(fields[0],fields[1])).getCanonicalFile();
            if(!file.getParentFile().equals(new File(project,FOLDER).getCanonicalFile())
                    || !file.isFile() || !fields[2].equals(FilesUtil.hash(file)))
                throw new IOException("Referenced material map was modified: "+key);
            byte[] bytes=Files.readAllBytes(file.toPath());
            validatePng(bytes);
            total+=bytes.length;
            if(total>MAX_TOTAL_BYTES)throw new IOException("Part texture stack exceeds 96 MiB budget");
            restored.put(key,bytes);
        }
        PartTextureMaps result=new PartTextureMaps(restored);
        result.requireCompatible(mesh);
        return result;
    }
    Map<String,File> outputs(File project) {
        TreeMap<String,File> out=new TreeMap<>();
        if(!isEmpty()){
            out.put(MANIFEST,new File(project,MANIFEST));
            for(String key:maps.keySet()){
                String[] p=key.split("\t",-1);
                String file=filename(p[0],p[1]);
                out.put(file,new File(project,file));
            }
        }
        return out;
    }
    private static void requireMesh(Mesh mesh) {
        if(mesh==null)throw new IllegalArgumentException("Missing editable mesh");
        mesh.validate();
    }
}
