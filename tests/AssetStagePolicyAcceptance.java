package com.asterion.foundry;

import java.io.IOException;

/** No draft workflow may accidentally grant NoWorld production approval. */
public final class AssetStagePolicyAcceptance {
    public static void main(String[] args)throws Exception{
        for(String state:new String[]{"complete","candidate"}){
            if(!AssetStagePolicy.editable(state))throw new AssertionError("Editable state blocked");
            AssetStagePolicy.requireEditable(state);
            AssetStagePolicy.requireExportReceipt(state,state);
        }
        for(String state:new String[]{"queued","running","failed","paused","missing",""}){
            if(AssetStagePolicy.editable(state))throw new AssertionError("Unfinished job became exportable");
            reject(()->AssetStagePolicy.requireEditable(state));
        }
        reject(()->AssetStagePolicy.requireExportReceipt("candidate","complete"));
        reject(()->AssetStagePolicy.requireExportReceipt("complete","candidate"));
        if(!AssetStagePolicy.candidate(true,"complete","complete"))
            throw new AssertionError("Reference reconstruction was promoted");
        if(!AssetStagePolicy.candidate(false,"candidate","complete"))
            throw new AssertionError("Editing a candidate silently promoted quality");
        if(!AssetStagePolicy.candidate(false,"complete","candidate"))
            throw new AssertionError("A candidate donor contaminated a supposedly finished assembly");
        if(AssetStagePolicy.candidate(false,"complete","complete"))
            throw new AssertionError("All completed parts were downgraded");
        if(!AssetStagePolicy.exportLabel("candidate").toLowerCase().contains("draft")
                ||!AssetStagePolicy.exportLabel("candidate").toLowerCase().contains("unverified"))
            throw new AssertionError("Candidate export was mislabeled as production ready");
        System.out.println("PASS retained image-to-3D candidates, editable draft exports and no silent NoWorld promotion");
    }
    interface Operation{void run()throws Exception;}
    private static void reject(Operation action)throws Exception{
        try{action.run();throw new AssertionError("Unsafe project state approved");}
        catch(IOException expected){}
    }
}
