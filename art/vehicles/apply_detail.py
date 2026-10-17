"""Patch the INSTALLED textures of a vehicle with the pixels that detail_pass added (geometry untouched, wheels / rams / appended atlases kept).

Builds the spec twice in a scratch dir (without and with the auto decals), checks the geometry is identical and that the installed geo
carries the same cubes, then copies only the pixels that differ into mods' textures/entity/NAME.png and regenerates the damaged / burnt /
glowmask variants from it.

usage: python3 apply_detail.py NAME [NAME...]     (run `python3 detail_pass.py NAME` first; originals saved in out/installed_pre_detail/)
"""
import copy
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

import detail_pass
import install
import shade

HERE = Path(__file__).resolve().parent
SK = Path('/Users/danylokulibaba/Desktop/minecraft-mods-builder/blockbench-military-pixel/scripts/build_vehicle.py')
BAK = HERE / 'out/installed_pre_detail'


def build(name, spec, work):
    (work / 'out').mkdir(parents=True, exist_ok=True)
    f = work / f'{name}.json'
    f.write_text(json.dumps(spec))
    r = subprocess.run(['python3', str(SK), str(f), '-o', str(work / 'out')], capture_output=True, text=True)
    if 'spec error' in r.stdout + r.stderr or 'Traceback' in r.stdout + r.stderr:
        raise SystemExit((r.stdout + r.stderr)[:600])
    shade.HERE = work
    shade.shade(name)
    return Image.open(work / f'out/{name}.png').convert('RGBA'), json.loads((work / f'out/{name}.geo.json').read_text())


def cubes(geo):
    return [(tuple(c['uv']), tuple(c['size']), tuple(c['origin'])) for b in geo['minecraft:geometry'][0]['bones'] for c in b.get('cubes', [])]


def apply(name):
    spec = json.loads((HERE / f'{name}.json').read_text())
    base = copy.deepcopy(spec)
    detail_pass.strip(base['parts'])
    work = Path(tempfile.mkdtemp(prefix=f'detail_{name}_'))
    b_im, b_geo = build(name, base, work / 'b')
    n_im, n_geo = build(name, spec, work / 'n')
    if cubes(b_geo) != cubes(n_geo) or b_im.size != n_im.size:
        raise SystemExit(f'{name}: geometry changed, abort')
    inst_geo = json.loads((install.ASSETS / f'geo/{name}.geo.json').read_text())
    missing = [c for c in set(cubes(n_geo)) if c not in set(cubes(inst_geo))]
    if missing:
        raise SystemExit(f'{name}: {len(missing)} built cubes are not in the installed geo (e.g. {missing[0]}), abort')
    tex = install.ASSETS / f'textures/entity/{name}.png'
    BAK.mkdir(parents=True, exist_ok=True)
    for suffix in ('', '_damaged', '_burnt', '_glowmask', '_damaged_glowmask', '_burnt_glowmask'):
        src = install.ASSETS / f'textures/entity/{name}{suffix}.png'
        if src.exists() and not (BAK / src.name).exists():
            shutil.copy(src, BAK / src.name)
    inst = Image.open(tex).convert('RGBA')
    bp, np_, ip = b_im.load(), n_im.load(), inst.load()
    changed = 0
    for y in range(b_im.size[1]):
        for x in range(b_im.size[0]):
            if bp[x, y] != np_[x, y]:
                ip[x, y] = np_[x, y]
                changed += 1
    tmp = work / f'{name}.png'
    inst.save(tmp)
    install.variants(tmp, name)
    print(name, 'pixels patched:', changed, 'of', b_im.size[0] * b_im.size[1])


if __name__ == '__main__':
    for n in sys.argv[1:]:
        apply(n)
