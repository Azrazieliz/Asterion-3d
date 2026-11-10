package com.asterion.foundry;
import java.util.*;
/** Bounded deterministic recipes; these are templates, never purported AI reconstruction. */
public final class Recipes {
 public static final String VERSION="recipes-1";
 static final float[] STEEL={.55f,.67f,.76f},GOLD={.67f,.43f,.16f},WOOD={.25f,.12f,.065f},STONE={.58f,.56f,.50f},GREEN={.13f,.34f,.19f},SKIN={.70f,.49f,.34f};
 public static Mesh create(String kind,float scale,int detail){
  if(!Float.isFinite(scale)||scale<.01f||scale>100)throw new IllegalArgumentException("Scale must be 0.01–100 metres");
  Mesh m=new Mesh();detail=Math.max(4,Math.min(24,detail));
  switch(kind){
   case "Sword":{
    float[] a={-.065f,.35f,0},b={.065f,.35f,0},tip={0,1.25f,0},front={0,.70f,.025f},back={0,.70f,-.025f};
    m.tri(a,b,front,STEEL,0);m.tri(b,tip,front,STEEL,0);m.tri(tip,a,front,STEEL,0);m.tri(b,a,back,STEEL,0);m.tri(tip,b,back,STEEL,0);m.tri(a,tip,back,STEEL,0);
    m.box(0,.32f,0,.32f,.055f,.07f,GOLD,0);m.box(0,.18f,0,.055f,.23f,.055f,WOOD,0);m.ellipsoid(0,.045f,0,.055f,.05f,.04f,GOLD,0,detail);break;
   }
   case "Pavilion":m.box(0,.1f,0,4,.2f,3,STONE,0);for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})m.box(x*1.7f,1.5f,z*1.2f,.22f,2.8f,.22f,STONE,0);m.box(0,3,0,4.2f,.2f,3.2f,WOOD,0);m.box(0,3.22f,0,3.6f,.24f,2.8f,WOOD,0);break;
   case "Tree":m.box(0,1.25f,0,.25f,2.5f,.25f,WOOD,0);for(int i=0;i<3;i++)m.ellipsoid((i-1)*.45f,2.5f+i*.25f,0,.85f,.95f,.85f,GREEN,0,detail);break;
   case "Terrain":{
    int n=detail*4;for(int x=0;x<n;x++)for(int z=0;z<n;z++){float[] a=ground(x,z,n),b=ground(x+1,z,n),c=ground(x+1,z+1,n),d=ground(x,z+1,n);m.quad(a,d,c,b,GREEN,0);}break;
   }
   case "Humanoid template", "Humanoid with separate tunic":{
    boolean clothed=kind.equals("Humanoid with separate tunic");
    if(clothed)m.beginPart("Body","body");
    int root=m.bone("root",-1,0,0,0),hips=m.bone("hips",root,0,.95f,0),chest=m.bone("chest",hips,0,1.25f,0),head=m.bone("head",chest,0,1.65f,0);
    m.ellipsoid(0,1.2f,0,.22f,.30f,.13f,SKIN,chest,detail);m.ellipsoid(0,1.66f,0,.13f,.17f,.12f,SKIN,head,detail);
    for(int side:new int[]{-1,1}){String s=side<0?"L":"R";int leg=m.bone("upper_leg_"+s,hips,side*.12f,.9f,0),shin=m.bone("lower_leg_"+s,leg,side*.12f,.5f,0),arm=m.bone("upper_arm_"+s,chest,side*.27f,1.4f,0),fore=m.bone("forearm_"+s,arm,side*.53f,1.4f,0);m.ellipsoid(side*.12f,.73f,0,.085f,.25f,.085f,SKIN,leg,detail);m.ellipsoid(side*.12f,.28f,0,.065f,.23f,.07f,SKIN,shin,detail);m.ellipsoid(side*.39f,1.4f,0,.18f,.065f,.065f,SKIN,arm,detail);m.ellipsoid(side*.67f,1.4f,0,.16f,.055f,.055f,SKIN,fore,detail);}
    if(clothed)tunic(m,chest,hips,detail);break;
   }
   case "Quadruped template":{
    int root=m.bone("root",-1,0,0,0),spine=m.bone("spine",root,0,.7f,0),neck=m.bone("neck",spine,0,.8f,.55f),head=m.bone("head",neck,0,.95f,.8f),tail=m.bone("tail",spine,0,.7f,-.65f);
    m.ellipsoid(0,.7f,0,.25f,.3f,.65f,WOOD,spine,detail);m.ellipsoid(0,.95f,.8f,.18f,.2f,.28f,WOOD,head,detail);m.ellipsoid(0,.65f,-.9f,.07f,.08f,.35f,WOOD,tail,detail);
    for(int x:new int[]{-1,1})for(int z:new int[]{-1,1}){int hip=m.bone("leg_"+x+"_"+z,spine,x*.2f,.6f,z*.42f),foot=m.bone("foot_"+x+"_"+z,hip,x*.2f,.28f,z*.42f);m.ellipsoid(x*.2f,.48f,z*.42f,.085f,.22f,.09f,WOOD,hip,detail);m.ellipsoid(x*.2f,.15f,z*.42f,.06f,.16f,.07f,WOOD,foot,detail);}break;
   }
   case "Articulated dragon":m=DragonRig.create(detail);break;
   default:throw new IllegalArgumentException("No validated production recipe for "+kind);
  }
  for(float[] v:m.vertices)for(int i=0;i<3;i++)v[i]*=scale;for(Mesh.Bone b:m.bones){b.x*=scale;b.y*=scale;b.z*=scale;}m.validate();return m;
 }
 private static void tunic(Mesh mesh,int chest,int hips,int detail){
  mesh.beginPart("Tunic","garment");
  float[][] rings={{.80f,.245f,.173f},{.95f,.205f,.145f},{1.12f,.230f,.149f},{1.32f,.229f,.143f},{1.47f,.152f,.112f}};
  float[] cloth={.08f,.24f,.29f},trim={.29f,.49f,.49f};
  int sides=2*detail;
  float[][] draped=ClothDrape.relax(rings,sides);
  for(int ring=0;ring<rings.length-1;ring++)for(int side=0;side<sides;side++){
   int a=ring*sides+side,b=ring*sides+(side+1)%sides;
   mesh.quad(draped[a],draped[a+sides],draped[b+sides],draped[b],ring==0?trim:cloth,ring<2?hips:chest);
  }
 }
 static float[] ground(int x,int z,int n){float a=(float)x/n*10-5,b=(float)z/n*10-5;return new float[]{a,(float)(.45*Math.sin(a*.8)*Math.cos(b*.6)),b};}
 /** Opaque foreground on transparent or near-uniform corner background. Pixel-cell closed extrusion, not hidden-surface reconstruction. */
 public static Mesh silhouette(int[] pixels,int width,int height,float scale,float depth){
  if(width<1||height<1||width>160||height>160||pixels.length!=width*height)throw new IllegalArgumentException("Invalid silhouette image");
  Mesh m=new Mesh();boolean alpha=false;for(int p:pixels)if((p>>>24)<32){alpha=true;break;}
  int bg=pixels[0],count=0;boolean[] fg=new boolean[pixels.length];
  for(int i=0;i<pixels.length;i++){int p=pixels[i],dist=Math.abs(((p>>16)&255)-((bg>>16)&255))+Math.abs(((p>>8)&255)-((bg>>8)&255))+Math.abs((p&255)-(bg&255));fg[i]=(p>>>24)>128&&(alpha||dist>75);if(fg[i])count++;}
  if(count<4||count>pixels.length*.9)throw new IllegalArgumentException("Foreground is ambiguous. Use a transparent or plain-background object image.");
  float unit=scale/Math.max(width,height),dz=depth/2;
  for(int y=0;y<height;y++)for(int x=0;x<width;x++){int k=y*width+x;if(!fg[k])continue;float l=(x-width/2f)*unit,r=l+unit,t=(height-y)*unit,b=t-unit;int p=pixels[k];float[] c={((p>>16)&255)/255f,((p>>8)&255)/255f,(p&255)/255f};
   float[] a={l,b,-dz},bb={r,b,-dz},cc={r,t,-dz},d={l,t,-dz},e={l,b,dz},f={r,b,dz},g={r,t,dz},h={l,t,dz};
   m.quad(a,d,cc,bb,c,0);m.quad(e,f,g,h,c,0);
   if(x==0||!fg[k-1])m.quad(a,e,h,d,c,0);if(x==width-1||!fg[k+1])m.quad(bb,cc,g,f,c,0);if(y==0||!fg[k-width])m.quad(d,h,g,cc,c,0);if(y==height-1||!fg[k+width])m.quad(a,bb,f,e,c,0);
  }m.validate();return m;
 }
}
