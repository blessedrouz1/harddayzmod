"""Stepped shading on side faces of painted parts (bumpers, aprons, crates, decks, kungs...).

Each side face gets discrete tone bands: a lit top row, the base, then two darker bands and a dark
bottom row - pixel-art "gradient" with hard steps, no noise. Lamps, glass and wheels are untouched.
Works on the build output (out/NAME.png) using the box UV of out/NAME.geo.json.
usage: python3 shade.py NAME [NAME...]
"""
import json
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
SKIP_NAMES = ('tyre', 'rim', 'hub', 'wheel', 'whip', 'antenna', 'glass', 'steering', 'seat', 'liner', 'dashboard', 'headliner',
              'cab_floor', 'mount')
# lamp and glass ramps of the skill palette: never re-tone them (glowmask and glass rely on exact colours)
KEEP = {(0x8E, 0x91, 0x88), (0xB4, 0xB7, 0xA9), (0xDB, 0xDE, 0xCE), (0xEE, 0xF0, 0xE4),
        (0x7A, 0x5A, 0x1E), (0xA8, 0x79, 0x2A), (0xD6, 0x9B, 0x2C), (0xE8, 0xB8, 0x5A),
        (0x5E, 0x28, 0x20), (0x7E, 0x35, 0x29), (0xA4, 0x42, 0x32), (0xC2, 0x5E, 0x4A),
        (0x2A, 0x33, 0x35), (0x5E, 0x7F, 0x8A), (0x8E, 0xC3, 0xD6), (0xC9, 0xE6, 0xEE)}


def band(t, h):
    """Brightness factor for row t of a face h rows tall (top = 0)."""
    if h < 3:
        return 1.0
    if t == 0:
        return 1.09
    if t == h - 1:
        return 0.84
    k = t / (h - 1)
    if h >= 6 and k >= 0.78:
        return 0.9
    if h >= 4 and k >= 0.5:
        return 0.95
    return 1.0


def shade(name):
    geo = json.loads((HERE / f'out/{name}.geo.json').read_text())['minecraft:geometry'][0]
    parts = json.loads((HERE / f'out/{name}.parts.json').read_text())
    png = HERE / f'out/{name}.png'
    im = Image.open(png).convert('RGBA')
    px = im.load()
    done = 0
    # chamfered parts: their north/south ends are one flat profile with the slope plates - leave them alone
    chamfered = {n.split('_chamfer_')[0] for n in parts.values() if '_chamfer_' in n}
    for b in geo['bones']:
        for i, c in enumerate(b.get('cubes', [])):
            pname = parts.get(f'{b["name"]}#{i}', '')
            if any(k in pname for k in SKIP_NAMES) or any(c.get('rotation', [0, 0, 0])):
                continue
            u, v = c['uv']
            w, h, d = (int(x) for x in c['size'])
            if h < 3:
                continue
            faces = [(u, d), (u + d, w), (u + d + w, d), (u + 2 * d + w, w)]  # east, north, west, south
            base = pname[:-4] if pname.endswith('_top') else pname
            if base in chamfered:
                faces = [faces[0], faces[2]]
            for fx, fw in faces:
                for t in range(h):
                    k = band(t, h)
                    if k == 1.0:
                        continue
                    for s in range(fw):
                        r, g, bl, a = px[fx + s, v + d + t]
                        if a < 255 or (r, g, bl) in KEEP:
                            continue
                        px[fx + s, v + d + t] = (min(255, int(r * k)), min(255, int(g * k)), min(255, int(bl * k)), a)
            done += 1
    im.save(png)
    print(name, 'shaded cubes:', done)


if __name__ == '__main__':
    for n in sys.argv[1:]:
        shade(n)
