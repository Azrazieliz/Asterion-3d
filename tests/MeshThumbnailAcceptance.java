package com.asterion.foundry;

import java.util.Arrays;

/** Real triangles, depth rasterization, deterministic result and interruption. */
public final class MeshThumbnailAcceptance {
    public static void main(String[] unused)throws Exception{
        Mesh sword=Recipes.create("Sword",1,12);
        MeshThumbnail.Result thumbnail=MeshThumbnail.render(sword,()->{});
        if(thumbnail.width()!=192||thumbnail.height()!=192
                ||thumbnail.argb().length!=192*192||thumbnail.visiblePixels()<70)
            throw new AssertionError("Actual geometry thumbnail was blank or mis-sized");
        int background=thumbnail.argb()[0];
        if(thumbnail.visiblePixels()>=thumbnail.argb().length)
            throw new AssertionError("Geometry thumbnail painted the whole background");
        if(Arrays.stream(thumbnail.argb()).allMatch(v->v==background))
            throw new AssertionError("Rasterized mesh is indistinguishable from background");
        MeshThumbnail.Result repeat=MeshThumbnail.render(sword,()->{});
        if(!Arrays.equals(thumbnail.argb(),repeat.argb()))
            throw new AssertionError("Same mesh was rasterized nondeterministically");
        try{
            MeshThumbnail.render(sword,()->{throw new java.io.IOException("interrupt");});
            throw new AssertionError("Thumbnail ignored thermal/interruption budget");
        }catch(java.io.IOException expected){}
        Mesh dragon=Recipes.create("Articulated dragon",1,12);
        MeshThumbnail.Result creature=MeshThumbnail.render(dragon,()->{});
        if(creature.visiblePixels()<100)
            throw new AssertionError("Creature preview had no visible geometry");
        System.out.println("PASS deterministic real-geometry thumbnail, colour, creature and budget cancellation");
    }
}
