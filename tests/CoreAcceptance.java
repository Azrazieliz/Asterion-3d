import com.asterion.foundry.*;import java.io.*;import java.nio.*;
public class CoreAcceptance {
 public static void main(String[] args)throws Exception{File dir=new File("test-output");dir.mkdirs();int count=0;for(String kind:new String[]{"Sword","Pavilion","Tree","Terrain","Humanoid template","Quadruped template"}){Mesh m=Recipes.create(kind,1,12);m.validate();if(!kind.equals("Terrain")){double volume=0;for(int i=0;i<m.vertices.size();i+=3){float[] x=m.vertices.get(i),y=m.vertices.get(i+1),z=m.vertices.get(i+2);volume+=(x[0]*(y[1]*z[2]-y[2]*z[1])+x[1]*(y[2]*z[0]-y[0]*z[2])+x[2]*(y[0]*z[1]-y[1]*z[0]))/6;}if(volume<=0)throw new AssertionError("Inward winding: "+kind);}File a=new File(dir,kind.replace(' ','_')+".mesh");m.save(a);Mesh restored=Mesh.load(a);if(restored.vertices.size()!=m.vertices.size())throw new AssertionError("Checkpoint changed geometry");File glb=new File(dir,kind.replace(' ','_')+".glb");Glb.write(restored,glb);System.out.println(kind+": "+m.vertices.size()/3+" triangles, "+m.bones.size()+" bones, "+glb.length()+" bytes");count++;}
 int[] pixels=new int[32*32];for(int y=3;y<29;y++)for(int x=13;x<19;x++)pixels[y*32+x]=0xff8877aa;Mesh image=Recipes.silhouette(pixels,32,32,1,.08f);Glb.write(image,new File(dir,"silhouette.glb"));image.validate();count++;
 try{Recipes.create("Unknown",1,12);throw new AssertionError("Unknown recipe silently accepted");}catch(IllegalArgumentException expected){count++;}
 try{Recipes.create("Sword",Float.NaN,12);throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){count++;}
 File bad=new File(dir,"bad.mesh");FilesUtil.write(bad,"bad");try{Mesh.load(bad);throw new AssertionError("Corrupt checkpoint accepted");}catch(IOException expected){count++;}
 Mesh invalid=Recipes.create("Sword",1,12);invalid.vertices.get(0)[0]=Float.NaN;try{invalid.validate();throw new AssertionError("Invalid geometry accepted");}catch(IllegalStateException expected){count++;}
 File obj=new File(dir,"triangle.obj");FilesUtil.write(obj,"v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n");if(Glb.readObj(obj).vertices.size()!=3)throw new AssertionError("OBJ failed");count++;
 count += reconstruction(dir);
 System.out.println("PASS "+count+" focused core cases; Android and visual acceptance not covered.");}
 private static int reconstruction(File dir) throws Exception {
  int side = 32;
  int[] front = new int[side*side], right = new int[side*side];
  for (int y=4; y<28; y++) {
   for (int x=8; x<24; x++) front[y*side+x] = 0xff82bc99;
   for (int x=12; x<20; x++) right[y*side+x] = 0xff428772;
  }
  VisualHull.Result result = VisualHull.reconstruct(new VisualHull.View(front,side,side),
          new VisualHull.View(right,side,side), null, 1, 24, () -> {});
  float[] bounds = result.mesh().bounds();
  if (result.frontIou() != 1 || result.rightIou() != 1 || result.components() != 1
          || Math.abs(bounds[3]-bounds[0]-2.0/3) > 1e-5
          || Math.abs(bounds[5]-bounds[2]-1.0/3) > 1e-5) {
   throw new AssertionError("Measured hull dimensions and silhouettes disagree");
  }
  checkClosed(result.mesh());
  Glb.write(result.mesh(), new File(dir,"multi_view.glb"));
  float[] depth = new float[side*side];
  for(int y=0; y<side; y++) for(int x=0; x<side; x++) depth[y*side+x] = x + y*.5f;
  Mesh relief = DepthRelief.build(front,side,side,depth,side,1,()->{});
  checkClosed(relief);
  Glb.write(relief,new File(dir,"depth_relief.glb"));
  try {
   DepthRelief.build(front,side,side,new float[side*side],side,1,()->{});
   throw new AssertionError("Flat model output was accepted");
  } catch (IllegalArgumentException expected) {}
  Mesh edited = MeshRevision.apply(result.mesh(),2,1,1,0xff102030);
  if (Math.abs(edited.bounds()[3]-2*bounds[3])>1e-5 || result.mesh().bounds()[3]!=bounds[3]
          || edited.vertices.get(0)[8] == result.mesh().vertices.get(0)[8]) {
   throw new AssertionError("Revision did not preserve the original or apply the edit");
  }
  Glb.write(edited,new File(dir,"revision.glb"));
  System.out.println("Visual hull: "+result.occupied()+" cells, unit overlap in both reference views");
  return 4;
 }
 private static void checkClosed(Mesh mesh) throws Exception {
  GeometryAudit.Result audit = GeometryAudit.inspect(mesh,()->{});
  if (audit.boundaryEdges()!=0 || audit.nonManifoldEdges()!=0
          || audit.windingConflicts()!=0 || audit.collapsedTriangles()!=0) {
   throw new AssertionError("Unexpected open or nonmanifold mesh: "+audit);
  }
 }
}
