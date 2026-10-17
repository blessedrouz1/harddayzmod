"""Panel detail for the Shahed / Geran delta-wing texture (geran2.png, shahed136.png share one geo + UV layout).

The atlas is painted flat: every swept wing strip is one colour, so the wing reads as a single black / white sheet. This reads each
face rectangle from the installed geo and adds, on top of the existing colour: lit leading edge, panel seams with rivet rows, per-strip
tone, paint chips, fuselage band seams and an exhaust stain behind the engine. Only brightness changes, hue is kept.

usage: python3 drone_detail.py NAME [NAME...]   (originals saved once as textures/entity/NAME.png.pre_detail next to the texture)
"""
import json
import random
import shutil
import sys
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent.parent / 'src/main/resources/assets/harddayz'


def rect(face):
    (u, v), (w, h) = face['uv'], face['uv_size']
    x0, x1 = sorted((u, u + w))
    y0, y1 = sorted((v, v + h))
    return int(x0), int(y0), int(x1 - x0), int(y1 - y0)


class Painter:
    def __init__(self, im, seed):
        self.px = im.load()
        self.size = im.size
        self.rnd = random.Random(seed)

    def tone(self, x, y, k):
        if not (0 <= x < self.size[0] and 0 <= y < self.size[1]):
            return
        r, g, b, a = self.px[x, y]
        if a:
            self.px[x, y] = (min(255, max(0, int(r * k))), min(255, max(0, int(g * k))), min(255, max(0, int(b * k))), a)

    def rect_tone(self, x, y, w, h, k):
        for j in range(h):
            for i in range(w):
                self.tone(x + i, y + j, k)

    def wing_strip(self, x, y, w, h, idx):
        """Swept wing strip top / bottom: w px across, h px along."""
        self.rect_tone(x, y, w, h, 1.0 + (idx % 3 - 1) * 0.045)       # strips are separate skin panels
        for j in range(h):
            self.tone(x, y + j, 1.16)                                  # lit edge
            self.tone(x + w - 1, y + j, 0.84)                          # shadow edge
        for j in range(5, h - 1, 6):                                   # panel seam + rivets either side
            for i in range(w):
                self.tone(x + i, y + j, 0.72)
                self.tone(x + i, y + j + 1, 1.07)
            if w >= 3:
                self.tone(x + 1, y + j - 1, 0.8)
        self.chips(x, y, w, h, 0.06)

    def flat_panel(self, x, y, w, h, seam=7):
        """Big flat face (wing root slab, wing tip skirt, fuselage side)."""
        for j in range(h):
            self.tone(x, y + j, 1.1 if h > 1 else 1.0)
        for i in range(seam, w - 1, seam):
            for j in range(h):
                self.tone(x + i, y + j, 0.74)
                self.tone(x + i + 1, y + j, 1.07)
        if h >= 4:
            for i in range(2, w - 1, 4):                               # rivet rows
                self.tone(x + i, y + 1, 0.78)
                self.tone(x + i, y + h - 2, 0.78)
        self.chips(x, y, w, h, 0.05)

    def chips(self, x, y, w, h, p):
        for j in range(h):
            for i in range(w):
                if self.rnd.random() < p:
                    self.tone(x + i, y + j, 1.22 if self.rnd.random() < 0.6 else 0.7)

    def stain(self, x, y, w, h):
        """Exhaust soot: darkest at the aft end (row 0 .. h)."""
        for j in range(h):
            for i in range(w):
                if self.rnd.random() < 0.85 * (1 - j / h) + 0.1:
                    self.tone(x + i, y + j, 0.82)


def run(name):
    geo = json.loads((ASSETS / f'geo/{name}.geo.json').read_text())['minecraft:geometry'][0]
    tex = ASSETS / f'textures/entity/{name}.png'
    bak = tex.with_suffix('.png.pre_detail')
    if not bak.exists():
        shutil.copy(tex, bak)
    im = Image.open(bak).convert('RGBA')                           # always start from the original, so a re-run does not stack
    P = Painter(im, name)
    strips = 0
    for b in geo['bones']:
        for ci, c in enumerate(b.get('cubes', [])):
            sx, sy, sz = c['size']
            for fname, face in c['uv'].items():
                x, y, w, h = rect(face)
                if w < 1 or h < 1:
                    continue
                if b['name'] == 'wing' and c.get('rotation'):
                    continue                                       # swept strips overlap coplanar: any per-strip pattern would z-fight
                if fname in ('up', 'down') and w >= 8 and h >= 5:
                    P.flat_panel(x, y, w, h)
                elif fname in ('east', 'west') and w >= 12 and h >= 3:
                    P.flat_panel(x, y, w, h, seam=8)
                elif fname == 'up' and b['name'] == 'body' and w >= 4 and h >= 10:
                    pass
    # body top / sides (cube 0: 5x5x34): seams every 8 px along the length, soot at the engine end
    c0 = geo['bones'][0]['cubes'][0]
    for fname in ('up', 'down'):
        x, y, w, h = rect(c0['uv'][fname])
        for j in range(8, h - 1, 8):
            for i in range(w):
                P.tone(x + i, y + j, 0.72)
                P.tone(x + i, y + j + 1, 1.08)
        P.stain(x, y + h - 6, w, 6)
    im.save(tex)
    print(name, 'wing strips detailed:', strips)


if __name__ == '__main__':
    for n in sys.argv[1:]:
        run(n)
