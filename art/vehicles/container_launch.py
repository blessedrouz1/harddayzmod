"""Container launchers (S-400, Patriot, NASAMS, Kalibr): lid + missile that slides out of every tube.

Run on the INSTALLED assets (after install.py): python3 container_launch.py NAME [NAME...]   (--strip NAME removes it again)
Replaces the old add_caps.py. Idempotent: it removes its previous bones and crops its previous atlas first.

Per tube i (slot order = LauncherGeometry.muzzle) the geo gets
  <lid>{i}  the cap plate: flies off when the launcher starts the arming sequence (LauncherRenderer)
  bore{i}   dark opening under the lid (a thin plate over the tube's solid front face)
  msl{i}    Iskander-style missile (missilekit), parked inside the tube; the renderer slides it out along -Z
The atlas for them (lid tile, bore tile, missile faces) is appended below the vehicle texture.
"""
import copy
import json
import math
import shutil
import statistics
import sys
from pathlib import Path

from PIL import Image

import install
import missilekit as mk

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parent.parent / 'src/main/resources/assets/harddayz'
BACKUP = Path('/private/tmp/claude-501/-Users-danylokulibaba-Desktop-dayz/9581efe1-b4f5-412b-8c35-eb355f01a29a/scratchpad/orig_container')
TILE = 16          # lid tile size in the atlas
BASE_KEY = 'harddayz_base_texture_height'

# slots are in geo-file coordinates (x already mirrored), in firing order (slot i fires when ammo == i + 1)
SPECS = {
    's400': dict(lid='tpk_cap', face_z=-24.0, lid_wh=(9, 8), bore_wh=(8, 7), L=78, D=4.9, palette='s400', keep_baked_lids=True,
                 slots=[(-6.5, 37.5), (6.5, 37.5), (-6.5, 50.5), (6.5, 50.5)],
                 box=lambda x, y: (x - 5.5, x + 5.5, y - 5.5, y + 5.5, -24, 60)),
    'patriot': dict(lid='canister_cap', face_z=-20.0, lid_wh=(14, 13), bore_wh=(12, 11), L=72, D=5.6, palette='patriot',
                    slots=[(-7.5, 40.5), (8, 40.5), (-7.5, 55), (8, 55)],
                    box=lambda x, y: (x - 7, x + 7, y - 6.5, y + 6.5, -20, 56)),
    'nasams': dict(lid='canister_cap', face_z=-30.0, lid_wh=(8.2, 8.6), bore_wh=(7, 7.4), L=44, D=3.6, palette='nasams',
                   slots=[(8.67, 38.5), (0, 38.5), (-8.67, 38.5), (8.67, 47.5), (0, 47.5), (-8.67, 47.5)],
                   box=lambda x, y: (x - 4.33, x + 4.33, y - 4.5, y + 4.5, -30, 20)),
    'kalibr': dict(lid='container_cap', face_z=-27.0, lid_wh=(17, 16), bore_wh=(15, 14), L=78, D=8, palette='kalibr',
                   slots=[(9.5, 43), (-9.5, 43), (9.5, 61), (-9.5, 61)],
                   box=lambda x, y: (x - 9.5, x + 9.5, y - 9, y + 9, -26, 57)),
}
FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def median_color(img, rect):
    u, v, w, h = (int(round(a)) for a in rect)
    px = [img.getpixel((x, y))[:3] for x in range(u, u + w) for y in range(v, v + h) if img.getpixel((x, y))[3]]
    return tuple(int(statistics.median(c[i] for c in px)) for i in range(3)) if px else (110, 114, 108)


def north_rect(cube):
    uv, s = cube['uv'], cube['size']
    if isinstance(uv, dict):
        f = uv['north']
        return f['uv'] + f['uv_size']
    d = int(s[2])
    return [uv[0] + d, uv[1] + d, int(s[0]), int(s[1])]


def corners(c):
    """All 8 corners of a cube, rotated about its pivot (conservative: X, then Y, then Z)."""
    o, sz = c['origin'], c['size']
    pts = [(o[0] + dx * sz[0], o[1] + dy * sz[1], o[2] + dz * sz[2]) for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
    r = c.get('rotation')
    if not r or not any(r):
        return pts
    p = c['pivot']
    rx, ry, rz = (math.radians(v) for v in r)
    out = []
    for x, y, z in pts:
        x, y, z = x - p[0], y - p[1], z - p[2]
        y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
        x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
        x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
        out.append((x + p[0], y + p[1], z + p[2]))
    return out


def lid_tile(base):
    """16x16 hatch lid: base colour, dark rim, light inner frame, four rivets."""
    t = Image.new('RGBA', (TILE, TILE), base + (255,))
    px = t.load()

    def mul(c, k):
        return tuple(max(0, min(255, int(v * k))) for v in c) + (255,)
    for i in range(TILE):
        for j in range(TILE):
            edge = min(i, j, TILE - 1 - i, TILE - 1 - j)
            if edge == 0:
                px[i, j] = mul(base, 0.62)
            elif edge == 1:
                px[i, j] = mul(base, 1.18)
            elif edge == 2:
                px[i, j] = mul(base, 0.86)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        px[x, y] = mul(base, 1.35)
    for i in range(5, 11):  # centre handle bar
        px[i, 7] = mul(base, 0.7)
        px[i, 8] = mul(base, 1.25)
    return t


def process(name):
    sp = SPECS[name]
    geo_path = ASSETS / f'geo/{name}.geo.json'
    tex_path = ASSETS / f'textures/entity/{name}.png'
    BACKUP.mkdir(parents=True, exist_ok=True)
    for p in (geo_path, tex_path):
        if not (BACKUP / p.name).exists():
            shutil.copy(p, BACKUP / p.name)
    geo = json.loads(geo_path.read_text())
    g = geo['minecraft:geometry'][0]
    desc = g['description']
    png = Image.open(tex_path).convert('RGBA')
    th = desc.get(BASE_KEY, desc['texture_height'])
    png = png.crop((0, 0, png.width, th))
    lid = sp['lid']
    prev_lids = [c for b in g['bones'] if b['name'].startswith(lid) for c in b.get('cubes', [])]
    bones = [b for b in g['bones'] if not (b['name'].startswith(('msl', 'bore')) or b['name'].startswith(lid))]
    byname = {b['name']: b for b in bones}
    rack = byname['rack']
    fz = sp['face_z']
    n = len(sp['slots'])

    # ---- lids: old bones were mis-positioned duplicates; S-400 keeps its baked plates (own texture), moved into the lid bones
    baked = []
    if sp.get('keep_baked_lids'):
        for c in list(rack['cubes']) + prev_lids:
            if c['size'] == [9, 8, 1] and abs(c['origin'][2] - (fz - 1)) < 1e-6 and isinstance(c['uv'], list) and c['uv'] != [0, 0] and c not in baked:
                baked.append(c)
                if c in rack['cubes']:
                    rack['cubes'].remove(c)
    # colour of the front face: biggest xy area among cubes at the front
    front = [c for c in rack['cubes'] if abs(c['origin'][2] - fz) < 1.01 and not c.get('rotation')]
    front = front or [c for c in rack['cubes'] if abs(c['origin'][2] - fz) < 3.01]
    ref = max(front, key=lambda c: c['size'][0] * c['size'][1])
    base = median_color(png, north_rect(ref))

    atlas_h = TILE
    atlas = Image.new('RGBA', (256, atlas_h), (0, 0, 0, 0))
    atlas.paste(lid_tile(base), (0, 0))
    atlas.paste(Image.new('RGBA', (4, 4), (14, 16, 14, 255)), (TILE, 0))
    lid_uv = {f: {'uv': [0, th], 'uv_size': [TILE, TILE]} for f in FACES}
    bore_uv = {'north': {'uv': [TILE, th], 'uv_size': [4, 4]}}

    # ---- missiles: one shared set of faces, copied to every slot
    allm = []
    per_slot = []
    for sx, sy in sp['slots']:
        cubes = mk.build(sp['L'], sp['D'], fz + 2, sx, sy)
        per_slot.append(cubes)
        allm += cubes
    m_atlas, mh = mk.pack(allm, mk.PALETTES[sp['palette']], 256, 0, th + atlas_h)
    # containment check (rotated plates are approximated by their unrotated box)
    for i, cubes in enumerate(per_slot):
        x0, x1, y0, y1, z0, z1 = sp['box'](*sp['slots'][i])
        pts = [p for c in cubes for p in corners(c)]
        lo = [min(p[k] for p in pts) for k in range(3)]
        hi = [max(p[k] for p in pts) for k in range(3)]
        if not (lo[0] >= x0 - 0.01 and hi[0] <= x1 + 0.01 and lo[1] >= y0 - 0.01 and hi[1] <= y1 + 0.01 and lo[2] >= z0 - 0.01 and hi[2] <= z1 + 0.01):
            print(f'  WARN {name} slot {i}: missile bbox {lo}..{hi} leaves tube {(x0, y0, z0)}..{(x1, y1, z1)}')

    new = []
    for i, (sx, sy) in enumerate(sp['slots']):
        lw, lh = sp['lid_wh']
        if baked:
            plate = min(baked, key=lambda c: (c['origin'][0] + c['size'][0] / 2 - sx) ** 2 + (c['origin'][1] + c['size'][1] / 2 - sy) ** 2)
            lid_cubes = [plate]
            cx, cy = plate['origin'][0] + plate['size'][0] / 2, plate['origin'][1] + plate['size'][1] / 2
        else:
            cx, cy = sx, sy
            lid_cubes = [{'origin': [round(cx - lw / 2, 3), round(cy - lh / 2, 3), fz - 1], 'size': [lw, lh, 1], 'uv': copy.deepcopy(lid_uv)}]
        new.append({'name': f'{lid}{i}', 'parent': 'rack', 'pivot': [cx, cy, fz - 0.5], 'cubes': lid_cubes})
        bw, bh = sp['bore_wh']
        new.append({'name': f'bore{i}', 'parent': 'rack', 'pivot': [sx, sy, fz],
                    'cubes': [{'origin': [round(sx - bw / 2, 3), round(sy - bh / 2, 3), fz - 0.4], 'size': [bw, bh, 0.4], 'uv': copy.deepcopy(bore_uv)}]})
        new.append({'name': f'msl{i}', 'parent': 'rack', 'pivot': [sx, sy, fz + 2], 'cubes': per_slot[i]})
    at = bones.index(rack) + 1
    bones[at:at] = new
    g['bones'] = bones

    total_h = th + atlas_h + mh
    out = Image.new('RGBA', (png.width, total_h), (0, 0, 0, 0))
    out.paste(png, (0, 0))
    out.paste(atlas, (0, th))
    out.paste(m_atlas, (0, th + atlas_h))
    desc['texture_height'] = total_h
    desc[BASE_KEY] = th
    geo_path.write_text(json.dumps(geo, indent=1))
    out.save(tex_path)
    install.variants(tex_path, name)  # damaged / burnt / glowmask copies with the new size
    print(f'{name}: {n} tubes, lid colour {base}, texture {png.width}x{th} -> {out.width}x{total_h}, {sum(len(c) for c in per_slot)} missile cubes')


def strip(name):
    """Remove lids, bores, missiles and the appended atlas again (back to the plain installed model)."""
    sp = SPECS[name]
    geo_path = ASSETS / f'geo/{name}.geo.json'
    tex_path = ASSETS / f'textures/entity/{name}.png'
    geo = json.loads(geo_path.read_text())
    g = geo['minecraft:geometry'][0]
    desc = g['description']
    if BASE_KEY not in desc:
        print(name, 'nothing to strip')
        return
    th = desc.pop(BASE_KEY)
    g['bones'] = [b for b in g['bones'] if not b['name'].startswith(('msl', 'bore', sp['lid']))]
    desc['texture_height'] = th
    geo_path.write_text(json.dumps(geo, indent=1))
    png = Image.open(tex_path).convert('RGBA')
    png.crop((0, 0, png.width, th)).save(tex_path)
    install.variants(tex_path, name)
    print(name, f'stripped, texture back to {png.width}x{th}')


if __name__ == '__main__':
    args = sys.argv[1:]
    if args and args[0] == '--strip':
        for a in args[1:]:
            strip(a)
    else:
        for a in args:
            process(a)
