"""Extra panel detail for big flat walls of the wheeled vehicles (texture decals only: geometry never changes).

Every whitelisted box part gets, on each big mostly-empty side / top face, a grid of pressed access panels (`hatch`) and louvre
vents (`vent`) laid out around the decals the spec already has. Added decals carry 'auto': True, so a re-run strips and redoes them.

usage: python3 detail_pass.py NAME [NAME...]    (rewrites <name>.json; backup in out/NAME.json.pre_detail)
       python3 detail_pass.py --strip NAME...   (removes the auto decals again)
gen.spec() calls panel_detail() too, so a regenerated spec keeps the detail.
"""
import json
import random
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent

WHITELIST = {'cab', 'cab_l', 'cab_r', 'cab_rear_wall', 'crew_box', 'kung', 'kung_roof', 'kung_box_l', 'kung_box_r', 'fuel_tank', 'generator',
             'front_apron', 'cab_body', 'nose', 'hull_wall', 'hull_front', 'hull_rear', 'pod', 'smerch_pack', 'uragan_pack', 'pack',
             'turret_body', 'side_wall', 'pump_box', 'front_bulkhead', 'ac_unit', 'roof', 'plenum', 'face_plate', 'hood_side', 'hood',
             'wing', 'wing_front', 'battery_box', 'tool_box', 'locker', 'side_locker_r'}
SOFT = {'rib', 'bolts', 'grime'}          # decals a panel may sit on
MIN_W, MIN_H = 6, 5


def _dims(p, face):
    a, b = p['from'], p['to']
    w, h, d = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
    return {'east': (d, h), 'west': (d, h), 'north': (w, h), 'south': (w, h), 'up': (w, d)}[face]


def _free_mask(p, face, fw, fh):
    free = [[1 <= s < fw - 1 and 1 <= t < fh - 1 for s in range(fw)] for t in range(fh)]
    for d in p.get('decals', []):
        if d['face'] != face or d.get('mat') in SOFT:
            continue
        x, y = int(d.get('x', 0)), int(d.get('y', 0))
        w = int(d['w']) if 'w' in d else fw - x
        h = int(d['h']) if 'h' in d else fh - y
        for t in range(max(0, y - 1), min(fh, y + h + 1)):        # one clear pixel around every real decal
            for s in range(max(0, x - 1), min(fw, x + w + 1)):
                free[t][s] = False
    return free


def _rect_free(free, x, y, w, h):
    return all(free[t][s] for t in range(y, y + h) for s in range(x, x + w))


def _layout(p, face, rnd):
    fw, fh = (int(v) for v in _dims(p, face))
    if fw < 10 or fh < 7:
        return []
    free = _free_mask(p, face, fw, fh)
    tw, th = rnd.choice((10, 12, 14)), min(rnd.choice((7, 9, 11)), fh - 2)
    if face == 'up':
        th = min(th + 4, fh - 2)
    out, y = [], 1
    while y + MIN_H <= fh - 1:
        x, placed = 1, False
        while x + MIN_W <= fw - 1:
            if not free[y][x]:
                x += 1
                continue
            w = 0
            while x + w < fw - 1 and w < tw and free[y][x + w]:
                w += 1
            h = min(th, fh - 1 - y)
            while h >= MIN_H and not _rect_free(free, x, y, w, h):
                h -= 1
            if w >= MIN_W and h >= MIN_H and _rect_free(free, x, y, w, h):
                kind = rnd.random()
                if kind < 0.2 and w >= 6 and h >= 5:
                    out.append({'face': face, 'x': x, 'y': y, 'w': min(w, 8), 'h': min(h, 6), 'mat': 'vent', 'auto': True})
                elif kind < 0.9:
                    out.append({'face': face, 'x': x, 'y': y, 'w': w, 'h': h, 'mat': 'hatch', 'auto': True})
                x += w + 2
                placed = True
            else:
                x += 1
        y += (th + 2) if placed else 1
        if len(out) >= 8:
            break
    return out


def strip(parts):
    for p in parts:
        if 'decals' in p:
            p['decals'] = [d for d in p['decals'] if not d.get('auto')]
            if not p['decals']:
                del p['decals']
    return parts


def panel_detail(parts):
    strip(parts)
    for p in parts:
        if p.get('type', 'box') != 'box' or p.get('chamfer') or p.get('rotation') or p.get('bone') in ('rack', 'turret') and p['name'] not in WHITELIST:
            continue
        base = p['name'].rstrip('0123456789')
        if base not in WHITELIST or p.get('mat', 'paint') not in ('paint', 'paint2'):
            continue
        faces = ['east', 'north', 'south', 'up'] if p.get('mirror') else ['east', 'west', 'north', 'south', 'up']
        add = []
        for face in faces:
            if p.get('faces', {}).get(face, 'paint') not in ('paint', 'paint2'):
                continue
            add += _layout(p, face, random.Random(f"{p['name']}:{face}"))
        if add:
            p.setdefault('decals', []).extend(add)
    return parts


def run(name, only_strip=False):
    f = HERE / f'{name}.json'
    sp = json.loads(f.read_text())
    bak = HERE / f'out/{name}.json.pre_detail'
    if not bak.exists():
        shutil.copy(f, bak)
    (strip if only_strip else panel_detail)(sp['parts'])
    f.write_text(json.dumps(sp, indent=1))
    n = sum(1 for p in sp['parts'] for d in p.get('decals', []) if d.get('auto'))
    print(name, 'auto decals:', n)


if __name__ == '__main__':
    args = sys.argv[1:]
    s = bool(args and args[0] == '--strip')
    for n in args[1 if s else 0:]:
        run(n, s)
