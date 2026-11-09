package com.asterion.foundry;
import android.content.*;import android.graphics.Bitmap;import android.graphics.BitmapFactory;import java.io.File;import android.opengl.*;import android.view.*;import java.nio.*;import javax.microedition.khronos.egl.EGLConfig;import javax.microedition.khronos.opengles.GL10;
/** Hardware preview; compute backend claims remain independent of rendering. */
public final class MeshView extends GLSurfaceView implements GLSurfaceView.Renderer {
 FloatBuffer data;int count,program,textureId;final File texture;float[] bounds;volatile float yaw=25,pitch=18,zoom=1;float px,py;int width=1,height=1;
 public MeshView(Context c,Mesh m){this(c,m,null);}
 public MeshView(Context c,Mesh m,File texture){super(c);this.texture=texture;setEGLContextClientVersion(2);count=m.vertices.size();data=ByteBuffer.allocateDirect(count*12*4).order(ByteOrder.nativeOrder()).asFloatBuffer();for(float[] v:m.vertices)data.put(v,0,12);data.position(0);bounds=m.bounds();setRenderer(this);setRenderMode(RENDERMODE_WHEN_DIRTY);}
 int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] ok={0};GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(s));return s;}
 public void onSurfaceCreated(GL10 gl,EGLConfig config){program=GLES20.glCreateProgram();int vs=shader(GLES20.GL_VERTEX_SHADER,"uniform mat4 mvp;uniform mat4 model;attribute vec3 p;attribute vec3 n;attribute vec4 c;attribute vec2 uv;varying vec4 col;varying vec2 st;void main(){st=uv;vec3 nn=normalize(mat3(model)*n);float l=0.3+0.7*max(dot(nn,normalize(vec3(0.4,0.8,1.0))),0.0);col=vec4(c.rgb*l,1.0);gl_Position=mvp*vec4(p,1.0);}"),fs=shader(GLES20.GL_FRAGMENT_SHADER,"precision mediump float;varying vec4 col;varying vec2 st;uniform sampler2D atlas;uniform bool textured;void main(){gl_FragColor=textured?col*texture2D(atlas,st):col;}");GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program);GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glClearColor(.055f,.07f,.10f,1);
  textureId=0;
  if(texture!=null&&texture.isFile()){
   Bitmap bitmap=BitmapFactory.decodeFile(texture.getAbsolutePath());
   if(bitmap!=null){
    int[] names={0};
    GLES20.glGenTextures(1,names,0);
    textureId=names[0];
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textureId);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);
    bitmap.recycle();
   }
  }
 }
 public void onSurfaceChanged(GL10 gl,int w,int h){width=w;height=h;GLES20.glViewport(0,0,w,h);}
 public void onDrawFrame(GL10 gl){GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);GLES20.glUseProgram(program);float[] proj=new float[16],view=new float[16],model=new float[16],pv=new float[16],mvp=new float[16];float size=Math.max(.01f,Math.max(bounds[3]-bounds[0],Math.max(bounds[4]-bounds[1],bounds[5]-bounds[2])));Matrix.perspectiveM(proj,0,40,(float)width/height,.01f,100);Matrix.setLookAtM(view,0,0,0,3.4f*zoom,0,0,0,0,1,0);Matrix.setIdentityM(model,0);Matrix.rotateM(model,0,pitch,1,0,0);Matrix.rotateM(model,0,yaw,0,1,0);Matrix.scaleM(model,0,1.8f/size,1.8f/size,1.8f/size);Matrix.translateM(model,0,-(bounds[0]+bounds[3])/2,-(bounds[1]+bounds[4])/2,-(bounds[2]+bounds[5])/2);Matrix.multiplyMM(pv,0,proj,0,view,0);Matrix.multiplyMM(mvp,0,pv,0,model,0);GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"mvp"),1,false,mvp,0);GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"model"),1,false,model,0);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textureId);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"atlas"),0);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"textured"),textureId==0?0:1);String[] names={"p","n","uv","c"};int[] offsets={0,3,6,8},sizes={3,3,2,4};for(int i=0;i<4;i++){int a=GLES20.glGetAttribLocation(program,names[i]);data.position(offsets[i]);GLES20.glEnableVertexAttribArray(a);GLES20.glVertexAttribPointer(a,sizes[i],GLES20.GL_FLOAT,false,48,data);}GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,count);}
 public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_MOVE){yaw+=(e.getX()-px)*.4f;pitch=Math.max(-85,Math.min(85,pitch+(e.getY()-py)*.4f));requestRender();}px=e.getX();py=e.getY();return true;}
 public void view(float y,float p){yaw=y;pitch=p;requestRender();}
}
