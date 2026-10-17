"""Second batch of specs: hooded-cab trucks (Ural, ZIL) and their launchers, MAZ-543 family, tracked IFVs, static posts.

Same conventions as gen.py (1 m = 16 px, front = -Z, right = +X, ground y = 0) and the mod's bone contract:
cab/hull (faction marks), turret > rack > tipN (aiming, ammo shown by tips), steer_Nx > wheel_Nx, wheel_Nx, jackN.
usage: python3 gen2.py  -> writes <name>.json next to this file
"""
import json
import math

import heal
from gen import (cab_medkit, side_mirror, shift, HERE, NATO_GREEN, OLIVE, PAINT2_DARK, PAINT2_SAND, SAND, arch, box, crate, deck, detail, frame, jacks, jerrycans, liners,
                 mudflaps, shovel, spec, steer_bones, tarp, underbody, wheels, kamaz_cab, fmtv_cab, kung, wheel, hatch_box)


def tip_grid(cols, rows, x_right, y0, pitch_x, pitch_y, z_face, size, pivot, nose='paint2', mat='canvas'):
    """Rocket noses poking out of tube mouths: one bone per tube (visible while that rocket is loaded).

    tip0 is the right-hand (+X) column of the bottom row; x_right is the +X edge of that cone. Cone = size x size x 1
    flush on the pack face (z_face), nose = 1 px square one further out."""
    bones, parts = [], []
    k = 0
    for r in range(rows):
        for c in range(cols):
            n = f'tip{k}'
            x1 = x_right - c * pitch_x
            y = y0 + r * pitch_y
            bones.append({'name': n, 'pivot': list(pivot), 'parent': 'rack'})
            parts.append(box(f'{n}_cone', [x1 - size, y, z_face - 1], [x1, y + size, z_face], mat, bone=n, panels=0))
            c0 = (size - 1) / 2
            parts.append(box(f'{n}_nose', [x1 - size + 1, y + 1, z_face - 2], [x1 - 1, y + size - 1, z_face - 1], nose, bone=n, panels=0))
            k += 1
    return bones, parts


# ---------------------------------------------------------------- round parts: tubes, missiles

def cyl(name, x0, y0, z0, z1, d, mat='paint', **kw):
    """Round-ish prism along Z (octagon from 2-3 stacked boxes). (x0, y0) is the lower -X/-Y corner, d the diameter in px (even, 2..16)."""
    if d <= 3 or z1 - z0 < 4:
        return [box(name, [x0, y0, z0], [x0 + d, y0 + d, z1], mat, panels=0, **kw)]
    if d <= 7:
        return [box(name, [x0, y0 + 1, z0], [x0 + d, y0 + d - 1, z1], mat, panels=0, **kw),
                box(name + '_b', [x0 + 1, y0, z0 + 0.5], [x0 + d - 1, y0 + d, z1 - 0.5], mat, panels=0, **kw)]
    return [box(name, [x0, y0 + 2, z0], [x0 + d, y0 + d - 2, z1], mat, panels=0, **kw),
            box(name + '_b', [x0 + 2, y0, z0 + 0.5], [x0 + d - 2, y0 + d, z1 - 0.5], mat, panels=0, **kw),
            box(name + '_c', [x0 + 1, y0 + 1, z0 + 1], [x0 + d - 1, y0 + d - 1, z1 - 1], mat, panels=0, **kw)]


MISSILE_GREEN = ['#3F4A2D', '#566240', '#6E7C51', '#8A9868']   # body of the Iskander missile (110, 124, 81)


def missile(name, bone, cx, cy, z_nose, length, d, body='canvas', dark='paint2', fins=True, finspan=None, rings=3, **kw):
    """Missile in the style of the Iskander model: olive body (mat canvas, set spec canvas = MISSILE_GREEN), dark steel belts,
    stepped pointed nose, nozzle, four swept tail fins, cable raceway on top. Nose at z_nose (-Z end)."""
    if bone:
        kw['bone'] = bone
    r = d / 2
    p = []
    z = z_nose
    steps = ((d - 8, 2), (d - 6, 2), (d - 4, 3), (d - 2, 3)) if d >= 10 else ((d - 4, 2), (d - 2, 2))
    for k, (dd, ln) in enumerate(steps):
        if dd >= 2:
            p += cyl(f'{name}_nose{k}', cx - dd / 2, cy - dd / 2, z, z + ln, dd, dark, **kw)
        z += ln
    tail = z_nose + length
    p += cyl(f'{name}_nose_ring', cx - r - 1, cy - r - 1, z, z + 2, d + 2, dark, **kw)
    z += 2
    end = tail - 2
    cuts = [z + round((end - z) * (i + 1) / (rings + 1)) for i in range(rings)] if d >= 6 else []
    a0 = z
    for i, c in enumerate(cuts):
        p += cyl(f'{name}_body{i}', cx - r, cy - r, a0, c, d, body, **kw)
        p += cyl(f'{name}_belt{i}', cx - r - 1, cy - r - 1, c, c + 2, d + 2, dark, **kw)
        a0 = c + 2
    p += cyl(f'{name}_body{len(cuts)}', cx - r, cy - r, a0, end, d, body, **kw)
    p += cyl(f'{name}_nozzle', cx - (d - 2) / 2, cy - (d - 2) / 2, end, tail, d - 2, 'metal', **kw)
    if d >= 8:
        p.append(box(f'{name}_raceway', [cx - 1, cy + r, z + 2], [cx + 1, cy + r + 2, end - 4], dark, panels=0, **kw))
    if fins:
        fs = finspan or d // 2 + 4
        fl = max(5, length // 6)
        p += [box(f'{name}_fin_h', [cx - fs, cy - 0.5, tail - fl], [cx + fs, cy + 0.5, tail - 1], dark, panels=0, **kw),
              box(f'{name}_fin_h2', [cx - fs + 1, cy + 0.5, tail - fl + 2], [cx + fs - 1, cy + 1.5, tail - 1], dark, panels=0, **kw),
              box(f'{name}_fin_v', [cx - 0.5, cy - fs, tail - fl + 1], [cx + 0.5, cy + fs, tail - 4], dark, panels=0, **kw)]
    return p


def maz_base(name, seed):
    """MAZ-543 chassis of the S-400 spec without its launcher: two cabs flanking the hood, crew box, 8x8 wheels, jacks."""
    sp = json.loads((HERE / 's400.json').read_text())
    keep = [p for p in sp['parts'] if p.get('bone') not in ('turret', 'rack', 'ram0', 'ram0_rod') and p['name'] not in ('tarp', 'crate0', 'crate1', 'crew_box_vent') and not p['name'].startswith('ram0')]
    bones = [b for b in sp['bones'] if b['name'] not in ('turret', 'rack', 'ram0', 'ram0_rod')]
    return bones, keep


def ram_set(name, y, z_foot, L, x_off, y_under, parent='turret', hinge='rack', mount=None, barrel=None, base_y=23):
    """Two-stage hydraulic pair. Barrel sits in a clevis foot (trunnion pin through its pivot); explicit moving pin follows the cradle;
    rigid sleeves slide at their seals: barrel > stage (4 px) > chrome rod (3 px) > head in the cradle eye."""
    kw = {'bone': name, 'mirror': True}
    ribs = [{'face': 'up', 'x': 1, 'y': k, 'w': 4, 'h': 1, 'mat': 'rib'} for k in range(2, L - 1, 4)]
    mk = {'bone': mount} if mount else {}
    stage, rod = f'{name}_stage', f'{name}_rod'
    pin_z = z_foot - L - 4.5
    parts = [box(f'{name}_barrel', [x_off - 3, y - 3, z_foot - L], [x_off + 3, y + 3, z_foot], 'paint', panels=0, decals=ribs, **kw),
             box(f'{name}_collar', [x_off - 4, y - 4, z_foot - L - 2], [x_off + 4, y + 4, z_foot - L], 'metal', panels=0, **kw),
             box(f'{name}_stagebar', [x_off - 2, y - 2, z_foot - L - 3], [x_off + 2, y + 2, z_foot - 4], 'metal', panels=0, bone=stage, mirror=True),
             box(f'{name}_stagecap', [x_off - 2.5, y - 2.5, z_foot - L - 4], [x_off + 2.5, y + 2.5, z_foot - L - 2], 'paint2', panels=0, bone=stage, mirror=True),
             box(f'{name}_rodbar', [x_off - 1.5, y - 1.5, pin_z], [x_off + 1.5, y + 1.5, pin_z + L - 2], 'grey', panels=0, bone=rod, mirror=True),
             box(f'{name}_rodhead', [x_off - 3, y - 3.5, pin_z - 2], [x_off + 3, y + 3.5, pin_z + 2], 'metal', panels=0, bone=rod, mirror=True),
             box(f'{name}_foot', [x_off - 4.5, y - 4, z_foot - 3.5], [x_off + 4.5, y + 4, z_foot + 3.5], 'metal', mirror=True, panels=0, **mk),
             box(f'{name}_foot_pin', [x_off - 5.5, y - 1.5, z_foot - 1.5], [x_off + 5.5, y + 1.5, z_foot + 1.5], 'grey', mirror=True, panels=0, **mk),
             box(f'{name}_eye', [x_off - 4, y - 4, z_foot - L - 6], [x_off + 4, y_under, z_foot - L - 3], 'metal', bone=hinge, mirror=True, panels=0)]
    if y - 3.5 > base_y:       # half-pixel offsets keep every ram face off the planes of the turntable / pack it stands on (no z-fighting)
        parts.append(box(f'{name}_pedestal', [x_off - 2.5, base_y - 1.5, z_foot - 2.5], [x_off + 2.5, y - 3.5, z_foot + 2.5], 'paint2', mirror=True, panels=0, **mk))
    pv = [0, y, z_foot]
    return [{'name': name, 'pivot': pv, **({'parent': parent} if parent else {})},
            {'name': stage, 'pivot': pv, 'parent': name}, {'name': rod, 'pivot': pv, 'parent': stage},
            {'name': f'{name}_mount', 'pivot': [0, y, pin_z], 'parent': hinge}], parts


def a_frame(name, z, y0, y1, x, bone='turret', pin_y=None):
    """Hinge posts either side of the swinging pack with a pin through the rack: reads as the lifting crane's heel."""
    pin_y = pin_y or y1 - 2
    return [box(f'{name}_post', [x, y0, z - 3], [x + 3, y1, z + 3], 'paint', bone=bone, mirror=True, panels=0,
                decals=[{'face': 'east', 'x': 1, 'y': 2, 'w': 4, 'h': y1 - y0 - 4, 'mat': 'hatch'}]),
            box(f'{name}_pin', [x - 2, pin_y - 1, z - 1], [x + 5, pin_y + 1, z + 1], 'metal', bone=bone, mirror=True, panels=0)]


# ---------------------------------------------------------------- hooded cab (Ural-4320 / ZIL-131 family)

def hood_cab(front_z, steer_z, paint_win=True):
    """Conventional truck front: long hood between wing fenders, flat-faced two-door cab, mirrors, bumper, winch, exhaust stack.

    front_z is the bumper face. hood 24 px, cab 26 px deep. All cab parts sit in bone 'cab'."""
    z = front_z
    hz1, cz0, cz1 = z + 26, z + 26, z + 52
    cab_dec = [{'face': 'north', 'x': 2, 'y': 5, 'w': 30, 'h': 11, 'mat': 'glass', 'clear': True},
               {'face': 'east', 'x': 3, 'y': 5, 'w': 11, 'h': 10, 'mat': 'glass', 'clear': True},
               {'face': 'east', 'x': 3, 'y': 5, 'w': 1, 'h': 13, 'mat': 'rib'},
               {'face': 'east', 'x': 15, 'y': 18, 'w': 3, 'h': 1, 'mat': 'handle'},
               {'face': 'east', 'x': 3, 'y': 18, 'w': 20, 'h': 1, 'mat': 'rib'},
               {'face': 'east', 'x': 2, 'y': 22, 'w': 22, 'h': 3, 'mat': 'grime'},
               {'face': 'west', 'x': 12, 'y': 5, 'w': 11, 'h': 10, 'mat': 'glass', 'clear': True},
               {'face': 'west', 'x': 22, 'y': 5, 'w': 1, 'h': 13, 'mat': 'rib'},
               {'face': 'west', 'x': 9, 'y': 18, 'w': 3, 'h': 1, 'mat': 'handle'},
               {'face': 'west', 'x': 3, 'y': 18, 'w': 20, 'h': 1, 'mat': 'rib'},
               {'face': 'west', 'x': 2, 'y': 22, 'w': 22, 'h': 3, 'mat': 'grime'},
               {'face': 'south', 'x': 8, 'y': 6, 'w': 18, 'h': 6, 'mat': 'glass'}]
    p = [
        box('cab', [-17, 22, cz0], [17, 48, cz1], 'paint', bone='cab', decals=cab_dec),
        box('cab_roof', [-17, 48, cz0 - 1], [17, 54, cz1 + 1], 'paint', bone='cab', chamfer={'legs': [3, 4], 'edges': ['left', 'right']},
            decals=[{'face': 'up', 'x': 11, 'y': 6, 'w': 12, 'h': 14, 'mat': 'rib'}]),
        box('visor', [-18, 46, cz0 - 3], [18, 47, cz0], 'paint2', bone='cab', panels=0),
        box('cab_base', [-12, 17.5, cz0 + 10.5], [12, 22.5, cz1 - 0.5], 'paint2', bone='cab', panels=0),
        box('cab_floor', [-16, 22.5, cz0 + 0.5], [16, 23.5, cz1 - 0.5], 'paint2', bone='cab'),
        box('dashboard', [-14, 27, cz0 + 1], [14, 31, cz0 + 4], 'paint2', bone='cab',
            decals=[{'face': 'south', 'x': 3, 'y': 1, 'w': 4, 'h': 2, 'mat': 'glass'}, {'face': 'south', 'x': 9, 'y': 1, 'w': 4, 'h': 2, 'mat': 'glass'},
                    {'face': 'south', 'x': 24, 'y': 1, 'w': 2, 'h': 1, 'mat': 'amber'}]),
        box('steering_column', [-10, 28, cz0 + 3], [-8, 30, cz0 + 7], 'metal', bone='cab'),
        box('steering_wheel', [-13, 29, cz0 + 6], [-5, 36, cz0 + 7], 'rubber', bone='cab', rotation=[-22.5, 0, 0], pivot=[-9, 32, cz0 + 6.5]),
        box('seat', [-14, 24, cz0 + 11], [14, 27, cz1 - 6], 'canvas', bone='cab', panels=0),
        box('seat_back', [-14, 27, cz1 - 7], [14, 39, cz1 - 5], 'canvas', bone='cab', panels=0),
        box('headliner', [-14, 46.25, cz0 + 1], [14, 47.25, cz1 - 1], 'paint2', bone='cab'),
        *side_mirror('mirror', 17, cz0 + 1, 31, 12, reach=5, w=4, bone='cab'),
        box('step', [17, 15, cz0 + 4], [21, 16, cz0 + 14], 'paint2', mirror=True, panels=0,
            decals=[{'face': 'up', 'x': 1, 'y': k, 'w': 3, 'h': 1, 'mat': 'rib'} for k in (2, 5, 8)]),
        box('step_bracket', [17, 16, cz0 + 5], [18, 24, cz0 + 7], 'metal', mirror=True),
        box('cab_rear_wall', [-17, 22, cz1], [17, 48, cz1 + 1], 'paint2', bone='cab', panels=0),
        # hood and nose
        box('hood', [-10, 29, z + 2], [10, 38, hz1], 'paint', bone='cab', chamfer={'legs': [3, 4], 'edges': ['left', 'right']},
            decals=[{'face': 'up', 'x': 4, 'y': 3, 'w': 2, 'h': 15, 'mat': 'rib'}, {'face': 'up', 'x': 14, 'y': 3, 'w': 2, 'h': 15, 'mat': 'rib'},
                    {'face': 'up', 'x': 7, 'y': 4, 'w': 6, 'h': 5, 'mat': 'vent'}]),
        box('hood_side', [-10, 22, z + 2], [10, 29, hz1], 'paint', bone='cab', panels=0,
            decals=[{'face': 'east', 'x': 3, 'y': 1, 'w': 17, 'h': 5, 'mat': 'vent'}, {'face': 'west', 'x': 3, 'y': 1, 'w': 17, 'h': 5, 'mat': 'vent'}]),
        box('grille', [-10, 20, z], [10, 34, z + 2], 'paint2', bone='cab', panels=0,
            decals=[{'face': 'north', 'x': 2, 'y': 2, 'w': 16, 'h': 10, 'mat': 'grille'}, {'face': 'north', 'x': 8, 'y': 0, 'w': 4, 'h': 1, 'mat': 'handle'}]),
        box('bumper', [-20, 14, z - 3], [20, 19, z + 1], 'paint2', panels=0,
            decals=[{'face': 'north', 'x': 3, 'y': 1, 'w': 34, 'h': 1, 'mat': 'bolts'}, {'face': 'north', 'x': 2, 'y': 3, 'w': 36, 'h': 2, 'mat': 'grime'}]),
        box('tow_hook', [8, 14, z - 6], [10, 17, z - 3], 'metal', mirror=True),
        box('winch', [-6, 15, z - 5], [6, 18, z - 3], 'metal', decals=[{'face': 'north', 'x': 1, 'y': 1, 'w': 10, 'h': 1, 'mat': 'rib'}]),
        # wing fenders with headlights, arch over the steered wheels
        box('wing', [10, 25, z + 3], [21, 27, hz1 + 4], 'paint', mirror=True, panels=0,
            decals=[{'face': 'east', 'x': 1, 'y': 0, 'w': 25, 'h': 1, 'mat': 'rib'}]),
        box('wing_front', [10, 17, z + 1], [21, 25, z + 3], 'paint', mirror=True, panels=0),
        {'type': 'lamp', 'name': 'headlamp', 'center': [13.5, 21, z + 1], 'size': [4, 4], 'face': 'north', 'mirror': True},
        {'type': 'lamp', 'name': 'indicator', 'center': [18.5, 22, z + 1], 'size': [2, 2], 'face': 'north', 'mat': 'amber', 'mirror': True},
        box('exhaust_stack', [18, 20, cz1 + 1], [20, 50, cz1 + 3], 'metal', mirror=False),
        box('exhaust_cap', [17, 50, cz1 + 0.5], [21, 52, cz1 + 3.5], 'metal'),
        box('air_cleaner', [-20, 22, cz1 + 1], [-17, 46, cz1 + 3], 'paint2', panels=0, decals=[{'face': 'west', 'x': 0, 'y': 3, 'w': 2, 'h': 8, 'mat': 'vent'}]),
        {'type': 'antenna', 'name': 'cab_whip', 'base': [-14, 54, cz0 + 4], 'height': 20, 'bone': 'cab'},
    ]
    p += liners(p[0])
    return p


def ural_chassis(front_z, rear_z_end, steer, rear, deckkw=None):
    """Chassis parts shared by the Ural launchers: frame, underbody, wheels, mudflaps, bumper rear."""
    return (frame(front_z + 4, rear_z_end, steer + rear) + underbody(steer, rear) + wheels(steer, rear) +
            mudflaps(rear_z_end - 4))


def grad():
    steer, rear = [-42], [16, 38]
    jb, jp = jacks([(17, 66), (-17, 66)])
    p = hood_cab(-66, steer) + ural_chassis(-66, 62, steer, rear) + deck(-10, 62, rear) + jp
    p += [box('subframe', [-6, 17, -12], [6, 19, 58], 'paint2', panels=0),
          box('fuel_tank', [13, 12, -17], [20, 20, -1], 'paint', panels=0, decals=[{'face': 'east', 'x': 2, 'y': 3, 'w': 12, 'h': 1, 'mat': 'strap'}]),
          box('battery_box', [-20, 12, -17], [-13, 19, -7], 'paint', panels=0, decals=[{'face': 'west', 'x': 2, 'y': 2, 'w': 6, 'h': 5, 'mat': 'hatch'}]),
          box('spare_rack', [-20, 12, -6], [-13, 19, 4], 'paint2', panels=0)]
    # turntable on the deck, 40-tube pack (10 x 4) on a rear cradle: two lift rams push it up (renderer animates bone ram0)
    t = [box('turntable', [-12, 23, 8], [12, 25, 36], 'metal', bone='turret'),
         box('bearing_ring', [-10, 25, 10], [10, 27, 34], 'metal', bone='turret'),
         ]
    t += a_frame('hinge', 22, 27, 39, 20, pin_y=36)
    rb, rp = ram_set('ram0', 30, 35, 40, 9, 34, base_y=25)
    bones = [{'name': 'cab', 'pivot': [0, 23, -40]}, {'name': 'turret', 'pivot': [0, 24, 22]},
             {'name': 'rack', 'pivot': [0, 36, 22], 'parent': 'turret'}] + rb + steer_bones(steer) + jb
    ribs = [{'face': 'up', 'x': 4 * c + 3, 'y': 1, 'w': 1, 'h': 44, 'mat': 'rib'} for c in range(9)]
    rows = [{'face': side, 'x': 1, 'y': y, 'w': 44, 'h': 1, 'mat': 'rib'} for side in ('east', 'west') for y in (4, 8, 12)]
    mouths = [{'face': 'north', 'x': 1 + c * 4, 'y': 1 + (3 - r) * 4, 'w': 3, 'h': 3, 'mat': 'dark'} for r in range(4) for c in range(10)]
    pack = [box('pack', [-20, 34, -8], [20, 52, 38], 'paint', bone='rack', panels=0, decals=ribs + rows + [
                {'face': 'east', 'x': 30, 'y': 14, 'w': 10, 'h': 3, 'mat': 'grime'}, {'face': 'west', 'x': 6, 'y': 14, 'w': 10, 'h': 3, 'mat': 'grime'}]),
            box('pack_face', [-20, 34, -9], [20, 52, -8], 'paint2', bone='rack', panels=0, decals=mouths)]
    pack += [box(f'band{i}', [-21, 33, z], [21, 53, z + 2], 'paint2', bone='rack', panels=0) for i, z in enumerate((-3, 20, 34))]
    pack += [box('cradle_rail', [-18, 31, 14], [-16, 34, 32], 'paint2', bone='rack', mirror=True, panels=0)]
    tb, tp = tip_grid(10, 4, 19, 36, 4, 4, -9, 3, [0, 36, 22])
    extra = [crate('crate0', [-12, 23, 42], [-2, 28, 52]), tarp('tarp', [2, 23, 40], [12, 27, 54]),
             box('ladder', [19, 24, 40], [20, 38, 44], 'metal'),
             box('extinguisher', [-21.5, 24, 40], [-19.5, 31, 42], 'paint2')] + jerrycans('cans', -5, 19.5, 62.5, 2)
    return detail(spec('grad', 'BM-21 Grad 122 mm MLRS on a Ural-375D 6x6. Scale 1 m = 16 px. Identifiers: hooded cab, 40-tube 10x4 pack '
                       'on a turntable above the rear tandem, two rear outriggers, olive.',
                       OLIVE, PAINT2_DARK, bones + tb, p + t + rp + pack + tp, 21), extra)


def shahed_launcher():
    steer, rear = [-42], [16, 38]
    jb, jp = jacks([(17, 66), (-17, 66)])
    p = hood_cab(-66, steer) + ural_chassis(-66, 62, steer, rear) + deck(-10, 62, rear) + jp
    p += [box('subframe', [-6, 17, -12], [6, 19, 58], 'paint2', panels=0),
          box('fuel_tank', [13, 12, -17], [20, 20, -1], 'paint', panels=0, decals=[{'face': 'east', 'x': 2, 'y': 3, 'w': 12, 'h': 1, 'mat': 'strap'}]),
          box('battery_box', [-20, 12, -17], [-13, 19, -7], 'paint', panels=0, decals=[{'face': 'west', 'x': 2, 'y': 2, 'w': 6, 'h': 5, 'mat': 'hatch'}])]
    t = [box('turntable', [-12, 23, 8], [12, 25, 36], 'metal', bone='turret'), box('bearing_ring', [-10, 25, 10], [10, 27, 34], 'metal', bone='turret')]
    t += a_frame('hinge', 22, 27, 39, 20, pin_y=36)
    rb, rp = ram_set('ram0', 30, 35, 40, 9, 36, base_y=25)
    # rack: tray, four corner posts, a shelf for each of the five drones; drone i = bone tipI (fuselage) + tipI_wing (wings)
    r = [box('tray', [-20, 36, -8], [20, 38, 50], 'paint2', bone='rack', panels=0, decals=[{'face': 'up', 'x': 2, 'y': 2, 'w': 36, 'h': 1, 'mat': 'bolts'}]),
         box('post_f', [20, 38, 5], [22, 78, 7], 'paint', bone='rack', mirror=True, panels=0),
         box('post_b', [20, 38, 31], [22, 78, 33], 'paint', bone='rack', mirror=True, panels=0),
         box('top_beam', [-20, 76, 5], [20, 78, 7], 'paint2', bone='rack', panels=0),
         box('top_beam_b', [-20, 76, 31], [20, 78, 33], 'paint2', bone='rack', panels=0),
         ]
    bones = [{'name': 'cab', 'pivot': [0, 23, -40]}, {'name': 'turret', 'pivot': [0, 24, 22]},
             {'name': 'rack', 'pivot': [0, 38, 22], 'parent': 'turret'}] + rb + steer_bones(steer) + jb
    tb, tp = [], []
    z0 = -3
    for i in range(5):
        y0 = 42 + 7 * i
        n = f'tip{i}'
        tb += [{'name': n, 'pivot': [0, 38, 22], 'parent': 'rack'}, {'name': n + '_wing', 'pivot': [0, 38, 22], 'parent': n}]
        r += [box(f'shelf{i}_a', [-20, y0 - 2, z0 + 8], [20, y0 - 1, z0 + 10], 'paint2', bone='rack', panels=0),
              box(f'shelf{i}_b', [-20, y0 - 2, z0 + 34], [20, y0 - 1, z0 + 36], 'paint2', bone='rack', panels=0),
              box(f'rail{i}', [-3, y0 - 1, z0 + 6], [3, y0, z0 + 40], 'metal', bone='rack', panels=0)]
        tp += [box(f'{n}_body', [-2, y0, z0 + 8], [2, y0 + 4, z0 + 38], 'grey', bone=n, panels=0),
               box(f'{n}_nose_a', [-2, y0, z0 + 2], [2, y0 + 4, z0 + 8], 'grey', bone=n, panels=0),
               box(f'{n}_nose_b', [-1, y0 + 1, z0], [1, y0 + 3, z0 + 2], 'paint2', bone=n, panels=0),
               box(f'{n}_engine', [-2, y0, z0 + 38], [2, y0 + 4, z0 + 46], 'paint2', bone=n, panels=0),
               box(f'{n}_prop', [-0.5, y0 + 0.5, z0 + 46], [0.5, y0 + 3.5, z0 + 47], 'metal', bone=n, panels=0),
               box(f'{n}_band', [-2.5, y0 - 0.5, z0 + 16], [2.5, y0 + 4.5, z0 + 18], 'paint2', bone=n, panels=0)]
        w = delta(f'{n}_w', 5, z0 + 14, 19, z0 + 36, z0 + 44, y0 + 1, bone=n + '_wing', mat='grey', cw=6)
        w += [box(f'{n}_wing_center', [-5, y0 + 0.5, z0 + 14], [5, y0 + 1.5, z0 + 44], 'grey', bone=n + '_wing', panels=0),
              box(f'{n}_winglet', [17.5, y0 + 1.5, z0 + 36], [18.5, y0 + 7.5, z0 + 43], 'paint2', bone=n + '_wing', mirror=True, panels=0)]
        tp += w
    extra = [crate('crate0', [-12, 23, 42], [-2, 28, 52]), tarp('tarp', [2, 23, 40], [12, 27, 54])] + jerrycans('cans', -5, 19.5, 62.5, 2)
    sp = spec('shahed_launcher', 'Shahed launcher on a Ural 6x6: five delta-wing drones stacked on a rear-hinged rail rack lifted by two rams. Scale 1 m = 16 px.',
              OLIVE, PAINT2_DARK, bones + tb, p + t + rp + r + tp, 23)
    return detail(sp, extra)


def lint(sp):
    """List every box with fractional size and every decal that does not fit its face, so one run shows all of them."""
    bad = []
    for p in sp['parts']:
        if p.get('type', 'box') != 'box':
            continue
        w, h, d = (t - f for f, t in zip(p['from'], p['to']))
        if any(abs(v - round(v)) > 1e-6 for v in (w, h, d)):
            bad.append(('size', p['name'], (w, h, d)))
            continue
        dims = {'north': (w, h), 'south': (w, h), 'east': (d, h), 'west': (d, h), 'up': (w, d), 'down': (w, d)}
        for dc in p.get('decals', []):
            fw, fh = dims[dc['face']]
            if dc['x'] < 0 or dc['y'] < 0 or dc['x'] + dc['w'] > fw + 1e-6 or dc['y'] + dc['h'] > fh + 1e-6 or any(
                    abs(dc[k] - round(dc[k])) > 1e-6 for k in ('x', 'y', 'w', 'h')):
                bad.append(('decal', p['name'], dc, (fw, fh)))
    for b in bad:
        print('LINT', sp['name'], *b)
    return not bad


def pack_launcher(name, cols, rows, mouth, pitch, x_right, y0, z_front, z_back, pivot, half_w, paint='paint', bands=(), ribbed=True):
    """Tube bundle on an elevating rack: body with dark mouths on the front, nose cones (tipN) while loaded."""
    w = 2 * half_w
    h = rows * pitch + 2
    off = (pitch - mouth) // 2
    mouths = [{'face': 'north', 'x': half_w - x_right + c * pitch, 'y': h - 1 - off - r * pitch - mouth, 'w': mouth, 'h': mouth, 'mat': 'dark'}
              for r in range(rows) for c in range(cols)]
    L = z_back - z_front
    dec = []
    if ribbed:
        dec += [{'face': 'up', 'x': pitch * c + pitch - 1, 'y': 1, 'w': 1, 'h': L - 2, 'mat': 'rib'} for c in range(cols - 1)]
        dec += [{'face': side, 'x': 1, 'y': pitch * r + pitch - 1, 'w': L - 2, 'h': 1, 'mat': 'rib'} for side in ('east', 'west') for r in range(rows - 1)]
    parts = [box(f'{name}_pack', [-half_w, y0, z_front], [half_w, y0 + h, z_back], paint, bone='rack', panels=0, decals=dec),
             box(f'{name}_pack_face', [-half_w, y0, z_front - 1], [half_w, y0 + h, z_front], 'paint2', bone='rack', panels=0, decals=mouths)]
    parts += [box(f'{name}_band{i}', [-half_w - 1, y0 - 1, z], [half_w + 1, y0 + h + 1, z + 2], 'paint2', bone='rack', panels=0) for i, z in enumerate(bands)]
    tb, tp = tip_grid(cols, rows, x_right, y0 + 1 + (pitch - mouth) // 2, pitch, pitch, z_front - 1, mouth, pivot)
    return parts, tb, tp


def smerch():
    bones, p = maz_base('smerch', 30)
    t = [box('erector_base', [-14, 23.5, -4], [14, 25.5, 22], 'metal', bone='turret'), box('bearing_ring', [-10, 25.5, 0], [10, 27.5, 18], 'metal', bone='turret')]
    t += a_frame('hinge', 58, 27, 43, 17, pin_y=40)
    rb, rp = ram_set('ram0', 30, 36, 35, 10, 34, base_y=24)
    pack, tb, tp = pack_launcher('smerch', 4, 3, 6, 8, 15, 34, -26, 58, [0, 40, 58], 16, bands=(-14, 12, 44))
    bones = [b for b in bones] + [{'name': 'turret', 'pivot': [0, 22, 8.8]}, {'name': 'rack', 'pivot': [0, 40, 58], 'parent': 'turret'}] + rb + tb
    extra = [tarp('tarp', [-15, 37, -54], [-5, 41, -32])]
    sp = detail(spec('smerch', '9A52-2 Smerch 300 mm MLRS on a MAZ-543M 8x8. Scale 1 m = 16 px. Identifiers: two cabs flanking the hood, crew box, '
                     '12 big tubes (4 x 3) in a bundle on a rear-hinged cradle lifted by two rams, four outriggers, olive.',
                     OLIVE, PAINT2_DARK, bones, p + t + rp + pack + tp, 31), extra)
    sp['canvas'] = MISSILE_GREEN
    return sp


def uragan():
    bones, p = maz_base('uragan', 40)
    t = [box('erector_base', [-14, 23.5, -4], [14, 25.5, 22], 'metal', bone='turret'), box('bearing_ring', [-10, 25.5, 0], [10, 27.5, 18], 'metal', bone='turret')]
    t += a_frame('hinge', 58, 27, 43, 14, pin_y=40)
    rb, rp = ram_set('ram0', 30, 36, 35, 8, 34, base_y=24)
    pack, tb, tp = pack_launcher('uragan', 4, 4, 4, 6, 11, 34, -26, 58, [0, 40, 58], 13, bands=(-12, 14, 44))
    bones = list(bones) + [{'name': 'turret', 'pivot': [0, 22, 8.8]}, {'name': 'rack', 'pivot': [0, 40, 58], 'parent': 'turret'}] + rb + tb
    extra = [crate('crate0', [6, 39, -52], [16, 44, -42]), tarp('tarp', [-15, 39, -54], [-3, 43, -32])]
    sp = detail(spec('uragan', 'BM-27 Uragan 220 mm MLRS on a ZIL-135LM 8x8. Scale 1 m = 16 px. Identifiers: two cabs flanking the hood, '
                     '16 tubes (4 x 4) in a bundle on a rear-hinged cradle lifted by two rams, four outriggers, camouflage.',
                     OLIVE, PAINT2_DARK, bones, p + t + rp + pack + tp, 41), extra)
    sp['camo'] = {'colors': ['olive', 'khaki', 'camo_black'], 'shares': [0.6, 0.25, 0.15], 'scale': 16}
    sp['canvas'] = MISSILE_GREEN
    return sp


def tochka():
    bones, p = kamaz_chassis(0)
    p += cab_medkit(-67)
    cy, hz = 38, 56
    t = [box('hinge_block', [-9, 23, 46], [9, 27, 52], 'metal'),
         box('hinge_pin', [-12, 35, hz], [12, 41, hz + 4], 'paint2', bone='erector'),
         box('trough', [-8, 27, -34], [8, 29, hz], 'paint2', bone='erector', panels=0,
             decals=[{'face': 'up', 'x': 7, 'y': 4, 'w': 2, 'h': 82, 'mat': 'rib'}]),
         box('rail_r', [8, 27, -34], [10, 34, hz], 'paint', bone='erector', mirror=True, panels=0),
         box('clamp0', [-9, 29, -17], [9, 46, -15], 'paint2', bone='erector', panels=0),
         box('clamp1', [-9, 29, 7], [9, 46, 9], 'paint2', bone='erector', panels=0),
         box('clamp2', [-9, 29, 31], [9, 46, 33], 'paint2', bone='erector', panels=0),
         ]
    erb, erp = ram_set('eram0', 28, 44, 30, 14, 31, parent=None, hinge='erector', base_y=24)
    bones = list(bones) + [{'name': 'erector', 'pivot': [0, cy, hz]}, {'name': 'missile', 'pivot': [0, cy, hz], 'parent': 'erector'}] + erb
    side = [box('side_wall', [12, 24, -32], [14, 33, 8], 'paint', mirror=True, panels=0,
                decals=[{'face': 'east', 'x': 6, 'y': 2, 'w': 14, 'h': 5, 'mat': 'hatch'}, {'face': 'east', 'x': 26, 'y': 2, 'w': 14, 'h': 5, 'mat': 'hatch'},
                        ]),
            box('front_bulkhead', [-14, 23, -37], [14, 38, -35], 'paint', panels=0, decals=[{'face': 'north', 'x': 8, 'y': 3, 'w': 12, 'h': 6, 'mat': 'hatch'}]),
            box('pump_box', [-14, 23, 58], [14, 31, 62], 'paint', panels=0, decals=[{'face': 'south', 'x': 3, 'y': 1, 'w': 8, 'h': 5, 'mat': 'hatch'}, {'face': 'south', 'x': 15, 'y': 1, 'w': 8, 'h': 5, 'mat': 'vent'}])]
    sp = detail(spec('tochka', 'Tochka-U 9P129 TEL on a BAZ-5921 6x6. Scale 1 m = 16 px. Identifiers: cab-over, low launch deck with side walls, '
                     'missile in a trough on a rear-hinged erector, rear hydraulic box.', OLIVE, PAINT2_DARK, bones, p + t + erp + side, 50),
                 jerrycans('cans', -5, 19.5, 62.5, 2))
    sp['canvas'] = MISSILE_GREEN
    sp['missile'] = {'bone': 'missile', 'L': 90, 'D': 10, 'zn': -34, 'cx': 0, 'cy': cy, 'palette': 'iskander'}
    return sp


def kamaz_chassis(seed):
    """KamAZ cab-over 6x6 chassis with a low deck: used by Tochka and the SAM."""
    steer, rear = [-51], [22, 46]
    jb, jp = jacks([(17, 66), (-17, 66)])
    p = kamaz_cab(-67, steer) + underbody(steer, rear) + frame(-64, 62, steer + rear) + mudflaps(58.5) + jp + wheels(steer, rear) + \
        deck(-34, 62, rear) + [
        box('fuel_tank', [13, 12, -33], [20, 19, -13], 'paint', panels=0, decals=[{'face': 'east', 'x': 2, 'y': 2, 'w': 16, 'h': 1, 'mat': 'strap'}]),
        box('spare_rack', [-20, 12, -33], [-13, 19, -13], 'paint2', panels=0)]
    bones = [{'name': 'cab', 'pivot': [0, 23, -54]}] + steer_bones(steer) + jb
    return bones, p


def iskander():
    steer, rear = [-62, -38], [27, 50]
    jb, jp = jacks([(19, 68), (-19, 68), (19, 11), (-19, 11)])
    p = kamaz_cab(-80, steer) + underbody(steer, rear) + frame(-76, 74, steer + rear) + wheels(steer, rear) + mudflaps(63) + jp + deck(-46, 74, [steer[1]] + rear)
    p += cab_medkit(-80)
    p = [q for q in p if not q['name'].startswith('rear_arch')]
    hz, cy = 68, 33
    p += [box('hull_wall', [17, 23, -46], [19, 39, 70], 'paint', mirror=True, panels=0,
              decals=[{'face': 'east', 'x': 4, 'y': 3, 'w': 16, 'h': 9, 'mat': 'hatch'}, {'face': 'east', 'x': 24, 'y': 3, 'w': 16, 'h': 9, 'mat': 'hatch'},
                      {'face': 'east', 'x': 44, 'y': 3, 'w': 16, 'h': 9, 'mat': 'hatch'}, {'face': 'east', 'x': 2, 'y': 13, 'w': 110, 'h': 1, 'mat': 'bolts'},
                      {'face': 'east', 'x': 6, 'y': 1, 'w': 8, 'h': 1, 'mat': 'rib'}]),
          box('hull_rail', [16.5, 39, -46], [19.5, 40, 70], 'metal', mirror=True, panels=0),
          box('hull_front', [-17, 23, -48], [17, 44, -46], 'paint', panels=0,
              decals=[{'face': 'north', 'x': 5, 'y': 4, 'w': 24, 'h': 10, 'mat': 'hatch'}, {'face': 'north', 'x': 2, 'y': 17, 'w': 30, 'h': 1, 'mat': 'bolts'}]),
          box('hull_rear', [-17, 23, 70], [17, 37, 72], 'paint', panels=0,
              decals=[{'face': 'south', 'x': 3, 'y': 3, 'w': 12, 'h': 8, 'mat': 'hatch'}, {'face': 'south', 'x': 19, 'y': 3, 'w': 12, 'h': 8, 'mat': 'hatch'}]),
          box('side_locker_r', [19, 13, -5], [22, 21, 7], 'paint', mirror=True, panels=0, decals=[{'face': 'east', 'x': 2, 'y': 1, 'w': 8, 'h': 6, 'mat': 'hatch'}]),
          box('fuel_tank', [13, 12, -22], [20, 20, -6], 'paint', panels=0, decals=[{'face': 'east', 'x': 2, 'y': 3, 'w': 10, 'h': 1, 'mat': 'strap'}]),
          box('exhaust_stack', [15, 23, -44], [17, 52, -42], 'metal'),
          ]
    # erector: two rails, low saddles, cross arm for the rams; missile lies in it nose to the cab
    t = [box('trough_r', [8, 24.5, -42], [10, 30.5, 62], 'paint2', bone='erector', mirror=True, panels=0,
             decals=[{'face': 'east', 'x': 3, 'y': 1, 'w': 98, 'h': 1, 'mat': 'rib'}]),
         box('erector_arm', [-14, 28, 29], [14, 33, 36], 'paint2', bone='erector', panels=0),
         box('hinge_block', [-12, 23, 62], [12, 31, 66], 'metal'), box('hinge_pin', [-14, 31, hz - 2], [14, 35, hz + 2], 'paint2', bone='erector')]
    t += [box(f'saddle{i}', [-8, 24, z], [8, 25, z + 3], 'paint2', bone='erector', panels=0) for i, z in enumerate((-30, -6, 20, 46))]
    erb, erp = ram_set('eram0', 28, 58, 24, 14, 32, parent=None, hinge='erector', base_y=24)
    bones = [{'name': 'cab', 'pivot': [0, 23, -66]}] + steer_bones(steer) + jb + [
        {'name': 'erector', 'pivot': [0, cy, hz]}, {'name': 'missile', 'pivot': [0, cy, hz], 'parent': 'erector'}] + erb
    extra = [crate('crate0', [-14, 23, -44], [-6, 28, -38])] + jerrycans('cans', -5, 19.5, 74.5, 2)
    sp = detail(spec('iskander', 'Iskander-M 9P78 TEL on a MZKT-7930 8x8. Scale 1 m = 16 px. Identifiers: wide cab-over, low hull walls with lockers, '
                     'ballistic missile in a rail trough on a rear-hinged erector lifted by hydraulic rams, four outriggers, olive.',
                     OLIVE, PAINT2_DARK, bones, p + t + erp, 77), extra)
    sp['canvas'] = MISSILE_GREEN
    sp['missile'] = {'bone': 'missile', 'L': 100, 'D': 14, 'zn': -40, 'cx': 0, 'cy': cy, 'palette': 'iskander'}
    return sp


KALIBR_SLATE = ['#2F353A', '#414A51', '#545F67', '#6B7780']   # naval slate of the launch module


def kalibr():
    """Kalibr coastal launcher: four vertical-launch cells (2 x 2) in an armoured module on a rear-hinged cradle lifted by two rams.
    Cells sit at x = +-9, y = 43 / 61 on the front face; the rams tilt the module to a launch angle (35 degrees in Launcher.KALIBR_LAUNCH_ELEV, not vertical) and the missiles leave through the cells.
    tip0..tip3 = the four missile noses (shown while loaded, tip0 +X low, tip1 -X low, tip2 +X high, tip3 -X high)."""
    bones, p = maz_base('kalibr', 60)
    t = [box('erector_base', [-14, 23.5, -4], [14, 25.5, 22], 'metal', bone='turret'), box('bearing_ring', [-10, 25.5, 0], [10, 27.5, 18], 'metal', bone='turret')]
    # turret frame: plate from the turntable back to the hinge posts; the ram feet stand on it, so lifting gear is bolted to the rotating base
    t += [box('turret_plate', [-22, 24, 22], [22, 27, 61], 'paint2', bone='turret', panels=0, decals=[
              {'face': 'up', 'x': 2, 'y': 2, 'w': 42, 'h': 1, 'mat': 'bolts'}, {'face': 'up', 'x': 2, 'y': 36, 'w': 42, 'h': 1, 'mat': 'bolts'}]),
          box('plate_gusset', [-12, 24.5, 20], [12, 26.5, 24], 'metal', bone='turret', panels=0)]
    t += a_frame('hinge', 58, 27, 43, 20, pin_y=39)
    rb, rp = ram_set('ram0', 30, 36, 35, 10, 34, base_y=26)
    side_e = [{'face': 'east', 'x': 4 + 22 * k, 'y': 3, 'w': 16, 'h': 12, 'mat': 'hatch'} for k in range(3)] + [
        {'face': 'east', 'x': 2, 'y': 18, 'w': 74, 'h': 1, 'mat': 'rib'}, {'face': 'east', 'x': 6, 'y': 21, 'w': 18, 'h': 7, 'mat': 'vent'},
        {'face': 'east', 'x': 34, 'y': 21, 'w': 18, 'h': 7, 'mat': 'vent'}, {'face': 'east', 'x': 2, 'y': 30, 'w': 74, 'h': 1, 'mat': 'bolts'}]
    side_w = [{'face': 'west', 'x': 8 + 22 * k, 'y': 3, 'w': 16, 'h': 12, 'mat': 'hatch'} for k in range(3)] + [
        {'face': 'west', 'x': 2, 'y': 18, 'w': 74, 'h': 1, 'mat': 'rib'}, {'face': 'west', 'x': 26, 'y': 21, 'w': 18, 'h': 7, 'mat': 'vent'},
        {'face': 'west', 'x': 54, 'y': 21, 'w': 18, 'h': 7, 'mat': 'vent'}, {'face': 'west', 'x': 2, 'y': 30, 'w': 74, 'h': 1, 'mat': 'bolts'}]
    cells = [(9, 43), (-9, 43), (9, 61), (-9, 61)]
    # decal x on the north face counts from the +X edge: cell centre cx -> x = 19 - cx - 6; y from the bottom (34)
    mouths = [{'face': 'north', 'x': 13 - cx, 'y': cy - 40, 'w': 12, 'h': 12, 'mat': 'dark'} for cx, cy in cells]
    face = [box('face_plate', [-19, 34, -24], [19, 70, -21], 'paint2', bone='rack', panels=0, decals=mouths + [
                {'face': 'north', 'x': 1, 'y': 1, 'w': 36, 'h': 1, 'mat': 'bolts'}, {'face': 'north', 'x': 1, 'y': 34, 'w': 36, 'h': 1, 'mat': 'bolts'},
                {'face': 'north', 'x': 18, 'y': 2, 'w': 2, 'h': 32, 'mat': 'rib'}, {'face': 'north', 'x': 1, 'y': 17, 'w': 36, 'h': 2, 'mat': 'rib'}])]
    for k, (cx, cy) in enumerate(cells):   # square launch collar round each cell
        face += [box(f'collar{k}_top', [cx - 8, cy + 6, -27], [cx + 8, cy + 8, -24], 'metal', bone='rack', panels=0,
                     decals=[{'face': 'north', 'x': 1, 'y': 0, 'w': 2, 'h': 2, 'mat': 'amber'}]),
                 box(f'collar{k}_bot', [cx - 8, cy - 8, -27], [cx + 8, cy - 6, -24], 'metal', bone='rack', panels=0),
                 box(f'collar{k}_l', [cx - 8, cy - 6, -27], [cx - 6, cy + 6, -24], 'metal', bone='rack', panels=0),
                 box(f'collar{k}_r', [cx + 6, cy - 6, -27], [cx + 8, cy + 6, -24], 'metal', bone='rack', panels=0)]
    mod = [box('module', [-19, 34, -21], [19, 66, 58], 'canvas', bone='rack', panels=0, decals=side_e + side_w),
           box('roof', [-17, 66, -19], [17, 70, 55], 'paint2', bone='rack', panels=0, decals=[
               {'face': 'up', 'x': 3, 'y': 6, 'w': 10, 'h': 10, 'mat': 'vent'}, {'face': 'up', 'x': 21, 'y': 6, 'w': 10, 'h': 10, 'mat': 'vent'},
               {'face': 'up', 'x': 3, 'y': 30, 'w': 10, 'h': 10, 'mat': 'vent'}, {'face': 'up', 'x': 21, 'y': 30, 'w': 10, 'h': 10, 'mat': 'vent'},
               {'face': 'up', 'x': 3, 'y': 54, 'w': 10, 'h': 10, 'mat': 'hatch'}, {'face': 'up', 'x': 21, 'y': 54, 'w': 10, 'h': 10, 'mat': 'hatch'},
               {'face': 'up', 'x': 16, 'y': 2, 'w': 2, 'h': 70, 'mat': 'rib'}]),
           box('lift_eye', [12, 70, -12], [15, 72, -8], 'metal', bone='rack', mirror=True, panels=0),
           box('plenum', [-19, 34, 58], [19, 70, 61], 'paint2', bone='rack', panels=0, decals=[
               {'face': 'south', 'x': 2, 'y': 2, 'w': 15, 'h': 14, 'mat': 'vent'}, {'face': 'south', 'x': 21, 'y': 2, 'w': 15, 'h': 14, 'mat': 'vent'},
               {'face': 'south', 'x': 2, 'y': 20, 'w': 15, 'h': 14, 'mat': 'vent'}, {'face': 'south', 'x': 21, 'y': 20, 'w': 15, 'h': 14, 'mat': 'vent'}]),
           box('rail', [14, 31.5, -21], [18, 33.5, 55], 'paint2', bone='rack', mirror=True, panels=0)]
    mod += [box(f'band{i}', [-20, 33, z], [20, 67, z + 2], 'paint2', bone='rack', panels=0) for i, z in enumerate((-14, 18, 48))]
    tips = [{'name': f'tip{k}', 'pivot': [0, 39, 58], 'parent': 'rack'} for k in range(4)]
    bones = list(bones) + [{'name': 'turret', 'pivot': [0, 22, 8.8]}, {'name': 'rack', 'pivot': [0, 39, 58], 'parent': 'turret'}] + rb + tips
    extra = []
    sp = detail(spec('kalibr', 'Kalibr coastal launcher on a MAZ-543M 8x8. Scale 1 m = 16 px. Identifiers: two cabs flanking the hood, '
                     'slate-grey armoured launch module with a 2 x 2 matrix of square vertical-launch cells and cruise-missile noses on a rear-hinged '
                     'cradle lifted by two rams, exhaust plenum at the back, olive chassis.',
                     OLIVE, PAINT2_DARK, bones, p + t + rp + face + mod, 61), extra)
    sp['canvas'] = KALIBR_SLATE
    sp['missile'] = [{'bone': f'tip{k}', 'L': 50, 'D': 10, 'zn': -42, 'cx': cx, 'cy': cy, 'palette': 'kalibr', 'trim': 30}
                     for k, (cx, cy) in enumerate(cells)]
    return sp


def sam():
    bones, p = kamaz_chassis(0)
    t = [box('turntable', [-12, 23, -6], [12, 25, 22], 'metal', bone='turret'), box('bearing_ring', [-8, 25, -2], [8, 27, 18], 'metal', bone='turret'),
         box('turret_body', [-8, 27.5, -4], [8, 38.5, 22], 'paint', bone='turret', panels=0,
             decals=[{'face': 'east', 'x': 4, 'y': 2, 'w': 14, 'h': 7, 'mat': 'hatch'}, {'face': 'west', 'x': 8, 'y': 2, 'w': 14, 'h': 7, 'mat': 'hatch'},
                     {'face': 'north', 'x': 3, 'y': 2, 'w': 10, 'h': 5, 'mat': 'vent'}]),
         box('ram_pedestal', [9, 24, 16], [15, 31, 21], 'metal', bone='turret', mirror=True, panels=0)]
    t += a_frame('hinge', 22, 27, 41, 19, pin_y=39)
    rb, rp = ram_set('ram0', 34, 16, 30, 12, 39, base_y=31)
    # radar mast on the rear deck (spins), phased array on top
    t += [box('radar_base', [-6, 23, 36], [6, 29, 46], 'paint2', bone='turret', panels=0),
          box('mast', [-2, 29, 39], [2, 40, 43], 'metal', bone='radar'),
          box('array', [-14, 40, 39], [14, 52, 41], 'paint', bone='radar', panels=0, decals=[{'face': 'north', 'x': 2, 'y': 2, 'w': 24, 'h': 8, 'mat': 'grille'}]),
          box('array_back', [-10, 42, 41], [10, 50, 44], 'paint2', bone='radar', panels=0),
          box('array_top', [-15, 52, 38], [15, 54, 42], 'paint2', bone='radar', panels=0),
          {'type': 'antenna', 'name': 'radar_whip', 'base': [11, 54, 40], 'height': 10, 'bone': 'radar'}]
    r = [box('cradle', [-18, 39, -20], [18, 41, 24], 'paint2', bone='rack', panels=0)]
    k = 0
    for xs in (2, 10, -18, -10):
        for ys in (41, 49):
            r += cyl(f'can{k}', xs, ys, -28, 28, 8, 'paint', bone='rack')
            r.append(box(f'can{k}_cap', [xs + 1, ys + 1, -29], [xs + 7, ys + 7, -28], 'paint2', bone='rack', panels=0, decals=[{'face': 'north', 'x': 1, 'y': 1, 'w': 4, 'h': 4, 'mat': 'dark'}]))
            r.append(box(f'can{k}_rear', [xs + 1, ys + 1, 28], [xs + 7, ys + 7, 29], 'metal', bone='rack', panels=0))
            k += 1
    r += [box(f'cband{i}', [-19, 40, z], [19, 58, z + 2], 'paint2', bone='rack', panels=0) for i, z in enumerate((-12, 8))]
    r += [box('eo_pod', [-2, 43, -31], [2, 49, -24], 'paint2', bone='rack', decals=[{'face': 'north', 'x': 0, 'y': 1, 'w': 4, 'h': 3, 'mat': 'glass'}]),
          box('gun_r', [-1, 50, -34], [1, 52, 4], 'metal', bone='rack'), box('gun_housing', [-3, 49, 4], [3, 54, 14], 'paint2', bone='rack', panels=0)]
    bones = list(bones) + [{'name': 'turret', 'pivot': [0, 25, 8]}, {'name': 'rack', 'pivot': [0, 40, 22], 'parent': 'turret'},
                           {'name': 'radar', 'pivot': [0, 40, 41], 'parent': 'turret'}] + rb
    extra = jerrycans('cans', -5, 19.5, 62.5, 2) + [crate('crate0', [4, 23, -32], [12, 28, -22])]
    sp = detail(spec('sam', 'Pantsir-S1 style SAM/gun system on a KamAZ 6x6. Scale 1 m = 16 px. Identifiers: cab-over, turret with eight canister missiles '
                     '(2 x 2 either side of the guns) on a cradle lifted by two rams, spinning phased-array radar on a rear mast, olive.',
                     OLIVE, PAINT2_DARK, bones, p + t + rp + r, 90), extra)
    sp['canvas'] = MISSILE_GREEN
    return sp


def add_rams(name, dy, ram_args, keep=(), extend=None, ram_kw=None):
    """Retrofit a launcher of gen.py: lift the swinging rack by dy, drop its static dummy ram and add real lift rams (bone ram0).
    Idempotent: the spec json is marked with 'rams'."""
    path = HERE / f'{name}.json'
    sp = json.loads(path.read_text())
    if sp.get('rams'):
        return
    sp['parts'] = [p for p in sp['parts'] if p['name'] not in ('lift_ram', 'erector_ram')]
    moved = [p for p in sp['parts'] if p.get('bone') == 'rack' and p['name'] not in keep]
    rest = [p for p in sp['parts'] if p not in moved]
    sp['parts'] = rest + shift(moved, dy=dy)
    for p in sp['parts']:
        if extend and p['name'] in extend:
            p['to'][1] += dy
    for b in sp['bones']:
        if b['name'] == 'rack':
            b['pivot'][1] += dy
    rb, rp = ram_set(*ram_args, **(ram_kw or {}))
    sp['bones'] += rb
    sp['parts'] += rp
    sp['rams'] = True
    path.write_text(json.dumps(sp, indent=1, ensure_ascii=False))
    print('rams added to', name)


# ---------------------------------------------------------------- tracked vehicles

CAMO3 = {'colors': ['olive', 'camo_brown', 'camo_black'], 'shares': [0.55, 0.25, 0.2], 'scale': 16}


def hullbox(name, frm, to, mat='paint', **kw):
    kw.setdefault('bone', 'hull')
    return box(name, frm, to, mat, **kw)


def bmp2():
    z0 = -50
    p = [{'type': 'track', 'name': 'track', 'x': [17, 25], 'z': [-46, 48], 'y': 0, 'height': 13, 'road_wheels': 6}]
    p += [hullbox('belly', [-17, 6, -48], [17, 14, 52], 'paint2', panels=0,
                  decals=[{'face': 'north', 'x': 3, 'y': 1, 'w': 28, 'h': 1, 'mat': 'bolts'}]),
          hullbox('hull', [-17, 14, -38], [17, 23, 52], 'paint', chamfer={'legs': [3, 4], 'edges': ['left', 'right']}),
          {'type': 'slab', 'name': 'glacis', 'axis': 'x', 'p0': [0, 14, -50], 'p1': [0, 23, -38], 'extent': [-16, 16], 'thickness': 2,
           'inside': [0, 16, -36], 'fit': 'exact', 'mat': 'paint', 'bone': 'hull'},
          hullbox('front_plate', [-17, 6, -50], [17, 14, -48], 'paint', panels=0,
                  decals=[{'face': 'north', 'x': 12, 'y': 3, 'w': 10, 'h': 4, 'mat': 'rib'}]),
          hullbox('driver_hump', [-14, 23, -40], [-6, 26, -32], 'paint', panels=0, decals=[{'face': 'north', 'x': 1, 'y': 0, 'w': 6, 'h': 2, 'mat': 'glass'}]),
          hullbox('driver_hatch', [-13, 26, -38], [-7, 27, -33], 'paint2', panels=0),
          hullbox('trim_vane', [-10, 20, -45], [10, 22, -44], 'paint2', panels=0),
          hullbox('fender', [17, 14, -46], [26, 15, 50], 'paint', mirror=True, panels=0,
                  decals=[{'face': 'up', 'x': 1, 'y': 3, 'w': 7, 'h': 1, 'mat': 'bolts'}, {'face': 'east', 'x': 3, 'y': 0, 'w': 90, 'h': 1, 'mat': 'rib'}]),
          hullbox('fender_front', [17, 12, -50], [26, 15, -46], 'paint', mirror=True, panels=0),
          {'type': 'lamp', 'name': 'headlamp', 'center': [22, 13, -50], 'size': [3, 3], 'face': 'north', 'mirror': True, 'bone': 'hull'},
          hullbox('rear_door_r', [1, 14, 52], [9, 23, 53], 'paint2', panels=0, decals=[{'face': 'south', 'x': 1, 'y': 1, 'w': 6, 'h': 7, 'mat': 'hatch'}, {'face': 'south', 'x': 1, 'y': 3, 'w': 2, 'h': 1, 'mat': 'handle'}]),
          hullbox('rear_door_l', [-9, 14, 52], [-1, 23, 53], 'paint2', panels=0, decals=[{'face': 'south', 'x': 1, 'y': 1, 'w': 6, 'h': 7, 'mat': 'hatch'}, {'face': 'south', 'x': 5, 'y': 3, 'w': 2, 'h': 1, 'mat': 'handle'}]),
          hullbox('fuel_tank', [19, 15, 24], [25, 22, 48], 'paint', mirror=True, panels=0, decals=[{'face': 'east', 'x': 2, 'y': 2, 'w': 20, 'h': 1, 'mat': 'strap'}]),
          hullbox('stowage', [19, 15, -36], [25, 19, -22], 'canvas', mirror=True, panels=0),
          hullbox('toolbox', [19, 15, -18], [25, 20, -6], 'paint2', mirror=True, panels=0, decals=[{'face': 'east', 'x': 2, 'y': 1, 'w': 8, 'h': 3, 'mat': 'hatch'}]),
          hullbox('exhaust', [10, 23, 40], [14, 25, 48], 'metal'),
          hullbox('engine_grille', [-10, 23, 28], [10, 24, 46], 'paint2', panels=0, decals=[{'face': 'up', 'x': 2, 'y': 2, 'w': 16, 'h': 14, 'mat': 'grille'}]),
          hullbox('tow_cable', [-15, 20, -45], [-14, 22, -38], 'metal'),
          {'type': 'antenna', 'name': 'whip', 'base': [-6, 32, 6], 'height': 22, 'bone': 'turret'}]
    # turret and 30 mm gun
    t = [box('turret', [-11, 23, -16], [11, 32, 10], 'paint', bone='turret', chamfer={'legs': [3, 4], 'edges': ['left', 'right']}),
         box('cupola', [3, 32, -2], [8, 34, 3], 'paint', bone='turret', panels=0),
         box('cupola_hatch', [3.5, 34, -1.5], [7.5, 35, 2.5], 'paint2', bone='turret'),
         box('gunner_sight', [-8, 32, -10], [-3, 35, -4], 'paint2', bone='turret', decals=[{'face': 'north', 'x': 1, 'y': 1, 'w': 3, 'h': 2, 'mat': 'glass'}]),
         box('smoke_r', [11, 25, -12], [13, 27, -4], 'metal', bone='turret', mirror=True),
         box('turret_bin', [-10, 24, 10], [10, 30, 14], 'paint2', bone='turret', panels=0, decals=[{'face': 'south', 'x': 3, 'y': 1, 'w': 14, 'h': 3, 'mat': 'strap'}]),
         box('atgm_rail', [-1, 32, 2], [1, 34, 10], 'metal', bone='turret'),
         box('atgm_missile', [-2, 34, 0], [2, 38, 12], 'paint2', bone='turret', panels=0, decals=[{'face': 'north', 'x': 1, 'y': 0, 'w': 2, 'h': 1, 'mat': 'red'}]),
         # gun cradle: mantlet, 30 mm barrel, brake, co-ax MG
         box('mantlet', [-4, 24, -19], [4, 31, -16], 'paint', bone='rack', panels=0),
         box('barrel', [-1.5, 26.5, -50], [1.5, 29.5, -19], 'metal', bone='rack'),
         box('barrel_jacket', [-2, 26, -32], [2, 30, -20], 'paint2', bone='rack', panels=0),
         box('muzzle', [-2, 26, -54], [2, 30, -50], 'metal', bone='rack'),
         box('coax', [3, 27, -30], [5, 29, -19], 'metal', bone='rack')]
    bones = [{'name': 'hull', 'pivot': [0, 4, 0]}, {'name': 'turret', 'pivot': [0, 23, -3]}, {'name': 'rack', 'pivot': [0, 28, -17], 'parent': 'turret'}]
    sp = detail(spec('bmp2', 'BMP-2 IFV. Scale 1 m = 16 px (6.7 x 3.2 m). Identifiers: sloped glacis with driver hump, flat hull with rear doors, '
                     'two-man turret with 30 mm 2A42 and a Konkurs missile on a rail, six road wheels, three-colour camouflage.',
                     OLIVE, PAINT2_DARK, bones, p + t, 33), [], 0.35)
    sp['camo'] = CAMO3
    return sp


# ---------------------------------------------------------------- static posts

def tripod(top_y, spread, bone='legs', leg=2, back=True):
    """Three-leg stand: two side legs splayed about Z, one rear leg splayed about X, all hinged at (0, top_y, 0); foot pads at the ends."""
    p = []
    th = math.degrees(math.atan2(spread, top_y))
    for sgn, nm, dz in ((1, 'leg_a', 0), (-1, 'leg_b', 0.5)):
        p.append(box(nm, [-leg / 2, 0, -leg / 2 + dz], [leg / 2, top_y, leg / 2 + dz], 'metal', bone=bone, rotation=[0, 0, sgn * th], pivot=[0, top_y, 0], panels=0))
        p.append(box(f'{nm}_foot', [sgn * spread - 2.5, 0, -2.5], [sgn * spread + 2.5, 2, 2.5], 'paint2', bone=bone, panels=0))
    if back:
        p.append(box('leg_c', [-leg / 2, 0, -leg / 2], [leg / 2, top_y, leg / 2], 'metal', bone=bone, rotation=[-th, 0, 0], pivot=[0, top_y, 0], panels=0))
        p.append(box('leg_c_foot', [-2.5, 0, spread - 2.5 + 1], [2.5, 2, spread + 2.5 + 1], 'paint2', bone=bone, panels=0))
    return p


def grad_p():
    y = 17
    p = tripod(y, 7)
    t = [box('yoke', [3, 13, -3], [5, 21, 3], 'metal', bone='turret', mirror=True, panels=0),
         box('yaw_collar', [-4, 12, -4], [4, 14, 4], 'metal', bone='turret', panels=0)]
    r = cyl('tube', -3, 14, -26, 18, 6, 'paint', bone='rack')
    r += [box('breech', [-3, 14, 18], [3, 20, 22], 'paint2', bone='rack', panels=0),
          box('breech_cap', [-2, 15, 22], [2, 19, 23], 'metal', bone='rack'),
          box('muzzle_ring', [-4, 13, -27], [4, 21, -25], 'metal', bone='rack', panels=0),
          box('band_a', [-4, 13, -12], [4, 21, -10], 'paint2', bone='rack', panels=0),
          box('band_b', [-4, 13, 6], [4, 21, 8], 'paint2', bone='rack', panels=0),
          box('rail_top', [-1, 21, -20], [1, 22, 14], 'metal', bone='rack'),
          box('sight', [-2, 22, -4], [2, 25, 4], 'paint2', bone='rack', decals=[{'face': 'north', 'x': 0, 'y': 1, 'w': 4, 'h': 2, 'mat': 'glass'}]),
          box('grip', [-1, 10, 10], [1, 14, 14], 'rubber', bone='rack')]
    bones = [{'name': 'legs', 'pivot': [0, 0, 0]}, {'name': 'turret', 'pivot': [0, y, 0]}, {'name': 'rack', 'pivot': [0, y, 0], 'parent': 'turret'},
             {'name': 'tip0', 'pivot': [0, y, 0], 'parent': 'rack'}]
    sp = spec('grad_p', 'Grad-P Partisan 122 mm single-tube launcher on a tripod. Scale 1 m = 16 px. Identifiers: three splayed legs, '
              'tube with muzzle ring and bands, top sight, rear breech.', OLIVE, PAINT2_DARK, bones, p + t + r, 71, 0.3)
    sp['canvas'] = MISSILE_GREEN
    sp['missile'] = {'bone': 'tip0', 'L': 26, 'D': 4, 'zn': -33, 'cx': 0, 'cy': 17, 'palette': 'grad'}
    return sp


def mortar():
    p = [box('plate', [-8, 0, -8], [8, 2, 8], 'metal', bone='base', panels=0,
             decals=[{'face': 'up', 'x': 2, 'y': 2, 'w': 12, 'h': 1, 'mat': 'bolts'}, {'face': 'up', 'x': 7, 'y': 0, 'w': 2, 'h': 16, 'mat': 'rib'},
                     {'face': 'up', 'x': 0, 'y': 7, 'w': 16, 'h': 2, 'mat': 'rib'}]),
         box('socket', [-2, 2, -2], [2, 7, 2], 'paint2', bone='turret', panels=0),
         box('ball', [-3, 4, -3], [3, 8, 3], 'metal', bone='turret', panels=0),
         box('screw', [-1, 2, -9], [1, 6, -7], 'metal', bone='base')]
    r = cyl('tube', -2, 6, -22, 4, 4, 'paint', bone='rack')
    r += [box('breech', [-3, 5, 4], [3, 11, 8], 'paint2', bone='rack', panels=0),
          box('muzzle_ring', [-3, 5, -23], [3, 11, -21], 'metal', bone='rack', panels=0),
          box('band', [-3, 5, -10], [3, 11, -8], 'paint2', bone='rack', panels=0),
          box('sight', [2, 10, -6], [5, 13, 0], 'paint2', bone='rack', decals=[{'face': 'north', 'x': 0, 'y': 1, 'w': 3, 'h': 2, 'mat': 'glass'}])]
    bones = [{'name': 'base', 'pivot': [0, 0, 0]}, {'name': 'turret', 'pivot': [0, 8, 0]}, {'name': 'rack', 'pivot': [0, 8, 0], 'parent': 'turret'}]
    return spec('mortar', '82 mm mortar on a base plate with ball socket. Scale 1 m = 16 px. Identifiers: square base plate with ribs, ball socket, '
                'tube with muzzle ring and bands, elevating screw, side sight.', OLIVE, PAINT2_DARK, bones, p + r, 72, 0.3)


def mg():
    y = 17
    p = tripod(y, 8)
    t = [box('yoke', [3, 13, -3], [5, 21, 3], 'metal', bone='turret', mirror=True, panels=0),
         box('yaw_collar', [-4, 12, -4], [4, 14, 4], 'metal', bone='turret', panels=0)]
    r = [box('receiver', [-2, 15, -6], [2, 19, 12], 'metal', bone='rack', decals=[{'face': 'east', 'x': 2, 'y': 1, 'w': 10, 'h': 1, 'mat': 'rib'}]),
         box('feed_cover', [-2.5, 19, -2], [2.5, 20, 8], 'paint2', bone='rack', panels=0),
         box('barrel_jacket', [-1.5, 16, -16], [1.5, 19, -6], 'paint2', bone='rack', panels=0, decals=[{'face': 'east', 'x': 1, 'y': 1, 'w': 8, 'h': 1, 'mat': 'vent'}]),
         box('barrel', [-1, 16.5, -26], [1, 18.5, -16], 'metal', bone='rack'),
         box('flash_hider', [-1.5, 16, -28], [1.5, 19, -26], 'metal', bone='rack'),
         box('front_sight', [-0.5, 19, -24], [0.5, 21, -23], 'metal', bone='rack'),
         box('rear_sight', [-1, 20, 6], [1, 22, 7], 'metal', bone='rack'),
         box('stock', [-1.5, 14, 12], [1.5, 19, 20], 'canvas', bone='rack', panels=0),
         box('grip', [-1, 11, 8], [1, 15, 11], 'rubber', bone='rack'),
         box('ammo_box', [-7, 14, 3], [-3, 20, 11], 'paint', bone='rack', panels=0, decals=[{'face': 'west', 'x': 2, 'y': 1, 'w': 3, 'h': 1, 'mat': 'handle'}]),
         box('belt', [-3, 17, 4], [-2, 19, 10], 'metal', bone='rack'),
         box('carry_handle', [-1, 20, -12], [1, 22, -8], 'metal', bone='rack'),
         box('bipod_l', [-3, 13, -12], [-2, 16, -10], 'metal', bone='rack'), box('bipod_r', [2, 13, -12], [3, 16, -10], 'metal', bone='rack')]
    bones = [{'name': 'legs', 'pivot': [0, 0, 0]}, {'name': 'turret', 'pivot': [0, y, 0]}, {'name': 'rack', 'pivot': [0, y, 0], 'parent': 'turret'}]
    return spec('mg', 'PKM machine gun on a tripod. Scale 1 m = 16 px. Identifiers: ribbed barrel jacket with flash hider, box-fed receiver '
                'with ammo can and belt, wooden stock, folded bipod, tripod.', OLIVE, PAINT2_DARK, bones, p + t + r, 73, 0.35)


def relay():
    p = [box('skid', [-16, 0, -16], [16, 1, 16], 'metal', bone='base', panels=0),
         box('shelter', [-15, 1, -15], [15, 13, 15], 'paint', bone='base', panels=10,
             decals=[{'face': 'north', 'x': 4, 'y': 2, 'w': 10, 'h': 10, 'mat': 'hatch'}, {'face': 'east', 'x': 8, 'y': 3, 'w': 12, 'h': 6, 'mat': 'vent'},
                     {'face': 'west', 'x': 6, 'y': 3, 'w': 10, 'h': 6, 'mat': 'vent'}, {'face': 'south', 'x': 3, 'y': 2, 'w': 8, 'h': 9, 'mat': 'hatch'}]),
         box('ac_unit', [-14, 13, 4], [-7, 17, 12], 'paint2', bone='base', panels=0, decals=[{'face': 'up', 'x': 1, 'y': 1, 'w': 5, 'h': 6, 'mat': 'grille'}]),
         box('cable_reel', [7, 13, 4], [14, 19, 10], 'paint2', bone='base', panels=0, decals=[{'face': 'north', 'x': 1, 'y': 1, 'w': 6, 'h': 4, 'mat': 'rib'}]),
         box('generator', [16, 0, -8], [22, 8, 4], 'paint', bone='base', panels=0, decals=[{'face': 'east', 'x': 1, 'y': 1, 'w': 10, 'h': 5, 'mat': 'vent'}]),
         box('fuel_can', [16, 0, 6], [21, 7, 11], 'paint2', bone='base', panels=0),
         box('step', [-6, 0, -20], [6, 1, -16], 'paint2', bone='base', panels=0, decals=[{'face': 'up', 'x': 1, 'y': 1, 'w': 10, 'h': 1, 'mat': 'rib'}]),
         box('exhaust', [17, 8, -6], [19, 16, -4], 'metal', bone='base')]
    t = [box('mast_base', [-6, 13, -6], [6, 15, 6], 'metal', bone='turret', panels=0)]
    for i, (x, z) in enumerate(((-5, -5), (3, -5), (-5, 3), (3, 3))):
        t.append(box(f'post{i}', [x, 17, z], [x + 2, 59, z + 2], 'metal', bone='turret', panels=0))
    for i, y in enumerate((15, 27, 39, 51)):
        t.append(box(f'ring{i}', [-6, y, -6], [6, y + 2, 6], 'paint2', bone='turret', panels=0))
    for i, y in enumerate((17, 29, 41)):
        t.append(box(f'brace{i}a', [-0.5, y, -4.5], [0.5, y + 13, -3.5], 'metal', bone='turret', rotation=[0, 0, 38], pivot=[0, y + 6.5, -4], panels=0))
        t.append(box(f'brace{i}b', [-0.5, y, -4.25], [0.5, y + 13, -3.25], 'metal', bone='turret', rotation=[0, 0, -38], pivot=[0, y + 6.5, -3.75], panels=0))
    t += [box('top_plate', [-6, 59, -6], [6, 61, 6], 'paint2', bone='turret', panels=0), box('ladder', [-7, 15, -1], [-6, 59, 1], 'metal', bone='turret', panels=0)]
    r = [box('boom', [-1, 61, -3], [1, 65, 3], 'metal', bone='rack', panels=0),
         box('yoke_arm', [-8, 65, -2], [8, 67, 2], 'paint2', bone='rack', panels=0),
         box('dish', [-9, 67, -3], [9, 85, -1], 'paint', bone='rack', panels=0, decals=[{'face': 'north', 'x': 2, 'y': 2, 'w': 14, 'h': 14, 'mat': 'grille'}]),
         box('dish_rim', [-10, 66, -5], [10, 68, -3], 'paint2', bone='rack', panels=0),
         box('feed_arm', [-0.5, 75, -10], [0.5, 77, -3], 'metal', bone='rack'),
         box('feed', [-1.5, 74, -14], [1.5, 78, -10], 'paint2', bone='rack', decals=[{'face': 'north', 'x': 0, 'y': 1, 'w': 3, 'h': 2, 'mat': 'amber'}]),
         box('yagi_boom', [8, 70, -2], [10, 72, 12], 'metal', bone='rack'), box('yagi_a', [5.5, 70.5, 0], [12.5, 71.5, 1], 'metal', bone='rack'),
         box('yagi_b', [5.5, 70.5, 5], [12.5, 71.5, 6], 'metal', bone='rack'), box('yagi_c', [5.5, 70.5, 10], [12.5, 71.5, 11], 'metal', bone='rack')]
    bones = [{'name': 'base', 'pivot': [0, 0, 0]}, {'name': 'turret', 'pivot': [0, 13, 0]}, {'name': 'rack', 'pivot': [0, 61, 0], 'parent': 'turret'}]
    return spec('relay', 'Field relay station: shelter with generator, lattice mast, rotating dish and yagi. Scale 1 m = 16 px.',
                OLIVE, PAINT2_DARK, bones, p + t + r, 74, 0.3)


def tube(name, cx, cy, half, z0, z1, mouth, paint='paint', **kw):
    """Square launch tube along Z: body, dark mouth on the front face. Missile nose pokes out of the mouth (missile spec, tipN bone)."""
    side = 2 * half
    return box(name, [cx - half, cy - half, z0], [cx + half, cy + half, z1], paint, panels=0, decals=[
        {'face': 'north', 'x': (side - mouth) // 2, 'y': (side - mouth) // 2, 'w': mouth, 'h': mouth, 'mat': 'dark'}], **kw)


def sam_post():
    """Ground SAM post: pad, turret column and a twin-tube launcher. Missiles sit inside the tubes, only the noses show (scale 1 m = 16 px:
    tubes 8 x 8 x 28 px = 0.5 x 1.75 m, missile D 5 px = 0.3 m)."""
    p = [box('pad', [-13, 0, -13], [13, 3, 13], 'metal', bone='base', panels=0, decals=[{'face': 'up', 'x': 2, 'y': 2, 'w': 22, 'h': 1, 'mat': 'bolts'}]),
         box('battery', [-13, 3, 3], [-7, 9, 12], 'paint', bone='base', panels=0, decals=[{'face': 'north', 'x': 1, 'y': 1, 'w': 4, 'h': 4, 'mat': 'hatch'}]),
         box('crate', [7, 3, 4], [13, 8, 12], 'paint2', bone='base', panels=0),
         box('pedestal', [-6, 3, -6], [6, 7, 6], 'paint2', bone='base', panels=0)]
    t = [box('column', [-5, 7, -5], [5, 17, 5], 'paint', bone='turret', panels=0, decals=[{'face': 'north', 'x': 2, 'y': 2, 'w': 6, 'h': 5, 'mat': 'hatch'}]),
         box('seeker_box', [-4, 17, -4], [4, 20, 4], 'paint2', bone='turret', panels=0)]
    cells = [(5.5, 26), (-5.5, 26)]
    r = [box('beam', [-10, 20, -10], [10, 22, 14], 'paint2', bone='rack', panels=0),
         box('trunnion', [-11, 21, -1], [11, 23, 1], 'metal', bone='rack', panels=0)]
    for k, (cx, cy) in enumerate(cells):
        r += [tube(f'tube{k}', cx, cy, 4, -14, 14, 6, 'paint', bone='rack'),
              box(f'tube{k}_cap', [cx - 4, cy - 4, 14], [cx + 4, cy + 4, 15], 'metal', bone='rack', panels=0)]
    r += [box(f'band{i}', [-10.5, 21, z], [10.5, 31, z + 2], 'paint2', bone='rack', panels=0) for i, z in enumerate((-8, 6))]
    r += [box('eo_pod', [-3, 30, -5], [3, 34, 1], 'paint2', bone='rack', decals=[{'face': 'north', 'x': 0, 'y': 1, 'w': 6, 'h': 2, 'mat': 'glass'}])]
    bones = [{'name': 'base', 'pivot': [0, 0, 0]}, {'name': 'turret', 'pivot': [0, 7, 0]}, {'name': 'rack', 'pivot': [0, 24, 0], 'parent': 'turret'},
             {'name': 'tip0', 'pivot': [0, 24, 0], 'parent': 'rack'}, {'name': 'tip1', 'pivot': [0, 24, 0], 'parent': 'rack'}]
    sp = spec('sam_post', 'Single SAM post: pad with battery, turret column and a twin-tube launcher; only the missile noses show. Scale 1 m = 16 px.',
              OLIVE, PAINT2_DARK, bones, p + t + r, 75, 0.3)
    sp['canvas'] = MISSILE_GREEN
    sp['missile'] = [{'bone': f'tip{k}', 'L': 26, 'D': 5, 'zn': -20, 'cx': cx, 'cy': cy, 'palette': 'sam', 'trim': 12} for k, (cx, cy) in enumerate(cells)]
    return sp


def sam_manual():
    """Man-portable SAM station: 2 x 2 cluster of tubes on a gimbal (tubes 6 x 6 x 20 px = 1.25 m, missile D 3 px)."""
    p = [box('pad', [-8, 0, -8], [8, 2, 8], 'metal', bone='base', panels=0),
         box('case', [-8, 2, -8], [8, 8, 8], 'paint', bone='base', panels=0, decals=[{'face': 'north', 'x': 3, 'y': 2, 'w': 10, 'h': 4, 'mat': 'hatch'}])]
    t = [box('column', [-3, 8, -3], [3, 17, 3], 'paint2', bone='turret', panels=0),
         box('gimbal', [-5, 17, -3], [5, 19, 5], 'metal', bone='turret', panels=0)]
    cells = [(-3, 24), (3, 24), (-3, 30), (3, 30)]
    r = [box('frame', [-7, 19, -8], [7, 21, 10], 'paint2', bone='rack', panels=0),
         box('grip', [-1, 15, 5], [1, 19, 9], 'rubber', bone='rack'),
         box('battery', [6, 24, -2], [9, 30, 6], 'paint2', bone='rack', decals=[{'face': 'east', 'x': 1, 'y': 1, 'w': 4, 'h': 2, 'mat': 'amber'}]),
         box('sight', [-2, 33, -6], [2, 36, 0], 'paint2', bone='rack', decals=[{'face': 'north', 'x': 0, 'y': 1, 'w': 4, 'h': 2, 'mat': 'glass'}])]
    for k, (cx, cy) in enumerate(cells):
        r.append(tube(f'tube{k}', cx, cy, 3, -10, 10, 4, 'paint', bone='rack'))
    r += [box(f'band{i}', [-6.5, 20.5, z], [6.5, 33.5, z + 1], 'paint2', bone='rack', panels=0) for i, z in enumerate((-4, 4))]
    bones = [{'name': 'base', 'pivot': [0, 0, 0]}, {'name': 'turret', 'pivot': [0, 8, 0]}, {'name': 'rack', 'pivot': [0, 20, 0], 'parent': 'turret'}] + \
        [{'name': f'tip{i}', 'pivot': [0, 20, 0], 'parent': 'rack'} for i in range(4)]
    sp = spec('sam_manual', 'Man-portable SAM station: pad, gimbal column, frame carrying four tubes in a 2 x 2 cluster; only the missile noses show. Scale 1 m = 16 px.',
              OLIVE, PAINT2_DARK, bones, p + t + r, 76, 0.3)
    sp['canvas'] = MISSILE_GREEN
    sp['missile'] = [{'bone': f'tip{i}', 'L': 16, 'D': 3, 'zn': -13, 'cx': cx, 'cy': cy, 'palette': 'javelin', 'trim': 7} for i, (cx, cy) in enumerate(cells)]
    return sp


# ---------------------------------------------------------------- drones (nose -Z, centred on the origin)

def arm(name, x, z, y=0.0, bone='frame', t=1, h=1):
    """Diagonal arm from the frame centre to a motor at (x, z): a thin beam rotated about Y."""
    L = round(math.hypot(x, z))
    ang = math.degrees(math.atan2(x, -z)) if False else math.degrees(math.atan2(z, x))
    return box(name, [0, y - h / 2, -t / 2], [L, y + h / 2, t / 2], 'metal', bone=bone, rotation=[0, -ang, 0], pivot=[0, y, 0], panels=0)


def quad_props(motors, y, blade=5, name='prop', first=0):
    """Props (bone propN at each motor): hub plus two crossed blades; the renderer spins the bone about Y."""
    bones, parts = [], []
    for i, (x, z) in enumerate(motors, first):
        n = f'{name}{i}'
        bones.append({'name': n, 'pivot': [x, y, z]})
        parts += [box(f'{n}_hub', [x - 1, y, z - 1], [x + 1, y + 2, z + 1], 'metal', bone=n, panels=0),
                  box(f'{n}_blade_a', [x - blade, y + 2, z - 0.5], [x + blade, y + 3, z + 0.5], 'rubber', bone=n, panels=0),
                  box(f'{n}_blade_b', [x - 0.5, y + 3, z - blade], [x + 0.5, y + 4, z + blade], 'rubber', bone=n, panels=0)]
    return bones, parts


def interceptor():
    motors = [(8, -7), (-8, -7), (8, 7), (-8, 7)]
    p = [box('body', [-3, -2, -4], [3, 2, 5], 'paint', bone='frame', panels=0, decals=[{'face': 'up', 'x': 1, 'y': 2, 'w': 4, 'h': 1, 'mat': 'strap'}]),
         box('battery', [-2, -3, -3], [2, -2, 4], 'paint2', bone='frame', panels=0),
         box('canopy', [-2, 2, -3], [2, 3, 3], 'paint2', bone='frame', panels=0),
         box('camera', [-1.5, -1, -6], [1.5, 1, -4], 'paint2', bone='frame', decals=[{'face': 'north', 'x': 0, 'y': 0, 'w': 3, 'h': 2, 'mat': 'glass'}]),
         box('antenna', [-0.5, 3, 3], [0.5, 7, 4], 'metal', bone='frame')]
    for i, (x, z) in enumerate(motors):
        p.append(arm(f'arm{i}', x, z, 0.3 * i - 0.45))
        p.append(box(f'motor{i}', [x - 1.5, -2, z - 1.5], [x + 1.5, 2, z + 1.5], 'paint2', bone='frame', panels=0))
        p.append(box(f'leg{i}', [x - 0.5, -4, z - 0.5], [x + 0.5, -2, z + 0.5], 'metal', bone='frame'))
    pb, pp = quad_props(motors, 2, 5)
    bones = [{'name': 'frame', 'pivot': [0, 0, 0]}] + pb
    return spec('interceptor', 'Interceptor quadcopter: X frame, camera pod, four props. Scale 1 m = 16 px.', OLIVE, PAINT2_DARK, bones, p + pp, 81, 0.2)


def yprop(x, y, z, blade=5, bone='prop', name='prop'):
    """Pusher/tractor prop spinning about Z (bone `bone`, pivot at (x, y, z)): hub block and two crossed blades."""
    return [box(f'{name}_hub', [x - 1, y - 1, z], [x + 1, y + 1, z + 2], 'metal', bone=bone, panels=0),
            box(f'{name}_blade_a', [x - blade, y - 0.5, z + 2], [x + blade, y + 0.5, z + 3], 'rubber', bone=bone, panels=0),
            box(f'{name}_blade_b', [x - 0.5, y - blade, z + 3], [x + 0.5, y + blade, z + 4], 'rubber', bone=bone, panels=0)]


def delta(name, x_root, z_lead_root, x_tip, z_lead_tip, z_trail, y, bone='wing', mat='paint', cw=5, mirror=True):
    """Swept wing half: a straight trailing slab plus a leading-edge plate rotated about Y by the sweep angle."""
    dx, dz = x_tip - x_root, z_lead_tip - z_lead_root
    L = round(math.hypot(dx, dz))
    ang = -math.degrees(math.atan2(dz, dx))
    zt = max(z_lead_root, z_lead_tip) + cw
    zt = min(zt, z_trail)
    return [box(f'{name}_trail', [x_root, y - 0.5, z_trail - 3], [x_tip, y + 0.5, z_trail], mat, bone=bone, panels=0, mirror=mirror),
            box(f'{name}_lead', [x_root, y - 0.25, z_lead_root], [x_root + L, y + 0.75, z_lead_root + cw], mat, bone=bone, panels=0, mirror=mirror,
                rotation=[0, ang, 0], pivot=[x_root, y, z_lead_root])]


def wing_recon():
    p = [box('fuselage', [-2, -2, -12], [2, 2, 12], 'paint', bone='body', panels=0, decals=[{'face': 'up', 'x': 1, 'y': 2, 'w': 2, 'h': 8, 'mat': 'rib'}]),
         box('nose', [-1, -1, -15], [1, 1, -12], 'paint2', bone='body', panels=0),
         box('lens', [-1.5, -3, -10], [1.5, -2, -6], 'paint2', bone='body', decals=[{'face': 'down', 'x': 0, 'y': 0, 'w': 3, 'h': 4, 'mat': 'glass'}]),
         box('pylon', [-1, 2, -4], [1, 3, 2], 'paint2', bone='body', panels=0),
         box('tail_boom', [-1, -1, 12], [1, 1, 15], 'paint', bone='body', panels=0),
         box('fin', [-0.5, 2, 10], [0.5, 8, 15], 'paint', bone='body', panels=0),
         box('stab', [-8, 1, 13], [8, 2, 16], 'paint', bone='body', panels=0),
         box('skid', [-1, -4, -6], [1, -2, 6], 'metal', bone='body', panels=0)]
    w = [box('wing', [-24, 3, -5], [24, 4, 3], 'paint', bone='wing', panels=0, decals=[{'face': 'up', 'x': 0, 'y': 0, 'w': 3, 'h': 8, 'mat': 'red'}, {'face': 'up', 'x': 45, 'y': 0, 'w': 3, 'h': 8, 'mat': 'red'}]),
         ]
    pr = yprop(0, 0, -17.5, 5)
    bones = [{'name': 'body', 'pivot': [0, 0, 0]}, {'name': 'wing', 'pivot': [0, 0, 0]}, {'name': 'prop', 'pivot': [0, 0, -16.5]}]
    return spec('wing_recon', 'Fixed-wing recon drone: tractor prop, high straight wing with red tips, single fin. Scale 1 m = 16 px.',
                'grey', PAINT2_DARK, bones, p + w + pr, 82, 0.15)


def lancet():
    p = cyl('fuselage', -2, -2, -13, 14, 4, 'paint', bone='body')
    p += [box('nose', [-1.5, -1.5, -16], [1.5, 1.5, -13], 'paint2', bone='body', decals=[{'face': 'north', 'x': 0, 'y': 0, 'w': 3, 'h': 3, 'mat': 'glass'}]),
          box('band', [-2.5, -2.5, -8], [2.5, 2.5, -6], 'red', bone='body', panels=0),
          box('tail_cap', [-1.5, -1.5, 14], [1.5, 1.5, 16], 'paint2', bone='body', panels=0)]
    w = []
    for i, (zc, half, ch) in enumerate(((-1, 11, 6), (10.5, 7, 3))):
        for j, ang in enumerate((45, -45)):
            w.append(box(f'wing{i}{j}', [-half, -0.5, zc - ch / 2 + 0.5 * j], [half, 0.5, zc + ch / 2 + 0.5 * j], 'paint', bone='wing', rotation=[0, 0, ang], pivot=[0, 0, zc + 0.5 * j], panels=0))
    pr = yprop(0, 0, 16, 5)
    bones = [{'name': 'body', 'pivot': [0, 0, 0]}, {'name': 'wing', 'pivot': [0, 0, 0]}, {'name': 'prop', 'pivot': [0, 0, 16.5]}]
    return spec('lancet', 'Lancet loitering munition: slim fuselage, X wings and X tail, nose camera, pusher prop. Scale 1 m = 16 px.',
                'sand', PAINT2_DARK, bones, p + w + pr, 83, 0.2)


def kub():
    p = [box('pod', [-3, -3, -8], [3, 3, 8], 'paint', bone='body', panels=0),
         box('nose', [-2, -2, -12], [2, 2, -8], 'paint2', bone='body', panels=0, decals=[{'face': 'north', 'x': 0, 'y': 0, 'w': 4, 'h': 4, 'mat': 'glass'}]),
         box('tail_cap', [-2, -2, 8], [2, 2, 10], 'paint2', bone='body', panels=0)]
    w = delta('kw', 6, -4, 12, 6, 8, 0, 'wing', 'paint', cw=5)
    w += [box('wing_center', [-6, -0.5, -4], [6, 0.5, 7], 'paint', bone='wing', panels=0)]
    pr = yprop(0, 0, 10, 4)
    bones = [{'name': 'body', 'pivot': [0, 0, 0]}, {'name': 'wing', 'pivot': [0, 0, 0]}, {'name': 'prop', 'pivot': [0, 0, 10.5]}]
    return spec('kub', 'KUB loitering munition: flying delta wing with a warhead pod and pusher prop. Scale 1 m = 16 px.',
                OLIVE, PAINT2_DARK, bones, p + w + pr, 84, 0.2)


def molniya():
    p = [box('pod', [-3, -3, -12], [3, 3, 3], 'paint', bone='body', panels=0, decals=[{'face': 'up', 'x': 1, 'y': 2, 'w': 4, 'h': 8, 'mat': 'rib'}]),
         box('nose', [-2, -2, -15], [2, 2, -12], 'paint2', bone='body', panels=0),
         box('boom', [5, -1, 0], [7, 1, 19], 'paint', bone='body', mirror=True, panels=0),
         box('boom_join', [3, -1, 0], [5, 1, 2], 'paint', bone='body', mirror=True, panels=0),
         box('tail_plane', [-7, 1, 17], [7, 2, 19], 'paint', bone='body', panels=0),
         box('fin', [5.5, 2, 14], [6.5, 7, 18], 'paint2', bone='body', mirror=True, panels=0),
         box('skid', [-1, -5, -8], [1, -3, 0], 'metal', bone='body', panels=0)]
    w = [box('wing', [-16, 1, -8], [16, 2, -1], 'paint', bone='wing', panels=0, decals=[{'face': 'up', 'x': 0, 'y': 0, 'w': 3, 'h': 7, 'mat': 'amber'}, {'face': 'up', 'x': 29, 'y': 0, 'w': 3, 'h': 7, 'mat': 'amber'}]),
         ]
    pr = yprop(0, 0, 3, 5)
    bones = [{'name': 'body', 'pivot': [0, 0, 0]}, {'name': 'wing', 'pivot': [0, 0, 0]}, {'name': 'prop', 'pivot': [0, 0, 4.5]}]
    return spec('molniya', 'Molniya UAV: straight wing, central pod with pusher prop, twin tail booms. Scale 1 m = 16 px.',
                'sand', PAINT2_DARK, bones, p + w + pr, 85, 0.2)


def baba_yaga():
    motors = [(-7, 12), (-14, 0), (-7, -12), (7, -12), (14, 0), (7, 12)]
    p = [box('body', [-5, -4, -5], [5, 2, 5], 'paint', bone='frame', panels=0),
         box('lid', [-4, 2, -4], [4, 3, 4], 'paint2', bone='frame', panels=0),
         box('payload', [-3, -8, -3], [3, -4, 3], 'paint2', bone='frame', panels=0, decals=[{'face': 'north', 'x': 1, 'y': 1, 'w': 4, 'h': 2, 'mat': 'red'}]),
         box('gps', [-1, 3, -1], [1, 6, 1], 'metal', bone='frame', panels=0)]
    for i, (x, z) in enumerate(motors):
        L = round(math.hypot(x, z))
        ang = math.degrees(math.atan2(z, x))
        p.append(box(f'arm{i}', [0, 0.3 * i - 1.25, -1], [L, 0.3 * i - 0.25, 1], 'metal', bone='frame', rotation=[0, -ang, 0], pivot=[0, 0, 0], panels=0))
        p.append(box(f'motor{i}', [x - 1.5, -2, z - 1.5], [x + 1.5, 2, z + 1.5], 'paint2', bone='frame', panels=0))
        p.append(box(f'leg{i}', [x - 0.5, -8, z - 0.5], [x + 0.5, -2, z + 0.5], 'metal', bone='frame'))
    pb, pp = quad_props(motors, 2, 6)
    bones = [{'name': 'frame', 'pivot': [0, 0, 0]}] + pb
    return spec('baba_yaga', 'Baba Yaga heavy hexacopter bomber: six arms, big payload pod, long legs. Scale 1 m = 16 px.',
                OLIVE, PAINT2_DARK, bones, p + pp, 86, 0.25)


def retrofit_rams():
    """Lift rams for the gen.py launchers; idempotent (add_rams skips specs already marked 'rams'). gen.py calls this after rewriting its specs."""
    add_rams('nasams', 4, ('ram0', 30, 17, 35, 8, 33), extend=('arm',))
    add_rams('patriot', 4, ('ram0', 30, 20, 34, 9, 33), keep=('hinge',), ram_kw={'base_y': 24})
    add_rams('s400', 4, ('ram0', 30, 20, 38, 8.5, 31))
    add_rams('himars', 4, ('ram0', 30, 30, 34, 8, 34))


if __name__ == '__main__':
    import sys
    only = sys.argv[1:]
    retrofit_rams()
    for fn in (grad, smerch, uragan, tochka, iskander, kalibr, sam, grad_p, mortar, mg, relay, sam_post, sam_manual, shahed_launcher, interceptor, wing_recon, lancet, kub, molniya, baba_yaga):
        s = fn()
        if only and s['name'] not in only:
            continue
        lint(s)
        heal.heal(s)
        (HERE / f'{s["name"]}.json').write_text(json.dumps(s, indent=1, ensure_ascii=False))
        print('wrote', s['name'])
