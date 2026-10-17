"""Split outrigger bones so LauncherRenderer can extend them (idempotent, INSTALLED assets).

Old: bone jackN holds leg + foot cubes -> nothing animates it. New: jackN (empty) > jackN_extension (leg, pivot on its
top, yScale stretches it down) + jackN_foot (drops by outrig * foot minY).

usage: python3 fix_jacks.py [NAME...]   (no names = every installed geo with jackN bones)
"""
import json
import re
import sys
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent.parent / 'src/main/resources/assets/harddayz/geo'


def fix(path):
    d = json.loads(path.read_text())
    bones = d['minecraft:geometry'][0]['bones']
    names = {b['name'] for b in bones}
    out, changed = [], False
    for b in bones:
        if not re.fullmatch(r'jack\d+', b['name']) or b['name'] + '_foot' in names or len(b.get('cubes', [])) != 2:
            out.append(b)
            continue
        leg, foot = sorted(b['cubes'], key=lambda c: -c['size'][1])
        n, (px, _, pz) = b['name'], b['pivot']
        top = leg['origin'][1] + leg['size'][1]
        out.append({k: v for k, v in b.items() if k != 'cubes'})
        out.append({'name': n + '_extension', 'parent': n, 'pivot': [px, top, pz], 'cubes': [leg]})
        out.append({'name': n + '_foot', 'parent': n, 'pivot': [px, foot['origin'][1], pz], 'cubes': [foot]})
        changed = True
    if changed:
        d['minecraft:geometry'][0]['bones'] = out
        path.write_text(json.dumps(d, indent=2))
    return changed


for p in sorted(ASSETS.glob('*.geo.json')):
    if sys.argv[1:] and p.name.split('.')[0] not in sys.argv[1:]:
        continue
    if fix(p):
        print('fixed', p.name)
