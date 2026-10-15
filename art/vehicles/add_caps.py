"""Add hatch-cap bones to SAM and Kalibr launcher geo.json files.

The renderer animates any bone whose name ends with '_cap': on flash>0 they fly upward.
We add these as separate bones (children of 'rack') that duplicate the existing
hatch-face cubes so they can animate independently.

Run: python3 add_caps.py
"""
import json
from pathlib import Path

GEO = Path(__file__).resolve().parent.parent.parent / 'src/main/resources/assets/harddayz/geo'


def add_bone(bones, name, parent, pivot, cubes):
    """Insert a new bone after its parent, if not already present."""
    if any(b['name'] == name for b in bones):
        return  # already added
    bone = {'name': name, 'parent': parent, 'pivot': pivot}
    if cubes:
        bone['cubes'] = cubes
    # Insert right after the parent
    for i, b in enumerate(bones):
        if b['name'] == parent:
            bones.insert(i + 1, bone)
            return
    bones.append(bone)


# ---------------------------------------------------------------------------
# S-400: 4 TPK tube caps at the front (north face z=-24..−25 in model coords)
# gen.py: tpk_cap{i} [x0+1, y0+1, -25]..[x0+10, y0+9, -24] bone='rack'
# In Bedrock geo (origin = model bottom-left): x is mirrored (model +X → geo -X)
# The gen.py→heal.py pipeline mirrors X: geo x = -model_x_max
# tpk offsets from gen.py: (-12,28),(1,28),(-12,41),(1,41)
# After mirror: tpk{i} origin=(−x0−11, y0, −24), size=(11,11,84)
# cap: origin=(−x0−10, y0+1, −25), size=(9,8,1) — single-cube front face
# pivot matches rack pivot so the cap rotates/translates in rack space.

def patch_s400():
    path = GEO / 's400.geo.json'
    geo = json.loads(path.read_text())
    bones = geo['minecraft:geometry'][0]['bones']

    # tpk positions in gen.py (model space): (x0,y0) pairs
    tpks = [(-12, 28), (1, 28), (-12, 41), (1, 41)]
    for i, (x0, y0) in enumerate(tpks):
        # geo origin (mirrored): geo_x = -(x0+10), geo_y = y0+1, geo_z = -25
        ox = -(x0 + 10)
        oy = y0 + 1
        oz = -25
        sz = [9, 8, 1]
        pivot = [ox + sz[0] / 2, oy + sz[1] / 2, oz]
        cubes = [{'origin': [ox, oy, oz], 'size': sz, 'uv': [0, 0]}]
        add_bone(bones, f'tpk_cap{i}', 'rack', pivot, cubes)

    path.write_text(json.dumps(geo, indent=1))
    print('patched s400')


# ---------------------------------------------------------------------------
# Patriot: 4 canister caps — front face at z=-20 (north end)
# gen.py: canister{i} [x0, y0, -20]..[x0+14, y0+13, 56] bone='rack'
# (x0,y0): (-15,30),(0.5,30),(-15,44.5),(0.5,44.5)
# Cap = thin slab at z=-20: [x0, y0, -21]..[x0+14, y0+13, -20]
# After mirror: geo_x = -(x0+14)

def patch_patriot():
    path = GEO / 'patriot.geo.json'
    geo = json.loads(path.read_text())
    bones = geo['minecraft:geometry'][0]['bones']

    canisters = [(-15, 30), (0.5, 30), (-15, 44.5), (0.5, 44.5)]
    for i, (x0, y0) in enumerate(canisters):
        ox = -(x0 + 14)
        oy = y0
        oz = -21
        sz = [14, 13, 1]
        pivot = [ox + sz[0] / 2, oy + sz[1] / 2, oz]
        cubes = [{'origin': [ox, oy, oz], 'size': sz, 'uv': [0, 0]}]
        add_bone(bones, f'canister_cap{i}', 'rack', pivot, cubes)

    path.write_text(json.dumps(geo, indent=1))
    print('patched patriot')


# ---------------------------------------------------------------------------
# NASAMS: 6 AMRAAM canister caps (2 rows × 3) — front face at z=-30
# gen.py: canisters box [-13,30,-30]..[13,48,20] bone='rack' (one big box, 6 hatches)
# Individual caps: 3 cols × 2 rows
# col x: -13, -5, 3  row y: 30, 39  size per cell: 8×9×1

def patch_nasams():
    path = GEO / 'nasams.geo.json'
    geo = json.loads(path.read_text())
    bones = geo['minecraft:geometry'][0]['bones']

    cols_x = [-13, -5, 3]  # model x left edge of each cell
    rows_y = [30, 39]       # model y bottom edge
    cw, ch = 8, 9
    for row, y0 in enumerate(rows_y):
        for col, x0 in enumerate(cols_x):
            idx = row * 3 + col
            ox = -(x0 + cw)  # mirror
            oy = y0
            oz = -31
            sz = [cw, ch, 1]
            pivot = [ox + sz[0] / 2, oy + sz[1] / 2, oz]
            cubes = [{'origin': [ox, oy, oz], 'size': sz, 'uv': [0, 0]}]
            add_bone(bones, f'canister_cap{idx}', 'rack', pivot, cubes)

    path.write_text(json.dumps(geo, indent=1))
    print('patched nasams')


# ---------------------------------------------------------------------------
# Kalibr: 4 container hatch caps at the top of each tube
# gen.py coords (from LauncherGeometry.muzzle): i%2==0 → x=−9, else x=9; y=43+i//2*18; z=−28
# The container rack has all tube geometry baked in.
# Add thin cap bones at the top opening of each tube (facing up, z=-28 end = front of rack)
# Container front end z=-28 in model coords → geo z=-28
# tube x positions model: i%2==0→-9±5, else 9±5 → geo: mirror

def patch_kalibr():
    path = GEO / 'kalibr.geo.json'
    geo = json.loads(path.read_text())
    bones = geo['minecraft:geometry'][0]['bones']

    # 4 tubes: 2 pairs side by side, 2 rows
    # From LauncherGeometry: i%2==0 → x=-9, else x=9; y starts 43+i//2*18
    # Each tube approx 10w × 10w; cap on top (y face)
    positions = [
        (-14, 43, -28),   # tube 0: left-front
        (4,   43, -28),   # tube 1: right-front
        (-14, 61, -28),   # tube 2: left-rear row
        (4,   61, -28),   # tube 3: right-rear row
    ]
    # Cap = thin slab on front face (z direction): size 10×10×1
    for i, (x0, y0, z0) in enumerate(positions):
        ox = -x0 - 10   # mirror
        oy = y0
        oz = z0 - 1
        sz = [10, 10, 1]
        pivot = [ox + sz[0] / 2, oy + sz[1] / 2, oz]
        cubes = [{'origin': [ox, oy, oz], 'size': sz, 'uv': [0, 0]}]
        add_bone(bones, f'container_cap{i}', 'rack', pivot, cubes)

    path.write_text(json.dumps(geo, indent=1))
    print('patched kalibr')


if __name__ == '__main__':
    patch_s400()
    patch_patriot()
    patch_nasams()
    patch_kalibr()
    print('done')
