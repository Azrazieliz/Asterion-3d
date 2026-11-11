package com.asterion.foundry;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

/** Per-part map isolation, byte-integrity and actual glTF material/image bindings. */
public final class PartTextureMapsAcceptance {
    private static final byte[] RGBA = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4b6zUAAAFXgHVnpJtqAAAAABJRU5ErkJggg==");
    public static void main(String[] ignored) throws Exception {
        File temp=Files.createTempDirectory("foundry-pbr-texture-").toFile();
        try {
            Mesh original=Recipes.create("Humanoid with separate tunic",1,12);
            if(original.parts().size()<2)throw new AssertionError("Expected separate parts");
            String first=original.parts().get(0).id(),second=original.parts().get(1).id();
            File parent=new File(temp,"parent.mesh");
            original.save(parent);
            String immutable=FilesUtil.hash(parent);
            PartTextureMaps start=PartTextureMaps.empty();
            PartTextureMaps changed=start.change(original,second,"normal",RGBA);
            changed=changed.change(original,second,"metallic_roughness",RGBA);
            changed=changed.change(original,second,"emissive",RGBA);
            changed=changed.change(original,second,"base_color",RGBA);
            if(changed.get(first,"normal")!=null||start.get(second,"normal")!=null)
                throw new AssertionError("Part map changed sibling or old revision");
            changed.save(temp,original);
            String sha=FilesUtil.hash(new File(temp,PartTextureMaps.MANIFEST));
            PartTextureMaps reloaded=PartTextureMaps.load(temp,original,sha);
            if(reloaded.size()!=4 || !java.util.Arrays.equals(reloaded.get(second,"normal"),RGBA))
                throw new AssertionError("Part map persistence lost content");
            if(!immutable.equals(FilesUtil.hash(parent)))
                throw new AssertionError("PBR editing modified the parent mesh");
            File glb=new File(temp,"pbr.glb");
            Glb.write(original,glb,null,null,PartMaterials.empty(),reloaded);
            byte[] file=Files.readAllBytes(glb.toPath());
            int length=ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
            String json=new String(file,20,length,StandardCharsets.UTF_8);
            if(!json.contains("\"normalTexture\":{\"index\":")
                    ||!json.contains("\"metallicRoughnessTexture\":{\"index\":")
                    ||!json.contains("\"emissiveTexture\":{\"index\":")
                    ||!json.contains("\"baseColorTexture\":{\"index\":")
                    ||!json.contains("\"alphaMode\":\"BLEND\"")
                    ||!json.contains("\"images\":[")
                    ||!json.contains("\"emissiveFactor\":[1,1,1]"))
                throw new AssertionError("Independent PBR maps absent from GLB material");
            File part=new File(temp,"part.glb");
            Glb.write(original.extractPart(1),part,null,null,PartMaterials.empty(),reloaded.only(second));
            if(!part.isFile())throw new AssertionError("Per-part GLB export missing");
            byte[] altered=RGBA.clone();altered[altered.length-5]^=1;
            try {
                PartTextureMaps.validatePng(altered);
                throw new AssertionError("Tampered PNG CRC was accepted");
            }catch(java.io.IOException expected){}
            FilesUtil.write(new File(temp,PartTextureMaps.filename(second,"normal")),altered);
            try{
                PartTextureMaps.load(temp,original,sha);
                throw new AssertionError("Tampered saved map was accepted");
            }catch(java.io.IOException expected){}
            final PartTextureMaps finalMaps=changed;
            reject(()->finalMaps.change(original,second,"opacity",RGBA));
            reject(()->finalMaps.change(original,"missing","normal",RGBA));
            System.out.println("PASS 4-channel per-part PNG maps, manifest, corrupt-source rejection, glTF export and immutable parent");
        }finally{
            cleanup(temp);
        }
    }
    private static void cleanup(File f)throws Exception{
        if(f.isDirectory())for(File child:f.listFiles())cleanup(child);
        Files.deleteIfExists(f.toPath());
    }
    private interface Operation{void run() throws Exception;}
    private static void reject(Operation op)throws Exception{
        try{op.run();throw new AssertionError("Invalid texture edit was accepted");}
        catch(IllegalArgumentException expected){}
    }
}
