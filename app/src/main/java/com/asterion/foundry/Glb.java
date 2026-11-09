package com.asterion.foundry;
import java.io.*;import java.nio.*;import java.util.*;
/** glTF 2.0 binary export with normals, UVs, vertex colours, exact rest transforms and inverse binds. */
public final class Glb {
 public static void write(Mesh m,File out)throws Exception{write(m,out,null);}
 public static void write(Mesh m,File out,byte[] png)throws Exception{
  m.validate();boolean skin=!m.bones.isEmpty();int n=m.vertices.size(),stride=skin?72:48,meshBytes=n*stride,boneBytes=skin?m.bones.size()*64:0;
  int textureBytes=png==null?0:((png.length+3)&~3);
  ByteBuffer bin=ByteBuffer.allocate(meshBytes+boneBytes+textureBytes).order(ByteOrder.LITTLE_ENDIAN);
  for(float[] v:m.vertices){for(int i=0;i<12;i++)bin.putFloat(v[i]);if(skin){bin.putShort((short)v[12]);for(int i=0;i<3;i++)bin.putShort((short)0);bin.putFloat(1);for(int i=0;i<3;i++)bin.putFloat(0);}}
  if(skin)for(Mesh.Bone b:m.bones)for(int i=0;i<16;i++)bin.putFloat(i==12?-b.x:i==13?-b.y:i==14?-b.z:(i==0||i==5||i==10||i==15)?1:0);
  if(png!=null)bin.put(png);
  String textures=png==null?"":",\"textures\":[{\"source\":0}],\"images\":[{\"bufferView\":"+(skin?2:1)+",\"mimeType\":\"image/png\"}]";
  float[] b=m.bounds();StringBuilder j=new StringBuilder("{\"asset\":{\"version\":\"2.0\",\"generator\":\"Asterion Foundry 0.1\"},\"scene\":0,\"scenes\":[{\"nodes\":[0");
  if(skin)for(int i=0;i<m.bones.size();i++)if(m.bones.get(i).parent==-1)j.append(',').append(i+1);
  j.append("]}],\"nodes\":[{\"name\":\"Asset\",\"mesh\":0").append(skin?",\"skin\":0":"").append("}");
  if(skin)for(int i=0;i<m.bones.size();i++){Mesh.Bone v=m.bones.get(i),p=v.parent<0?new Mesh.Bone("",-1,0,0,0):m.bones.get(v.parent);j.append(",{\"name\":").append(FilesUtil.quote(v.name)).append(",\"translation\":[").append(v.x-p.x).append(',').append(v.y-p.y).append(',').append(v.z-p.z).append(']');ArrayList<Integer> ch=new ArrayList<>();for(int k=0;k<m.bones.size();k++)if(m.bones.get(k).parent==i)ch.add(k+1);if(!ch.isEmpty())j.append(",\"children\":").append(ch);j.append('}');}
  j.append("],\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0,\"NORMAL\":1,\"TEXCOORD_0\":2,\"COLOR_0\":3").append(skin?",\"JOINTS_0\":4,\"WEIGHTS_0\":5":"").append("},\"material\":0,\"mode\":4}]}],\"materials\":[{\"name\":\"Vertex palette\",\"pbrMetallicRoughness\":{\"baseColorFactor\":[1,1,1,1]").append(png==null?"":",\"baseColorTexture\":{\"index\":0}").append(",\"metallicFactor\":0,\"roughnessFactor\":0.8},\"alphaMode\":\"OPAQUE\"}]").append(textures).append(",\"buffers\":[{\"byteLength\":").append(bin.capacity()).append("}],\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":").append(meshBytes).append(",\"byteStride\":").append(stride).append(",\"target\":34962}");
  if(skin)j.append(",{\"buffer\":0,\"byteOffset\":").append(meshBytes).append(",\"byteLength\":").append(boneBytes).append('}');
  if(png!=null)j.append(",{\"buffer\":0,\"byteOffset\":").append(meshBytes+boneBytes).append(",\"byteLength\":").append(png.length).append('}');
  j.append("],\"accessors\":[");access(j,0,0,5126,n,"VEC3");j.setLength(j.length()-1);j.append(",\"min\":[").append(b[0]).append(',').append(b[1]).append(',').append(b[2]).append("],\"max\":[").append(b[3]).append(',').append(b[4]).append(',').append(b[5]).append("]},");access(j,0,12,5126,n,"VEC3");j.append(',');access(j,0,24,5126,n,"VEC2");j.append(',');access(j,0,32,5126,n,"VEC4");
  if(skin){j.append(',');access(j,0,48,5123,n,"VEC4");j.append(',');access(j,0,56,5126,n,"VEC4");j.append(',');access(j,1,0,5126,m.bones.size(),"MAT4");}
  j.append(']');if(skin){j.append(",\"skins\":[{\"inverseBindMatrices\":6,\"joints\":[");for(int i=0;i<m.bones.size();i++){if(i>0)j.append(',');j.append(i+1);}j.append("]}]");}j.append('}');
  byte[] json=j.toString().getBytes("UTF-8");int pad=(4-json.length%4)%4;ByteBuffer header=ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).putInt(0x46546c67).putInt(2).putInt(28+json.length+pad+bin.capacity()).putInt(json.length+pad).putInt(0x4e4f534a);
  File tmp=new File(out+".tmp");try(FileOutputStream o=new FileOutputStream(tmp)){o.write(header.array());o.write(json);for(int i=0;i<pad;i++)o.write(32);o.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(bin.capacity()).putInt(0x004e4942).array());o.write(bin.array());o.getFD().sync();}FilesUtil.move(tmp,out);
 }
 static void access(StringBuilder j,int view,int off,int type,int count,String shape){j.append("{\"bufferView\":").append(view).append(",\"byteOffset\":").append(off).append(",\"componentType\":").append(type).append(",\"count\":").append(count).append(",\"type\":").append(FilesUtil.quote(shape)).append('}');}
 public static Mesh readObj(File f)throws Exception{
  Mesh m=new Mesh();ArrayList<float[]> pts=new ArrayList<>();try(BufferedReader r=new BufferedReader(new FileReader(f))){String s;while((s=r.readLine())!=null){if(s.length()>65536)throw new IOException("OBJ line exceeds safety limit");String[] a=s.trim().split("\\s+");if(a.length==0)continue;if(a[0].equals("v")&&a.length>=4){if(pts.size()>500000)throw new IOException("OBJ exceeds memory budget");pts.add(new float[]{Float.parseFloat(a[1]),Float.parseFloat(a[2]),Float.parseFloat(a[3])});}else if(a[0].equals("f")){if(a.length!=4)throw new IOException("Only triangulated OBJ is supported; refusing guessed polygon triangulation");float[][] p=new float[3][];for(int i=0;i<3;i++){int v=Integer.parseInt(a[i+1].split("/")[0]);int ix=v<0?pts.size()+v:v-1;if(ix<0||ix>=pts.size())throw new IOException("Invalid OBJ index");p[i]=pts.get(ix);}m.tri(p[0],p[1],p[2],Recipes.STONE,0);}}}m.validate();return m;
 }
}
