import com.asterion.foundry.*;import java.io.*;import java.nio.*;
public class CoreAcceptance {
 public static void main(String[] args)throws Exception{File dir=new File("test-output");dir.mkdirs();int count=0;for(String kind:new String[]{"Sword","Pavilion","Tree","Terrain","Humanoid template","Quadruped template"}){Mesh m=Recipes.create(kind,1,12);m.validate();File a=new File(dir,kind.replace(' ','_')+".mesh");m.save(a);Mesh restored=Mesh.load(a);if(restored.vertices.size()!=m.vertices.size())throw new AssertionError("Checkpoint changed geometry");File glb=new File(dir,kind.replace(' ','_')+".glb");Glb.write(restored,glb);System.out.println(kind+": "+m.vertices.size()/3+" triangles, "+m.bones.size()+" bones, "+glb.length()+" bytes");count++;}
 int[] pixels=new int[32*32];for(int y=3;y<29;y++)for(int x=13;x<19;x++)pixels[y*32+x]=0xff8877aa;Mesh image=Recipes.silhouette(pixels,32,32,1,.08f);Glb.write(image,new File(dir,"silhouette.glb"));image.validate();count++;
 try{Recipes.create("Unknown",1,12);throw new AssertionError("Unknown recipe silently accepted");}catch(IllegalArgumentException expected){count++;}
 try{Recipes.create("Sword",Float.NaN,12);throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){count++;}
 File bad=new File(dir,"bad.mesh");FilesUtil.write(bad,"bad");try{Mesh.load(bad);throw new AssertionError("Corrupt checkpoint accepted");}catch(IOException expected){count++;}
 Mesh invalid=Recipes.create("Sword",1,12);invalid.vertices.get(0)[0]=Float.NaN;try{invalid.validate();throw new AssertionError("Invalid geometry accepted");}catch(IllegalStateException expected){count++;}
 File obj=new File(dir,"triangle.obj");FilesUtil.write(obj,"v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n");if(Glb.readObj(obj).vertices.size()!=3)throw new AssertionError("OBJ failed");count++;
 System.out.println("PASS "+count+" focused core cases; Android and visual acceptance not covered.");}
}
