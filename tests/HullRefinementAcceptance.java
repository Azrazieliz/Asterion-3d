package com.asterion.foundry;

/** Source-silhouette evidence may improve, but no result claims character fidelity. */
public final class HullRefinementAcceptance {
    public static void main(String[] unused)throws Exception {
        int side=32;
        int[] front=new int[side*side],right=new int[side*side];
        for(int y=4;y<28;y++){
            for(int x=8;x<24;x++)front[y*side+x]=0xff778899;
            if(y>=15&&y<=18)continue; // intentional hole in right projection
            for(int x=11;x<21;x++)right[y*side+x]=0xff229988;
        }
        HullRefinement.Outcome result=HullRefinement.reconstruct(
                new VisualHull.View(front,side,side),
                new VisualHull.View(right,side,side),null,1,24,()->{});
        if(result.attempts()!=2||result.refined()==null)
            throw new AssertionError("A low-agreement source did not exercise second-pass refinement");
        if(result.chosen()!=result.coarse()&&result.chosen()!=result.refined())
            throw new AssertionError("Refinement fabricated a different candidate");
        if(result.promoted()&&result.refinedScore()<result.coarseScore())
            throw new AssertionError("Lower agreement was promoted");
        result.chosen().mesh().validate();

        int[] full=new int[side*side];
        for(int y=4;y<28;y++)for(int x=8;x<24;x++)full[y*side+x]=0xff77aa88;
        HullRefinement.Outcome stable=HullRefinement.reconstruct(
                new VisualHull.View(full,side,side),
                new VisualHull.View(full,side,side),null,1,24,()->{});
        if(stable.attempts()!=1||stable.promoted())
            throw new AssertionError("A high-agreement hull wasted a second inference pass");
        if(!stable.passedSilhouetteGate())
            throw new AssertionError("A valid connected fixture failed the silhouette gate");
        System.out.println("PASS bounded reference silhouette refinement, selection and no-op high-quality gate");
    }
}
