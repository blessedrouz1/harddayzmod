"""Hand-pixelled 16x16 inventory icons (vanilla style: hard alpha, 4-tone ramps, light from the top-left, hue-shifted dark outline)."""
import math
from PIL import Image
import colorsys

# ramps: dark -> light
R = {
    'olive': [(38, 48, 30), (62, 78, 46), (88, 108, 64), (122, 146, 88)],
    'green': [(30, 58, 36), (46, 92, 54), (72, 130, 76), (118, 178, 110)],
    'steel': [(42, 46, 54), (74, 80, 90), (112, 120, 132), (170, 180, 192)],
    'iron': [(26, 28, 34), (44, 48, 56), (66, 72, 82), (98, 106, 118)],
    'white': [(120, 126, 140), (170, 176, 190), (212, 218, 230), (250, 252, 255)],
    'red': [(90, 20, 22), (150, 34, 34), (204, 58, 48), (240, 110, 90)],
    'orange': [(130, 56, 16), (196, 98, 28), (240, 150, 52), (255, 204, 110)],
    'brass': [(100, 70, 16), (160, 118, 28), (212, 170, 52), (250, 222, 120)],
    'wood': [(72, 44, 22), (110, 70, 36), (148, 98, 52), (186, 132, 76)],
    'screen': [(10, 40, 24), (20, 100, 52), (50, 170, 90), (130, 240, 160)],
    'paper': [(150, 128, 92), (192, 170, 124), (222, 204, 158), (244, 232, 196)],
    'black': [(14, 15, 19), (28, 30, 36), (46, 49, 58), (72, 78, 90)],
    'glass': [(30, 52, 70), (60, 110, 140), (110, 180, 210), (200, 236, 248)],
}


class C:
    def __init__(self, w=16, h=16):
        self.im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        self.p = self.im.load()
        self.w, self.h = w, h

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[x, y] = tuple(c) + (255,)

    def rect(self, x0, y0, x1, y1, ramp, bevel=True):
        """Inclusive box; light top/left edge, dark bottom/right edge."""
        r = R[ramp]
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                k = 2
                if bevel:
                    if y == y0 or x == x0:
                        k = 3
                    elif y == y1 or x == x1:
                        k = 1
                    if y == y1 and x == x1:
                        k = 0
                self.put(x, y, r[k])

    def ell(self, cx, cy, rx, ry, ramp, light=(-0.55, -0.7)):
        r = R[ramp]
        for y in range(self.h):
            for x in range(self.w):
                dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
                d = dx * dx + dy * dy
                if d <= 1.0:
                    nz = math.sqrt(max(0.0, 1 - d))
                    lam = -(dx * light[0] + dy * light[1]) * 0.55 + nz * 0.6
                    k = 0 if lam < 0.12 else 1 if lam < 0.45 else 2 if lam < 0.78 else 3
                    self.put(x, y, r[k])

    def line(self, x0, y0, x1, y1, color):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(n + 1):
            self.put(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n), color)

    def poly(self, pts, ramp, light=2):
        r = R[ramp]
        xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
        for y in range(min(ys), max(ys) + 1):
            for x in range(min(xs), max(xs) + 1):
                inside = False
                j = len(pts) - 1
                for i in range(len(pts)):
                    xi, yi, xj, yj = pts[i][0], pts[i][1], pts[j][0], pts[j][1]
                    if (yi > y + 0.5) != (yj > y + 0.5) and (x + 0.5) < (xj - xi) * (y + 0.5 - yi) / (yj - yi) + xi:
                        inside = not inside
                    j = i
                if inside:
                    self.put(x, y, r[light])

    def outline(self, strength=0.4):
        w, h = self.w, self.h
        src = self.im.copy().load()
        for y in range(h):
            for x in range(w):
                if src[x, y][3]:
                    continue
                ns = [src[x + dx, y + dy] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)) if 0 <= x + dx < w and 0 <= y + dy < h and src[x + dx, y + dy][3]]
                if ns:
                    r = sum(n[0] for n in ns) // len(ns); g = sum(n[1] for n in ns) // len(ns); b = sum(n[2] for n in ns) // len(ns)
                    hh, ll, ss = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
                    rr, gg, bb = colorsys.hls_to_rgb(hh, max(0.04, ll * strength), min(1, ss * 0.55))
                    self.p[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), 255)
        return self.im


def bandage():
    c = C(); c.ell(7, 7.5, 6, 6, 'white')
    c.ell(7, 7.5, 3.4, 3.4, 'white', light=(0.5, 0.6))
    c.ell(7, 7.5, 1.7, 1.7, 'iron')
    for a in range(0, 360, 12):
        for rr in (3.9, 5.1):
            c.put(round(7 + rr * math.cos(math.radians(a)) - 0.5), round(7.5 + rr * math.sin(math.radians(a)) - 0.5), R['white'][1])
    c.poly([(9, 12), (15, 10), (15, 14), (10, 15)], 'white', 3)
    c.line(10, 14, 14, 12, R['white'][1]); c.line(11, 15, 15, 13, R['white'][0])
    for x, y in ((11, 12), (12, 12)): c.put(x, y, R['red'][2])
    c.put(11, 13, R['red'][2]); c.put(12, 13, R['red'][1])
    return c.outline()


def tourniquet():
    c = C()
    c.rect(2, 10, 13, 12, 'iron'); c.rect(2, 4, 4, 12, 'iron'); c.rect(11, 4, 13, 12, 'iron'); c.rect(2, 4, 13, 5, 'iron')
    c.rect(1, 6, 4, 9, 'steel'); c.put(2, 7, R['iron'][0]); c.put(3, 7, R['iron'][0])
    for x in range(5, 11): c.put(x, 11, R['iron'][3] if x % 2 else R['iron'][1])
    c.line(5, 13, 12, 2, R['orange'][2]); c.line(6, 13, 13, 2, R['orange'][1]); c.line(5, 12, 12, 1, R['orange'][3])
    c.rect(11, 1, 14, 3, 'orange')
    c.rect(9, 12, 12, 14, 'steel')
    return c.outline()


def medkit():
    c = C()
    c.rect(1, 5, 14, 14, 'olive'); c.rect(1, 5, 14, 7, 'olive')
    for x in range(1, 15): c.put(x, 8, R['olive'][0])
    c.rect(5, 2, 10, 4, 'steel'); c.rect(6, 3, 9, 4, 'iron'); c.put(6, 3, (0, 0, 0))
    c.rect(5, 9, 10, 13, 'white')
    for y in range(10, 13): c.put(7, y, R['red'][2]); c.put(8, y, R['red'][2])
    for x in range(6, 10): c.put(x, 11, R['red'][2])
    c.put(7, 10, R['red'][3]); c.put(6, 11, R['red'][3])
    c.rect(2, 6, 3, 8, 'steel'); c.rect(12, 6, 13, 8, 'steel')
    return c.outline()


def painkiller():
    c = C()
    c.rect(3, 6, 10, 14, 'orange'); c.rect(3, 6, 10, 8, 'orange')
    for y in range(7, 14): c.put(4, y, R['orange'][3])
    c.rect(3, 3, 10, 5, 'white'); c.line(3, 4, 10, 4, R['white'][1])
    c.rect(4, 9, 9, 12, 'white')
    for x in range(5, 9): c.put(x, 10, R['red'][2])
    c.put(6, 11, R['red'][2]); c.put(7, 11, R['red'][2])
    c.ell(12.2, 12.8, 2, 1.4, 'white'); c.line(11, 13, 13, 12, R['white'][0])
    c.ell(13.2, 9.8, 1.6, 1.2, 'orange')
    return c.outline()


def frag_grenade():
    c = C(); c.ell(7.5, 9.5, 5.2, 5.8, 'olive')
    for y in (7, 10, 13): c.line(3, y, 12, y, R['olive'][0])
    for x in (5, 8, 10): c.line(x, 5, x, 14, R['olive'][0])
    c.put(5, 6, R['olive'][3]); c.put(6, 5, R['olive'][3])
    c.rect(6, 2, 9, 4, 'steel'); c.rect(7, 1, 8, 2, 'iron')
    c.line(10, 3, 13, 5, R['steel'][2]); c.line(10, 4, 13, 7, R['steel'][1]); c.line(13, 5, 13, 11, R['steel'][1])
    c.ell(3.2, 3.2, 2, 2, 'steel'); c.put(3, 3, (0, 0, 0, ))
    c.line(4, 4, 6, 3, R['steel'][2])
    return c.outline()


def smoke_grenade():
    c = C(); c.rect(4, 4, 11, 14, 'steel')
    for y in range(5, 14): c.put(5, y, R['steel'][3])
    c.rect(4, 7, 11, 9, 'orange'); c.line(4, 8, 11, 8, R['orange'][1])
    c.rect(5, 2, 10, 3, 'iron'); c.rect(6, 1, 9, 2, 'steel')
    c.line(12, 3, 12, 11, R['steel'][2]); c.line(11, 2, 13, 3, R['steel'][2])
    c.rect(4, 13, 11, 14, 'iron')
    c.put(7, 11, R['iron'][0]); c.put(8, 11, R['iron'][0])
    return c.outline()


def jerrycan(full=True):
    c = C(); body = 'olive' if full else 'steel'
    c.rect(2, 4, 12, 14, body)
    for x in range(3, 12): c.put(x, 5, R[body][3]) if x % 2 else None
    c.rect(3, 6, 11, 13, body, bevel=False)
    c.line(3, 6, 11, 13, R[body][1]); c.line(11, 6, 3, 13, R[body][1])
    c.rect(2, 2, 7, 3, 'iron'); c.rect(2, 3, 3, 5, 'iron'); c.rect(6, 3, 7, 5, 'iron')
    c.rect(9, 1, 12, 3, 'steel')
    if full:
        c.rect(9, 0, 12, 1, 'orange')
    else:
        c.put(10, 1, R['iron'][0]); c.put(11, 1, R['iron'][0])
        for x, y in ((4, 9), (5, 9), (4, 10), (9, 11)): c.put(x, y, R[body][0])
    return c.outline()


def mine_ap():
    c = C(); c.ell(7.5, 10, 7, 4, 'olive')
    c.ell(7.5, 8.6, 7, 3.6, 'olive', light=(-0.3, -0.9))
    c.ell(7.5, 8.2, 4, 1.8, 'iron')
    c.rect(7, 4, 8, 7, 'steel'); c.put(7, 3, R['steel'][3]); c.put(8, 3, R['steel'][2])
    c.line(5, 7, 3, 4, R['steel'][2]); c.line(10, 7, 12, 4, R['steel'][1])
    for x in (3, 5, 7, 9, 11): c.put(x, 12, R['olive'][0])
    return c.outline()


def mine_at():
    c = C(); c.ell(7.5, 10.5, 7.5, 4.6, 'olive')
    c.ell(7.5, 8.4, 7.5, 4.2, 'olive', light=(-0.3, -0.9))
    c.ell(7.5, 8.2, 5, 2.6, 'red')
    c.ell(7.5, 7.6, 1.8, 1.2, 'steel')
    c.rect(1, 6, 3, 7, 'steel'); c.rect(12, 6, 14, 7, 'steel')
    for x in (3, 5, 7, 9, 11, 12): c.put(x, 13, R['olive'][0])
    return c.outline()


def mg_belt():
    c = C()
    for i in range(4):
        x = 1 + i * 3; y = 9 - i * 2
        c.rect(x, y, x + 1, y + 6, 'brass'); c.put(x, y, R['brass'][3]); c.put(x + 1, y, R['wood'][2]); c.put(x, y - 1, R['wood'][3]); c.put(x + 1, y - 1, R['wood'][1])
        c.rect(x, y + 4, x + 1, y + 4, 'steel')
    c.line(2, 12, 12, 9, R['iron'][3]); c.line(2, 13, 12, 10, R['iron'][1])
    for i in range(4):
        c.rect(1 + i * 3 - 1, 12 - i * 1, 1 + i * 3 + 2, 12 - i * 1, 'steel')
    return c.outline()


def cannon_shells():
    c = C()
    for i, x in enumerate((1, 6, 11)):
        y = 6 + (i % 2)
        c.rect(x, y + 2, x + 3, 14, 'brass')
        for yy in range(y + 3, 14): c.put(x + 1, yy, R['brass'][3])
        c.rect(x, 13, x + 3, 14, 'steel')
        c.rect(x + 1, y, x + 2, y + 1, 'wood'); c.put(x + 1, y - 1, R['wood'][3])
    return c.outline()


def designator():
    c = C(); c.rect(2, 6, 13, 11, 'iron')
    c.rect(1, 8, 3, 12, 'black'); c.rect(12, 8, 14, 12, 'black')
    c.ell(4.5, 4.5, 2.6, 2.6, 'iron'); c.ell(11.5, 4.5, 2.6, 2.6, 'iron')
    c.ell(4.5, 4.3, 1.5, 1.5, 'red'); c.ell(11.5, 4.3, 1.5, 1.5, 'glass')
    c.rect(6, 7, 9, 9, 'steel'); c.put(7, 8, R['red'][2])
    c.rect(6, 12, 9, 15, 'black')
    c.line(2, 6, 13, 6, R['iron'][3])
    return c.outline()


def operator_terminal():
    c = C(); c.rect(1, 5, 14, 13, 'iron')
    c.rect(1, 7, 3, 14, 'black'); c.rect(12, 7, 14, 14, 'black')
    c.rect(5, 6, 10, 10, 'screen')
    for x, y in ((6, 8), (7, 9), (8, 7), (9, 8)): c.put(x, y, R['screen'][3])
    c.ell(3.6, 11.6, 1.5, 1.5, 'steel'); c.ell(12.4, 11.6, 1.5, 1.5, 'steel')
    c.put(7, 12, R['red'][2]); c.put(9, 12, R['orange'][2]); c.put(8, 11, R['screen'][2])
    c.line(2, 5, 2, 1, R['steel'][2]); c.line(13, 5, 13, 1, R['steel'][2]); c.put(2, 0, R['red'][2]); c.put(13, 0, R['screen'][3])
    return c.outline()


def supply_tablet():
    c = C(); c.rect(1, 1, 14, 14, 'iron')
    c.rect(2, 2, 13, 13, 'black', bevel=False)
    c.rect(3, 3, 12, 12, 'screen', bevel=False)
    for y, w in ((4, 8), (6, 6), (8, 7), (10, 5)):
        c.line(4, y, 3 + w, y, R['screen'][3])
    c.rect(10, 9, 12, 12, 'orange')
    c.put(11, 10, R['white'][3]); c.put(11, 11, R['white'][3])
    c.put(2, 7, R['steel'][2]); c.put(13, 7, R['steel'][2])
    return c.outline()


def radar_display():
    c = C(); c.rect(1, 1, 14, 14, 'iron'); c.rect(2, 2, 13, 13, 'black', bevel=False)
    c.ell(8, 8, 5.4, 5.4, 'screen')
    for rr in (2, 4):
        for a in range(0, 360, 20): c.put(round(8 + rr * math.cos(math.radians(a)) - 0.5), round(8 + rr * math.sin(math.radians(a)) - 0.5), R['screen'][1])
    c.line(8, 8, 12, 4, R['screen'][3]); c.line(8, 8, 11, 4, R['screen'][2])
    c.put(6, 10, R['red'][3]); c.put(10, 9, R['orange'][3]); c.put(5, 6, R['white'][3])
    c.put(2, 2, R['red'][2]); c.put(13, 2, R['screen'][3])
    return c.outline()


def target_log():
    c = C(); c.rect(2, 1, 13, 14, 'wood')
    c.rect(3, 3, 12, 14, 'paper')
    c.rect(5, 0, 10, 2, 'steel'); c.put(7, 1, R['iron'][0]); c.put(8, 1, R['iron'][0])
    c.ell(8, 8, 3.6, 3.6, 'red'); c.ell(8, 8, 2.2, 2.2, 'paper'); c.put(8, 8, R['red'][2])
    c.line(8, 4, 8, 12, R['red'][1]); c.line(4, 8, 12, 8, R['red'][1])
    for y in (12, 13): c.line(4, y, 11 - (y - 12) * 3, y, R['paper'][0])
    return c.outline()


def bunker_door():
    c = C(); c.rect(2, 1, 13, 14, 'steel')
    c.rect(3, 2, 12, 13, 'iron', bevel=False)
    c.rect(4, 3, 11, 12, 'steel')
    c.ell(8, 8, 2.8, 2.8, 'iron'); c.ell(8, 8, 1.6, 1.6, 'steel')
    c.line(8, 4, 8, 12, R['iron'][0]); c.line(4, 8, 12, 8, R['iron'][0])
    for x, y in ((4, 3), (11, 3), (4, 12), (11, 12)): c.put(x, y, R['iron'][0])
    c.rect(1, 3, 2, 4, 'black'); c.rect(1, 11, 2, 12, 'black')
    return c.outline()


ALL = {'bandage': bandage, 'tourniquet': tourniquet, 'medkit': medkit, 'painkiller': painkiller, 'frag_grenade': frag_grenade, 'smoke_grenade': smoke_grenade,
       'jerrycan': lambda: jerrycan(True), 'empty_jerrycan': lambda: jerrycan(False), 'mine_ap': mine_ap, 'mine_at': mine_at, 'mg_belt': mg_belt,
       'cannon_shells': cannon_shells, 'designator': designator, 'operator_terminal': operator_terminal, 'supply_tablet': supply_tablet,
       'radar_display': radar_display, 'target_log': target_log, 'bunker_door': bunker_door}

if __name__ == '__main__':
    import pixelize as px
    ims = [f() for f in ALL.values()]
    px.sheet(ims, 12, 6).save('out/icons/_hand.png')
