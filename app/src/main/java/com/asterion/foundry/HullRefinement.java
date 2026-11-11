package com.asterion.foundry;

/**
 * Conservatively chooses the higher-evidence of at most two visual-hull passes.
 * Scores are orthographic silhouette overlap, NOT human likeness, texture
 * quality, hidden-anatomy fidelity, deformation or engine acceptance.
 * The input views are retained by the caller and neither mesh is mutated.
 */
final class HullRefinement {
    static final String VERSION="bounded-hull-silhouette-refinement-v1";
    private static final double MIN_IMPROVEMENT=0.005;
    private static final double REFINE_BELOW=0.97;

    record Outcome(VisualHull.Result chosen, VisualHull.Result coarse,
                   VisualHull.Result refined, double coarseScore,
                   double refinedScore, boolean promoted) {
        int attempts(){return refined==null?1:2;}
        boolean passedSilhouetteGate(){
            return minAgreement(chosen)>=0.84 && chosen.components()<=3;
        }
    }

    private HullRefinement(){}

    static Outcome reconstruct(VisualHull.View front,VisualHull.View right,VisualHull.View back,
                               float size,int detail,VisualHull.Budget budget)throws Exception {
        VisualHull.Result coarse=VisualHull.reconstruct(front,right,back,size,detail,budget);
        double coarseScore=agreement(coarse);
        if(detail>=64||minAgreement(coarse)>=REFINE_BELOW){
            return new Outcome(coarse,coarse,null,coarseScore,Double.NaN,false);
        }
        budget.check();
        int detail2=Math.min(64,Math.max(detail+8,(int)Math.ceil(detail*1.5)));
        VisualHull.Result refined=VisualHull.reconstruct(front,right,back,size,detail2,budget);
        double fineScore=agreement(refined);
        // Never accept a nominally better view score by creating far more isolated
        // disconnected pieces; this protects segmented or unusually narrow shapes.
        boolean promote=fineScore>=coarseScore+MIN_IMPROVEMENT
                && refined.components()<=Math.max(3,coarse.components());
        return new Outcome(promote?refined:coarse,coarse,refined,
                coarseScore,fineScore,promote);
    }

    private static double agreement(VisualHull.Result mesh){
        double score=mesh.frontIou()+mesh.rightIou();
        int count=2;
        if(mesh.backIou()!=null){score+=mesh.backIou();count++;}
        return score/count;
    }

    private static double minAgreement(VisualHull.Result result) {
        double value=Math.min(result.frontIou(),result.rightIou());
        return result.backIou()==null?value:Math.min(value,result.backIou());
    }
}
