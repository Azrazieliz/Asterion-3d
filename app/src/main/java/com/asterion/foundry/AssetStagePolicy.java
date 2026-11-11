package com.asterion.foundry;

import java.io.IOException;

/**
 * Workflow state contract, deliberately independent of Android.
 * A candidate may be exported or revised as an editable DRAFT, but never
 * promoted to an accepted NoWorld asset by making a material/mesh/rig edit.
 */
final class AssetStagePolicy {
    private AssetStagePolicy(){}
    static boolean editable(String state){
        return "complete".equals(state) || "candidate".equals(state);
    }
    static boolean candidate(boolean methodCandidate, String parentState, String donorState){
        return methodCandidate || "candidate".equals(parentState) || "candidate".equals(donorState);
    }
    static void requireEditable(String state) throws IOException{
        if(!editable(state))
            throw new IOException("Only a completed asset or preserved candidate draft can be revised");
    }
    static void requireExportReceipt(String state,String receiptOutcome) throws IOException{
        requireEditable(state);
        if(!state.equals(receiptOutcome))
            throw new IOException("Saved job state and receipt quality classification disagree");
    }
    static String exportLabel(String state){
        return "candidate".equals(state)?"Editable draft · reference quality unverified":
                "Editable asset · native NoWorld acceptance unverified";
    }
}
