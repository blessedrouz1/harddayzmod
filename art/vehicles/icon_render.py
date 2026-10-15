"""Render model icons through the running Blockbench: transparent background, orthographic side view -> PIL image.
usage: icon_render.py NAME [NAME...] -> writes out/icons/NAME.big.png (hi-res cut-out); pixelize.py turns it into an item texture."""
import base64, io, json, sys
from pathlib import Path
sys.path.insert(0, '/Users/danylokulibaba/Desktop/minecraft-mods-builder/blockbench-military-pixel/scripts')
import bb_bridge as b
import missilekit as mk
from PIL import Image

HERE = Path(__file__).resolve().parent
OUT = HERE / 'out/icons'
OUT.mkdir(parents=True, exist_ok=True)

JS = '''
const preview = Preview.selected;
const sc = (typeof scene !== 'undefined' && scene && scene.traverse) ? scene : preview.scene;
const renderer = preview.renderer;
const restores = [];
sc.traverse((o) => { if (o === sc || !o.visible) return; const t = o.type || '';
  if (o.isGridHelper || o.isAxesHelper || t === 'GridHelper' || t === 'AxesHelper' || t === 'LineSegments' || t === 'Line' || t === 'LineLoop') { restores.push([o, o.visible]); o.visible = false; } });
if (typeof Canvas !== 'undefined' && Array.isArray(Canvas.gizmos)) Canvas.gizmos.forEach((g) => { if (g && g.visible) { restores.push([g, g.visible]); g.visible = false; } });
const oldBg = sc.background;
sc.background = null;
renderer.setClearColor(0x000000, 0);
try { preview.setProjectionMode(true); } catch (e) {}
const P = %s;
preview.camera.position.set(P.pos[0], P.pos[1], P.pos[2]);
if (preview.controls) preview.controls.target.set(P.target[0], P.target[1], P.target[2]);
preview.camera.zoom = P.zoom;
preview.camera.updateProjectionMatrix();
if (preview.controls && preview.controls.updateSceneScale) preview.controls.updateSceneScale();
preview.render();
const gl = preview.canvas || renderer.domElement;
const c = document.createElement('canvas'); c.width = gl.width; c.height = gl.height;
c.getContext('2d').drawImage(gl, 0, 0);
const url = c.toDataURL('image/png');
sc.background = oldBg;
restores.forEach(([o, v]) => { o.visible = v; });
try { preview.setProjectionMode(false); } catch (e) {}
preview.render();
return url;
'''


def geo_to_bbmodel(name):
    g = json.loads((mk.ASSETS / f'geo/{name}.geo.json').read_text())['minecraft:geometry'][0]
    tw, th = g['description']['texture_width'], g['description']['texture_height']
    png = Image.open(mk.ASSETS / f'textures/entity/{name}.png').convert('RGBA')
    cubes = []
    for bn in g['bones']:
        for c in bn.get('cubes', []):
            c = dict(c)
            if isinstance(c.get('uv'), list):  # box uv -> per-face
                u, v = c['uv']
                w, h, d = (int(x) for x in c['size'])
                c['uv'] = {'east': {'uv': [u, v + d], 'uv_size': [d, h]}, 'north': {'uv': [u + d, v + d], 'uv_size': [w, h]},
                           'west': {'uv': [u + d + w, v + d], 'uv_size': [d, h]}, 'south': {'uv': [u + 2 * d + w, v + d], 'uv_size': [w, h]},
                           'up': {'uv': [u + d + w, v + d], 'uv_size': [-w, -d]}, 'down': {'uv': [u + d + 2 * w, v], 'uv_size': [-w, d]}}
            cubes.append(c)
    els = mk.to_elements(cubes, name)
    buf = io.BytesIO(); png.save(buf, 'PNG')
    bb = {'meta': {'format_version': '4.5', 'model_format': 'geckolib_model', 'box_uv': False}, 'name': name, 'model_identifier': name,
          'visible_box': [4, 3, 0], 'variable_placeholders': '', 'variable_placeholder_buttons': [], 'timeline_setups': [], 'unhandled_root_fields': {},
          'resolution': {'width': tw, 'height': th}, 'elements': els,
          'outliner': [{'name': 'body', 'origin': [0, 0, 0], 'rotation': [0, 0, 0], 'color': 0, 'uuid': mk._uid(name, 'g'), 'export': True, 'mirror_uv': False,
                        'isOpen': True, 'locked': False, 'visibility': True, 'autouv': 0, 'children': [e['uuid'] for e in els]}],
          'textures': [{'path': '', 'name': f'{name}.png', 'folder': 'block', 'namespace': '', 'id': '0', 'width': png.width, 'height': png.height, 'uv_width': tw,
                        'uv_height': th, 'particle': False, 'layers_enabled': False, 'sync_to_project': '', 'render_mode': 'default', 'render_sides': 'auto',
                        'frame_time': 1, 'frame_order_type': 'loop', 'frame_order': '', 'frame_interpolate': False, 'visible': True, 'internal': True,
                        'saved': False, 'uuid': mk._uid(name, 't'), 'source': 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()}]}
    p = OUT / f'{name}.bbmodel'
    p.write_text(json.dumps(bb))
    return p


def render(name, view='side', zoom=None):
    p = HERE / f'out/{name}.bbmodel'
    if not p.exists():
        p = geo_to_bbmodel(name)
    b.open_model(p)
    box = b.call('execute_script', {'code': 'const bb=Cube.all; let lo=[1e9,1e9,1e9],hi=[-1e9,-1e9,-1e9]; Cube.all.forEach(c=>{for(let i=0;i<3;i++){lo[i]=Math.min(lo[i],c.from[i],c.to[i]);hi[i]=Math.max(hi[i],c.from[i],c.to[i]);}}); return [lo,hi];'})
    lo, hi = box
    cen = [(lo[i] + hi[i]) / 2 for i in range(3)]
    size = max(hi[i] - lo[i] for i in range(3))
    views = {'side': (1.0, 0.55, -0.45), 'front34': (0.7, 0.6, -1.0), 'top34': (0.8, 1.0, -0.5), 'upright': (0.9, 0.45, -0.6),
             'topz': (0.35, 1.0, 0.02), 'south34': (0.5, 0.45, 1.0), 'front': (0.3, 0.35, -1.0)}
    v = views[view]
    d = 400
    n = (v[0] ** 2 + v[1] ** 2 + v[2] ** 2) ** 0.5
    pos = [cen[i] + v[i] / n * d for i in range(3)]
    def shot(z):
        url = b.call('execute_script', {'code': JS % json.dumps({'pos': pos, 'target': cen, 'zoom': z})})
        return Image.open(io.BytesIO(base64.b64decode(url.split(',', 1)[1]))).convert('RGBA')
    z = zoom or 0.1
    im = shot(z)
    if not zoom:  # fit: one calibration pass, the silhouette should fill ~85% of the shorter canvas side
        bx = im.getchannel('A').point(lambda a: 255 if a > 20 else 0).getbbox()
        if bx:
            z = z * 0.85 * min(im.size) / max(bx[2] - bx[0], bx[3] - bx[1])
            im = shot(z)
    bx = im.getchannel('A').point(lambda a: 255 if a > 20 else 0).getbbox()
    im = im.crop(bx) if bx else im
    im.save(OUT / f'{name}.big.png')
    return im


if __name__ == '__main__':
    for n in sys.argv[1:]:
        im = render(n)
        print(n, im.size, im.getbbox())
