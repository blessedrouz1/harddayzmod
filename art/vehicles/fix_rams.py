"""Hydraulic lifts: make the cylinders read as solid, connected steel (they looked like glass).

Run on INSTALLED assets: python3 fix_rams.py NAME [NAME...]   (idempotent)
  * stage / rod share one pale blue-white tile -> recoloured to neutral steel
  * rod-end eye (clevis) gets the dark barrel colour, so it reads as a mount, not part of the glass rod
  * the barrel mouth flange turns dark steel like the barrel
  * a dark gland collar ring is added at the front of every stage, where the rod leaves it
"""
import json
import shutil
import sys
from pathlib import Path

from PIL import Image

import install

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parent.parent / 'src/main/resources/assets/harddayz'
BACKUP = Path('/private/tmp/claude-501/-Users-danylokulibaba-Desktop-dayz/9581efe1-b4f5-412b-8c35-eb355f01a29a/scratchpad/orig_rams')
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
# old pale tile luminance -> neutral steel (light to dark gradient of the 4 rows)
STEEL = [(170, 174, 180), (152, 156, 162), (138, 142, 148), (116, 120, 126)]


def lum(c):
    return 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]


def rects(cube):
    return [(f['uv'], f['uv_size']) for f in cube['uv'].values()] if isinstance(cube['uv'], dict) else []


def process(name):
    geo_path = ASSETS / f'geo/{name}.geo.json'
    tex_path = ASSETS / f'textures/entity/{name}.png'
    BACKUP.mkdir(parents=True, exist_ok=True)
    for p in (geo_path, tex_path):
        if not (BACKUP / p.name).exists():
            shutil.copy(p, BACKUP / p.name)
    geo = json.loads(geo_path.read_text())
    bones = {b['name']: b for b in geo['minecraft:geometry'][0]['bones']}
    img = Image.open(tex_path).convert('RGBA')
    px = img.load()
    rams = [n[:-6] for n in bones if n.endswith('_mount') and n[:-6] + '_stage' in bones and n[:-6] + '_rod' in bones]
    if not rams:
        print(name, 'no lifts')
        return
    # dark steel texel from the barrel's side
    barrel = bones[rams[0]]['cubes'][0]
    bu, bv = barrel['uv']
    d, w, h = int(barrel['size'][2]), int(barrel['size'][0]), int(barrel['size'][1])
    dark = None
    for x in range(int(bu), int(bu) + 2 * (d + w)):
        for y in range(int(bv) + d, int(bv) + d + h):
            if px[x, y][:3] == (74, 76, 85):
                dark = (x, y)
                break
        if dark:
            break
    dark = dark or (int(bu) + d, int(bv) + d)
    dark_uv = {f: {'uv': [dark[0], dark[1]], 'uv_size': [1, 1]} for f in FACES}
    done = set()
    recol = 0
    for r in rams:
        for c in bones[r]['cubes']:
            if c['size'] == [8, 8, 2]:   # barrel mouth flange: olive-brown -> dark steel like the barrel
                c['uv'] = json.loads(json.dumps(dark_uv))
        for part in (r + '_stage', r + '_rod'):
            for c in bones[part]['cubes']:
                if c['size'][0] == 4 and c['size'][1] == 4 and c['size'][2] == 3 and part.endswith('_rod'):
                    c['uv'] = json.loads(json.dumps(dark_uv))   # clevis eye
                    continue
                for (u, v), (uw, vh) in rects(c):
                    x0, x1 = sorted((int(u), int(u + uw)))
                    y0, y1 = sorted((int(v), int(v + vh)))
                    for x in range(x0, x1):
                        for y in range(y0, y1):
                            if (x, y) in done:
                                continue
                            done.add((x, y))
                            col = px[x, y]
                            if col[0] < 140 or tuple(col[:3]) in STEEL:
                                continue   # already steel (re-run) or not the pale tile
                            l = lum(col)
                            k = 0 if l > 190 else 1 if l > 170 else 2 if l > 150 else 3
                            px[x, y] = STEEL[k] + (255,)
                            recol += 1
        # gland collar where the rod leaves the stage (once)
        st = bones[r + '_stage']
        if not any(c.get('size', [0])[2] == 2 and c['size'][0] == 5.4 for c in st['cubes']):
            for c in list(st['cubes']):
                o, s = c['origin'], c['size']
                if s[2] > 10:
                    st['cubes'].append({'origin': [o[0] - 0.7, o[1] - 0.7, o[2]], 'size': [s[0] + 1.4, s[1] + 1.4, 2], 'uv': json.loads(json.dumps(dark_uv))})
    geo_path.write_text(json.dumps(geo, indent=1))
    img.save(tex_path)
    install.variants(tex_path, name)
    print(f'{name}: {len(rams)} lifts, {recol} texels recoloured')


if __name__ == '__main__':
    for a in sys.argv[1:]:
        process(a)
