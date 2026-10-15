"""Rigid rear subframe, bearings and shared axle at the existing animated cradle pivot."""
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/harddayz/geo'

for path in ROOT.glob('*.geo.json'):
    data = json.loads(path.read_text())
    bones = {b['name']: b for b in data['minecraft:geometry'][0]['bones']}
    ram = bones.get('ram0', bones.get('eram0'))
    if ram is None:
        continue
    cradle = bones['rack' if 'ram0' in bones else 'erector']
    fixed = bones[cradle['parent']]
    for bone in (fixed, cradle):
        bone['cubes'] = [c for c in bone.get('cubes', []) if not c.get('name', '').startswith('hd_rear_hinge_')]
    _, y, z = cradle['pivot']
    # Width of the cradle at its pivot, excluding ornaments and the payload above it.
    cross_section = [c for c in cradle['cubes'] if c['origin'][1] <= y <= c['origin'][1]+c['size'][1]
                     and c['origin'][2] <= z <= c['origin'][2]+c['size'][2] and not c.get('rotation')]
    assert cross_section, (path, 'cradle has no material at its pivot')
    half_width = max(max(abs(c['origin'][0]), abs(c['origin'][0]+c['size'][0])) for c in cross_section)
    post_x = half_width + 2
    if fixed['name'] == 'turret':
        base = fixed['cubes'][0]
        base_y = base['origin'][1]+base['size'][1]-1
        front_z = min(z, base['origin'][2]+base['size'][2]/2)
    else:
        base_y, front_z = 22, z-10
    # Reuse six fully opaque atlas islands from the unchanged hydraulic barrel.
    donor = ram['cubes'][0]
    u, v = donor['uv']
    w, h, d = donor['size']
    uv = {
        'east': {'uv': [u, v+d], 'uv_size': [d, h]},
        'north': {'uv': [u+d, v+d], 'uv_size': [w, h]},
        'west': {'uv': [u+d+w, v+d], 'uv_size': [d, h]},
        'south': {'uv': [u+2*d+w, v+d], 'uv_size': [w, h]},
        'up': {'uv': [u+d, v], 'uv_size': [w, d]},
        'down': {'uv': [u+d+w, v+d], 'uv_size': [w, -d]},
    }
    def box(bone, name, lo, hi):
        bone['cubes'].append({'name': 'hd_rear_hinge_'+name, 'origin': lo,
                              'size': [b-a for a,b in zip(lo,hi)], 'uv': copy.deepcopy(uv)})
    for side in (-1, 1):
        x = post_x*side
        box(fixed, f'rail_{side}', [x-1.5,base_y,front_z-3], [x+1.5,base_y+3,z+3])
        box(fixed, f'post_{side}', [x-1.5,base_y+1,z-2], [x+1.5,y+2,z+2])
        box(fixed, f'bearing_{side}', [x-2,y-3,z-3], [x+2,y+3,z+3])
    box(fixed, 'front_crossmember', [-post_x-2,base_y,front_z-3], [post_x+2,base_y+3,front_z+3])
    box(fixed, 'rear_crossmember', [-post_x-2,base_y,z-3], [post_x+2,base_y+3,z+3])
    box(fixed, 'axle', [-post_x-3,y-1,z-1], [post_x+3,y+1,z+1])
    box(cradle, 'cradle_crossmember', [-half_width-0.5,y-2,z-2], [half_width+0.5,y+2,z+2])
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n')
    print(path.name, 'rear axle at', cradle['pivot'])
