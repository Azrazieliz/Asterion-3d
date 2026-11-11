package com.asterion.foundry;

/** Real geometry-independent crop coordinate tests for multi-view concept sheets. */
public final class ReferenceCropAcceptance {
    public static void main(String[] args) {
        final int width=1672,height=941;
        ReferenceCrop.Box[] views=new ReferenceCrop.Box[3];
        // Simulate the three different front/profile/back areas of a concept
        // board without ever submitting the entire board to one-view inference.
        views[0]=ReferenceCrop.fromNormalized(.11f,.09f,.37f,.95f,width,height);
        views[1]=ReferenceCrop.fromNormalized(.38f,.09f,.62f,.95f,width,height);
        views[2]=ReferenceCrop.fromNormalized(.63f,.09f,.85f,.95f,width,height);
        for(ReferenceCrop.Box view:views){
            view.requireInside(width,height);
            if(view.width()<=200||view.height()<=400)
                throw new AssertionError("Crop lost source resolution");
        }
        if(views[0].right()>views[1].left()||views[1].right()>views[2].left())
            throw new AssertionError("Different character-view crops overlap");
        for(int i=0;i<5;i++){
            ReferenceCrop.Box subject=ReferenceCrop.fromNormalized(
                    (i*.2f)+.015f,.02f,(i+1)*.2f-.01f,.70f,1536,864);
            if(subject.width()<ReferenceCrop.MIN_PIXELS||subject.isFull(1536,864))
                throw new AssertionError("Five separate character candidates collapsed to one");
        }
        ReferenceCrop.Box full=ReferenceCrop.fromNormalized(0,0,1,1,width,height);
        if(!full.isFull(width,height))throw new AssertionError("Deliberate full view changed");
        // Fit-center in a 320x320 view letterboxes the actual 16:9 sheet.
        float[] mid=ReferenceCrop.normalizedPoint(160,160,320,320,width,height);
        if(Math.abs(mid[0]-.5)>1e-6||Math.abs(mid[1]-.5)>1e-6)
            throw new AssertionError("Crop preview center does not map to source center");
        float[] top=ReferenceCrop.normalizedPoint(160,0,320,320,width,height);
        if(top[1]!=0)throw new AssertionError("Preview letterbox was misinterpreted as source");
        float[] bottom=ReferenceCrop.normalizedPoint(160,320,320,320,width,height);
        if(bottom[1]!=1)throw new AssertionError("Crop crossed the bottom of source image");
        ReferenceCrop.Box drag=ReferenceCrop.normalizedSelection(
                new float[]{.81f,.9f},new float[]{.25f,.14f},width,height);
        if(drag.left()>=drag.right()||drag.top()>=drag.bottom())
            throw new AssertionError("Reverse direction finger drag failed");
        refuse(()->ReferenceCrop.fromNormalized(0,0,.001f,.05f,width,height));
        refuse(()->ReferenceCrop.fromNormalized(-.1f,0,.5f,1,width,height));
        refuse(()->ReferenceCrop.fromNormalized(0,0,1.2f,1,width,height));
        refuse(()->ReferenceCrop.fromNormalized(Float.NaN,0,.8f,1,width,height));
        refuse(()->ReferenceCrop.normalizedSelection(null,new float[]{1,1},width,height));
        refuse(()->new ReferenceCrop.Box(0,0,10,10).requireInside(width,height));
        System.out.println("PASS 5-subject and 3-view source crops, fit-center touch mapping, immutable pixel bounds and invalid selection rejection");
    }
    private interface Check{void run();}
    private static void refuse(Check test){
        try{test.run();throw new AssertionError("Unsafe reference sheet crop accepted");}
        catch(IllegalArgumentException expected){}
    }
}
