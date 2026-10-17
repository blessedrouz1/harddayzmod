"""Repaint the FPV drone (entity texture + hi-res UV) after the real photo: carbon frame, blue LiPo with black strap,
gold-olive PG-7 style warhead with black clamps, red-tipped antenna, green-black motors.

usage: python3 fpv_tex.py [--install] [out.png]
Reads the ORIGINAL geo (out/fpv.geo.json.pre_hires, 1 texel per unit), scales every face UV by S, paints every face by cube role,
writes out/fpv.png + out/fpv.geo.json; --install copies both into src/main/resources/assets/harddayz.
"""
import json
import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
OUT = HERE / 'out'
ASSETS = HERE.parent.parent / 'src/main/resources/assets/harddayz'
S = 4
TW, TH = 256 * S, 32 * S
rng = np.random.default_rng(1720)


# ---------------------------------------------------------------- helpers
# Mod style (see shade.py / grad.png): flat muted ramps, hard tone steps, 1px panel seams + rivets, sparse dark wear, NO noise / gloss.
def col(c):
    return np.array(c, float)


CARBON = ((24, 26, 28), (36, 39, 41), (54, 58, 60))
METAL = ((66, 70, 68), (102, 106, 102), (142, 146, 140))
OLIVE = ((38, 42, 32), (54, 60, 44), (76, 82, 62))
BLUE = ((38, 54, 78), (58, 82, 114), (84, 112, 146))
BLACK = ((16, 17, 18), (26, 27, 28), (44, 46, 48))
GOLD = ((88, 76, 36), (128, 110, 52), (166, 146, 80))
LAMP_Y = ((0x7A, 0x5A, 0x1E), (0xA8, 0x79, 0x2A), (0xD6, 0x9B, 0x2C))
LAMP_R = ((0x5E, 0x28, 0x20), (0x7E, 0x35, 0x29), (0xA4, 0x42, 0x32))
GLASS = ((0x2A, 0x33, 0x35), (0x5E, 0x7F, 0x8A), (0x8E, 0xC3, 0xD6), (0xC9, 0xE6, 0xEE))
MARK = (0xB4, 0xB7, 0xA9)


class Face:
    """one painted rectangle of the atlas; .px is a float (h, w, 3) view, name = north/south/east/west/up/down"""

    def __init__(self, img, name, x, y, w, h):
        self.img, self.name, self.x, self.y, self.w, self.h = img, name, x, y, w, h
        self.px = np.zeros((h, w, 3))

    @property
    def side(self):
        return self.name in ('north', 'south', 'east', 'west')

    def fill(self, c):
        self.px[:] = col(c)

    def rect(self, x0, y0, x1, y1, c):
        x0, y0, x1, y1 = max(0, int(x0)), max(0, int(y0)), min(self.w, int(x1)), min(self.h, int(y1))
        if x1 > x0 and y1 > y0:
            self.px[y0:y1, x0:x1] = col(c)

    def paint(self, ramp):
        """stepped shading: lit top row, base, two darker bands, dark bottom row (up = base, down = darkest)"""
        dark, mid, light = ramp[:3]
        if self.name == 'up':
            self.fill(mid)
            self.rect(0, 0, self.w, 1, light)
            return
        if self.name == 'down':
            self.fill(dark)
            return
        h, w = self.h, self.w
        self.fill(mid)
        if h >= 4:
            self.rect(0, 0, w, max(1, h // 8), light)
            self.rect(0, int(h * 0.58), w, int(h * 0.82), tuple((np.array(mid) + np.array(dark)) / 2))
            self.rect(0, int(h * 0.82), w, h - max(1, h // 8), dark)
            self.rect(0, h - max(1, h // 8), w, h, tuple(np.array(dark) * 0.7))

    def seam(self, step, c, offset=0, vertical=True):
        if vertical:
            for x in range(offset, self.w, step):
                self.rect(x, 0, x + 1, self.h, c)
        else:
            for y in range(offset, self.h, step):
                self.rect(0, y, self.w, y + 1, c)

    def rivets(self, step, c, margin=2):
        for y in range(margin, self.h - 1, step):
            for x in range(margin, self.w - 1, step):
                self.rect(x, y, x + 1, y + 1, c)

    def wear(self, ramp, density=0.012):
        m = rng.random((self.h, self.w)) < density
        self.px[m] = col(ramp[0]) * 0.8

    def commit(self):
        self.img[self.y:self.y + self.h, self.x:self.x + self.w, :3] = np.clip(np.round(self.px), 0, 255)
        self.img[self.y:self.y + self.h, self.x:self.x + self.w, 3] = 255


FONT = {'0': '111101101101111', '1': '010110010010111', '2': '111001111100111', '3': '111001111001111',
        '4': '101101111001001', '5': '111100111001111', '6': '111100111101111', '7': '111001001001001'}


def glyph(f, ch, x, y, c, k=1):
    for i, bit in enumerate(FONT[ch]):
        if bit == '1':
            f.rect(x + (i % 3) * k, y + (i // 3) * k, x + (i % 3) * k + k, y + (i // 3) * k + k, c)


# ---------------------------------------------------------------- role painters
def carbon(f, bolts=False):
    f.paint(CARBON)
    if f.name == 'up':
        f.seam(max(8, f.w), CARBON[0], f.w // 2)
        if bolts and f.h >= 12:
            for yb in (3, f.h - 4):
                f.rect(f.w // 2 - 1, yb - 1, f.w // 2 + 1, yb + 1, METAL[1])
    f.wear(CARBON, 0.01)


def standoff(f):
    f.paint(METAL)
    if f.side:
        f.rect(f.w - 1, 0, f.w, f.h, METAL[0])
    f.rect(0, 0, f.w, 1, GOLD[1])


def pcb(f, green):
    f.paint(((18, 30, 24), (28, 44, 34), (46, 66, 50)) if green else BLACK)
    if f.side and f.w > 6:
        f.rect(f.w // 2 - 3, 0, f.w // 2 + 3, f.h, LAMP_Y[1])         # XT60 connector
        f.rect(f.w // 2 - 3, f.h - 1, f.w // 2 + 3, f.h, LAMP_Y[0])
        f.rect(f.w // 2 - 1, 0, f.w // 2 + 1, f.h, LAMP_R[1])
    else:
        f.rivets(4, GOLD[1], 1)


def motor_bell(f):
    f.paint(OLIVE)
    if f.side:
        f.seam(2, OLIVE[0], 1)
        f.rect(0, 0, f.w, 1, METAL[1])
        f.rect(0, f.h - 1, f.w, f.h, OLIVE[0])
    else:
        f.rect(0, 0, f.w, f.h, OLIVE[0]) if f.name == 'down' else None


def motor_cap(f):
    f.paint(OLIVE)
    if f.name == 'up':
        cx, cy = f.w / 2, f.h / 2
        yy, xx = np.mgrid[0:f.h, 0:f.w]
        r = np.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
        f.px[r < f.w * 0.40] = col(OLIVE[0])
        f.px[r < f.w * 0.30] = col(METAL[1])
        f.px[r < f.w * 0.16] = col(METAL[0])
        for sx in (0.14, 0.86):
            for sy in (0.14, 0.86):
                f.rect(int(f.w * sx), int(f.h * sy), int(f.w * sx) + 1, int(f.h * sy) + 1, METAL[2])


def hub(f):
    f.paint(METAL)
    f.rect(0, 0, f.w, 1, METAL[2])


BLADE = ((44, 47, 46), (62, 66, 64), (96, 100, 96))


def blade(f, tip=False):
    f.paint(BLADE)
    if f.name in ('up', 'down'):
        f.rect(0, 0, f.w, 1, BLADE[2])                               # leading edge
        f.rect(0, f.h - 1, f.w, f.h, BLADE[0])                       # trailing edge
    if tip:
        f.fill(BLADE[2] if f.name != 'down' else BLADE[1])


def battery(f):
    f.paint(BLUE)
    if f.side and f.w > 16:
        f.rect(int(f.w * 0.58), 1, int(f.w * 0.58) + 1, f.h - 1, BLUE[0])        # shrink-wrap seam
    if f.name == 'up':
        f.rect(0, 0, f.w, 1, BLUE[2])
        f.rect(0, f.h - 1, f.w, f.h, BLUE[0])
        f.rect(0, 0, 1, f.h, BLUE[2])
        f.rect(f.w - 1, 0, f.w, f.h, BLUE[0])
    f.wear(BLUE, 0.008)


def strap(f, mark=False):
    f.paint(BLACK)
    if f.side:
        f.seam(4, BLACK[0], 0, vertical=False) if f.h >= 8 else None
    if mark and f.name in ('north', 'south') and f.w >= 12 and f.h >= 9:
        x = 2
        for ch in '2701':
            glyph(f, ch, x, 3, LAMP_Y[1])
            x += 4
        f.rect(f.w - 5, 2, f.w - 2, f.h - 2, METAL[1])
        f.rect(f.w - 4, 3, f.w - 3, f.h - 3, BLACK[0])


def cam_body(f):
    f.paint(BLACK)
    if f.name in ('east', 'west') and f.w > 4:
        f.rect(f.w // 2 - 1, f.h // 2 - 1, f.w // 2 + 1, f.h // 2 + 1, METAL[1])


def lens(f):
    if f.name == 'north':
        f.fill(BLACK[0])
        cx, cy, R = f.w / 2, f.h / 2, min(f.w, f.h) / 2
        yy, xx = np.mgrid[0:f.h, 0:f.w]
        r = np.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
        f.px[r < R * 0.80] = col(METAL[0])
        f.px[r < R * 0.60] = col(GLASS[1])
        f.px[r < R * 0.34] = col(GLASS[0])
        f.rect(int(cx - R * 0.40), int(cy - R * 0.40), int(cx - R * 0.40) + 2, int(cy - R * 0.40) + 2, GLASS[3])
    else:
        f.paint(BLACK)


def antenna(f):
    f.paint(BLACK)


def antenna_tip(f):
    f.paint(LAMP_R)


def warhead(f):
    if not f.side and f.name != 'up' and f.name != 'down':
        f.fill(GOLD[0])
        return
    if f.name in ('north', 'south'):
        f.fill(GOLD[0])
        return
    f.paint(GOLD)
    if f.name in ('east', 'west') and f.w >= 24:
        mx = int(f.w * (0.63 if f.name == 'east' else 0.37))
        glyph(f, '0', mx - 4, f.h // 2 - 3, MARK, 1) if f.h >= 10 else None
        glyph(f, '1', mx + 1, f.h // 2 - 3, MARK, 1) if f.h >= 10 else None
    f.wear(GOLD, 0.016)


def nose_plate(f):
    f.paint(GOLD)
    f.wear(GOLD, 0.012)


def nozzle(f, tip):
    f.paint(METAL)
    if f.name == 'north' and tip:
        f.rect(f.w // 2 - 1, f.h // 2 - 1, f.w // 2 + 1, f.h // 2 + 1, BLACK[0])


def clamp(f):
    f.paint(BLACK)
    if f.side and f.w >= 8:
        f.rect(f.w // 2 - 1, f.h // 2 - 1, f.w // 2 + 1, f.h // 2 + 1, METAL[1])
    f.wear(BLACK, 0.01)


# ---------------------------------------------------------------- role table  (bone, cube index) -> painter
def roles():
    r = {}
    fr = 'frame'
    r[(fr, 0)] = lambda f: carbon(f, True)            # bottom plate
    r[(fr, 1)] = lambda f: carbon(f, True)            # top plate
    for i in range(2, 6):
        r[(fr, i)] = standoff
    r[(fr, 6)] = lambda f: pcb(f, True)
    r[(fr, 7)] = lambda f: pcb(f, False)
    for i in range(8, 12):
        r[(fr, i)] = carbon                         # arms
    for i in range(12, 20, 2):
        r[(fr, i)] = motor_bell
        r[(fr, i + 1)] = motor_cap
    r[(fr, 20)] = battery
    r[(fr, 21)] = lambda f: strap(f)
    r[(fr, 22)] = lambda f: strap(f, True)
    r[(fr, 23)] = cam_body
    r[(fr, 24)] = lens
    r[(fr, 25)] = antenna
    r[(fr, 26)] = antenna_tip
    for p in range(4):
        r[(f'prop{p}', 0)] = hub
        r[(f'prop{p}', 1)] = blade
    r[('warhead', 0)] = warhead
    for i in range(1, 13):
        r[('warhead', i)] = nose_plate
    r[('warhead', 13)] = lambda f: nozzle(f, False)
    r[('warhead', 14)] = lambda f: nozzle(f, True)
    r[('warhead', 15)] = clamp
    r[('warhead', 16)] = clamp
    return r


# 3-blade propeller: tapered, twisted segments (x0, x1, width, pitch deg), root -> tip
BLADE_SEGS = [(0.5, 1.9, 0.7, 16), (1.9, 3.4, 1.2, 13), (3.4, 4.7, 1.0, 10), (4.7, 5.3, 0.6, 7)]
BLADE_T = 0.25
BLADES = 3


def prop_blades(b, spin_sign, atlas):
    """replace the 2-cube bar by BLADES tapered, pitched blades; UV rects are allocated once per segment and shared"""
    cx, yc, cz = b['pivot']
    yc += 0.1
    cubes = []
    for az in range(BLADES):
        for k, (x0, x1, wd, pitch) in enumerate(BLADE_SEGS):
            uv = atlas[k]
            cubes.append({'origin': [cx + x0, yc - BLADE_T / 2, cz - wd / 2], 'size': [x1 - x0, BLADE_T, wd], 'pivot': [cx, yc, cz],
                          'rotation': [pitch * spin_sign, az * 360 / BLADES + 12, 0], 'uv': uv})
    return cubes


def alloc_blade_atlas(img):
    """one set of 6 face rects per blade segment in the free rows below the main atlas"""
    atlas, x, y = [], 0, 88
    for k, (x0, x1, wd, pitch) in enumerate(BLADE_SEGS):
        L, W, T = max(2, round((x1 - x0) * S)), max(2, round(wd * S)), max(1, round(BLADE_T * S))
        dims = {'up': (L, W), 'down': (L, W), 'north': (L, T), 'south': (L, T), 'east': (W, T), 'west': (W, T)}
        uv = {}
        for name, (w, h) in dims.items():
            Face_ = Face(img, name, x, y, w, h)
            blade(Face_, tip=(k == len(BLADE_SEGS) - 1))
            Face_.commit()
            uv[name] = {'uv': [x, y], 'uv_size': [w, h]}
            x += w + 1
        atlas.append(uv)
        x, y = 0, y + 8
    return atlas


def main():
    install = '--install' in sys.argv
    geo = json.loads((OUT / 'fpv.geo.json.pre_hires').read_text())
    d = geo['minecraft:geometry'][0]
    d['description']['texture_width'] = TW
    d['description']['texture_height'] = TH
    img = np.zeros((TH, TW, 4), np.uint8)
    table = roles()
    atlas = alloc_blade_atlas(img)
    for b in d['bones']:
        for i, c in enumerate(b['cubes']):
            if b['name'].startswith('prop') and i == 1:
                continue
            paint = table[(b['name'], i)]
            for name, fc in c['uv'].items():
                u, v = fc['uv']
                w, h = fc['uv_size']
                x0, y0 = min(u, u + w), min(v, v + h)
                x, y, pw, ph = int(x0 * S), int(y0 * S), int(abs(w) * S), int(abs(h) * S)
                f = Face(img, name, x, y, pw, ph)
                paint(f)
                f.commit()
                fc['uv'] = [u * S, v * S]
                fc['uv_size'] = [w * S, h * S]
    for b in d['bones']:
        if b['name'].startswith('prop'):
            n = int(b['name'][4:])
            b['cubes'] = b['cubes'][:1] + prop_blades(b, 1 if n in (0, 3) else -1, atlas)
    outpng = OUT / 'fpv.png'
    Image.fromarray(img, 'RGBA').save(outpng)
    (OUT / 'fpv.geo.json').write_text(json.dumps(geo, indent=2))
    if install:
        shutil.copy(outpng, ASSETS / 'textures/entity/fpv.png')
        shutil.copy(OUT / 'fpv.geo.json', ASSETS / 'geo/fpv.geo.json')
    print('written', outpng, 'installed' if install else '')


if __name__ == '__main__':
    main()
