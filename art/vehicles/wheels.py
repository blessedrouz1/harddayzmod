"""Repaint wheels of a built vehicle: round disc with lug nuts on the hub plate, patterned tyre sidewall.

Run on the fresh build output (bc.sh does it right after build_vehicle): out/NAME.png + out/NAME.geo.json.
Colour ramps are sampled from the original hub (paint, per vehicle) and tread (rubber), so every vehicle keeps its own
paint. A repeat run on an already painted PNG is skipped (hash kept in out/NAME.wheels.json).

Sidewall: the eight tyre plates share one plane, so the pattern depends only on (column, ring row) and leaves the
plate ends (columns 0 and 8, inner rows) in plain rubber: where two plates overlap they show the same colour.
usage: python3 wheels.py [--installed] NAME [NAME...]   (--installed repaints the mod textures in place, variants included)
"""
import base64
import hashlib
import json
import math
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import install  # noqa: E402  bone_name, variants, ASSETS


def face_rect(c, face):
    u, v = c['uv']
    w, h, d = (int(x) for x in c['size'])
    return {'east': (u, v + d, d, h), 'west': (u + d + w, v + d, d, h), 'up': (u + d, v, w, d)}[face]


def lum(c):
    return c[0] * 0.3 + c[1] * 0.59 + c[2] * 0.11


def scale(c, k):
    return tuple(min(255, max(0, int(round(x * k)))) for x in c[:3])


def paint_ramp(px, hub):
    """(DEEP, SHADOW, BASE, LIGHT) of the vehicle paint, read from the untouched hub plate."""
    x, y, n, _ = face_rect(hub, 'east')
    q = max(1, n // 9)
    light, shadow = px[x, y][:3], px[x + n - 1, y + n - 1][:3]
    deep = px[x + q, y + n // 2][:3]
    ring = {px[x + s, y + t][:3] for t in range(n) for s in range(n)
            if 2 * q <= min(s, n - 1 - s, t, n - 1 - t) < 4 * q}
    base = max(ring, key=lum)
    return deep, shadow, base, light


def rubber_ramp(px, tyre):
    x, y, w, h = face_rect(tyre, 'up')
    cols = sorted({px[x + s, y + t][:3] for t in range(h) for s in range(w)}, key=lum)
    shadow, base = cols[0], cols[-1]
    return scale(shadow, 0.72), shadow, base, scale(base, 1.55)


def hub_pattern(n, ramp):
    """n x n hub face: dark seat, two-pixel lip, ring of 8 lug nuts on a steel plate, domed centre cap."""
    deep, shadow, base, light = ramp
    f = 12 / n                                           # design is drawn for n = 12 and scaled
    half = n / 2
    nut = set()
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        nut.add((int(half + 2.9 / f * math.cos(a) + (0 if math.cos(a) > 0 else 0)),
                 int(half + 2.9 / f * math.sin(a))))
    grid = [[deep] * n for _ in range(n)]
    for j in range(n):
        for i in range(n):
            dx, dy = i + 0.5 - half, j + 0.5 - half
            r = math.hypot(dx, dy) * f
            lit = dx + dy < 0
            if r >= 6.1:
                c = deep                                   # seat shadow in the square corners
            elif r >= 4.6:
                c = light if lit else base                 # lip, two pixels thick
            elif r >= 3.9:
                c = deep                                   # groove behind the lip
            elif r >= 2.2:
                c = shadow                                 # steel plate
                if (i, j) in nut:
                    c = light
            elif r >= 1.5:
                c = deep                                   # shadow gap round the cap
            else:
                c = light if (lit and r > 0.9) else base   # raised centre cap
            grid[j][i] = c
    # lug nut gets a dark pixel under it on the shaded side
    for (i, j) in nut:
        for oi, oj in ((1, 0), (0, 1)):
            ii, jj = i + oi, j + oj
            if 0 <= ii < n and 0 <= jj < n and grid[jj][ii] == shadow:
                grid[jj][ii] = deep
    # keep the sampling texels of paint_ramp valid on a repaint
    grid[0][0], grid[n - 1][n - 1] = light, shadow
    q = max(1, n // 9)
    grid[n // 2][q] = deep
    return grid


def sidewall_pattern(w, h, ramp):
    """w x h sidewall of one tyre plate (w = 9 columns along the plate, h = 4 rows from the outside in)."""
    deep, shadow, base, light = ramp
    grid = [[shadow] * w for _ in range(h)]
    ends = {0, w - 1}
    for s in range(w):
        if s in ends:
            continue
        grid[0][s] = base if s % 2 == 1 else shadow                  # shoulder lugs
    # embossed lettering on the second ring row: two short dashes either side of the middle
    if h > 1:
        for s in list(range(2, w // 2 - 1)) + list(range(w // 2 + 2, w - 2)):
            grid[1][s] = light
    # bead ring against the rim
    if h > 3:
        for s in range(1, w - 1):
            grid[h - 1][s] = deep
    return grid


def blit(px, rect, grid):
    x, y, w, h = rect
    for j in range(h):
        for i in range(w):
            px[x + i, y + j] = tuple(grid[j][i][:3]) + (255,)


def wheel_cubes(geo_path):
    """(hubs, tyres) cube dicts of the dish wheels in geo_path. A dish wheel bone holds eight tyre plates, four rim bars
    and the hub boss, in that order (build_vehicle.dish_wheel_cubes)."""
    hubs, tyres = [], []
    for b in json.loads(geo_path.read_text())['minecraft:geometry'][0]['bones']:
        cubes = b.get('cubes', [])
        if not b['name'].startswith(('wheel', 'spare_wheel')) or len(cubes) < 12:
            continue
        if any(c['size'] != cubes[0]['size'] for c in cubes[:8]):
            raise SystemExit(f'{geo_path.name}: bone {b["name"]} is not a dish wheel')
        tyres += cubes[:8]
        if len(cubes) == 13:                      # old square hub plate (gen.py wheel_hubs now builds a round boss instead)
            hubs.append(cubes[12])
    return hubs, tyres


def embed_in_bbmodel(name):
    """Blockbench opens out/NAME.bbmodel, which carries its own copy of the texture: refresh it from out/NAME.png."""
    bb = HERE / f'out/{name}.bbmodel'
    if not bb.exists():
        return
    data = json.loads(bb.read_text())
    src = 'data:image/png;base64,' + base64.b64encode((HERE / f'out/{name}.png').read_bytes()).decode()
    for t in data.get('textures', []):
        if t.get('name') == f'{name}.png':
            t['source'] = src
    bb.write_text(json.dumps(data))


def paint(name, installed=False):
    """Paint out/NAME.png, or with installed=True the mod's texture (+ damaged / burnt / glowmask variants)."""
    ents = install.ASSETS / 'textures/entity'
    png = ents / f'{name}.png' if installed else HERE / f'out/{name}.png'
    geo_path = install.ASSETS / f'geo/{name}.geo.json' if installed else HERE / f'out/{name}.geo.json'
    stamp = HERE / f'out/{name}.wheels{".inst" if installed else ""}.json'
    digest = hashlib.sha1(png.read_bytes()).hexdigest()
    if stamp.exists() and json.loads(stamp.read_text()).get('painted') == digest:
        print(name, 'wheels already painted')
        if not installed:
            embed_in_bbmodel(name)
        return
    im = Image.open(png).convert('RGBA')
    px = im.load()
    hubs, tyres = wheel_cubes(geo_path)
    if not tyres:
        print(name, 'no dish wheels')
        return
    rr = rubber_ramp(px, tyres[0])
    pr = paint_ramp(px, hubs[0]) if hubs else None
    for c in hubs:
        n = face_rect(c, 'east')[2]
        grid = hub_pattern(n, pr)
        for f in ('east', 'west'):
            blit(px, face_rect(c, f), grid)
    for c in tyres:
        for f in ('east', 'west'):
            rect = face_rect(c, f)
            blit(px, rect, sidewall_pattern(rect[2], rect[3], rr))
    im.save(png)
    if installed:
        install.variants(png, name)
    else:
        embed_in_bbmodel(name)
    stamp.write_text(json.dumps({'painted': hashlib.sha1(png.read_bytes()).hexdigest()}))
    print(name, f'wheels painted{" (installed)" if installed else ""}: {len(tyres)} tyre plates, {len(hubs)} hubs')


if __name__ == '__main__':
    args = sys.argv[1:]
    inst = '--installed' in args
    for n in (a for a in args if a != '--installed'):
        paint(n, inst)
