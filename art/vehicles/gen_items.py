"""Small hand-held items as tiny 3D models (rendered to inventory icons through icon_render): it_<name>.json specs, 16 px = 1 block.
usage: python3 gen_items.py -> writes it_*.json and builds out/it_*.bbmodel"""
import json, subprocess, sys
from pathlib import Path
from gen import box, spec, PAINT2_DARK
from gen2 import cyl

HERE = Path(__file__).resolve().parent
SK = '/Users/danylokulibaba/Desktop/minecraft-mods-builder/blockbench-military-pixel/scripts/build_vehicle.py'
BR = ['#6B4F12', '#A87C1E', '#D9B43A', '#F6DC78']   # brass
BLACK = ['#0E0F13', '#181A20', '#262932', '#3A3E4A']


def item(name, parts, paint='olive', paint2=None, metal=None, seed=1):
    sp = spec(f'it_{name}', f'inventory icon model: {name}', paint, paint2 or PAINT2_DARK, [{'name': 'body', 'pivot': [0, 0, 0]}], parts, seed, 0.1)
    if metal:
        sp['metal'] = metal
    sp['panel_step'] = 0
    return sp


def specs():
    out = {}
    # grenades: upright cylinder drawn along Z, tilted by the render view
    out['frag_grenade'] = item('frag_grenade', cyl('body', -5, -5, -6, 6, 10, 'paint') + [
        box('ring_a', [-5.5, -5.5, -3], [5.5, 5.5, -2], 'paint2', panels=0), box('ring_b', [-5.5, -5.5, 0], [5.5, 5.5, 1], 'paint2', panels=0),
        box('ring_c', [-5.5, -5.5, 3], [5.5, 5.5, 4], 'paint2', panels=0),
        box('neck', [-2, -2, 6], [2, 2, 8], 'metal', panels=0), box('fuse', [-1.5, -1.5, 8], [1.5, 1.5, 10], 'paint2', panels=0),
        box('lever', [1, -4, 5], [2, 4, 8], 'metal', panels=0), box('pin', [-3, 3, 7], [-2, 6, 8], 'metal', panels=0), box('pin_ring', [-4, 5, 7], [-1, 6, 8], 'metal', panels=0)],
        'green')
    out['smoke_grenade'] = item('smoke_grenade', cyl('body', -4, -4, -7, 5, 8, 'paint') + [
        box('band', [-4.5, -4.5, -2], [4.5, 4.5, 0], 'amber', panels=0), box('cap', [-3, -3, 5], [3, 3, 7], 'metal', panels=0),
        box('top', [-4, -4, -9], [4, 4, -7], 'metal', panels=0), box('lever', [1, -5, 4], [2, 5, 7], 'metal', panels=0), box('pin', [-3, 3, 6], [-2, 6, 7], 'metal', panels=0),
        box('vent', [-1, -1, -10], [1, 1, -9], 'dark', panels=0)], 'grey')
    can = lambda dent, col: [box('can', [-6, -7, -3], [6, 7, 3], col, panels=0, decals=[
        {'face': 'north', 'x': 1, 'y': 1, 'w': 10, 'h': 1, 'mat': 'rib'}, {'face': 'north', 'x': 1, 'y': 12, 'w': 10, 'h': 1, 'mat': 'rib'},
        {'face': 'north', 'x': 1, 'y': 3, 'w': 1, 'h': 8, 'mat': 'rib'}, {'face': 'north', 'x': 10, 'y': 3, 'w': 1, 'h': 8, 'mat': 'rib'},
        {'face': 'east', 'x': 1, 'y': 6, 'w': 4, 'h': 2, 'mat': 'rib'}]),
        box('handle_a', [-6, 7, -1], [-2, 9, 1], 'metal', panels=0), box('handle_b', [-2, 7, -1], [2, 9, 1], 'metal', panels=0),
        box('handle_c', [2, 7, -1], [6, 9, 1], 'metal', panels=0), box('spout', [3, 7, 1], [6, 10, 3], 'metal', panels=0)] + ([] if dent else [
        box('cap', [3, 10, 1], [6, 12, 3], 'amber', panels=0)])
    out['jerrycan'] = item('jerrycan', can(False, 'paint'), 'olive')
    out['empty_jerrycan'] = item('empty_jerrycan', can(True, 'paint'), 'grey')
    out['medkit'] = item('medkit', [
        box('case', [-8, -5, -3], [8, 5, 3], 'paint', panels=0), box('lid', [-8, 5, -3], [8, 7, 3], 'paint2', panels=0),
        box('handle', [-3, 7, -1], [3, 8, 1], 'metal', panels=0), box('latch_a', [-5, 3, 3], [-3, 6, 4], 'metal', panels=0), box('latch_b', [3, 3, 3], [5, 6, 4], 'metal', panels=0),
        box('plate', [-3, -2, 3], [3, 4, 4], 'headlight', panels=0), box('cross_h', [-2, 0, 4], [2, 2, 5], 'red', panels=0), box('cross_v', [-1, -1, 4], [1, 3, 5], 'red', panels=0)], 'olive')
    out['bandage'] = item('bandage', cyl('roll', -5, -5, -2, 2, 10, 'headlight') + [
        box('core', [-2, -2, -2.5], [2, 2, 2.5], 'dark', panels=0), box('tail', [-4, -6, -2], [4, -5, 2], 'headlight', panels=0), box('tail2', [2, -9, -2], [5, -6, 2], 'headlight', panels=0),
        box('band_a', [-5.5, -5.5, -1], [5.5, 5.5, 0], 'red', panels=0)], 'grey')
    out['tourniquet'] = item('tourniquet', [
        box('strap_a', [-8, -1, -2], [8, 1, 2], 'paint2', panels=0), box('strap_b', [-8, 1, -2], [-6, 7, 2], 'paint2', panels=0), box('strap_c', [6, 1, -2], [8, 7, 2], 'paint2', panels=0),
        box('strap_d', [-8, 7, -2], [8, 9, 2], 'paint2', panels=0),
        box('buckle', [-2, 7, -3], [2, 9, 3], 'metal', panels=0), box('rod', [-1, -3, -1], [1, 11, 1], 'amber', panels=0), box('rod_top', [-2, 11, -2], [2, 13, 2], 'amber', panels=0),
        box('clip', [3, -2, -3], [6, 0, 3], 'metal', panels=0)], 'olive')
    out['painkiller'] = item('painkiller', cyl('bottle', -4, -4, -6, 4, 8, 'amber') + [
        box('cap', [-3.5, -3.5, 4], [3.5, 3.5, 6], 'headlight', panels=0), box('label', [-4.5, -2, -4], [4.5, 2, 2], 'headlight', panels=0,
                                                                              decals=[{'face': 'east', 'x': 1, 'y': 1, 'w': 4, 'h': 2, 'mat': 'red'}]),
        box('seal', [-2, -2, 6], [2, 2, 7], 'metal', panels=0)], 'olive')
    out['field_shovel'] = item('field_shovel', [
        box('handle', [-1, -1, -2], [1, 1, 16], 'brown', panels=0), box('grip', [-2, -2, 14], [2, 2, 18], 'rubber', panels=0),
        box('collar', [-1.5, -1.5, -4], [1.5, 1.5, -2], 'metal', panels=0), box('blade', [-4, -0.5, -12], [4, 0.5, -4], 'metal', panels=0),
        box('blade_tip', [-2, -0.5, -14], [2, 0.5, -12], 'metal', panels=0), box('rib', [-0.5, 0.5, -10], [0.5, 1.5, -4], 'metal', panels=0)], 'olive')
    out['designator'] = item('designator', [
        box('body', [-6, -3, -6], [6, 3, 6], 'paint2', panels=0), box('lens_a', [-5, -3, 6], [-1, 1, 10], 'metal', panels=0), box('lens_b', [1, -3, 6], [5, 1, 10], 'metal', panels=0),
        box('glass_a', [-4.5, -2.5, 10], [-1.5, 0.5, 11], 'red', panels=0), box('glass_b', [1.5, -2.5, 10], [4.5, 0.5, 11], 'glass', panels=0),
        box('eyepiece', [-5, 3, -4], [5, 5, -2], 'rubber', panels=0), box('handle', [-2, -7, -3], [2, -3, 3], 'rubber', panels=0), box('button', [-1, -3, -4], [1, -2, -2], 'red', panels=0),
        box('antenna', [4, 3, -6], [5, 8, -5], 'metal', panels=0)], 'olive', BLACK)
    out['operator_terminal'] = item('operator_terminal', [
        box('body', [-8, -1, -5], [8, 2, 5], 'paint2', panels=0), box('screen', [-3, 2, -4], [3, 3, 2], 'glass', panels=0),
        box('grip_l', [-9, -2, -3], [-7, 1, 4], 'rubber', panels=0), box('grip_r', [7, -2, -3], [9, 1, 4], 'rubber', panels=0),
        box('stick_l', [-6, 2, 2], [-4, 5, 4], 'metal', panels=0), box('stick_r', [4, 2, 2], [6, 5, 4], 'metal', panels=0),
        box('btn_a', [1, 2, 3], [2, 3, 4], 'red', panels=0), box('btn_b', [3, 2, 0], [4, 3, 1], 'amber', panels=0),
        box('ant_l', [-7, 2, -5], [-6, 10, -4], 'metal', panels=0), box('ant_r', [6, 2, -5], [7, 10, -4], 'metal', panels=0)], 'olive', BLACK)
    out['supply_tablet'] = item('supply_tablet', [
        box('body', [-6, -1, -8], [6, 1, 8], 'paint2', panels=0), box('screen', [-5, 1, -7], [5, 2, 7], 'dark', panels=0,
                                                                    decals=[{'face': 'up', 'x': 1, 'y': 1, 'w': 8, 'h': 1, 'mat': 'green'}, {'face': 'up', 'x': 1, 'y': 4, 'w': 6, 'h': 1, 'mat': 'green'},
                                                                            {'face': 'up', 'x': 1, 'y': 7, 'w': 7, 'h': 1, 'mat': 'green'}, {'face': 'up', 'x': 1, 'y': 10, 'w': 5, 'h': 1, 'mat': 'amber'}]),
        box('badge', [3, 1, 5], [5, 2, 7], 'amber', panels=0), box('stand', [-3, -2, -5], [3, -1, 5], 'metal', panels=0)], 'olive', BLACK)
    out['mine_ap'] = item('mine_ap', cyl('body', -5, -5, -2, 2, 10, 'paint') + [
        box('top', [-3, -3, 2], [3, 3, 3], 'paint2', panels=0), box('fuse', [-1, -1, 3], [1, 1, 5], 'metal', panels=0),
        box('prong_a', [-0.5, 1, 3], [0.5, 4, 4], 'metal', panels=0), box('prong_b', [-4, -0.5, 3], [-1, 0.5, 4], 'metal', panels=0), box('prong_c', [1, -0.5, 3], [4, 0.5, 4], 'metal', panels=0),
        box('band', [-5.5, -5.5, -1], [5.5, 5.5, 0], 'paint2', panels=0)], 'olive')
    out['mine_at'] = item('mine_at', cyl('body', -7, -7, -2, 3, 14, 'paint') + [
        box('plate', [-5, -5, 3], [5, 5, 4], 'red', panels=0), box('fuse', [-2, -2, 4], [2, 2, 6], 'metal', panels=0),
        box('handle', [-6, -1, 4], [-3, 1, 6], 'metal', panels=0), box('handle2', [3, -1, 4], [6, 1, 6], 'metal', panels=0), box('band', [-7.5, -7.5, 0], [7.5, 7.5, 1], 'paint2', panels=0)], 'olive')
    belt = [box('belt_link', [-7, -0.5, -1], [7, 0.5, 1], 'metal', panels=0)]
    for i in range(5):
        belt.append(box(f'bullet{i}', [-7 + i * 3, -2, -3], [-5 + i * 3, 2, 5], 'amber', panels=0))
        belt.append(box(f'tip{i}', [-7 + i * 3, -1.5, -5], [-5 + i * 3, 1.5, -3], 'brown', panels=0))
    out['mg_belt'] = item('mg_belt', belt, 'olive')
    shells = []
    for i, x in enumerate((-5, 0, 5)):
        shells += cyl(f's{i}', x - 2, -2, -6, 6, 4, 'amber') + [box(f'p{i}', [x - 1.5, -1.5, -9], [x + 1.5, 1.5, -6], 'brown', panels=0), box(f'r{i}', [x - 2.5, -2.5, 5], [x + 2.5, 2.5, 6], 'metal', panels=0)]
    out['cannon_shells'] = item('cannon_shells', shells, 'olive')
    return out


if __name__ == '__main__':
    for n, sp in specs().items():
        (HERE / f'it_{n}.json').write_text(json.dumps(sp, indent=1))
        r = subprocess.run(['python3', SK, str(HERE / f'it_{n}.json'), '-o', str(HERE / 'out')], capture_output=True, text=True)
        print(n, 'OK' if 'spec error' not in r.stdout + r.stderr else (r.stdout + r.stderr).strip().splitlines()[0])
