"""Install a built vehicle (out/<name>.*) into the mod: bone names per mod contract, texture variants, glowmasks.

usage: python3 install.py NAME [NAME...]
"""
import json
import random
import re
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parent.parent / 'src/main/resources/assets/harddayz'
# lit materials of the skill palette (headlight, amber, red: base and light tones) glow at night
GLOW = {(0xDB, 0xDE, 0xCE), (0xEE, 0xF0, 0xE4), (0xD6, 0x9B, 0x2C), (0xE8, 0xB8, 0x5A), (0xA4, 0x42, 0x32), (0xC2, 0x5E, 0x4A)}


def bone_name(n):
    n = re.sub(r'^(wheel|steer)_(\d+)_(r|l)$', r'\1_\2\3', n)
    n = re.sub(r'^(wheel|steer)_(\d+)_right$', r'\1_\2r', n)
    n = re.sub(r'^(wheel|steer)_(\d+)_left$', r'\1_\2l', n)
    return n


def blobs(size, seed, count, rmin, rmax, cell=4):
    """Blocky blobs (4 px cells) for soot and burns: connected spots, not per-pixel noise."""
    rnd = random.Random(seed)
    w, h = size
    out = set()
    for _ in range(count):
        cx, cy = rnd.randrange(w // cell), rnd.randrange(h // cell)
        r = rnd.uniform(rmin, rmax)
        for dx in range(-int(r) - 1, int(r) + 2):
            for dy in range(-int(r) - 1, int(r) + 2):
                if dx * dx + dy * dy <= r * r * rnd.uniform(0.6, 1.0):
                    out.add((cx + dx, cy + dy))
    return out


def variants(src, name):
    im = Image.open(src).convert('RGBA')
    im.save(ASSETS / f'textures/entity/{name}.png')
    w, h = im.size
    px = im.load()
    soot = blobs(im.size, hash(name) & 0xFFFF, w * h // 4000, 1.0, 3.0)
    for suffix, dark, soot_k in (('_damaged', 0.82, 0.55), ('_burnt', 0.42, 0.3)):
        out = im.copy()
        q = out.load()
        for y in range(h):
            for x in range(w):
                r, g, b, a = px[x, y]
                if a == 0:
                    continue
                k = dark * (soot_k if (x // 4, y // 4) in soot else 1.0)
                if suffix == '_burnt':
                    grey = (r + g + b) / 3  # paint burns off to grey-brown char
                    r, g, b = grey * 1.05, grey * 0.92, grey * 0.8
                    a = 255 if a < 255 else a  # smoked glass
                q[x, y] = (int(r * k), int(g * k), int(b * k), a)
        out.save(ASSETS / f'textures/entity/{name}{suffix}.png')
    for suffix in ('', '_damaged', '_burnt'):
        glow = Image.new('RGBA', im.size, (0, 0, 0, 255))
        g = glow.load()
        if suffix != '_burnt':  # burnt-out wreck has no lights
            for y in range(h):
                for x in range(w):
                    if px[x, y][:3] in GLOW:
                        g[x, y] = px[x, y][:3] + (255,)
        glow.save(ASSETS / f'textures/entity/{name}{suffix}_glowmask.png')


def install(name):
    geo = json.loads((HERE / f'out/{name}.geo.json').read_text())
    g = geo['minecraft:geometry'][0]
    for b in g['bones']:
        b['name'] = bone_name(b['name'])
        if 'parent' in b:
            b['parent'] = bone_name(b['parent'])
    names = {b['name'] for b in g['bones']}
    (ASSETS / f'geo/{name}.geo.json').write_text(json.dumps(geo, indent=1))
    variants(HERE / f'out/{name}.png', name)
    print(name, 'bones:', sorted(n for n in names if re.match(r'(wheel|steer|jack)', n) or n in ('cab', 'hull', 'turret', 'rack', 'radar')))


if __name__ == '__main__':
    for n in sys.argv[1:]:
        install(n)
