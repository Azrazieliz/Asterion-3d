"""Independent binary layout, bounds, rigid skin, rest-pose and animation checks."""
import json,struct,sys,math,pathlib

COMPONENTS = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}

def check_animation(gltf, binary):
 animations = gltf.get('animations', [])
 for animation in animations:
  channels, samplers = animation['channels'], animation['samplers']
  assert channels and samplers
  joints = {node for skin in gltf['skins'] for node in skin['joints']}
  targets = set()
  for channel in channels:
   target = channel['target']
   node, path = target['node'], target['path']
   assert node in joints and (node, path) not in targets
   targets.add((node, path))
   sampler = samplers[channel['sampler']]
   assert sampler.get('interpolation', 'LINEAR') == 'LINEAR'
   time_accessor = gltf['accessors'][sampler['input']]
   value_accessor = gltf['accessors'][sampler['output']]
   assert time_accessor['type'] == 'SCALAR'
   assert value_accessor['type'] == {'translation': 'VEC3', 'rotation': 'VEC4'}[path]
   assert time_accessor['count'] == value_accessor['count'] > 0
   decoded = []
   for accessor in (time_accessor, value_accessor):
    assert accessor['componentType'] == 5126
    view = gltf['bufferViews'][accessor['bufferView']]
    assert view['byteOffset'] % 4 == 0 and 'target' not in view and 'byteStride' not in view
    start = view['byteOffset'] + accessor.get('byteOffset', 0)
    components = COMPONENTS[accessor['type']]
    end = start + accessor['count'] * components * 4
    assert 0 <= start < end <= len(binary)
    values = list(struct.iter_unpack('<' + 'f' * components, binary[start:end]))
    assert all(math.isfinite(value) for vector in values for value in vector)
    decoded.append(values)
   times = [vector[0] for vector in decoded[0]]
   assert times[0] >= 0 and all(a < b for a, b in zip(times, times[1:]))
   assert math.isclose(times[0], time_accessor['min'][0], rel_tol=1e-7)
   assert math.isclose(times[-1], time_accessor['max'][0], rel_tol=1e-7)
   if path == 'rotation':
    assert all(abs(sum(value * value for value in vector) - 1) < 1e-6 for vector in decoded[1])
 return len(animations)

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
  v=g['bufferViews'][a['bufferView']];components=COMPONENTS[a['type']];width=4 if a['componentType']==5126 else 2;stride=v.get('byteStride',components*width)
  assert a.get('byteOffset',0)+(a['count']-1)*stride+components*width<=v['byteLength']
 stride=g['bufferViews'][0]['byteStride']
 ranges=[]
 for mesh in g['meshes']:
  for primitive in mesh['primitives']:
   attributes=primitive['attributes']
   pos=g['accessors'][attributes['POSITION']]
   start=pos.get('byteOffset',0)
   n=pos['count']
   assert start%stride==0 and n%3==0 and pos['bufferView']==0
   for key,offset in {'NORMAL':12,'TEXCOORD_0':24,'COLOR_0':32}.items():
    accessor=g['accessors'][attributes[key]]
    assert accessor['count']==n and accessor['bufferView']==0 and accessor['byteOffset']==start+offset
   ranges.append((start,n))
   positions=[]
   for i in range(n):
    v=struct.unpack_from('<12f',binary,start+i*stride)
    assert all(map(math.isfinite,v));positions.append(v[:3]);assert abs(sum(x*x for x in v[3:6])-1)<1e-4
    if 'textures' in g: assert all(0<=axis<=1 for axis in v[6:8])
   for k in range(3):
    assert abs(min(v[k] for v in positions)-pos['min'][k])<1e-5
    assert abs(max(v[k] for v in positions)-pos['max'][k])<1e-5
   if 'skins' in g:
    for key,offset in {'JOINTS_0':48,'WEIGHTS_0':56}.items():
     accessor=g['accessors'][attributes[key]]
     assert accessor['count']==n and accessor['bufferView']==0 and accessor['byteOffset']==start+offset
 end=0
 for start,n in sorted(ranges):
  assert start==end,'Mesh parts must have distinct contiguous vertex data'
  end+=n*stride
 assert end==g['bufferViews'][0]['byteLength']
 if 'skins' in g:
  joints=g['skins'][0]['joints'];parents={child:i for i,node in enumerate(g['nodes']) for child in node.get('children',[])}
  def root(i):
   visited=set()
   while i in parents:
    assert i not in visited, 'Joint hierarchy contains a cycle'
    visited.add(i)
    i=parents[i]
   return i
  roots={root(i) for i in joints}
  assert len(roots)==1 and roots.issubset(g['scenes'][g['scene']]['nodes']), 'Skinned joints need a common hierarchy root'
  def world(i):
   t=g['nodes'][i].get('translation',[0,0,0]);p=world(parents[i]) if i in parents else [0,0,0];return [x+y for x,y in zip(t,p)]
  off=g['bufferViews'][1]['byteOffset']
  for k,node in enumerate(joints):
   inv=struct.unpack_from('<16f',binary,off+k*64);assert all(abs(x+y)<1e-5 for x,y in zip(world(node),inv[12:15]))
  for start,n in ranges:
   for i in range(n):
    js=struct.unpack_from('<4H',binary,start+i*stride+48);ws=struct.unpack_from('<4f',binary,start+i*stride+56)
    assert max(js)<len(joints) and min(ws)>=0 and abs(sum(ws)-1)<1e-6
 animations = check_animation(g, binary)
 print(pathlib.Path(name).name,'binary/accessor/bounds/rest-pose checks PASS'
       + (f'; animation channel/sampler checks PASS ({animations} clip)' if animations else ''))
