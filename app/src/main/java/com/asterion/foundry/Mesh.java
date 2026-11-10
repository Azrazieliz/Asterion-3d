package com.asterion.foundry;
import java.io.*;
import java.util.*;
/** Editable triangles in metres, Y up, with optional four-joint linear skin binding. */
public final class Mesh {
 public final ArrayList<float[]> vertices=new ArrayList<>();
 public final ArrayList<Bone> bones=new ArrayList<>();
 private final ArrayList<Part> parts=new ArrayList<>();
 public static final int STRIDE=13, LIMIT=1500000;
 private static final int WEIGHTED_STRIDE=20, LEGACY_MAGIC=0x41463344, WEIGHTED_MAGIC=0x41463345;
 static final int INFLUENCES=4;
 private static final int PART_MAGIC=0x41463346, PART_ID_MAGIC=0x41463347;
 private static final Set<String> PART_ROLES=Set.of("body","face","hair","garment","armor","accessory",
         "appendage","wing_membrane","equipment","structure","vegetation","surface");
 record Part(String id,String name,String role,int firstVertex) {}
 void beginPart(String name,String role){
  beginPart(defaultPartId(parts.size()),name,role);
 }
 void beginPart(String id,String name,String role){
  if(!validPart(id,name,role)||parts.size()>=64
          ||parts.stream().anyMatch(p->p.id().equals(id)||p.name().equals(name))){
   throw new IllegalArgumentException("Invalid or duplicate mesh part");
  }
  int start=vertices.size();
  if(start%3!=0||parts.isEmpty()&&start!=0||!parts.isEmpty()&&parts.get(parts.size()-1).firstVertex()==start){
   throw new IllegalStateException("Mesh parts must begin before their triangles");
  }
  parts.add(new Part(id,name,role,start));
 }
 static boolean validPart(String id,String name,String role){
  return id!=null&&id.matches("[a-z][a-z0-9_-]{0,63}")&&name!=null&&!name.isBlank()&&name.length()<=80
          &&name.chars().noneMatch(Character::isISOControl)&&role!=null&&PART_ROLES.contains(role);
 }
 private static String defaultPartId(int index){return "part-"+(index+1);}
 List<Part> parts(){return parts.isEmpty()?List.of(new Part("asset","Asset","surface",0)):List.copyOf(parts);}
 void copyPartsTo(Mesh destination){destination.parts.addAll(parts);}
 int partEnd(int index){return index+1<parts.size()?parts.get(index+1).firstVertex():vertices.size();}
 Mesh extractPart(int index){
  Part part=parts().get(index);Mesh result=new Mesh();
  result.beginPart(part.id(),part.name(),part.role());
  result.bones.addAll(bones);
  result.vertices.addAll(vertices.subList(part.firstVertex(),partEnd(index)));
  return result;
 }
 public static final class Bone { public String name; public int parent; public float x,y,z; public Bone(String n,int p,float a,float b,float c){name=n;parent=p;x=a;y=b;z=c;} }
 public void tri(float[] a,float[] b,float[] c,float[] color,int joint){
  if(vertices.size()+3>LIMIT)throw new IllegalArgumentException("Mesh exceeds current memory budget");
  float ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
  float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,l=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
  if(l<1e-10) return;
  for(float[] p:new float[][]{a,b,c})vertices.add(new float[]{p[0],p[1],p[2],nx/l,ny/l,nz/l,p[0],p[1],color[0],color[1],color[2],1,joint});
 }
 public void quad(float[] a,float[] b,float[] c,float[] d,float[] color,int j){tri(a,b,c,color,j);tri(a,c,d,color,j);}
 public void box(float x,float y,float z,float w,float h,float d,float[] c,int j){
  float[][] p={{x-w/2,y-h/2,z-d/2},{x+w/2,y-h/2,z-d/2},{x+w/2,y+h/2,z-d/2},{x-w/2,y+h/2,z-d/2},{x-w/2,y-h/2,z+d/2},{x+w/2,y-h/2,z+d/2},{x+w/2,y+h/2,z+d/2},{x-w/2,y+h/2,z+d/2}};
  int[][] f={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}};
  for(int[] q:f)quad(p[q[0]],p[q[1]],p[q[2]],p[q[3]],c,j);
 }
 public void ellipsoid(float x,float y,float z,float rx,float ry,float rz,float[] c,int j,int detail){
  int rings=detail,sides=detail*2;
  for(int r=0;r<rings;r++)for(int s=0;s<sides;s++){
   float[] a=point(x,y,z,rx,ry,rz,r,s,rings,sides),b=point(x,y,z,rx,ry,rz,r,s+1,rings,sides),d=point(x,y,z,rx,ry,rz,r+1,s,rings,sides),e=point(x,y,z,rx,ry,rz,r+1,s+1,rings,sides);
   quad(a,b,e,d,c,j);
  }
 }
 private float[] point(float x,float y,float z,float rx,float ry,float rz,int r,int s,int rings,int sides){double p=Math.PI*r/rings,t=2*Math.PI*s/sides;return new float[]{x+rx*(float)(Math.sin(p)*Math.cos(t)),y+ry*(float)Math.cos(p),z+rz*(float)(Math.sin(p)*Math.sin(t))};}
 public int bone(String name,int parent,float x,float y,float z){bones.add(new Bone(name,parent,x,y,z));return bones.size()-1;}
 static int joint(float[] vertex,int slot){return slot==0?(int)vertex[12]:vertex.length==STRIDE?0:(int)vertex[12+2*slot];}
 static float weight(float[] vertex,int slot){return vertex.length==STRIDE?(slot==0?1:0):vertex[13+2*slot];}
 static float[] bindPair(float[] vertex,int first,int second,float secondWeight){
  float[] bound=Arrays.copyOf(vertex,WEIGHTED_STRIDE);
  Arrays.fill(bound,12,WEIGHTED_STRIDE,0);
  bound[12]=first;bound[13]=1-secondWeight;bound[14]=second;bound[15]=secondWeight;
  return bound;
 }
 static void offsetJoints(float[] vertex,int offset){
  for(int slot=0;slot<INFLUENCES;slot++)if(weight(vertex,slot)>0)vertex[12+2*slot]+=offset;
 }
 boolean blended(){for(float[] v:vertices)if(v.length==WEIGHTED_STRIDE)return true;return false;}
 public float[] bounds(){return bounds(0,vertices.size());}
 float[] bounds(int first,int end){float[] b={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};for(int i=first;i<end;i++){float[] v=vertices.get(i);for(int k=0;k<3;k++){b[k]=Math.min(b[k],v[k]);b[k+3]=Math.max(b[k+3],v[k]);}}return b;}
 public String validate(){
  if(vertices.isEmpty()||vertices.size()%3!=0)throw new IllegalStateException("Empty or incomplete mesh");
  if(!parts.isEmpty()&&parts.get(parts.size()-1).firstVertex()>=vertices.size())throw new IllegalStateException("Empty mesh part");
  for(int i=0;i<bones.size();i++){Bone b=bones.get(i);if(b.parent>=i||b.parent< -1)throw new IllegalStateException("Invalid bone hierarchy");}
  boolean blended=false;
  for(float[] v:vertices){
   if(v.length!=STRIDE&&(v.length!=WEIGHTED_STRIDE||bones.isEmpty()))throw new IllegalStateException("Invalid vertex layout");
   for(float f:v)if(!Float.isFinite(f))throw new IllegalStateException("Non-finite mesh data");
   float len=v[3]*v[3]+v[4]*v[4]+v[5]*v[5];if(Math.abs(len-1)>0.001)throw new IllegalStateException("Invalid normal");
   if(bones.isEmpty())continue;
   blended|=v.length==WEIGHTED_STRIDE;
   float sum=0;
   for(int slot=0;slot<INFLUENCES;slot++){
    int index=joint(v,slot);float amount=weight(v,slot);
    if(index<0||index>=bones.size()||amount<0||amount>1||(v.length!=STRIDE||slot==0)&&v[12+2*slot]!=index)throw new IllegalStateException("Invalid skin binding");
    for(int earlier=0;earlier<slot&&amount>0;earlier++)if(weight(v,earlier)>0&&joint(v,earlier)==index)throw new IllegalStateException("Duplicate skin joint");
    sum+=amount;
   }
   if(Math.abs(sum-1)>1e-5)throw new IllegalStateException("Skin weights must sum to one");
  }
  return "{\"finite_geometry\":true,\"unit_normals\":true,\"triangles\":"+vertices.size()/3+",\"bones\":"+bones.size()+",\"skin_weights\":\""+(bones.isEmpty()?"not_applicable":blended?"normalized_blended":"normalized_rigid")+"\",\"manifold\":\"not_checked\",\"uv_overlap\":\"not_checked\",\"deformation\":\"not_visually_validated\",\"reference_fidelity\":\"not_validated\"}";
 }
 public void save(File f)throws IOException{
  boolean weighted=blended();File tmp=new File(f+".tmp");
  try(FileOutputStream file=new FileOutputStream(tmp);DataOutputStream o=new DataOutputStream(new BufferedOutputStream(file))){
   o.writeInt(parts.isEmpty()?(weighted?WEIGHTED_MAGIC:LEGACY_MAGIC):PART_ID_MAGIC);
   if(!parts.isEmpty())o.writeBoolean(weighted);
   o.writeInt(bones.size());
   for(Bone b:bones){o.writeUTF(b.name);o.writeInt(b.parent);o.writeFloat(b.x);o.writeFloat(b.y);o.writeFloat(b.z);}
   o.writeInt(vertices.size());
   for(float[] v:vertices){
    for(float n:v)o.writeFloat(n);
    if(weighted&&v.length==STRIDE){o.writeFloat(1);for(int i=0;i<6;i++)o.writeFloat(0);}
   }
   if(!parts.isEmpty()){
    o.writeInt(parts.size());
    for(Part part:parts){o.writeUTF(part.id());o.writeUTF(part.name());o.writeUTF(part.role());o.writeInt(part.firstVertex());}
   }
   o.flush();file.getFD().sync();
  }
  FilesUtil.move(tmp,f);
 }
 public static Mesh load(File f)throws IOException{
  Mesh m=new Mesh();
  try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(f)))){
   int magic=in.readInt();int stride=switch(magic){case LEGACY_MAGIC->STRIDE;case WEIGHTED_MAGIC->WEIGHTED_STRIDE;case PART_MAGIC,PART_ID_MAGIC->in.readBoolean()?WEIGHTED_STRIDE:STRIDE;default->throw new IOException("Not a Foundry mesh");};
   int n=in.readInt();if(n<0||n>512)throw new IOException("Invalid bone count");
   for(int i=0;i<n;i++)m.bone(in.readUTF(),in.readInt(),in.readFloat(),in.readFloat(),in.readFloat());
   n=in.readInt();if(n<0||n>LIMIT||n*(long)stride*4>f.length())throw new IOException("Invalid vertex count");
   for(int i=0;i<n;i++){float[] v=new float[stride];for(int j=0;j<stride;j++)v[j]=in.readFloat();m.vertices.add(v);}
   if(magic==PART_MAGIC||magic==PART_ID_MAGIC){
    int count=in.readInt();if(count<1||count>64)throw new IOException("Invalid mesh part count");
    Set<String> names=new HashSet<>(),ids=new HashSet<>();int previous=-1;
    for(int i=0;i<count;i++){
     String id=magic==PART_ID_MAGIC?in.readUTF():defaultPartId(i);
     String name=in.readUTF(),role=in.readUTF();int start=in.readInt();
     if(!validPart(id,name,role)||!names.add(name)||!ids.add(id)||i==0&&start!=0||start<=previous||start>=n||start%3!=0)throw new IOException("Invalid mesh part");
     m.parts.add(new Part(id,name,role,start));previous=start;
    }
   }
   if(in.read()!=-1)throw new IOException("Unexpected mesh data after vertices");
  }
  m.validate();return m;
 }
}
