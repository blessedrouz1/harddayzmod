"""Preview standalone missiles in Blockbench: prevm.py OUT.png name [name...] (reads assets geo/<name>.geo.json + texture)."""
import base64, io, json, sys
sys.path.insert(0, '/Users/danylokulibaba/Desktop/minecraft-mods-builder/blockbench-military-pixel/scripts')
import bb_bridge as b
import missilekit as mk
from PIL import Image
from pathlib import Path

out, names = sys.argv[1], sys.argv[2:]
ims = []
for n in names:
    g = json.loads((mk.ASSETS / f'geo/{n}.geo.json').read_text())['minecraft:geometry'][0]
    cubes = [c for bn in g['bones'] for c in bn.get('cubes', [])]
    png = Image.open(mk.ASSETS / f'textures/entity/{n}.png').convert('RGBA')
    tw, th = g['description']['texture_width'], g['description']['texture_height']
    els = mk.to_elements(cubes, n)
    buf = io.BytesIO(); png.save(buf, 'PNG')
    bb = {'meta': {'format_version': '4.5', 'model_format': 'geckolib_model', 'box_uv': False}, 'name': n, 'model_identifier': n,
          'visible_box': [4, 3, 0], 'variable_placeholders': '', 'variable_placeholder_buttons': [], 'timeline_setups': [], 'unhandled_root_fields': {},
          'resolution': {'width': tw, 'height': th}, 'elements': els,
          'outliner': [{'name': 'body', 'origin': [0, 0, 0], 'rotation': [0, 0, 0], 'color': 0, 'uuid': mk._uid(n, 'g'), 'export': True, 'mirror_uv': False,
                        'isOpen': True, 'locked': False, 'visibility': True, 'autouv': 0, 'children': [e['uuid'] for e in els]}],
          'textures': [{'path': '', 'name': f'{n}.png', 'folder': 'block', 'namespace': '', 'id': '0', 'width': tw, 'height': th, 'uv_width': tw, 'uv_height': th,
                        'particle': False, 'layers_enabled': False, 'sync_to_project': '', 'render_mode': 'default', 'render_sides': 'auto', 'frame_time': 1,
                        'frame_order_type': 'loop', 'frame_order': '', 'frame_interpolate': False, 'visible': True, 'internal': True, 'saved': False,
                        'uuid': mk._uid(n, 't'), 'source': 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()}]}
    p = Path(out).parent / f'{n}_prev.bbmodel'
    p.write_text(json.dumps(bb))
    b.open_model(p)
    res = b.call('screenshot_views', {'views': [{'position': [60, 30, -60], 'target': [0, 0, 0]}, {'position': [0, 60, 0.1], 'target': [0, 0, 0]}], 'width': 500, 'height': 500})
    for k, shot in enumerate(res['shots']):
        data = (shot.get('data_url') or shot.get('base64')).split(',', 1)[-1]
        q = Path(out).parent / f'{n}_{k}.png'; q.write_bytes(base64.b64decode(data)); ims.append(Image.open(q).convert('RGB'))
m = Image.new('RGB', (500 * min(4, len(ims)), 500 * ((len(ims) + 3) // 4)))
for i, im in enumerate(ims): m.paste(im, ((i % 4) * 500, (i // 4) * 500))
m.save(out)
