"""Specs for the wheeled vehicles of HardDayz (blockbench-military-pixel format).

Scale 1 m = 16 px, front = -Z, right side = +X, ground y = 0. Each vehicle keeps the mod's bone contract:
cab (faction marks), turret > rack / radar (aiming), steer_Nx > wheel_Nx (steering), wheel_Nx (spin), jackN (outriggers).
usage: python3 gen.py  -> writes <name>.json next to this file
"""
import copy
import json
import heal
from pathlib import Path

HERE = Path(__file__).resolve().parent
SAND = ["#6A5843", "#9E8661", "#B39B70", "#CFBA8B"]
OLIVE = "olive"
NATO_GREEN = ["#2B3A2A", "#3F5139", "#4C6045", "#647A59"]
PAINT2_SAND = ["#2E2820", "#3A332A", "#4A4134", "#5C5141"]
PAINT2_DARK = ["#1E1F1A", "#2A2B24", "#34362D", "#43453A"]
METAL = ["#1C1D21", "#26272D", "#33343B", "#45474F"]


def box(name, frm, to, mat='paint', **kw):
    p = {'name': name, 'from': list(frm), 'to': list(to), 'mat': mat}
    p.update(kw)
    return p


def wheel(name, center, d=22, w=9, **kw):
    p = {'type': 'wheel', 'name': name, 'center': list(center), 'diameter': d, 'width': w}
    p.update(kw)
    return p


def shift(parts, dx=0, dy=0, dz=0):
    out = copy.deepcopy(parts)
    for p in out:
        for k in ('from', 'to', 'pivot', 'center', 'p0', 'p1', 'inside', 'base'):
            if k in p:
                p[k] = [p[k][0] + dx, p[k][1] + dy, p[k][2] + dz]
    return out


def spec(name, note, paint, paint2, bones, parts, seed, wear=0.25):
    return {'name': name, 'note': note, 'paint': paint, 'paint2': paint2, 'metal': METAL, 'wear': wear, 'seed': seed,
            'bones': bones, 'parts': parts}


# ---------------------------------------------------------------- truck pieces

HIMARS = json.loads((HERE / 'himars_base.json').read_text())
FMTV_CAB = [p for p in HIMARS['parts'] if p.get('bone') == 'cab' or p['name'] in ('bumper', 'tow_hook', 'exhaust_pipe', 'air_intake')]

# ---------------------------------------------------------------- small kit: crates, cans, tarps, tools

def jerrycans(name, x0, y0, z0, n=2, step=6, bone=None):
    """Row of jerrycans along X (5 wide, 7 tall, 3 deep) on a bracket plate."""
    kw = {'bone': bone} if bone else {}
    out = [box(f'{name}_bracket', [x0 - 0.5, y0 - 1, z0 - 0.5], [x0 + step * n - 0.5, y0, z0 + 3.5], 'metal', **kw)]
    for i in range(n):
        x = x0 + i * step
        out.append(box(f'{name}{i}', [x, y0, z0], [x + 5, y0 + 7, z0 + 3], 'paint', panels=0, **kw,
                       decals=[{'face': 'south', 'x': 1, 'y': 1, 'w': 3, 'h': 1, 'mat': 'rib'}, {'face': 'south', 'x': 1, 'y': 4, 'w': 3, 'h': 1, 'mat': 'rib'},
                               {'face': 'north', 'x': 1, 'y': 1, 'w': 3, 'h': 1, 'mat': 'rib'}]))
        out.append(box(f'{name}{i}_cap', [x + 3, y0 + 7, z0 + 1], [x + 4, y0 + 8, z0 + 2], 'metal', **kw))
    return out


def crate(name, frm, to, **kw):
    """Ammo / equipment crate: lid seam, handles, strap."""
    w = to[0] - frm[0]
    d = to[2] - frm[2]
    h = to[1] - frm[1]
    dec = [{'face': 'east', 'x': 1, 'y': 1, 'w': max(1, d - 2), 'h': 1, 'mat': 'rib'}, {'face': 'west', 'x': 1, 'y': 1, 'w': max(1, d - 2), 'h': 1, 'mat': 'rib'}]
    if h >= 4 and d >= 6:
        dec += [{'face': 'east', 'x': d // 2 - 1, 'y': 2, 'w': 2, 'h': 1, 'mat': 'handle'}, {'face': 'west', 'x': d // 2 - 1, 'y': 2, 'w': 2, 'h': 1, 'mat': 'handle'}]
    if w >= 6:
        dec += [{'face': 'up', 'x': w // 2 - 1, 'y': 0, 'w': 2, 'h': d, 'mat': 'strap'}]
    return box(name, frm, to, kw.pop('mat', 'paint2'), panels=0, decals=dec, **kw)


def tarp(name, frm, to, **kw):
    """Rolled tarpaulin with tie-down straps."""
    d = to[2] - frm[2]
    w = to[0] - frm[0]
    dec = []
    if d > w:
        for k in (3, d - 5):
            dec += [{'face': 'up', 'x': 0, 'y': k, 'w': w, 'h': 2, 'mat': 'strap'}, {'face': 'east', 'x': k, 'y': 0, 'w': 2, 'h': to[1] - frm[1], 'mat': 'strap'},
                    {'face': 'west', 'x': k, 'y': 0, 'w': 2, 'h': to[1] - frm[1], 'mat': 'strap'}]
    else:
        for k in (3, w - 5):
            dec += [{'face': 'up', 'x': k, 'y': 0, 'w': 2, 'h': d, 'mat': 'strap'}, {'face': 'north', 'x': k, 'y': 0, 'w': 2, 'h': to[1] - frm[1], 'mat': 'strap'}]
    return box(name, frm, to, 'canvas', panels=0, decals=dec, **kw)


def shovel(name, x, y, z, side=1):
    """Entrenching shovel on a side: handle + blade."""
    return [box(f'{name}_handle', [x, y, z], [x + 1, y + 1, z + 12], 'paint2'),
            box(f'{name}_blade', [x, y - 1.5, z + 12], [x + 1, y + 2.5, z + 16], 'metal')]


def grime_sides(part, h=3):
    """Mud along the bottom of the long sides of a box part."""
    L = int(part['to'][2] - part['from'][2])
    H = int(part['to'][1] - part['from'][1])
    part.setdefault('decals', [])
    part['decals'] += [{'face': 'east', 'x': 1, 'y': H - h, 'w': L - 2, 'h': h, 'mat': 'grime'},
                       {'face': 'west', 'x': 1, 'y': H - h, 'w': L - 2, 'h': h, 'mat': 'grime'}]
    return part


def detail(sp, extra, wear=0.45):
    """Add kit parts, more wear and mud on decks/kungs/fuel tanks."""
    for p in sp['parts']:
        if p['name'] in ('deck', 'kung', 'fuel_tank', 'battery_box', 'tool_box', 'crew_box', 'generator') and p.get('type', 'box') == 'box':
            H = p['to'][1] - p['from'][1]
            grime_sides(p, 2 if H <= 8 else 4)
    sp['parts'] += extra
    for p in sp['parts']:
        if p.get('type', 'box') == 'box' and p.get('mat') in ('paint', 'canvas'):
            p.setdefault('faces', {})['down'] = 'paint2'
    sp['wear'] = wear
    return sp
  # incl. cab_base, front_apron




# ---------------------------------------------------------------- cab liners, wheel arches

def liners(cab, bone=None, faces=('north', 'south', 'east', 'west')):
    """Inner wall panels for a cab with clear windows: every wall gets a 1 px liner just inside it, with holes
    cut where the clear windows are (from the cab's own decals). Without liners the far wall of the cab is
    invisible from inside (one-sided faces) and you look straight through the cab."""
    (x0, y0, z0), (x1, y1, z1) = cab['from'], cab['to']
    w, h, d = int(x1 - x0), int(y1 - y0), int(z1 - z0)
    bone = bone or cab.get('bone')
    out = []
    for face in faces:
        fw = w if face in ('north', 'south') else d
        win = [dc for dc in cab.get('decals', []) if dc['face'] == face and dc.get('mat') == 'glass' and dc.get('clear')]
        s0, s1 = (2, fw - 2) if face in ('north', 'south') else (1, fw - 1)
        ch = cab.get('chamfer')
        t_from = ch['legs'][1] + 1 if ch and face == 'north' and 'front' in ch['edges'] else 1
        free = [[s0 <= s < s1 and not any(dc['x'] <= s < dc['x'] + dc['w'] and dc['y'] <= t < dc['y'] + dc['h'] for dc in win)
                 for s in range(fw)] for t in range(h)]
        rows = []
        for t in range(t_from, h - 1):
            runs, s = [], 0
            while s < fw:
                if free[t][s]:
                    a = s
                    while s < fw and free[t][s]:
                        s += 1
                    runs.append((a, s))
                else:
                    s += 1
            rows.append((t, runs))
        rects, open_ = [], {}
        for t, runs in rows + [(h - 1, [])]:
            cur = {}
            for r in runs:
                cur[r] = open_.pop(r, t)
            for r, t0 in open_.items():
                rects.append((r[0], r[1], t0, t))
            open_ = cur
        for i, (sa, sb, ta, tb) in enumerate(rects):
            ya, yb = y1 - tb, y1 - ta
            if face == 'north':
                frm, to = [x1 - sb, ya, z0 + 0.75], [x1 - sa, yb, z0 + 1.75]
            elif face == 'south':
                frm, to = [x0 + sa, ya, z1 - 1.75], [x0 + sb, yb, z1 - 0.75]
            elif face == 'east':
                frm, to = [x1 - 1.75, ya, z1 - sb], [x1 - 0.75, yb, z1 - sa]
            else:
                frm, to = [x0 + 0.75, ya, z0 + sa], [x0 + 1.75, yb, z0 + sb]
            kw = {'bone': bone} if bone else {}
            out.append(box(f'{cab["name"]}_liner_{face}{i}', frm, to, 'paint2', panels=0, **kw))
    return out


def arch(name, zcs, x_in, x_out, top, slopes=True, bone=None, r=11):
    """Mudguard over a wheel or a tandem: flat top plate, optional 3-4-5 slopes front and rear, both sides."""
    z0, z1 = min(zcs) - r + 1, max(zcs) + r - 1
    kw = {'bone': bone} if bone else {}
    mid = (x_in + x_out) / 2
    out = [box(f'{name}_top', [x_in, top, z0], [x_out, top + 1, z1], 'paint2', mirror=True, panels=0,
               decals=[{'face': 'east', 'x': 1, 'y': 0, 'w': int(z1 - z0) - 2, 'h': 1, 'mat': 'grime'}], **kw)]
    if slopes:
        out += [{'type': 'slab', 'name': f'{name}_front', 'axis': 'x', 'p0': [0, top + 1, z0], 'p1': [0, top - 3, z0 - 3],
                 'extent': [x_in + 0.5, x_out - 0.5], 'thickness': 1, 'inside': [mid, top - 3, z0 + 1], 'fit': 'exact', 'mat': 'paint2', 'mirror': True, **kw},
                {'type': 'slab', 'name': f'{name}_rear', 'axis': 'x', 'p0': [0, top + 1, z1], 'p1': [0, top - 3, z1 + 3],
                 'extent': [x_in + 0.5, x_out - 0.5], 'thickness': 1, 'inside': [mid, top - 3, z1 - 1], 'fit': 'exact', 'mat': 'paint2', 'mirror': True, **kw}]
    return out


def finish_cab(parts, steer_z):
    """Liners for the cab with clear windows and an arch over each steered wheel under the cab."""
    cab = next(p for p in parts if p['name'] == 'cab')
    return parts + liners(cab) + arch('front_arch', steer_z, 17.5, 22.5, 23.5, bone='cab', r=10.5)


# ---------------------------------------------------------------- underbody: what you see from below

def underbody(steer_z, rear_z, rails=(7, 10)):
    """Engine, gearbox, driveshafts, differentials, leaf springs and cross members between the frame rails."""
    zf = steer_z[0]
    out = [box('engine', [-6, 11, zf + 2], [6, 17, zf + 16], 'paint2', panels=0,
               decals=[{'face': 'east', 'x': 2, 'y': 1, 'w': 10, 'h': 1, 'mat': 'rib'}, {'face': 'west', 'x': 2, 'y': 1, 'w': 10, 'h': 1, 'mat': 'rib'}]),
           box('oil_pan', [-4, 9, zf + 4], [4, 11, zf + 12], 'metal'),
           box('gearbox', [-4, 12, zf + 16], [4, 17, zf + 24], 'metal')]
    axles = sorted(steer_z + rear_z)
    shaft_from = zf + 24
    for i, z in enumerate(axles):
        out.append(box(f'diff{i}', [-3, 8, z - 3], [3, 13, z + 3], 'metal'))
        if z > shaft_from + 4:
            out.append(box(f'shaft{i}', [-1, 11.5, shaft_from], [1, 13.5, z - 3], 'metal'))
            shaft_from = z + 3
        if z in rear_z:
            out.append(box(f'spring{i}', [rails[0], 10.5, z - 9], [rails[1], 11.5, z + 9], 'metal', mirror=True))
    for j, z in enumerate(range(int(zf) + 30, int(max(rear_z)), 22)):
        if all(abs(z + 1 - a) > 6 for a in axles):
            out.append(box(f'cross{j}', [-7, 13.5, z], [7, 16.5, z + 2], 'paint2', panels=0))
    return out


def fmtv_cab(front_z, steer_z=None):
    """FMTV armoured cab-over from the HIMARS spec; its front face lands on front_z."""
    parts = shift(FMTV_CAB, dz=front_z - (-55))
    return finish_cab(parts, steer_z) if steer_z else parts


def kamaz_cab(front_z, steer_z):
    """KamAZ cab-over: one wide panoramic windshield, horizontal grille slats, sun visor, round roof edges."""
    parts = fmtv_cab(front_z)
    for p in parts:
        if p['name'] == 'cab':
            p['decals'] = [d for d in p['decals'] if not (d['face'] == 'north')] + [
                {'face': 'north', 'x': 3, 'y': 3, 'w': 30, 'h': 10, 'mat': 'glass', 'clear': True},
                {'face': 'north', 'x': 5, 'y': 16, 'w': 26, 'h': 2, 'mat': 'rib'},
                {'face': 'north', 'x': 5, 'y': 19, 'w': 26, 'h': 2, 'mat': 'rib'},
                {'face': 'north', 'x': 5, 'y': 22, 'w': 26, 'h': 2, 'mat': 'rib'},
                {'face': 'north', 'x': 2, 'y': 20, 'w': 3, 'h': 3, 'mat': 'amber'},
                {'face': 'north', 'x': 31, 'y': 20, 'w': 3, 'h': 3, 'mat': 'amber'}]
        if p['name'] == 'cab_roof':
            p['chamfer'] = {'legs': [3, 4], 'edges': ['left', 'right']}
            p['to'][1] += 2
        if p['name'] in ('air_intake',):
            p['from'][0], p['to'][0] = -17, -11
            p['decals'] = [{'face': 'west', 'x': 1, 'y': 2, 'w': 3, 'h': 6, 'mat': 'vent'}]
        if p['name'] == 'exhaust_pipe':
            p['from'][0], p['to'][0] = 14, 17
    z = front_z
    for p in parts:
        if p['name'] in ('roof_hatch', 'roof_rail', 'beacon', 'spotlight', 'cab_whip'):
            for k in ('from', 'to', 'base'):
                if k in p:
                    p[k][1] += 2
    parts.append(box('sun_visor', [-18, 46, z - 3], [18, 47, z], 'paint2', bone='cab', panels=0))
    return finish_cab(parts, steer_z)


def frame(z0, z1, axles, w=22, paint2=True):
    parts = [box('frame_rail', [7, 12, z0], [10, 18, z1], 'paint2', mirror=True, panels=0)]
    for i, z in enumerate(axles):
        parts.append(box(f'axle_{i}', [-13, 10, z - 1], [13, 12, z + 1], 'metal'))
    return parts


def wheels(steer_z, rear_z, x=17.5, y=11, d=22, w=9):
    parts = []
    for i, z in enumerate(steer_z):
        parts.append(wheel(f'wheel_{i}_r', [x, y, z], d, w, bone_parent=f'steer_{i}_r'))
        parts.append(wheel(f'wheel_{i}_l', [-x, y, z], d, w, bone_parent=f'steer_{i}_l'))
    for j, z in enumerate(rear_z):
        parts.append(wheel(f'wheel_{len(steer_z) + j}', [x, y, z], d, w, mirror=True))
    return parts


def steer_bones(steer_z, x=17.5, y=11):
    out = []
    for i, z in enumerate(steer_z):
        out += [{'name': f'steer_{i}_r', 'pivot': [x, y, z]}, {'name': f'steer_{i}_l', 'pivot': [-x, y, z]}]
    return out


def jacks(points):
    """Outriggers: a leg and a foot per point; bone jackN pivots on the top of the leg."""
    bones, parts = [], []
    for i, (x, z) in enumerate(points):
        s = 'r' if x > 0 else 'l'
        bones.append({'name': f'jack{i}', 'pivot': [x, 14, z]})
        parts.append(box(f'jack{i}_leg_{s}', [x - 1.5, 8, z - 2], [x + 1.5, 20, z + 2], 'metal', bone=f'jack{i}'))
        parts.append(box(f'jack{i}_foot_{s}', [x - 2.5, 7, z - 3], [x + 2.5, 8, z + 3], 'metal', bone=f'jack{i}'))
    return bones, parts


def deck(z0, z1, rear_z, y0=19, y1=23):
    """Load deck that clears the rear wheels: a full-length centre between the tyres, side strips before and
    after the tandem, a mudguard over the tandem, a closed rear bumper with lamps."""
    groups = []
    for z in sorted(rear_z):
        if groups and z - groups[-1][-1] <= 30:
            groups[-1].append(z)
        else:
            groups.append([z])
    za, zb = min(groups[0]) - 11, max(groups[-1]) + 11
    side = lambda nm, a, b, dec: box(nm, [12, y0, a], [19, y1, b], 'paint', mirror=True, panels=0, decals=dec)
    lamp = [{'face': 'south', 'x': 2, 'y': 1, 'w': 3, 'h': 2, 'mat': 'red'}]
    out = [box('deck', [-12, y0, z0], [12, y1, z1], 'paint', panels=12,
               decals=[{'face': 'up', 'x': 2, 'y': 2, 'w': 20, 'h': 1, 'mat': 'bolts'}]),
           box('bumper_rear', [-19, y0 - 5, z1 - 1], [19, y0, z1 + 2], 'paint2', panels=0,
               decals=[{'face': 'south', 'x': 3, 'y': 1, 'w': 3, 'h': 2, 'mat': 'red'}, {'face': 'south', 'x': 32, 'y': 1, 'w': 3, 'h': 2, 'mat': 'red'},
                       {'face': 'south', 'x': 9, 'y': 1, 'w': 20, 'h': 1, 'mat': 'bolts'}])]
    if za - z0 >= 4:
        out.append(side('deck_side_front', z0, za, [{'face': 'east', 'x': 1, 'y': 1, 'w': za - z0 - 2, 'h': 1, 'mat': 'bolts'}]))
    for i in range(len(groups) - 1):
        a, b = max(groups[i]) + 11, min(groups[i + 1]) - 11
        out.append(side(f'deck_side_mid{i}', a, b, []))
    if z1 - zb >= 2:
        out.append(side('deck_side_rear', zb, z1, lamp))
    for i, g in enumerate(groups):
        out += arch(f'rear_arch{i}', g, 12, 22, y1, slopes=False)
    return out


def mudflaps(z):
    return [box('mudflap', [14, 5, z], [21, 21, z + 1], 'mudflap', mirror=True)]


def kung(z0, z1, y0=23, y1=55, windows=True):
    """Box body (kung) with a rear door, side windows, vents and a ladder."""
    L = z1 - z0
    side = [{'face': 'east', 'x': 6, 'y': 6, 'w': 8, 'h': 6, 'mat': 'glass'}, {'face': 'east', 'x': L - 22, 'y': 4, 'w': 10, 'h': 6, 'mat': 'vent'},
            {'face': 'west', 'x': 14, 'y': 6, 'w': 8, 'h': 6, 'mat': 'glass'}, {'face': 'west', 'x': L - 30, 'y': 4, 'w': 10, 'h': 6, 'mat': 'vent'},
            {'face': 'east', 'x': 2, 'y': y1 - y0 - 3, 'w': L - 4, 'h': 1, 'mat': 'bolts'}, {'face': 'west', 'x': 2, 'y': y1 - y0 - 3, 'w': L - 4, 'h': 1, 'mat': 'bolts'},
            {'face': 'south', 'x': 12, 'y': 4, 'w': 14, 'h': y1 - y0 - 6, 'mat': 'hatch'}, {'face': 'south', 'x': 23, 'y': 16, 'w': 2, 'h': 2, 'mat': 'handle'}]
    if not windows:
        side = [d for d in side if d['mat'] != 'glass']
    return [box('kung', [-19, y0, z0], [19, y1, z1], 'paint', panels=14, decals=side),
            box('kung_roof', [-20, y1, z0 - 1], [20, y1 + 2, z1 + 1], 'paint2', panels=0),
            box('kung_ladder', [11, y0 - 8, z1 + 0.5], [15, y0 + 6, z1 + 1.5], 'metal', panels=0),
            box('kung_step', [11, y0 - 9, z1 - 1], [15, y0 - 8, z1 + 2], 'tread_plate')]


# ---------------------------------------------------------------- vehicles

def nasams():
    steer, rear = [-48], [22, 46]
    jb, jp = jacks([(17, 61), (-17, 61)])
    p = fmtv_cab(-64, steer) + underbody(steer, rear) + frame(-60, 62, steer + rear) + deck(-32, 62, rear) + mudflaps(57.5) + jp + wheels(steer, rear)
    p += [box('fuel_tank', [13, 12, -30], [20, 19, -12], 'paint', panels=0, chamfer={'legs': [3, 4], 'edges': ['right']},
              decals=[{'face': 'east', 'x': 2, 'y': 2, 'w': 14, 'h': 1, 'mat': 'strap'}]),
          box('battery_box', [-20, 12, -30], [-13, 19, -16], 'paint', panels=0, decals=[{'face': 'west', 'x': 2, 'y': 2, 'w': 10, 'h': 5, 'mat': 'hatch'}])]
    # launcher: turntable, two side arms, six AMRAAM canisters (2 rows x 3) that elevate about the pivot
    t = [box('turntable', [-13, 23.5, -4], [13, 25.5, 22], 'metal', bone='turret'), box('bearing_ring', [-10, 25.5, -1], [10, 27.5, 19], 'metal', bone='turret'),
         box('arm', [14, 26, 2], [16, 40, 16], 'paint', bone='turret', mirror=True, panels=0,
             decals=[{'face': 'east', 'x': 4, 'y': 3, 'w': 6, 'h': 6, 'mat': 'hatch'}]),
         box('arm_base', [-16.5, 26.5, 4], [16.5, 29.5, 14], 'paint2', bone='turret', panels=0),
         box('canisters', [-13, 30, -30], [13, 48, 20], 'paint', bone='rack', panels=0,
             decals=[{'face': 'north', 'x': 1, 'y': 1, 'w': 7, 'h': 7, 'mat': 'hatch'}, {'face': 'north', 'x': 9, 'y': 1, 'w': 8, 'h': 7, 'mat': 'hatch'},
                     {'face': 'north', 'x': 18, 'y': 1, 'w': 7, 'h': 7, 'mat': 'hatch'}, {'face': 'north', 'x': 1, 'y': 10, 'w': 7, 'h': 7, 'mat': 'hatch'},
                     {'face': 'north', 'x': 9, 'y': 10, 'w': 8, 'h': 7, 'mat': 'hatch'}, {'face': 'north', 'x': 18, 'y': 10, 'w': 7, 'h': 7, 'mat': 'hatch'},
                     {'face': 'east', 'x': 4, 'y': 8, 'w': 42, 'h': 2, 'mat': 'rib'}, {'face': 'west', 'x': 4, 'y': 8, 'w': 42, 'h': 2, 'mat': 'rib'},
                     {'face': 'up', 'x': 8, 'y': 4, 'w': 10, 'h': 40, 'mat': 'grime'}]),
         box('band_front', [-14, 29, -24], [14, 49, -22], 'paint2', bone='rack', panels=0),
         box('band_rear', [-14, 29, 11], [14, 49, 13], 'paint2', bone='rack', panels=0),
         box('trunnion', [13.5, 35, 7], [15.5, 39, 11], 'metal', bone='rack', mirror=True)]
    bones = [{'name': 'cab', 'pivot': [0, 23, -51]}, {'name': 'turret', 'pivot': [0, 22, 8.8]},
             {'name': 'rack', 'pivot': [0, 37.6, 8.8], 'parent': 'turret'}] + steer_bones(steer) + jb
    extra = jerrycans('cans', -17, 19.5, 63.5, 2) + [
        crate('crate0', [1, 23, 28], [11, 29, 40]), crate('crate1', [2, 29, 29], [10, 33, 39], mat='paint'),
        tarp('tarp', [-12, 23, 28], [-2, 28, 54]), crate('crate2', [1, 23, 44], [11, 28, 56])
    ]
    return detail(spec('nasams', 'NASAMS launcher on an FMTV 6x6. Scale 1 m = 16 px. Identifiers: armoured cab-over, six AMRAAM canisters in two rows '
                'on a trainable elevating launcher with side arms, front axle far from the rear tandem, NATO green.',
                NATO_GREEN, PAINT2_DARK, bones, p + t, 701), extra)


def patriot():
    steer, rear = [-56, -32], [30, 54]
    jb, jp = jacks([(18, -14), (-18, -14), (18, 68.5), (-18, 68.5)])
    p = [q for q in fmtv_cab(-75, steer[:1]) if not q['name'].startswith('step')] + underbody(steer, rear) + frame(-72, 70, steer + rear) + deck(-44, 70, steer[1:] + rear) + mudflaps(65.25) + jp + wheels(steer, rear)
    p += [box('generator', [-12, 23, -44], [12, 37, -30], 'paint', panels=0,
              decals=[{'face': 'east', 'x': 2, 'y': 3, 'w': 10, 'h': 8, 'mat': 'vent'}, {'face': 'west', 'x': 2, 'y': 3, 'w': 10, 'h': 8, 'mat': 'vent'}]),
          box('cable_reel', [-11, 37, -42], [-3, 43, -32], 'paint2', panels=0),
          box('fuel_tank', [13, 12, -20], [20, 19, 8], 'paint', panels=0, decals=[{'face': 'east', 'x': 2, 'y': 2, 'w': 26, 'h': 1, 'mat': 'strap'}]),
          box('tool_box', [-20, 12, -20], [-13, 19, 8], 'paint', panels=0, decals=[{'face': 'west', 'x': 4, 'y': 2, 'w': 20, 'h': 4, 'mat': 'hatch'}])]
    # launcher: four large canisters 2x2, hinged at the rear, front rises
    c = []
    for i, (x0, y0) in enumerate([(-15, 30), (0.5, 30), (-15, 44.5), (0.5, 44.5)]):
        c.append(box(f'canister{i}', [x0, y0, -20], [x0 + 14, y0 + 13, 56], 'paint', bone='rack', panels=0,
                     decals=[{'face': 'north', 'x': 1, 'y': 1, 'w': 12, 'h': 11, 'mat': 'hatch'},
                             {'face': 'east', 'x': 6, 'y': 5, 'w': 60, 'h': 2, 'mat': 'rib'}, {'face': 'west', 'x': 6, 'y': 5, 'w': 60, 'h': 2, 'mat': 'rib'}]))
    c += [box('frame_front', [-16, 29, -14], [16, 59, -12], 'paint2', bone='rack', panels=0),
          box('frame_mid', [-16, 29, 18], [16, 59, 20], 'paint2', bone='rack', panels=0),
          box('frame_rear', [-16, 29, 50], [16, 59, 52], 'paint2', bone='rack', panels=0),
          box('hinge', [-12, 25, 52], [12, 30, 58], 'metal', bone='rack'),
          box('turntable', [-14, 23.5, -4], [14, 25.5, 22], 'metal', bone='turret'), box('bearing_ring', [-10, 25.5, 0], [10, 27.5, 18], 'metal', bone='turret'),
          box('lift_ram', [-2, 24, -10], [2, 30, 0], 'metal', bone='turret')]
    bones = [{'name': 'cab', 'pivot': [0, 23, -62]}, {'name': 'turret', 'pivot': [0, 22, 8.8]},
             {'name': 'rack', 'pivot': [0, 37.6, 56], 'parent': 'turret'}] + steer_bones(steer) + jb
    extra = jerrycans('cans', -17, 19.5, 71.5, 2) + [
        tarp('tarp', [-1, 37, -43], [11, 41, -31]),
        crate('crate1', [-11, 23, 58], [-5, 28, 66])]
    return detail(spec('patriot', 'Patriot M903 launching station on a HEMTT-style 8x8. Scale 1 m = 16 px. Identifiers: four big canisters 2x2 '
                'in a frame hinged at the rear, generator behind the cab, two steered front axles, sand CARC.',
                SAND, PAINT2_SAND, bones, p + c, 903), extra)


def s400():
    steer, rear = [-62, -38], [27, 50]
    jb, jp = jacks([(18, -22), (-18, -22), (18, 62), (-18, 62)])
    p = frame(-76, 70, steer + rear) + underbody(steer, rear) + deck(-26, 70, rear) + mudflaps(62) + jp + wheels(steer, rear) + arch('front_arch', steer, 12.5, 22.5, 22.5)
    # MAZ-543: two separate cabs flank the engine hood
    for s, x0 in (('r', 7), ('l', -20)):
        x1 = x0 + 13
        win = [{'face': 'north', 'x': 2, 'y': 2, 'w': 9, 'h': 7, 'mat': 'glass', 'clear': True}]
        sd = 'east' if s == 'r' else 'west'
        win += [{'face': sd, 'x': 4, 'y': 2, 'w': 14, 'h': 18, 'mat': 'hatch'}, {'face': sd, 'x': 6, 'y': 4, 'w': 9, 'h': 6, 'mat': 'glass', 'clear': True}]
        p += [box(f'cab_{s}', [x0, 23, -79], [x1, 45, -56], 'paint', bone='cab', decals=win),
              box(f'cab_roof_{s}', [x0 - 1, 45, -80], [x1 + 1, 49, -55], 'paint', bone='cab', chamfer={'legs': [4, 3], 'edges': ['left', 'right']}),
              box(f'seat_{s}', [x0 + 3, 24.5, -66], [x1 - 3, 28.5, -60], 'canvas', bone='cab'),
              box(f'seat_back_{s}', [x0 + 3, 28.5, -61], [x1 - 3, 38.5, -59], 'canvas', bone='cab'),
              box(f'dashboard_{s}', [x0 + 1.5, 31.5, -78.5], [x1 - 1.5, 34.5, -75.5], 'paint2', bone='cab',
                  decals=[{'face': 'south', 'x': 2, 'y': 1, 'w': 3, 'h': 1, 'mat': 'glass'}, {'face': 'south', 'x': 6, 'y': 1, 'w': 2, 'h': 1, 'mat': 'amber'}]),
              box(f'cab_floor_{s}', [x0 + 1, 23.5, -78.5], [x1 - 1, 24.5, -56.5], 'paint2', bone='cab')]
        p += liners(next(q for q in p if q['name'] == f'cab_{s}'))
    p += [box('hood', [-7, 23, -78], [7, 37, -56], 'paint', chamfer={'legs': [3, 4], 'edges': ['left', 'right']}, panels=0,
              decals=[{'face': 'up', 'x': 2, 'y': 4, 'w': 4, 'h': 14, 'mat': 'vent'}][:0]),
          box('hood_vent', [-3, 37, -74], [3, 38, -60], 'paint2', panels=0),
          box('grille', [-6, 24, -79], [6, 34, -78], 'paint2', panels=0, decals=[{'face': 'north', 'x': 1, 'y': 1, 'w': 10, 'h': 8, 'mat': 'grille'}]),
          box('bumper', [-21, 13, -82], [21, 18, -79], 'paint2', panels=0,
              decals=[{'face': 'north', 'x': 3, 'y': 1, 'w': 4, 'h': 3, 'mat': 'headlight'}, {'face': 'north', 'x': 35, 'y': 1, 'w': 4, 'h': 3, 'mat': 'headlight'}]),
          box('crew_box', [-19, 23, -55], [19, 39, -30], 'paint', panels=0,
              decals=[{'face': 'east', 'x': 4, 'y': 3, 'w': 12, 'h': 9, 'mat': 'hatch'}, {'face': 'west', 'x': 9, 'y': 3, 'w': 12, 'h': 9, 'mat': 'hatch'},
                      {'face': 'north', 'x': 14, 'y': 3, 'w': 10, 'h': 6, 'mat': 'vent'}]),
          box('exhaust', [-3, 39, -40], [3, 44, -34], 'metal'),
          box('cab_base', [-12, 17.5, -78.5], [12, 23.5, -56.5], 'paint2', panels=0),
          box('steering_wheel', [-17, 33, -73], [-11, 39, -72], 'rubber', bone='cab', rotation=[-22.5, 0, 0], pivot=[-14, 36, -72.5]),
          box('steering_column', [-15, 34.5, -75], [-13, 36.5, -72], 'metal', bone='cab'),
          box('front_apron', [-19.5, 15, -79.5], [19.5, 24, -75.5], 'paint', panels=0)]
    # four TPK tubes 2x2 on an erector hinged at the rear, rising to vertical
    t = [box('erector_base', [-14, 23.5, -4], [14, 25.5, 22], 'metal', bone='turret'), box('bearing_ring', [-10, 25.5, 0], [10, 27.5, 18], 'metal', bone='turret')]
    for i, (x0, y0) in enumerate([(-12, 28), (1, 28), (-12, 41), (1, 41)]):
        t.append(box(f'tpk{i}', [x0, y0, -24], [x0 + 11, y0 + 11, 60], 'paint', bone='rack', panels=0,
                     chamfer={'legs': [3, 4], 'edges': ['left', 'right']}))
        t.append(box(f'tpk_cap{i}', [x0 + 1, y0 + 1, -25], [x0 + 10, y0 + 9, -24], 'paint2', bone='rack', panels=0,
                     decals=[{'face': 'north', 'x': 1, 'y': 1, 'w': 7, 'h': 6, 'mat': 'hatch'}]))
    for j, z in enumerate((-16, 12, 44)):
        t.append(box(f'tpk_belt{j}', [-13, 27, z], [13, 53, z + 3], 'paint2', bone='rack', panels=0))
    t.append(box('erector_hinge', [-10, 24, 58], [10, 30, 64], 'metal', bone='rack'))
    bones = [{'name': 'cab', 'pivot': [0, 23, -67]}, {'name': 'turret', 'pivot': [0, 22, 8.8]},
             {'name': 'rack', 'pivot': [0, 37.6, 58], 'parent': 'turret'}] + steer_bones(steer) + jb
    extra = [tarp('tarp', [-15, 39, -54], [-3, 43, -32]), crate('crate0', [6, 39, -52], [16, 44, -42]),
             crate('crate1', [7, 39, -40], [15, 43, -32], mat='paint')] + jerrycans('cans', -17, 19.5, 71.5, 2) + \
        [box('ladder', [19, 24, -50], [20, 39, -46], 'metal'), box('extinguisher', [-21, 27, -40], [-19, 34, -38], 'paint2')]
    return detail(spec('s400', 'S-400 5P85SM2 launcher on a MAZ-543M 8x8. Scale 1 m = 16 px. Identifiers: two separate cabs flanking the engine hood, '
                'two steered front axles, four TPK tubes 2x2 with stiffening belts on a rear-hinged erector, crew box behind the cabs, olive.',
                OLIVE, PAINT2_DARK, bones, p + t, 400), extra)


def kamaz_base(name, seed):
    steer, rear = [-51], [22, 46]
    jb, jp = jacks([(17, -30), (-17, -30), (17, 65), (-17, 65)])
    p = kamaz_cab(-67, steer) + underbody(steer, rear) + frame(-64, 60, steer + rear) + mudflaps(57.5) + jp + wheels(steer, rear) + kung(-38, 58) + \
        arch('rear_arch', rear, 12.5, 22.5, 22) + [
        box('rear_panel', [-19, 17, 60], [19, 23, 61], 'paint2', panels=0, decals=[{'face': 'south', 'x': 2, 'y': 1, 'w': 34, 'h': 1, 'mat': 'bolts'}]),
        box('bumper_rear', [-19, 12, 60], [19, 17, 63], 'paint2', panels=0,
            decals=[{'face': 'south', 'x': 3, 'y': 1, 'w': 3, 'h': 2, 'mat': 'red'}, {'face': 'south', 'x': 32, 'y': 1, 'w': 3, 'h': 2, 'mat': 'red'},
                    {'face': 'south', 'x': 8, 'y': 1, 'w': 4, 'h': 2, 'mat': 'amber'}, {'face': 'south', 'x': 26, 'y': 1, 'w': 4, 'h': 2, 'mat': 'amber'}])]
    p += [box('subframe', [-12, 18, -39], [12, 23, 60], 'paint2', panels=0),
          box('fuel_tank', [13, 12, -33], [20, 19, -13], 'paint', panels=0, decals=[{'face': 'east', 'x': 2, 'y': 2, 'w': 16, 'h': 1, 'mat': 'strap'}]),
          box('spare_rack', [-20, 12, -33], [-13, 19, -13], 'paint2', panels=0)]
    bones = [{'name': 'cab', 'pivot': [0, 23, -54]}] + steer_bones(steer) + jb
    return bones, p


def kung_kit():
    """Roof AC unit, cable reels, roof rails, boxes under the kung, jerrycans by the door."""
    return [box('ac_unit', [1, 57, -34], [11, 61, -24], 'paint', panels=0,
                decals=[{'face': 'north', 'x': 1, 'y': 1, 'w': 8, 'h': 2, 'mat': 'vent'}, {'face': 'up', 'x': 2, 'y': 2, 'w': 6, 'h': 6, 'mat': 'grille'}]),
            box('kung_rail', [17, 57, -10], [18, 59, 56], 'metal', mirror=True),
            box('cable_reel', [-17, 57, 40], [-9, 63, 48], 'paint2', panels=0, decals=[{'face': 'east', 'x': 1, 'y': 1, 'w': 6, 'h': 4, 'mat': 'rib'}]),
            box('cable_reel2', [-7, 57, 40], [1, 63, 48], 'paint2', panels=0, decals=[{'face': 'east', 'x': 1, 'y': 1, 'w': 6, 'h': 4, 'mat': 'rib'}]),
            crate('kung_box_r', [13, 12, -10], [20, 23, 8], mat='paint'), crate('kung_box_l', [-20, 12, -10], [-13, 23, 8], mat='paint'),
            box('extinguisher', [-21.5, 27, 50], [-19.5, 34, 52], 'paint2')] + jerrycans('cans', -18, 19, 59.5, 1)


def ew():
    bones, p = kamaz_base('ew', 4)
    t = [box('mast_base', [-6, 57, 18], [6, 60, 30], 'metal', bone='turret'),
         box('mast', [-2, 60, 22], [2, 72, 26], 'metal', bone='turret'),
         box('head', [-8, 71, 18], [8, 76, 30], 'paint', bone='rack', panels=0),
         box('dish', [-22, 70, 12], [22, 100, 15], 'paint', bone='rack', panels=0,
             decals=[{'face': 'north', 'x': 2, 'y': 2, 'w': 40, 'h': 26, 'mat': 'grille'}]),
         box('dish_rim', [-23, 69, 15.5], [23, 71, 17.5], 'paint2', bone='rack', panels=0),
         box('feed_arm', [-1, 84, -6], [1, 86, 12], 'metal', bone='rack'),
         box('feed', [-2, 83, -9], [2, 87, -6], 'paint2', bone='rack'),
         box('dish_strut', [-1, 80, 15], [1, 82, 22], 'metal', bone='rack')]
    p += [box('generator', [-19, 55.5, -36], [-5, 61.5, -22], 'paint2', panels=0, decals=[{'face': 'west', 'x': 2, 'y': 1, 'w': 10, 'h': 4, 'mat': 'vent'}])]
    p.append({'type': 'antenna', 'name': 'whip', 'base': [15, 57, -34], 'height': 18})
    bones += [{'name': 'turret', 'pivot': [0, 57, 24]}, {'name': 'rack', 'pivot': [0, 70, 24], 'parent': 'turret'}]
    return detail(spec('ew', 'EW station (Krasukha-style) on a KamAZ 6x6 with a kung body. Scale 1 m = 16 px. Identifiers: KamAZ cab-over with a panoramic '
                'windshield and slatted grille, long kung with rear door and ladder, rotating dish antenna on a mast, whip antenna, olive.',
                OLIVE, PAINT2_DARK, bones, p + t, 14), kung_kit())


def radar():
    bones, p = kamaz_base('radar', 5)
    t = [box('mast_base', [-6, 57, 18], [6, 60, 30], 'metal', bone='turret'),
         box('mast', [-2, 60, 22], [2, 68, 26], 'metal', bone='turret'),
         box('yoke', [-12, 67, 21], [12, 70, 27], 'paint2', bone='radar', panels=0),
         box('array', [-20, 70, 22], [20, 94, 25], 'paint', bone='radar', panels=0, rotation=[-22.5, 0, 0], pivot=[0, 70, 25],
             decals=[{'face': 'north', 'x': 2, 'y': 2, 'w': 36, 'h': 20, 'mat': 'grille'}]),
         box('array_back', [-12, 72, 25.5], [12, 86, 27.5], 'paint2', bone='radar', panels=0, rotation=[-22.5, 0, 0], pivot=[0, 70, 25])]
    p += [box('generator', [-19, 55.5, -36], [-5, 61.5, -22], 'paint2', panels=0, decals=[{'face': 'west', 'x': 2, 'y': 1, 'w': 10, 'h': 4, 'mat': 'vent'}])]
    p.append({'type': 'antenna', 'name': 'whip', 'base': [15, 57, -34], 'height': 18})
    bones += [{'name': 'turret', 'pivot': [0, 57, 24]}, {'name': 'radar', 'pivot': [0, 60, 24], 'parent': 'turret'}]
    return detail(spec('radar', 'Air-search radar on a KamAZ 6x6 with a kung body. Scale 1 m = 16 px. Identifiers: KamAZ cab-over, long kung, '
                'flat phased-array antenna tilted back on a rotating yoke above the roof, whip antenna, olive.',
                OLIVE, PAINT2_DARK, bones, p + t, 15), kung_kit())


def himars():
    sp = json.loads((HERE / 'himars_base.json').read_text())
    sp['parts'] = [p for p in sp['parts'] if p['name'] not in ('deck', 'bumper_rear')]
    sp['parts'] = [p for p in sp['parts'] if p['name'] not in ('transfer_case', 'cross_member')]
    sp['parts'] = finish_cab(sp['parts'], [-40]) + deck(-23, 56, [14, 38]) + underbody([-40], [14, 38])
    for q in sp['parts']:
        if q['name'] == 'cable_tray':
            q['to'][2] = 20
    extra = [{'type': 'wheel', 'name': 'spare_wheel', 'center': [0, 34, -19], 'diameter': 22, 'width': 9},
             crate('crate0', [7, 23, -22], [15, 29, -10]), crate('crate1', [7.5, 29, -21], [14.5, 32, -11], mat='paint'),
             tarp('tarp', [-15, 23, -22], [-7, 27, -8])] + jerrycans('cans', -17, 19.5, 58.5, 2)
    return detail(sp, extra)


def pickup(name, weapon, seed):
    """Land Cruiser 79 / Hilux style technical, single cab.

    Real 5.2 x 1.9 x 1.95 m -> 84 x 32 x 34 px. Identifiers: separate front fenders with flared arches and a
    lower hood between them, two-tier cab (narrower glasshouse on a wider lower body) with a sloped roof
    front, tall bed sitting over the wheels with its own arches, vertical tail lamps, steel bull bar and
    winch, roll bar with spotlights behind the cab, weapon on a pintle in the bed, sandbags.
    """
    D, W, WX, WY = 17, 8, 12, 8.5          # wheels: diameter, width, track half, centre height
    FZ, RZ = -27, 22                         # axles
    sand_dec = lambda L, H: [{'face': 'up', 'x': 1, 'y': L // 2 - 1, 'w': 3, 'h': 2, 'mat': 'strap'}]
    p = [
        # chassis
        box('frame_rail', [4, 6, -42], [7, 9, 42], 'paint2', mirror=True, panels=0),
        box('axle_0', [-9, 7.5, FZ - 1], [9, 9.5, FZ + 1], 'metal'), box('axle_1', [-9, 7.5, RZ - 1], [9, 9.5, RZ + 1], 'metal'),
        box('diff_rear', [-3, 5, RZ - 3], [3, 10, RZ + 3], 'metal'), box('diff_front', [-3, 5, FZ - 2.5], [3, 10, FZ + 2.5], 'metal'),
        box('driveshaft', [-1, 6.5, FZ + 3], [1, 8.5, RZ - 3], 'metal'),
        box('leaf_spring', [5.5, 9, RZ - 8], [7.5, 10, RZ + 8], 'metal', mirror=True),
        box('fuel_tank', [-8, 8, 26], [2, 13, 38], 'paint2', panels=0),
        box('exhaust', [-11, 6.5, -16], [-9, 8.5, 10], 'metal'),
        box('tailpipe', [-15, 6.5, 8], [-11, 8.5, 10], 'metal'),
        box('muffler', [-12, 6, -6], [-8, 9, 4], 'metal'),
        box('oil_pan', [-4, 7, -23.5], [4, 10, -17.5], 'metal'),
        box('gearbox', [-3, 7, -17], [3, 11, -9], 'metal'),
        box('transfer', [-3, 6, -9], [3, 10, -4], 'metal'),
        box('cross0', [-4, 7, 0], [4, 9, 2], 'paint2', panels=0), box('cross1', [-4, 7, 38], [4, 9, 40], 'paint2', panels=0),
        box('spare_under', [-6, 7.5, 28], [6, 10.5, 37], 'rubber'),

        # front end: bumper with winch and bull bar
        box('bumper', [-16, 7, -45], [16, 12, -42], 'paint2', panels=0,
            decals=[{'face': 'north', 'x': 3, 'y': 1, 'w': 26, 'h': 1, 'mat': 'bolts'}, {'face': 'north', 'x': 1, 'y': 3, 'w': 30, 'h': 2, 'mat': 'grime'}]),
        box('winch', [-5, 8, -46], [5, 12, -45], 'metal', decals=[{'face': 'north', 'x': 1, 'y': 1, 'w': 8, 'h': 2, 'mat': 'rib'}]),
        box('tow_hook', [10, 6, -46], [12, 8, -44], 'metal', mirror=True),
        box('bull_post', [8, 11, -46.5], [10, 27, -44.5], 'metal', mirror=True),
        box('bull_top', [-10.5, 27, -46.5], [10.5, 29, -44.5], 'metal'),
        box('bull_mid', [-8, 21, -46], [8, 22, -45], 'metal'),
        box('grille_panel', [-10, 12, -42], [10, 25, -41], 'paint2', panels=0,
            decals=[{'face': 'north', 'x': 3, 'y': 1, 'w': 14, 'h': 9, 'mat': 'grille'}, {'face': 'north', 'x': 8, 'y': 11, 'w': 4, 'h': 1, 'mat': 'rib'}]),
        {'type': 'lamp', 'name': 'headlamp', 'center': [13, 21, -41], 'size': [3, 3], 'face': 'north', 'mirror': True},
        {'type': 'lamp', 'name': 'indicator', 'center': [13, 16, -41], 'size': [2, 1], 'face': 'north', 'mat': 'amber', 'mirror': True},
        box('nose', [-10, 10, -41], [10, 21, -18], 'paint', panels=0),
        box('hood', [-10, 21, -41], [10, 26, -18], 'paint', panels=0,
            decals=[{'face': 'up', 'x': 4, 'y': 4, 'w': 2, 'h': 16, 'mat': 'rib'}, {'face': 'up', 'x': 14, 'y': 4, 'w': 2, 'h': 16, 'mat': 'rib'},
                    {'face': 'up', 'x': 7, 'y': 14, 'w': 6, 'h': 4, 'mat': 'vent'}, {'face': 'north', 'x': 1, 'y': 0, 'w': 18, 'h': 1, 'mat': 'rib'}]),
        box('cowl', [-15, 24.5, -18.5], [15, 26.5, -15.5], 'paint2', panels=0),

        # front fenders: flat-topped wings with flared arches, wheel opening z -35.5..-18.5
        box('fender_top', [10, 18, -41], [16, 24, -18], 'paint', mirror=True, panels=0,
            decals=[{'face': 'east', 'x': 1, 'y': 0, 'w': 21, 'h': 1, 'mat': 'rib'}]),
        box('fender_front', [10, 12, -41], [16, 18, -36], 'paint', mirror=True, panels=0,
            decals=[{'face': 'east', 'x': 1, 'y': 3, 'w': 2, 'h': 2, 'mat': 'grime'}]),
        box('mudflap_front', [10.5, 3, -16.5], [16.5, 14, -15.5], 'mudflap', mirror=True),
        box('mudflap_front_bar', [10.5, 14, -17], [16.5, 15, -15], 'metal', mirror=True),

        # cab: wide lower body (doors) + narrower glasshouse with a sloped roof front
        box('cab_body', [-15, 12, -18], [15, 24, 3], 'paint', bone='cab',
            decals=[{'face': 'east', 'x': 3, 'y': 0, 'w': 14, 'h': 11, 'mat': 'hatch'}, {'face': 'east', 'x': 4, 'y': 2, 'w': 3, 'h': 1, 'mat': 'handle'},
                    {'face': 'west', 'x': 4, 'y': 0, 'w': 14, 'h': 11, 'mat': 'hatch'}, {'face': 'west', 'x': 14, 'y': 2, 'w': 3, 'h': 1, 'mat': 'handle'},
                    {'face': 'east', 'x': 1, 'y': 9, 'w': 19, 'h': 2, 'mat': 'grime'}, {'face': 'west', 'x': 1, 'y': 9, 'w': 19, 'h': 2, 'mat': 'grime'}]),
        box('glasshouse', [-14, 24, -16], [14, 37, 2], 'paint', bone='cab', chamfer={'legs': [3, 4], 'edges': ['front']},
            decals=[{'face': 'north', 'x': 2, 'y': 5, 'w': 24, 'h': 7, 'mat': 'glass', 'clear': True},
                    {'face': 'east', 'x': 2, 'y': 5, 'w': 12, 'h': 7, 'mat': 'glass', 'clear': True}, {'face': 'west', 'x': 4, 'y': 5, 'w': 12, 'h': 7, 'mat': 'glass', 'clear': True},
                    {'face': 'south', 'x': 6, 'y': 5, 'w': 16, 'h': 5, 'mat': 'glass'}]),
        box('roof_gutter', [14, 36, -12], [15, 37, 2], 'paint2', bone='cab', mirror=True),
        box('dashboard', [-13, 24.5, -15.5], [13, 27.5, -13.5], 'paint2', bone='cab',
            decals=[{'face': 'south', 'x': 2, 'y': 1, 'w': 3, 'h': 1, 'mat': 'glass'}, {'face': 'south', 'x': 6, 'y': 1, 'w': 3, 'h': 1, 'mat': 'glass'},
                    {'face': 'south', 'x': 12, 'y': 1, 'w': 2, 'h': 1, 'mat': 'amber'}]),
        box('steering_column', [-8, 26.5, -14], [-6, 28.5, -12], 'metal', bone='cab'),
        box('console', [-2, 24.5, -13.5], [2, 27.5, -5.5], 'paint2', bone='cab'),
        box('gear_lever', [-0.5, 27.5, -9], [0.5, 30.5, -8], 'metal', bone='cab'),
        box('headrest', [3, 32.5, -3], [10, 34.5, -1], 'canvas', bone='cab', mirror=True),
        box('steering_wheel', [-10, 25, -13], [-4, 31, -12], 'rubber', bone='cab', rotation=[-22.5, 0, 0], pivot=[-7, 28, -12.5]),
        box('seat', [2, 24.5, -9], [11, 26.5, -2], 'canvas', bone='cab', mirror=True),
        box('seat_back', [2, 26.5, -3], [11, 32.5, -1], 'canvas', bone='cab', mirror=True),
        box('headliner', [-13, 35.25, -12.5], [13, 36.25, 0.5], 'paint2', bone='cab'),
        box('mirror_arm', [15, 26, -16], [18, 27, -15], 'metal', bone='cab', mirror=True),
        box('mirror_head', [18, 24, -17], [19, 29, -14], 'paint2', bone='cab', mirror=True),
        box('snorkel', [15.5, 15, -14.5], [17.5, 36, -12.5], 'paint2'),
        box('snorkel_head', [15.5, 36, -16.5], [17.5, 39, -11.5], 'paint2'),
        box('step', [15, 10, -15], [20, 11, -1], 'paint2', mirror=True, panels=0, decals=[{'face': 'up', 'x': 1, 'y': 3, 'w': 3, 'h': 1, 'mat': 'rib'}, {'face': 'up', 'x': 1, 'y': 9, 'w': 3, 'h': 1, 'mat': 'rib'}]),

        # bed over the wheels, own arches, rear wheel opening z 13.5..30.5
        box('bed_floor', [-14, 18, 4], [14, 20, 41], 'paint2', panels=0),
        box('bed_side', [14, 20, 4], [15, 27, 41], 'paint', mirror=True, panels=0,
            decals=[{'face': 'east', 'x': 2, 'y': 2, 'w': 33, 'h': 1, 'mat': 'rib'}, {'face': 'east', 'x': 1, 'y': 5, 'w': 35, 'h': 2, 'mat': 'grime'}]),
        box('bed_rail', [13.5, 27, 4], [15.5, 28, 41], 'metal', mirror=True),
        box('bed_skirt_front', [14, 12, 4], [15, 20, 12], 'paint', mirror=True, panels=0),
        box('bed_skirt_rear', [14, 12, 31], [15, 20, 41], 'paint', mirror=True, panels=0,
            decals=[{'face': 'east', 'x': 1, 'y': 5, 'w': 7, 'h': 2, 'mat': 'grime'}]),
        box('wheel_tub', [9, 17, 13], [14, 18, 32], 'paint2', mirror=True, panels=0),
        box('bed_front', [-14, 20, 4], [14, 27, 5], 'paint', panels=0),
        box('tailgate', [-14, 20, 40], [14, 27, 41], 'paint', panels=0,
            decals=[{'face': 'south', 'x': 3, 'y': 2, 'w': 22, 'h': 1, 'mat': 'rib'}, {'face': 'south', 'x': 12, 'y': 4, 'w': 4, 'h': 1, 'mat': 'handle'}]),
        {'type': 'lamp', 'name': 'tail_lamp', 'center': [14.5, 23.5, 41.5], 'size': [1, 4], 'face': 'south', 'mat': 'red', 'mirror': True},
        box('bumper_rear', [-15, 11, 41], [15, 14, 43], 'paint2', panels=0, decals=[{'face': 'south', 'x': 2, 'y': 1, 'w': 26, 'h': 1, 'mat': 'bolts'}]),
        box('hitch', [-1.5, 10, 43], [1.5, 12, 46], 'metal'),
        box('rear_apron', [-14, 14, 40], [14, 18, 41], 'paint2', panels=0),
        box('mudflap_rear', [10.5, 3, 33], [16.5, 14, 34], 'mudflap', mirror=True),
        box('mudflap_rear_bar', [10.5, 14, 32.5], [16.5, 15, 34.5], 'metal', mirror=True),

        # roll bar with spotlights
        box('roll_post', [12, 28, 4.5], [14, 33, 6.5], 'metal', mirror=True),
        box('roll_top', [-14, 33, 4.5], [14, 35, 6.5], 'metal'),
        box('roll_brace', [12.5, 28, 7], [13.5, 33, 8], 'metal', mirror=True),
        {'type': 'lamp', 'name': 'spot', 'center': [12, 37, 6], 'size': [3, 3], 'face': 'north', 'housing': 'paint2', 'mirror': True},

        # cargo: sandbags at the bed front, jerrycans, ammo
        box('sandbag0', [-13, 20, 5.5], [-6, 23, 9.5], 'canvas', panels=0, decals=sand_dec(4, 3)),
        box('sandbag1', [6, 20, 5.5], [13, 23, 9.5], 'canvas', panels=0, decals=sand_dec(4, 3)),
        box('sandbag2', [-12, 23, 6], [-5, 26, 10], 'canvas', panels=0),
        box('sandbag3', [5, 23, 6], [12, 26, 10], 'canvas', panels=0),
        crate('ammo0', [7, 20, 30], [13, 24, 38]), crate('ammo1', [7.5, 24, 31], [12.5, 27, 37], mat='paint'),
    ]
    p += liners(next(q for q in p if q['name'] == 'glasshouse'))
    for zc, nm in ((FZ, 'flare_front'), (RZ, 'flare_rear')):
        p += arch(nm, [zc], 15, 17, 17.5, r=7)
        p += [box(f'{nm}_leg_a', [16, 10.5, zc - 10], [17, 14.5, zc - 9], 'paint2', mirror=True),
              box(f'{nm}_leg_b', [16, 10.5, zc + 9], [17, 14.5, zc + 10], 'paint2', mirror=True)]
    p += jerrycans('cans', -13, 20.5, 35, 2, step=6)
    p.append({'type': 'antenna', 'name': 'whip', 'base': [-12, 37, -2], 'height': 16, 'bone': 'cab'})
    p += [wheel('wheel_0_r', [WX, WY, FZ], D, W, bone_parent='steer_0_r'), wheel('wheel_0_l', [-WX, WY, FZ], D, W, bone_parent='steer_0_l'),
          wheel('wheel_1', [WX, WY, RZ], D, W, mirror=True)]

    # weapon on a pintle in the bed (turret yaws, rack pitches)
    t = [box('pintle_base', [-4, 20, 14], [4, 22, 30], 'metal', bone='turret'),
         box('pintle', [-1, 21.5, 17], [1, 37.5, 19], 'metal', bone='turret'),
         box('pintle_brace', [-3, 22, 16], [3, 24, 20], 'paint2', bone='turret')]
    if weapon == 'mg':
        t += [box('cradle', [-2.5, 37, 14], [2.5, 40, 22], 'paint2', bone='rack'),
              box('receiver', [-2, 40, 8], [2, 44, 24], 'metal', bone='rack', decals=[{'face': 'east', 'x': 2, 'y': 1, 'w': 10, 'h': 1, 'mat': 'rib'}]),
              box('feed_cover', [-2.5, 44, 12], [2.5, 45, 20], 'paint2', bone='rack'),
              box('barrel_jacket', [-1.5, 40.5, -2], [1.5, 43.5, 8], 'metal', bone='rack', decals=[{'face': 'east', 'x': 1, 'y': 1, 'w': 8, 'h': 1, 'mat': 'vent'}]),
              box('barrel', [-1, 41, -18], [1, 43, -2], 'metal', bone='rack'),
              box('muzzle_brake', [-1.5, 40.5, -21], [1.5, 43.5, -18], 'metal', bone='rack'),
              box('front_sight', [-0.5, 43, -16], [0.5, 45, -15], 'metal', bone='rack'),
              box('rear_sight', [-1, 44.5, 18], [1, 46.5, 19], 'metal', bone='rack'),
              box('grips', [-2, 38, 24], [2, 41, 27], 'rubber', bone='rack'),
              box('ammo_can', [-7, 37, 12], [-3, 42, 19], 'paint', bone='rack', panels=0, decals=[{'face': 'west', 'x': 2, 'y': 1, 'w': 3, 'h': 1, 'mat': 'handle'}]),
              box('ammo_belt', [-3.5, 39.5, 14], [-2.5, 41.5, 18], 'metal', bone='rack'),
              box('shield', [-9, 37, 9], [9, 49, 10], 'paint', bone='rack', panels=0,
                  decals=[{'face': 'north', 'x': 7, 'y': 5, 'w': 4, 'h': 2, 'mat': 'dark'}, {'face': 'north', 'x': 1, 'y': 9, 'w': 16, 'h': 2, 'mat': 'grime'}]),
              box('shield_wing', [9, 37, 9.5], [11, 47, 13.5], 'paint', bone='rack', mirror=True, panels=0)]
    else:
        t += [box('cradle', [-8, 37.5, 12], [8, 40.5, 24], 'paint2', bone='rack', panels=0),
              box('tube_r', [3, 41, -8], [7, 45, 26], 'paint', bone='rack', panels=0,
                  decals=[{'face': 'east', 'x': 6, 'y': 1, 'w': 20, 'h': 1, 'mat': 'rib'}]),
              box('tube_l', [-7, 41, -8], [-3, 45, 26], 'paint', bone='rack', panels=0,
                  decals=[{'face': 'west', 'x': 6, 'y': 1, 'w': 20, 'h': 1, 'mat': 'rib'}]),
              box('tube_cap', [2.5, 40.5, -9], [7.5, 45.5, -8], 'paint2', bone='rack', mirror=True),
              box('tube_band', [2.5, 40.5, 4], [7.5, 45.5, 6], 'paint2', bone='rack', mirror=True),
              box('sight', [-2, 42, 8], [2, 46, 16], 'paint2', bone='rack', decals=[{'face': 'north', 'x': 1, 'y': 1, 'w': 2, 'h': 2, 'mat': 'glass'}]),
              box('iff_box', [-2, 46, 10], [2, 48, 15], 'paint2', bone='rack', decals=[{'face': 'north', 'x': 1, 'y': 0, 'w': 2, 'h': 1, 'mat': 'amber'}]),
              box('iff_whip', [-0.5, 48, 12], [0.5, 54, 13], 'metal', bone='rack'),
              box('battery', [-2, 38, 17.5], [2, 41, 23.5], 'paint2', bone='rack'),
              box('grips', [-1.5, 37.5, 24], [1.5, 40.5, 27], 'rubber', bone='rack'),
              box('seat_post', [-1, 22, 25], [1, 30, 27], 'metal', bone='turret'),
              box('gunner_seat', [-4, 30, 24], [4, 32, 30], 'canvas', bone='turret')]
    bones = [{'name': 'cab', 'pivot': [0, 17, -10]}, {'name': 'turret', 'pivot': [0, 16, 17.6]},
             {'name': 'rack', 'pivot': [0, 40, 17.6], 'parent': 'turret'}] + steer_bones([FZ], WX, WY)
    what = 'heavy machine gun behind a shield' if weapon == 'mg' else 'twin MANPADS launcher with IFF and a gunner seat'
    return detail(spec(name, f'Land Cruiser 79 / Hilux technical with a {what} on a pintle in the bed. Scale 1 m = 16 px. '
                       'Identifiers: flared front fenders with a lower hood between them, two-tier cab with a sloped roof front, '
                       'tall bed over the wheels with arches, vertical tail lamps, bull bar and winch, roll bar with spotlights, sandbags.',
                       ["#6A5843", "#AC946A", "#B39B70", "#CFBA8B"], PAINT2_SAND, bones, p + t, seed), [], 0.55)


# ---------------------------------------------------------------- extra mounted kit on antennas, launchers and sides

def hatch_box(name, frm, to, face='east', mat='paint', **kw):
    """Small mounted cabinet with an access door on one face."""
    w, h, d = (to[0] - frm[0], to[1] - frm[1], to[2] - frm[2])
    fw = d if face in ('east', 'west') else w
    return box(name, frm, to, mat, panels=0, decals=[{'face': face, 'x': 1, 'y': 1, 'w': max(1, int(fw) - 2), 'h': max(1, int(h) - 2), 'mat': 'hatch'}], **kw)


ROT = {'rotation': [-22.5, 0, 0], 'pivot': [0, 70, 25]}
MORE = {
    'radar': [
        box('array_frame_top', [-21, 94, 21.5], [21, 96, 25.5], 'paint2', bone='radar', panels=0, **ROT),
        box('array_frame_side', [20, 70, 21.5], [22, 94, 25.5], 'paint2', bone='radar', panels=0, mirror=True, **ROT),
        box('iff_strip', [-15, 96, 22.5], [15, 98, 24.5], 'paint', bone='radar', panels=0, **ROT),
        hatch_box('array_box', [-8, 74, 27.5], [8, 84, 31.5], face='south', bone='radar', **ROT),
        box('yoke_arm', [22.5, 67, 22], [24.5, 73, 26], 'paint2', bone='radar', mirror=True),
        box('mast_cable', [-1, 60, 26.5], [1, 68, 28.5], 'rubber', bone='turret'),
        hatch_box('drive_box', [6.5, 57, 20], [10.5, 62, 28], bone='turret', mirror=True),
    ],
    'ew': [
        box('dish_rib', [-13, 72, 15.5], [-11, 98, 17.5], 'paint2', bone='rack', panels=0, mirror=True),
        box('dish_cross', [-20, 84, 16], [20, 86, 17], 'paint2', bone='rack', panels=0),
        hatch_box('head_box', [8.5, 71, 19], [12.5, 77, 29], bone='rack', mirror=True),
        box('feed_cable', [1.5, 83, -5], [2.5, 84, 12], 'rubber', bone='rack'),
        box('mast_ladder', [2.5, 60, 24], [3.5, 71, 25], 'metal', bone='turret'),
        hatch_box('drive_box', [6.5, 57, 20], [10.5, 62, 28], bone='turret', mirror=True),
    ],
    'nasams': [
        hatch_box('arm_box', [16.5, 28, 3], [19.5, 34, 11], bone='turret', mirror=True),
        box('sensor', [-3, 48.5, -27], [3, 51.5, -21], 'paint2', bone='rack', decals=[{'face': 'north', 'x': 1, 'y': 1, 'w': 4, 'h': 1, 'mat': 'glass'}]),
        box('lug', [10, 48, -14], [12, 50, -10], 'metal', bone='rack', mirror=True),
        box('cable_tray', [-3, 28.5, 0], [3, 30.5, 16], 'paint2', bone='turret'),
        box('lift_ram', [-9, 27, 2], [-6, 32, 8], 'metal', bone='turret', mirror=True),
        hatch_box('side_bin', [13, 12, 2], [20, 19, 10], mirror=True),
    ],
    'patriot': [
        hatch_box('frame_box', [14.5, 32, -10], [17.5, 40, 0], bone='rack', mirror=True),
        box('cable_tray', [-12, 27.5, -10], [-8, 29.5, 48], 'paint2', bone='rack', panels=0),
        box('lug', [-14, 58.5, -18], [-12, 60.5, -16], 'metal', bone='rack', mirror=True),
        hatch_box('turret_box', [14.5, 23.5, 2], [18.5, 29.5, 12], bone='turret', mirror=True),
        box('antenna_mast', [8, 40.5, -35], [10, 56.5, -33], 'metal'),
        box('antenna_head', [7, 56.5, -36], [11, 59.5, -32], 'paint2'),
        hatch_box('side_bin', [13, 12, 10], [20, 19, 18], mirror=True),
    ],
    's400': [
        box('tpk_tray', [12.5, 30, -20], [14.5, 32, 56], 'paint2', bone='rack', mirror=True, panels=0),
        hatch_box('erector_box', [14.5, 33, 50], [18.5, 41, 58], bone='rack', mirror=True),
        box('erector_ram', [-9, 26, 2], [-6, 32, 10], 'metal', bone='turret', mirror=True),
        hatch_box('turret_box', [14.5, 23.5, 2], [18.5, 29.5, 12], bone='turret', mirror=True),
        hatch_box('side_bin', [13, 12, -19], [20, 19, -9], mirror=True),
        box('crew_box_vent', [-2, 39, -50], [2, 40, -46], 'paint2'),
    ],
    'himars': [],
    'pickup_mg': [],
    'pickup_sam': [],
}


if __name__ == '__main__':
    for s in (himars(), nasams(), patriot(), s400(), ew(), radar(), pickup('pickup_mg', 'mg', 21), pickup('pickup_sam', 'sam', 22)):
        s['parts'] += MORE.get(s['name'], [])
        heal.heal(s)
        (HERE / f'{s["name"]}.json').write_text(json.dumps(s, indent=1, ensure_ascii=False))
        print('wrote', s['name'])
