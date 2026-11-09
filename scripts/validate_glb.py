"""Independent binary layout, bounds, rigid skin/rest-pose and winding checks."""
import json,struct,sys,math,pathlib
for name in sys.argv[1:]:
 data=pathlib.Path(name).read_bytes();magic,version,total=struct.unpack_from('<III',data)
 assert (magic,version,total)==(0x46546c67,2,len(data))
 size,kind=struct.unpack_from('<II',data,12);assert kind==0x4e4f534a
 g=json.loads(data[20:20+size]);bs,bkind=struct.unpack_from('<II',data,20+size);assert bkind==0x004e4942
 binary=data[28+size:];assert len(binary)==bs==g['buffers'][0]['byteLength']
 if 'textures' in g:
  binding=g['materials'][0]['pbrMetallicRoughness']['baseColorTexture']['index']
  image=g['images'][g['textures'][binding]['source']]
  assert image['mimeType']=='image/png'
  view=g['bufferViews'][image['bufferView']]
  start=view['byteOffset'];end=start+view['byteLength']
  assert start%4==0 and end<=len(binary)
  assert binary[start:start+8]==b'\x89PNG\r\n\x1a\n' and binary[end-8:end-4]==b'IEND'
 for a in g['accessors']:
  v=g['bufferViews'][a['bufferView']];components={'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}[a['type']];width=4 if a['componentType']==5126 else 2;stride=v.get('byteStride',components*width)
  assert a.get('byteOffset',0)+(a['count']-1)*stride+components*width<=v['byteLength']
 stride=g['bufferViews'][0]['byteStride'];n=g['accessors'][0]['count'];positions=[]
 for i in range(n):
  v=struct.unpack_from('<12f',binary,i*stride);assert all(map(math.isfinite,v));positions.append(v[:3]);assert abs(sum(x*x for x in v[3:6])-1)<1e-4
  if 'textures' in g: assert all(0<=axis<=1 for axis in v[6:8])
 for k in range(3):
  assert abs(min(v[k] for v in positions)-g['accessors'][0]['min'][k])<1e-5
  assert abs(max(v[k] for v in positions)-g['accessors'][0]['max'][k])<1e-5
 if 'skins' in g:
  joints=g['skins'][0]['joints'];parents={child:i for i,node in enumerate(g['nodes']) for child in node.get('children',[])}
  def world(i):
   t=g['nodes'][i].get('translation',[0,0,0]);p=world(parents[i]) if i in parents else [0,0,0];return [x+y for x,y in zip(t,p)]
  off=g['bufferViews'][1]['byteOffset']
  for k,node in enumerate(joints):
   inv=struct.unpack_from('<16f',binary,off+k*64);assert all(abs(x+y)<1e-5 for x,y in zip(world(node),inv[12:15]))
  for i in range(n):
   js=struct.unpack_from('<4H',binary,i*stride+48);ws=struct.unpack_from('<4f',binary,i*stride+56);assert max(js)<len(joints) and abs(sum(ws)-1)<1e-6
 print(pathlib.Path(name).name,'binary/accessor/bounds/rest-pose checks PASS')
