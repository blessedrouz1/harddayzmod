"""Floating-part check: every cube must touch (or overlap) the rest of the model in the rest pose.
usage: attach.py NAME [NAME...]   (reads out/NAME.geo.json + out/NAME.parts.json, so run after bc.sh; missiles included)"""
import json, math, sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
EPS = 0.06


def rot_aabb(o, s, rot, piv):
    pts = [(o[0] + dx * s[0], o[1] + dy * s[1], o[2] + dz * s[2]) for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
    rx, ry, rz = (math.radians(v) for v in rot)
    out = []
    for p in pts:
        x, y, z = (p[0] - piv[0], p[1] - piv[1], p[2] - piv[2])
        y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
        x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
        x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
        out.append((x + piv[0], y + piv[1], z + piv[2]))
    return [min(p[i] for p in out) for i in range(3)], [max(p[i] for p in out) for i in range(3)]


def check(name, quiet=False):
    bb = json.loads((HERE / f'out/{name}.bbmodel').read_text())
    bone_of = {}

    def walk(node, bone):
        if isinstance(node, dict):
            for c in node.get('children', []):
                walk(c, node['name'])
        else:
            bone_of[node] = bone
    for g in bb['outliner']:
        walk(g, 'root')
    boxes = []
    for e in bb['elements']:
        o = e['from']
        s = [e['to'][k] - e['from'][k] for k in range(3)]
        rot = e.get('rotation')
        if rot and any(rot):
            lo, hi = rot_aabb(o, s, rot, e.get('origin', [o[k] + s[k] / 2 for k in range(3)]))
        else:
            lo, hi = list(o), [o[k] + s[k] for k in range(3)]
        boxes.append((lo, hi, e['name'], bone_of.get(e['uuid'], 'root'), s[0] * s[1] * s[2]))
    n = len(boxes)
    par = list(range(n))

    def find(a):
        while par[a] != a:
            par[a] = par[par[a]]
            a = par[a]
        return a
    for i in range(n):
        for j in range(i + 1, n):
            a, b = boxes[i], boxes[j]
            if all(a[0][k] <= b[1][k] + EPS and b[0][k] <= a[1][k] + EPS for k in range(3)):
                par[find(i)] = find(j)
    comps = {}
    for i in range(n):
        comps.setdefault(find(i), []).append(i)
    big = max(comps.values(), key=lambda c: sum(boxes[i][4] for i in c))
    bad = [c for c in comps.values() if c is not big]
    if not quiet:
        print(f'{name}: {n} cubes, {len(comps)} groups, floating groups: {len(bad)}')
        for c in sorted(bad, key=len):
            lo = [min(boxes[i][0][k] for i in c) for k in range(3)]
            hi = [max(boxes[i][1][k] for i in c) for k in range(3)]
            best = None
            for i in c:
                for j in big:
                    a, b = boxes[i], boxes[j]
                    g = [max(a[0][k] - b[1][k], b[0][k] - a[1][k]) for k in range(3)]
                    d = math.sqrt(sum(max(0, v) ** 2 for v in g))
                    if best is None or d < best[0]:
                        best = (d, a[2], b[2], [round(v, 2) for v in g])
            print('   FLOAT', [boxes[i][2] for i in c][:4], '...' if len(c) > 4 else '', 'bone', boxes[c[0]][3],
                  'bbox', [round(v, 1) for v in lo], [round(v, 1) for v in hi], '| nearest', best[1], '<->', best[2], 'gap', best[3])
    return bad


if __name__ == '__main__':
    for nm in sys.argv[1:]:
        check(nm)
