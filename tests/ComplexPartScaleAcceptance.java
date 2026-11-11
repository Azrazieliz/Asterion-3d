package com.asterion.foundry;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

/**
 * Structural stress—not evidence of likeness to an illustration. Checks >64
 * separate assets, 4 PBR maps per component and independently printable shells.
 */
public final class ComplexPartScaleAcceptance {
    private static final byte[] PNG=Base64.getDecoder().decode(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4b6zUAAAFXgHVnpJtqAAAAABJRU5ErkJggg==");
    public static void main(String[] unused)throws Exception {
        File dir=Files.createTempDirectory("foundry-many-parts").toFile();
        try {
            Mesh source=new Mesh();
            for(int i=0;i<128;i++){
                source.beginPart("Distinct authored component "+i,
                  i<6?"wing_membrane":i<24?"appendage":i<36?"hair":i<50?"armor":"accessory");
                source.box((i%16)*.16f,0,(i/16)*.16f,.12f,.15f,.10f,
                  new float[]{.2f,.6f,.8f},0);
            }
            source.validate();
            File original=new File(dir,"original.mesh");
            source.save(original);
            String expected=FilesUtil.hash(original);
            Mesh restored=Mesh.load(original);
            if(restored.parts().size()!=128||!source.parts().equals(restored.parts()))
                throw new AssertionError("128 stable independent part identities were lost");
            PartMaterials materials=PartMaterials.empty();
            for(int i=0;i<128;i++)
                materials=materials.change(restored,restored.parts().get(i).id(),.2,.48);
            File finishes=new File(dir,PartMaterials.FILE);
            materials.save(finishes);
            PartMaterials loaded=PartMaterials.load(finishes,restored);
            if(loaded.forPart(restored.parts().get(127)).metallic()!=.2)
                throw new AssertionError("PBR factors beyond legacy 64-part limit were discarded");
            PartTextureMaps maps=PartTextureMaps.empty();
            // Cross the old 64-part x4-map manifest cap with independently pinned bytes.
            for(int i=0;i<72;i++)
                for(String channel:PartTextureMaps.CHANNELS)
                    maps=maps.change(restored,restored.parts().get(i).id(),channel,PNG);
            maps.save(dir,restored);
            String manifest=FilesUtil.hash(new File(dir,PartTextureMaps.MANIFEST));
            PartTextureMaps revived=PartTextureMaps.load(dir,restored,manifest);
            if(revived.size()!=288)throw new AssertionError("PBR map set truncated at old limit");
            byte[] exterior=revived.get(restored.parts().get(71).id(),"emissive");
            exterior[0]=0;
            if(revived.get(restored.parts().get(71).id(),"emissive")[0]==0)
                throw new AssertionError("A caller mutated a saved map through a returned array");
            File glb=new File("test-output/complex_128_parts.glb");
            Files.createDirectories(glb.toPath().getParent());
            Glb.write(restored,glb,null,null,loaded,revived);
            byte[] exported=Files.readAllBytes(glb.toPath());
            int jsonLen=ByteBuffer.wrap(exported).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
            String json=new String(exported,20,jsonLen,StandardCharsets.UTF_8);
            if(count(json,"foundry_part_id")!=128
                  || count(json,"baseColorTexture")<72
                  || count(json,"metallicRoughnessTexture")<72
                  || count(json,"normalTexture")<72
                  || count(json,"emissiveTexture")<72)
                throw new AssertionError("128-part GLB or 288 independent texture bindings were lost");
            PrintExport.Audit one=PrintExport.inspect(restored,new PrintExport.Options(150),()->{});
            if(one.disconnectedShells()!=128||one.technicallyClosed())
                throw new AssertionError("Detached complex component assembly was silently treated as one printable solid");
            PrintExport.Audit pieces=PrintExport.inspect(restored,new PrintExport.Options(150,true),()->{});
            if(!pieces.technicallyClosed())
                throw new AssertionError("Legitimate closed independent print parts could not be exported");
            if(!expected.equals(FilesUtil.hash(original)))
                throw new AssertionError("Structural/print pipeline overwrote a source checkpoint");

            Mesh limit=new Mesh();
            for(int i=0;i<Mesh.MAX_PARTS;i++){
                limit.beginPart("Component "+i,"appendage");
                limit.box(i*.008f,0,0,.005f,.06f,.005f,new float[]{1,1,1},0);
            }
            if(limit.parts().size()!=Mesh.MAX_PARTS)throw new AssertionError("Declared limit mismatch");
            try{
                limit.beginPart("Overflow","appendage");
                throw new AssertionError("Unbounded component count accepted");
            }catch(IllegalArgumentException expectedLimit){}
            System.out.println("PASS complex structural scalability, 128 independent parts, 288 maps, strict print shell gate and 256-part cap");
        }finally {
            deleteTree(dir);
        }
    }
    private static int count(String s,String needle){
        int result=0,offset=0;
        while((offset=s.indexOf(needle,offset))>=0){result++;offset+=needle.length();}
        return result;
    }
    private static void deleteTree(File f)throws Exception {
        if(f.isDirectory())for(File c:f.listFiles())deleteTree(c);
        Files.deleteIfExists(f.toPath());
    }
}
