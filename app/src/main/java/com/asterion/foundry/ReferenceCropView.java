package com.asterion.foundry;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Direct preview of the actual preserved image, with a visible selection.
 * One deliberate finger drag selects ONE subject/view; no implicit full-sheet
 * inference. Never saves a raster without SourceFiles' hash-locked derivation.
 */
final class ReferenceCropView extends View {
    private final Bitmap preview;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF frame=new RectF(),selection=new RectF();
    private float[] anchor,point;
    private boolean selected;
    ReferenceCropView(Context context,Bitmap image) {
        super(context);
        if(image==null)throw new IllegalArgumentException("No reference preview");
        preview=image;
        setContentDescription("Original reference image. Drag from the top left to bottom right of one subject or one orthographic view.");
    }
    boolean hasSelection(){return selected;}
    ReferenceCrop.Box selectedBox(int sourceWidth,int sourceHeight){
        if(!selected||anchor==null||point==null)
            throw new IllegalArgumentException("Drag a rectangle around exactly one character or view first");
        return ReferenceCrop.normalizedSelection(anchor,point,sourceWidth,sourceHeight);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        canvas.drawColor(0xff0c1624);
        float margin=14*getResources().getDisplayMetrics().density;
        float width=getWidth()-margin*2,height=getHeight()-margin*2;
        if(width<=0||height<=0)return;
        float scale=Math.min(width/preview.getWidth(),height/preview.getHeight());
        float w=preview.getWidth()*scale,h=preview.getHeight()*scale;
        float left=(getWidth()-w)/2f,top=(getHeight()-h)/2f;
        frame.set(left,top,left+w,top+h);
        paint.setColor(Color.WHITE);
        canvas.drawBitmap(preview,null,frame,paint);
        if(selected && anchor!=null&&point!=null){
            float sx=frame.left+Math.min(anchor[0],point[0])*frame.width();
            float ex=frame.left+Math.max(anchor[0],point[0])*frame.width();
            float sy=frame.top+Math.min(anchor[1],point[1])*frame.height();
            float ey=frame.top+Math.max(anchor[1],point[1])*frame.height();
            selection.set(sx,sy,ex,ey);
            paint.setColor(0x8c061321);
            canvas.drawRect(frame.left,frame.top,frame.right,sy,paint);
            canvas.drawRect(frame.left,ey,frame.right,frame.bottom,paint);
            canvas.drawRect(frame.left,sy,sx,ey,paint);
            canvas.drawRect(ex,sy,frame.right,ey,paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f*getResources().getDisplayMetrics().density);
            paint.setColor(0xff80e6ca);
            canvas.drawRect(selection,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xff80e6ca);
            float radius=4*getResources().getDisplayMetrics().density;
            canvas.drawCircle(sx,sy,radius,paint);
            canvas.drawCircle(ex,ey,radius,paint);
        }
    }
    private float[] position(float x,float y){
        if(frame.isEmpty())return new float[]{0f,0f};
        return new float[]{
            ReferenceCrop.clamp((x-frame.left)/frame.width()),
            ReferenceCrop.clamp((y-frame.top)/frame.height())
        };
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                if(!frame.contains(event.getX(),event.getY()))return false;
                anchor=position(event.getX(),event.getY());
                point=anchor.clone();
                selected=true;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if(!selected)return false;
                point=position(event.getX(),event.getY());
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if(!selected)return false;
                point=position(event.getX(),event.getY());
                invalidate();
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                selected=false;invalidate();return true;
        }
        return super.onTouchEvent(event);
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDetachedFromWindow(){
        super.onDetachedFromWindow();
        if(!preview.isRecycled())preview.recycle();
    }
}
