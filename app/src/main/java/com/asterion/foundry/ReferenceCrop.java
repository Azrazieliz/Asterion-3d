package com.asterion.foundry;

/**
 * Canonical source-sheet crop coordinates, independent of Android Views.
 * A crop never changes the original; the byte-identical original is kept by
 * SourceFiles, and the derived image has its own immutable content address.
 * Pixel boxes use half-open [left,right) x [top,bottom) coordinates.
 */
final class ReferenceCrop {
    static final String VERSION = "reference-sheet-crop-v1";
    static final int MAX_PREVIEW = 1024;
    static final int MAX_MODEL_SOURCE = 3072;
    static final int MIN_PIXELS = 64;

    record Box(int left, int top, int right, int bottom) {
        Box {
            if (left < 0 || top < 0 || right <= left || bottom <= top)
                throw new IllegalArgumentException("Invalid image crop bounds");
        }
        int width(){return right-left;}
        int height(){return bottom-top;}
        boolean isFull(int imageWidth,int imageHeight){
            return left==0 && top==0 && right==imageWidth && bottom==imageHeight;
        }
        void requireInside(int width,int height) {
            if (right>width || bottom>height || width<MIN_PIXELS || height<MIN_PIXELS)
                throw new IllegalArgumentException("Crop exceeds original image");
            if (width()<MIN_PIXELS || height()<MIN_PIXELS)
                throw new IllegalArgumentException("Crop is too small for 3D reconstruction");
        }
    }

    /** Converts the exact user-selected normalized frame into original pixels. */
    static Box fromNormalized(float l,float t,float r,float b,int width,int height){
        if(!Float.isFinite(l)||!Float.isFinite(t)||!Float.isFinite(r)||!Float.isFinite(b)
                || l<0 || t<0 || r>1 || b>1 || r<=l || b<=t)
            throw new IllegalArgumentException("Crop fractions must be within 0..1");
        Box result=new Box(Math.max(0,(int)Math.floor(l*width)),
                Math.max(0,(int)Math.floor(t*height)),
                Math.min(width,(int)Math.ceil(r*width)),
                Math.min(height,(int)Math.ceil(b*height)));
        result.requireInside(width,height);
        return result;
    }

    /** Map pointer location from fit-center preview coordinates onto source. */
    static float[] normalizedPoint(float touchX,float touchY,int viewWidth,int viewHeight,
                                   int sourceWidth,int sourceHeight){
        if(viewWidth<=0||viewHeight<=0||sourceWidth<=0||sourceHeight<=0)
            throw new IllegalArgumentException("Image preview has no measurable dimensions");
        double scale=Math.min((double)viewWidth/sourceWidth,(double)viewHeight/sourceHeight);
        double contentWidth=sourceWidth*scale,contentHeight=sourceHeight*scale;
        double offsetX=(viewWidth-contentWidth)/2.,offsetY=(viewHeight-contentHeight)/2.;
        float x=(float)((touchX-offsetX)/contentWidth),y=(float)((touchY-offsetY)/contentHeight);
        return new float[]{clamp(x),clamp(y)};
    }

    /** Expose a deliberate 3D minimum crop rather than silently scaling up tiny thumbnails. */
    static Box normalizedSelection(float[] start,float[] end,int width,int height){
        if(start==null||end==null||start.length<2||end.length<2)
            throw new IllegalArgumentException("No crop selection");
        float l=Math.min(start[0],end[0]),r=Math.max(start[0],end[0]);
        float t=Math.min(start[1],end[1]),b=Math.max(start[1],end[1]);
        return fromNormalized(l,t,r,b,width,height);
    }
    static float clamp(float value) {
        if(!Float.isFinite(value))throw new IllegalArgumentException("Invalid image point");
        return Math.max(0f,Math.min(1f,value));
    }
    private ReferenceCrop(){}
}
