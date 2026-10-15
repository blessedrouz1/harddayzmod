"""Build inventory icons from the models through Blockbench: make_icons.py [group|item ...]  (groups: vehicles drones statics missiles)."""
import base64, io, json, sys
from pathlib import Path
from PIL import Image, ImageFilter
import icon_render as ir
import missilekit as mk
import pixelize as px

HERE = Path(__file__).resolve().parent
ITEMS = mk.ASSETS / 'textures/item'

VEHICLES = {  # item -> model (side view, 64 px)
    'grad_launcher': 'grad', 'ew_station': 'ew', 'himars_launcher': 'himars', 'iskander_launcher': 'iskander', 'kalibr_launcher': 'kalibr',
    'nasams_launcher': 'nasams', 'patriot_launcher': 'patriot', 's400_launcher': 's400', 'sam_launcher': 'sam', 'shahed_launcher': 'shahed_launcher',
    'smerch_launcher': 'smerch', 'tochka_launcher': 'tochka', 'uragan_launcher': 'uragan', 'pickup_mg': 'pickup_mg', 'pickup_sam': 'pickup_sam',
    'radar_truck': 'radar', 'ramp_launcher': 'shahed_ground',
}
DRONES = {  # item -> model (3/4 top view, 48 px)
    'fpv_drone': 'fpv', 'interceptor_drone': 'interceptor', 'recon_drone': 'recon', 'kub_drone': 'kub', 'lancet': 'lancet', 'molniya': 'molniya',
    'wing_recon': 'wing_recon', 'baba_yaga': 'baba_yaga', 'shahed_131': 'geran2', 'shahed_238': 'shahed238', 'shahed_drone': 'shahed136',
    'geran5': 'shahed238', 'gerbera': 'gerbera',
}
STATICS = {'grad_p_launcher': 'grad_p', 'sam_post': 'sam_post', 'sam_manual': 'sam_manual', 'mg_nest': 'mg', 'mortar': 'mortar', 'relay': 'relay'}
MISSILES = {  # item -> (L, D, palette)  (32 px, nose up-right)
    'ballistic_missile': (100, 12, 'iskander'), 'tochka_missile': (90, 10, 'iskander'), 'grad_rocket': (44, 6, 'grad'), 'himars_rocket': (46, 8, 'gmlrs'),
    'javelin_missile': (26, 5, 'javelin'), 'kalibr_missile': (62, 9, 'kalibr'), 'nasams_missile': (50, 8, 'nasams'), 'patriot_missile': (64, 10, 'patriot'),
    's400_missile': (70, 12, 's400'), 'sam_missile': (34, 7, 'sam'), 'smerch_rocket': (60, 9, 'smerch'), 'uragan_rocket': (54, 7, 'uragan'),
    'tos_rocket': (40, 10, 'tos'), 'mortar_shell': (24, 5, 'mortar'), 'banderol': (52, 8, 'kalibr'),
}


def sharpen(big):
    a = big.getchannel('A')
    rgb = big.convert('RGB').filter(ImageFilter.UnsharpMask(radius=max(big.size) / 60, percent=220, threshold=2)).convert('RGBA')
    rgb.putalpha(a)
    return rgb


def missile_model(item, L, D, pal):
    cubes = mk.build(L, D, zn=-L / 2, cx=0, cy=0)
    atlas, h = mk.pack(cubes, mk.PALETTES[pal], 128)
    th = max(16, -(-h // 8) * 8)
    img = Image.new('RGBA', (128, th), (0, 0, 0, 0)); img.paste(atlas, (0, 0))
    els = mk.to_elements(cubes, item)
    buf = io.BytesIO(); img.save(buf, 'PNG')
    bb = {'meta': {'format_version': '4.5', 'model_format': 'geckolib_model', 'box_uv': False}, 'name': item, 'model_identifier': item, 'visible_box': [4, 3, 0],
          'variable_placeholders': '', 'variable_placeholder_buttons': [], 'timeline_setups': [], 'unhandled_root_fields': {}, 'resolution': {'width': 128, 'height': th},
          'elements': els, 'outliner': [{'name': 'body', 'origin': [0, 0, 0], 'rotation': [0, 0, 0], 'color': 0, 'uuid': mk._uid(item, 'g'), 'export': True, 'mirror_uv': False,
                                        'isOpen': True, 'locked': False, 'visibility': True, 'autouv': 0, 'children': [e['uuid'] for e in els]}],
          'textures': [{'path': '', 'name': f'{item}.png', 'folder': 'block', 'namespace': '', 'id': '0', 'width': 128, 'height': th, 'uv_width': 128, 'uv_height': th,
                        'particle': False, 'layers_enabled': False, 'sync_to_project': '', 'render_mode': 'default', 'render_sides': 'auto', 'frame_time': 1,
                        'frame_order_type': 'loop', 'frame_order': '', 'frame_interpolate': False, 'visible': True, 'internal': True, 'saved': False,
                        'uuid': mk._uid(item, 't'), 'source': 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()}]}
    p = ir.OUT / f'{item}.bbmodel'
    p.write_text(json.dumps(bb))
    return p


def render_named(model, view, bbpath=None):
    if bbpath is not None:
        ir.b.open_model(bbpath)
        # reuse render() path by pretending the file lives in out/
        target = HERE / f'out/{model}.bbmodel'
        target.write_text(bbpath.read_text())
        try:
            return ir.render(model, view)
        finally:
            target.unlink()
    return ir.render(model, view)


def make(item, model, view, size, colors, angle=0, **kw):
    big = sharpen(render_named(model, view))
    icon = px.icon(big, size, colors, angle=angle, **kw)
    icon.save(ir.OUT / f'{item}.icon.png')
    return icon


def run(groups):
    done = {}
    if 'vehicles' in groups:
        for it, m in VEHICLES.items():
            done[it] = make(it, m, 'side', 64, 26)
    if 'drones' in groups:
        for it, m in DRONES.items():
            done[it] = make(it, m, 'top34', 48, 22)
    if 'statics' in groups:
        for it, m in STATICS.items():
            done[it] = make(it, m, 'front34', 48, 22)
    if 'missiles' in groups:
        for it, (L, D, pal) in MISSILES.items():
            p = missile_model(it, L, D, pal)
            done[it] = make(it, it, 'side', 32, 18, angle=45, bbpath=None) if False else None
            big = sharpen(render_named(it, 'side', p))
            done[it] = px.icon(big, 32, 18, angle=45)
            done[it].save(ir.OUT / f'{it}.icon.png')
    return done


if __name__ == '__main__':
    run(set(sys.argv[1:]) or {'vehicles', 'drones', 'statics', 'missiles'})


def redo():
    """Re-pixelize every icon from the cached hi-res renders (out/icons/<model>.big.png) after tweaking pixelize.py."""
    for group, size, colors, angle in ((VEHICLES, 64, 26, 0), (DRONES, 48, 22, 0), (STATICS, 48, 22, 0)):
        for it, m in group.items():
            big = sharpen(Image.open(ir.OUT / f'{m}.big.png'))
            px.icon(big, size, colors, angle=angle).save(ir.OUT / f'{it}.icon.png')
    for it in MISSILES:
        big = sharpen(Image.open(ir.OUT / f'{it}.big.png'))
        px.icon(big, 32, 18, angle=45).save(ir.OUT / f'{it}.icon.png')
