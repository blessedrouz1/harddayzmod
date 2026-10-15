"""Missiles in the style of the mod's own Iskander model.

The template is the original missile from the Iskander geo (square body, square belts, nose and tail fins made of slanted plates).
Every missile of the mod is that same shape scaled to its own length and diameter, differing only in colours:
  * cross section scales by k = D / 12, the nose (17 px) and tail (21 px) keep their shape (uniformly scaled by k),
  * the straight middle stretches to fit the length (belts and side plates keep their thickness).

usage as a module:
  build(L, D, zn, cx, cy, palette)  -> cubes in Bedrock file coordinates (uv still symbolic), face rects
  standalone(name, L, D, palette)   -> writes assets geo/<name>.geo.json + textures/entity/<name>.png (centred, nose -Z)
  inject(name, spec_missile)        -> adds the missile to out/<name>.geo.json / .png / .bbmodel of a built vehicle
"""
import base64
import copy
import io
import json
import sys
import uuid
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parent.parent / 'src/main/resources/assets/harddayz'
TEMPLATE_PNG = Image.open(HERE / 'template/iskander.png').convert('RGBA')
_geo = json.loads((HERE / 'template/iskander.geo.json').read_text())['minecraft:geometry'][0]
TEMPLATE = [c for b in _geo['bones'] if b['name'] in ('missile', 'body') for c in b['cubes']]

AX_Y = 40.0            # template axis height
NOSE0, NOSE1 = -69.15, -52.0   # nose zone
TAIL0, TAIL1 = 21.0, 42.0      # tail zone
BODY_D = 12.0

# body colour ramps (dark, mid, light); None keeps the original Iskander green
PALETTES = {
    'iskander': None,
    'grad': [(54, 60, 40), (84, 92, 58), (112, 122, 78)],
    'gmlrs': [(112, 120, 126), (160, 168, 172), (204, 210, 212)],
    's400': [(134, 140, 144), (184, 190, 192), (224, 228, 228)],
    'patriot': [(150, 154, 156), (196, 200, 200), (234, 236, 234)],
    'sam': [(88, 96, 82), (122, 132, 110), (162, 170, 146)],
    'javelin': [(84, 80, 56), (118, 112, 80), (152, 144, 106)],
    'rpg': [(64, 66, 44), (92, 92, 60), (122, 120, 80)],
    'mortar': [(40, 48, 34), (62, 72, 50), (86, 96, 68)],
    's8': [(92, 100, 88), (124, 132, 118), (160, 166, 152)],
    'kalibr': [(126, 134, 140), (170, 178, 182), (208, 214, 216)],
    'tos': [(92, 86, 48), (130, 122, 68), (166, 156, 94)],
    'smerch': [(60, 66, 44), (92, 100, 64), (122, 132, 86)],
    'uragan': [(70, 72, 52), (104, 106, 74), (136, 138, 98)],
    'nasams': [(120, 126, 130), (166, 172, 174), (206, 210, 210)],
}


def _lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def recolor(img, pal):
    """Greenish body texels are mapped (by brightness) onto the palette ramp; greys, blacks and tails stay."""
    if pal is None:
        return img
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a and g - b > 25 and g >= r:
                lum = 0.3 * r + 0.59 * g + 0.11 * b
                t = max(0.0, min(1.0, (lum - 60) / 75))
                c = _lerp(pal[0], pal[1], t * 2) if t < 0.5 else _lerp(pal[1], pal[2], (t - 0.5) * 2)
                px[x, y] = c + (a,)
    return img


def _rot(c):
    return any(abs(v) > 1e-6 for v in c.get('rotation', [0, 0, 0]))


def build(L, D, zn=0.0, cx=0.0, cy=0.0):
    """Scaled copy of the template: nose front at z = zn, axis at (cx, cy), total length L. Returns cube dicts with symbolic faces."""
    k = D / BODY_D
    nose_len, tail_len = (NOSE1 - NOSE0) * k, (TAIL1 - TAIL0) * k
    mid = L - nose_len - tail_len
    if mid < 4:
        raise ValueError(f'length {L} too short for diameter {D} (needs > {nose_len + tail_len + 4:.1f})')
    s = mid / (TAIL0 - NOSE1)
    n_end = zn + nose_len
    t_start = n_end + mid

    def f(z):
        if z <= NOSE1:
            return zn + (z - NOSE0) * k
        if z >= TAIL0:
            return t_start + (z - TAIL0) * k
        return n_end + (z - NOSE1) * s

    def pt(p, anchor):
        """Uniform scaling of a point about the zone anchor (rotated plates, fins, tail)."""
        a_t = n_end if anchor == 'nose' else t_start
        a_z = NOSE1 if anchor == 'nose' else TAIL0
        return [cx + p[0] * k, cy + (p[1] - AX_Y) * k, a_t + (p[2] - a_z) * k]

    out = []
    for src in TEMPLATE:
        c = copy.deepcopy(src)
        o, sz = src['origin'], src['size']
        zc = o[2] + sz[2] / 2
        if _rot(src):
            anchor = 'nose' if src['pivot'][2] < -40 else 'tail'
            c['origin'] = pt(o, anchor)
            c['size'] = [v * k for v in sz]
            c['pivot'] = pt(src['pivot'], anchor)
        elif sz[2] > 60:  # the body box: stretches
            z0, z1 = f(o[2]), f(o[2] + sz[2])
            c['origin'] = [cx + o[0] * k, cy + (o[1] - AX_Y) * k, z0]
            c['size'] = [sz[0] * k, sz[1] * k, z1 - z0]
        elif NOSE1 < zc < TAIL0:  # belts, raceway, side plates: keep thickness
            zz = f(zc)
            c['size'] = [sz[0] * k, sz[1] * k, sz[2] * k]
            c['origin'] = [cx + o[0] * k, cy + (o[1] - AX_Y) * k, zz - c['size'][2] / 2]
        else:
            anchor = 'nose' if zc <= NOSE1 else 'tail'
            c['origin'] = pt(o, anchor)
            c['size'] = [v * k for v in sz]
        c['origin'] = [round(v, 3) for v in c['origin']]
        c['size'] = [round(v, 3) for v in c['size']]
        if 'pivot' in c:
            c['pivot'] = [round(v, 3) for v in c['pivot']]
        out.append(c)
    return out


def pack(cubes, pal, width, x0=0, y0=0):
    """Copy every face rect of the cubes out of the Iskander texture into a fresh atlas (recoloured); rewrites uv in place.
    Returns (atlas image of size width x height, height)."""
    rects = {}
    for c in cubes:
        for face in c['uv'].values():
            u, v = face['uv']
            w, h = face['uv_size']
            rects[(u, v, w, h)] = None
    order = sorted(rects, key=lambda r: (-abs(r[3]), -abs(r[2])))
    x, y, row_h = 0, 0, 0
    for r in order:
        w, h = abs(int(r[2])), abs(int(r[3]))
        if x + w > width:
            x, y, row_h = 0, y + row_h, 0
        rects[r] = (x, y)
        x += w
        row_h = max(row_h, h)
    height = y + row_h
    atlas = Image.new('RGBA', (width, max(height, 1)), (0, 0, 0, 0))
    for r, (nx, ny) in rects.items():
        u, v, w, h = r
        aw, ah = abs(int(w)), abs(int(h))
        su, sv = (u - aw if w < 0 else u), (v - ah if h < 0 else v)
        atlas.paste(TEMPLATE_PNG.crop((int(su), int(sv), int(su) + aw, int(sv) + ah)), (nx, ny))
    recolor(atlas, pal)
    for c in cubes:
        for face in c['uv'].values():
            key = tuple(face['uv'] + face['uv_size'])
            nx, ny = rects[key]
            w, h = face['uv_size']
            face['uv'] = [x0 + nx + (abs(int(w)) if w < 0 else 0), y0 + ny + (abs(int(h)) if h < 0 else 0)]
    return atlas, height


def geo_doc(name, bones, tw, th):
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': f'geometry.{name}', 'texture_width': tw, 'texture_height': th,
                        'visible_bounds_width': 4, 'visible_bounds_height': 4, 'visible_bounds_offset': [0, 1, 0]},
        'bones': bones}]}


def standalone(name, L, D, palette):
    """Centred missile for flight / item use: nose at -Z, geometric centre at the origin."""
    cubes = build(L, D, zn=-L / 2, cx=0, cy=0)
    atlas, h = pack(cubes, PALETTES[palette], 128)
    th = max(16, -(-h // 8) * 8)
    img = Image.new('RGBA', (128, th), (0, 0, 0, 0))
    img.paste(atlas, (0, 0))
    (ASSETS / f'geo/{name}.geo.json').write_text(json.dumps(geo_doc(name, [{'name': 'body', 'pivot': [0, 0, 0], 'cubes': cubes}], 128, th), indent=1))
    img.save(ASSETS / f'textures/entity/{name}.png')
    gm = Image.new('RGBA', img.size, (0, 0, 0, 255))
    gm.save(ASSETS / f'textures/entity/{name}_glowmask.png')
    return cubes, img


# ---------------------------------------------------------------- Blockbench

def _uid(*p):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'missilekit:' + ':'.join(map(str, p))))


def to_elements(cubes, name, tex_id=0):
    """Bedrock-file cubes -> bbmodel elements with per-face uv (x un-mirrored)."""
    els = []
    for i, c in enumerate(cubes):
        o, s = c['origin'], c['size']
        frm = [-(o[0] + s[0]), o[1], o[2]]
        to = [-o[0], o[1] + s[1], o[2] + s[2]]
        faces = {}
        for f, d in c['uv'].items():
            u, v = d['uv']
            w, h = d['uv_size']
            faces[f] = {'uv': [u, v, u + w, v + h], 'texture': tex_id}
        e = {'name': f'{name}_{i}', 'box_uv': False, 'rescale': False, 'locked': False, 'render_order': 'default', 'allow_mirror_modeling': True,
             'from': frm, 'to': to, 'autouv': 0, 'color': 0, 'inflate': 0, 'origin': frm if not c.get('pivot') else [-c['pivot'][0], c['pivot'][1], c['pivot'][2]],
             'rotation': [0, 0, 0], 'faces': faces, 'type': 'cube', 'uuid': _uid(name, i)}
        if c.get('rotation'):
            r = c['rotation']
            e['rotation'] = [-r[0], -r[1], r[2]]
        else:
            e['origin'] = [(frm[0] + to[0]) / 2, (frm[1] + to[1]) / 2, (frm[2] + to[2]) / 2]
        els.append(e)
    return els


def inject(name, m):
    """Add the missile(s) described by spec['missile'] (dict or list of dicts) to the build output of vehicle `name` (geo, png, bbmodel).
    m = {'bone': 'missile', 'L', 'D', 'zn', 'cx', 'cy', 'palette'}"""
    if isinstance(m, list):
        for one in m:
            inject(name, one)
        return
    out = HERE / 'out'
    geo = json.loads((out / f'{name}.geo.json').read_text())
    g = geo['minecraft:geometry'][0]
    png = Image.open(out / f'{name}.png').convert('RGBA')
    tw, th = g['description']['texture_width'], g['description']['texture_height']
    # strip a previous injection
    g['bones'] = [b for b in g['bones']]
    cubes = build(m['L'], m['D'], m['zn'], m['cx'], m['cy'])
    atlas, ah = pack(cubes, PALETTES[m.get('palette', 'iskander')], 256, 0, th)
    for b in g['bones']:
        if b['name'] == m.get('bone', 'missile'):
            b['cubes'] = b.get('cubes', []) + cubes
            break
    else:
        raise KeyError('bone for missile not found')
    new = Image.new('RGBA', (png.width, th + ah), (0, 0, 0, 0))
    new.paste(png, (0, 0))
    new.paste(atlas, (0, th))
    g['description']['texture_height'] = th + ah
    (out / f'{name}.geo.json').write_text(json.dumps(geo, indent=1))
    new.save(out / f'{name}.png')
    bbp = out / f'{name}.bbmodel'
    if bbp.exists():
        bb = json.loads(bbp.read_text())
        els = to_elements(cubes, f"{name}_{m.get('bone', 'missile')}_{abs(hash(json.dumps(m, sort_keys=True))) % 10000}")
        bb['elements'] += els
        for grp in bb['outliner']:
            _attach(grp, m.get('bone', 'missile'), [e['uuid'] for e in els])
        bb['resolution']['height'] = th + ah
        buf = io.BytesIO()
        new.save(buf, 'PNG')
        t = bb['textures'][0]
        t['height'], t['uv_height'] = th + ah, th + ah
        t['source'] = 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()
        bbp.write_text(json.dumps(bb))


def _attach(node, bone, uuids):
    if isinstance(node, dict):
        if node.get('name') == bone:
            node['children'] = node.get('children', []) + uuids
            return True
        return any(_attach(c, bone, uuids) for c in node.get('children', []))
    return False


if __name__ == '__main__':
    # python3 missilekit.py NAME L D PALETTE   -> standalone model
    n, L, D, p = sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), sys.argv[4]
    standalone(n, L, D, p)
    print('wrote', n)
