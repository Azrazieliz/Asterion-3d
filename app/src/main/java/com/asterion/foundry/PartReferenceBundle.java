package com.asterion.foundry;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.json.JSONArray;
import org.json.JSONObject;

/** Reads immutable, independently labelled part views without extracting ZIP paths. */
final class PartReferenceBundle {
    private static final int MANIFEST_LIMIT=16*1024, IMAGE_LIMIT=3*1024*1024;
    private static final String SCHEMA="asterion.foundry.part-references.v1";
    private static final String MANIFEST="manifest.json";

    static PartHull.Result reconstruct(File source,float heightMetres,int resolution,VisualHull.Budget budget)
            throws Exception {
        if(source==null)throw new IOException("Choose a part reference ZIP");
        try(ZipFile archive=new ZipFile(source)){
            JSONObject manifest=new JSONObject(new String(bytes(archive,MANIFEST,MANIFEST_LIMIT),StandardCharsets.UTF_8));
            if(!SCHEMA.equals(manifest.getString("schema")))throw new IOException("Unsupported part reference schema");
            JSONArray descriptions=manifest.getJSONArray("parts");
            if(descriptions.length()<1||descriptions.length()>PartHull.MAX_PARTS)throw new IOException("Use one to eight parts");
            List<PartHull.Part> parts=new ArrayList<>();
            Set<String> allowed=new HashSet<>(Set.of(MANIFEST,"parts/"));
            for(int i=0;i<descriptions.length();i++){
                JSONObject description=descriptions.getJSONObject(i);
                PartHull.Part part=new PartHull.Part(description.getString("id"),description.getString("name"),description.getString("role"));
                parts.add(part);
                allowed.add("parts/"+part.id()+"/");
                for(String view:List.of("front","right","back"))allowed.add(path(part,view));
            }
            Set<String> seen=new HashSet<>();
            for(Enumeration<? extends ZipEntry> all=archive.entries();all.hasMoreElements();){
                ZipEntry entry=all.nextElement();
                String name=entry.getName();
                if(!allowed.contains(name)||!seen.add(name))throw new IOException("Unexpected or repeated ZIP entry");
                if(entry.isDirectory()!=name.endsWith("/"))throw new IOException("Invalid ZIP entry type");
            }
            return PartHull.reconstruct(parts,part->new PartHull.Views(view(archive,part,"front"),view(archive,part,"right"),
                    archive.getEntry(path(part,"back"))==null?null:view(archive,part,"back")),heightMetres,resolution,budget);
        }
    }

    private static String path(PartHull.Part part,String view){return "parts/"+part.id()+"/"+view+".png";}

    private static VisualHull.View view(ZipFile archive,PartHull.Part part,String role) throws IOException {
        String path=path(part,role);
        byte[] png=bytes(archive,path,IMAGE_LIMIT);
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(png,0,png.length,options);
        if(!"image/png".equals(options.outMimeType)||options.outWidth<4||options.outWidth>512
                ||options.outHeight<4||options.outHeight>512){
            throw new IOException("Use PNG part views from 4 to 512 pixels per axis: "+path);
        }
        options.inJustDecodeBounds=false;
        options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap image=BitmapFactory.decodeByteArray(png,0,png.length,options);
        if(image==null)throw new IOException("Unreadable part PNG: "+path);
        try{
            int width=image.getWidth(),height=image.getHeight();
            int[] pixels=new int[width*height];
            image.getPixels(pixels,0,width,0,0,width,height);
            return VisualHull.View.calibrated(pixels,width,height);
        }finally{image.recycle();}
    }

    private static byte[] bytes(ZipFile archive,String name,int limit) throws IOException {
        ZipEntry entry=archive.getEntry(name);
        if(entry==null||entry.isDirectory())throw new IOException("Missing part reference entry: "+name);
        if(entry.getSize()<1||entry.getSize()>limit)throw new IOException("Part reference entry exceeds its size budget: "+name);
        try(InputStream input=archive.getInputStream(entry)){
            byte[] contents=input.readNBytes((int)entry.getSize());
            if(contents.length!=entry.getSize()||input.read()!=-1)throw new IOException("Part reference entry size mismatch: "+name);
            return contents;
        }
    }
}
