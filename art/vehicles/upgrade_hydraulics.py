"""Upgrade existing UV atlases in place; gen2.ram_set emits the same rig for newly generated models."""
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/harddayz/geo'

for path in ROOT.glob('*.geo.json'):
    data = json.loads(path.read_text())
    bones = data['minecraft:geometry'][0]['bones']
    by_name = {b['name']: b for b in bones}
    changed = False
    for name in ('ram0', 'eram0'):
        if name not in by_name or name + '_stage' in by_name:
            continue
        barrel, rod = by_name[name], by_name[name + '_rod']
        _, y, z = barrel['pivot']
        length = barrel['cubes'][0]['size'][2]
        pin_z = z - length - 4.5
        stage = copy.deepcopy(rod)
        stage['name'], stage['parent'] = name + '_stage', name
        rod['parent'] = stage['name']
        heads = []
        for sleeve, shaft in zip(stage['cubes'], rod['cubes']):
            cx = shaft['origin'][0] + shaft['size'][0] / 2
            sleeve['origin'], sleeve['size'] = [cx - 2, y - 2, z - length - 3], [4, 4, length - 1]
            shaft['origin'], shaft['size'] = [cx - 1.25, y - 1.25, pin_z], [2.5, 2.5, length - 2]
            head = copy.deepcopy(shaft)
            head['origin'], head['size'] = [cx - 2, y - 2, pin_z - 1.5], [4, 4, 3]
            heads.append(head)
        rod['cubes'].extend(heads)
        bones.extend([stage, {'name': name + '_mount', 'parent': 'erector' if name.startswith('eram') else 'rack',
                              'pivot': [0, y, pin_z]}])
        changed = True
    # Resizing a box changes automatic box-UV offsets. Keep the original atlas faces explicitly.
    # The donor rod was 4 x 4 x (barrel length + 1); all six regions are opaque.
    by_name = {b['name']: b for b in bones}
    for name in ('ram0', 'eram0'):
        if name not in by_name:
            continue
        length = by_name[name]['cubes'][0]['size'][2]
        # Freshly generated stages have their own atlas islands and need no repair.
        stage_uvs = {tuple(c['uv']) for c in by_name.get(name + '_stage', {}).get('cubes', []) if isinstance(c.get('uv'), list)}
        rod_uvs = {tuple(c['uv']) for c in by_name.get(name + '_rod', {}).get('cubes', []) if isinstance(c.get('uv'), list)}
        legacy_uvs = stage_uvs & rod_uvs
        for suffix in ('_stage', '_rod'):
            for cube in by_name.get(name + suffix, {}).get('cubes', []):
                uv = cube.get('uv')
                if not isinstance(uv, list) or tuple(uv) not in legacy_uvs:
                    continue
                u, v = uv
                w = h = 4
                d = length + 1
                cube['uv'] = {
                    'east': {'uv': [u, v+d], 'uv_size': [d, h]},
                    'north': {'uv': [u+d, v+d], 'uv_size': [w, h]},
                    'west': {'uv': [u+d+w, v+d], 'uv_size': [d, h]},
                    'south': {'uv': [u+2*d+w, v+d], 'uv_size': [w, h]},
                    'up': {'uv': [u+d, v], 'uv_size': [w, d]},
                    'down': {'uv': [u+d+w, v+d], 'uv_size': [w, -d]},
                }
                changed = True
    if changed:
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
        print(path.name)
