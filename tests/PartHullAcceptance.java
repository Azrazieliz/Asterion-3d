package com.asterion.foundry;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;

public final class PartHullAcceptance {
    public static void main(String[] args) throws Exception {
        File output=new File("test-output");
        output.mkdirs();
        PartHull.Part body=new PartHull.Part("body-main","Main body","body");
        PartHull.Part hood=new PartHull.Part("hood-outer","Separate hood","garment");
        int blue=0xff2040d0,gold=0xffe8ac38;
        PartHull.Views bodyViews=new PartHull.Views(rectangle(64,8,20,29,54,blue),rectangle(48,6,18,29,54,blue),null);
        PartHull.Views hoodViews=new PartHull.Views(rectangle(64,42,58,6,22,gold),rectangle(48,30,42,6,22,gold),null);
        PartHull.Result scene=PartHull.reconstruct(List.of(body,hood),part->part.equals(body)?bodyViews:hoodViews,1,64,()->{});
        Mesh mesh=scene.mesh();
        float[] a=mesh.extractPart(0).bounds(),b=mesh.extractPart(1).bounds();
        if(a[3]>=b[0]||a[4]>=b[1]||a[2]<=b[5])throw new AssertionError("Part placement was lost in a calibrated view");
        if(!mesh.parts().stream().map(Mesh.Part::id).toList().equals(List.of(body.id(),hood.id()))
                ||scene.parts().stream().anyMatch(part->part.frontIou()!=1||part.rightIou()!=1
                ||part.topology().boundaryEdges()!=0))throw new AssertionError("Part identity or evidence was lost");
        checkAtlas(scene,0,blue);
        checkAtlas(scene,1,gold);

        File checkpoint=new File(output,"calibrated-parts.mesh");
        mesh.save(checkpoint);
        Mesh saved=Mesh.load(checkpoint);
        if(!saved.parts().equals(mesh.parts())||!MeshRevision.apply(saved,1.1f,1,1,null).parts().equals(mesh.parts())
                ||!saved.extractPart(1).parts().get(0).id().equals(hood.id())){
            throw new AssertionError("Part identity did not survive checkpoint, revision and extraction");
        }
        BufferedImage atlas=new BufferedImage(scene.width(),scene.height(),BufferedImage.TYPE_INT_ARGB);
        atlas.setRGB(0,0,scene.width(),scene.height(),scene.atlas(),0,scene.width());
        ByteArrayOutputStream png=new ByteArrayOutputStream();
        ImageIO.write(atlas,"PNG",png);
        File glb=new File(output,"calibrated_parts.glb");
        Glb.write(saved,glb,png.toByteArray());
        String bytes=new String(Files.readAllBytes(glb.toPath()),StandardCharsets.ISO_8859_1);
        if(!bytes.contains("\"foundry_part_id\":\"hood-outer\""))throw new AssertionError("Export lost the stable part ID");

        fail(()->PartHull.reconstruct(List.of(body,body),part->bodyViews,1,64,()->{}));
        fail(()->PartHull.reconstruct(List.of(body,hood),part->part.equals(body)?bodyViews:
                new PartHull.Views(rectangle(60,42,58,6,22,gold),hoodViews.right(),null),1,64,()->{}));
        fail(()->PartHull.reconstruct(List.of(body),part->new PartHull.Views(bodyViews.front(),hoodViews.right(),null),1,64,()->{}));

        File legacy=new File(output,"legacy-parts.mesh");
        try(DataOutputStream file=new DataOutputStream(new FileOutputStream(legacy))){
            file.writeInt(0x41463346);
            file.writeBoolean(false);
            file.writeInt(0);
            file.writeInt(3);
            for(int i=0;i<3;i++)for(float component:mesh.vertices.get(i))file.writeFloat(component);
            file.writeInt(1);
            file.writeUTF("Old body");file.writeUTF("body");file.writeInt(0);
        }
        if(!Mesh.load(legacy).parts().get(0).id().equals("part-1"))throw new AssertionError("Old multipart checkpoint did not migrate");
        System.out.println("Calibrated part hull passed shared placement, materials, mesh IDs and contradictory-input checks");
    }

    private static void checkAtlas(PartHull.Result scene,int index,int expected){
        Mesh mesh=scene.mesh();
        for(int i=mesh.parts().get(index).firstVertex();i<mesh.partEnd(index);i++){
            float[] v=mesh.vertices.get(i);
            if(v[6]<=0||v[6]>=1||v[7]<=0||v[7]>=1)throw new AssertionError("UV escaped the shared atlas");
            int x=(int)(v[6]*scene.width()),y=(int)(v[7]*scene.height());
            if(v[5]>.9&&scene.atlas()[y*scene.width()+x]==expected)return;
        }
        throw new AssertionError("The part lost its reference colour in the shared atlas");
    }

    private static VisualHull.View rectangle(int width,int x1,int x2,int y1,int y2,int color){
        int[] pixels=new int[width*64];
        for(int y=y1;y<y2;y++)for(int x=x1;x<x2;x++)pixels[y*width+x]=color;
        return VisualHull.View.calibrated(pixels,width,64);
    }

    private static void fail(VisualHull.Budget action) throws Exception {
        try{action.check();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError("Conflicting part evidence was accepted");
    }
}
