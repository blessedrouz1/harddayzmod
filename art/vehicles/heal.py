"""Spec-level floating-part healer: parts are merged into touching groups (AABB contact); a group that does not touch the main group is
snapped to it along the shortest gap when that gap is small, otherwise it is reported. Works on the spec dict (box, slab, wheel, lamp,
antenna parts; mirror=True adds the X-mirrored copy to the contact test and the move)."""
import math

EPS = 0.06
SNAP_MAX = 1.6


def aabb(p):
    t = p.get('type', 'box')
    if t == 'box':
        lo, hi = list(p['from']), list(p['to'])
        rot = p.get('rotation')
        if rot and any(rot):
            piv = p.get('pivot') or [(lo[i] + hi[i]) / 2 for i in range(3)]
            pts = [(x, y, z) for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]
            rx, ry, rz = (math.radians(v) for v in rot)
            out = []
            for q in pts:
                x, y, z = (q[0] - piv[0], q[1] - piv[1], q[2] - piv[2])
                y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
                x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
                x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
                out.append((x + piv[0], y + piv[1], z + piv[2]))
            lo = [min(o[i] for o in out) for i in range(3)]
            hi = [max(o[i] for o in out) for i in range(3)]
        return lo, hi
    if t == 'wheel':
        c, d, w = p['center'], p['diameter'], p['width']
        return [c[0] - w / 2, c[1] - d / 2, c[2] - d / 2], [c[0] + w / 2, c[1] + d / 2, c[2] + d / 2]
    if t == 'slab':
        ax = {'x': 0, 'y': 1, 'z': 2}[p['axis']]
        pts = [p['p0'], p['p1']]
        lo = [min(q[i] for q in pts) - 1 for i in range(3)]
        hi = [max(q[i] for q in pts) + 1 for i in range(3)]
        lo[ax], hi[ax] = p['extent']
        return lo, hi
    if t == 'lamp':
        c = p['center']
        s = p.get('size', [3, 3])
        return [c[0] - s[0] / 2 - 1, c[1] - s[1] / 2 - 1, c[2] - 1], [c[0] + s[0] / 2 + 1, c[1] + s[1] / 2 + 1, c[2] + 1]
    if t == 'antenna':
        b = p['base']
        return [b[0] - 1, b[1], b[2] - 1], [b[0] + 1, b[1] + p.get('height', 10), b[2] + 1]
    return None


def mirrored(bb):
    lo, hi = bb
    return [-hi[0], lo[1], lo[2]], [-lo[0], hi[1], hi[2]]


def boxes_of(p):
    bb = aabb(p)
    if bb is None:
        return []
    out = [bb]
    if p.get('mirror') or (p.get('type') == 'wheel' and p.get('mirror')):
        out.append(mirrored(bb))
    return out


def gap(a, b):
    """Per-axis separation (>0) or overlap (<=0) between two AABBs."""
    return [max(a[0][k] - b[1][k], b[0][k] - a[1][k]) for k in range(3)]


def touching(a, b):
    return all(g <= EPS for g in gap(a, b))


def shift_part(p, d):
    for k in ('from', 'to', 'pivot', 'center', 'p0', 'p1', 'inside', 'base'):
        if k in p:
            p[k] = [p[k][0] + d[0], p[k][1] + d[1], p[k][2] + d[2]]
    if 'extent' in p and p.get('type') == 'slab':
        ax = {'x': 0, 'y': 1, 'z': 2}[p['axis']]
        p['extent'] = [p['extent'][0] + d[ax], p['extent'][1] + d[ax]]


def groups(parts):
    bx = [boxes_of(p) for p in parts]
    n = len(parts)
    par = list(range(n))

    def find(a):
        while par[a] != a:
            par[a] = par[par[a]]
            a = par[a]
        return a
    for i in range(n):
        for j in range(i + 1, n):
            if any(touching(a, b) for a in bx[i] for b in bx[j]):
                par[find(i)] = find(j)
    comp = {}
    for i in range(n):
        if bx[i]:
            comp.setdefault(find(i), []).append(i)
    return comp, bx


def heal(spec, quiet=False, passes=6):
    parts = spec['parts']
    for _ in range(passes):
        comp, bx = groups(parts)
        if len(comp) <= 1:
            break
        vol = lambda c: sum((lambda b: (b[1][0] - b[0][0]) * (b[1][1] - b[0][1]) * (b[1][2] - b[0][2]))(bx[i][0]) for i in c)
        main = max(comp.values(), key=vol)
        moved = False
        for c in comp.values():
            if c is main:
                continue
            best = None
            for i in c:
                for a in bx[i]:
                    for m in main:
                        for b in bx[m]:
                            g = gap(a, b)
                            pos = [max(0, v) for v in g]
                            dist = math.sqrt(sum(v * v for v in pos))
                            if best is None or dist < best[0]:
                                best = (dist, a, b, g)
            if best and best[0] <= SNAP_MAX:
                _, a, b, g = best
                d = [0, 0, 0]
                for k in range(3):
                    if g[k] > EPS:  # move toward the other box along this axis
                        d[k] = (b[0][k] - a[1][k]) if a[1][k] <= b[0][k] else (b[1][k] - a[0][k])
                for i in c:
                    shift_part(parts[i], d)
                moved = True
        if not moved:
            break
    comp, bx = groups(parts)
    if len(comp) > 1 and not quiet:
        vol = lambda c: sum((lambda b: (b[1][0] - b[0][0]) * (b[1][1] - b[0][1]) * (b[1][2] - b[0][2]))(bx[i][0]) for i in c)
        main = max(comp.values(), key=vol)
        for c in comp.values():
            if c is not main:
                print('   FLOAT', spec['name'], [parts[i]['name'] for i in c][:5])
    return len(comp) - 1
