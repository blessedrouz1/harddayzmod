"""Custom-camera screenshots: cam.py OUT.png NAME 'px,py,pz:tx,ty,tz' [...more views] (Blockbench px, model space)."""
import base64
import sys
from pathlib import Path

sys.path.insert(0, '/Users/danylokulibaba/Desktop/minecraft-mods-builder/blockbench-military-pixel/scripts')
import bb_bridge as b  # noqa: E402
from PIL import Image

out, name, specs = sys.argv[1], sys.argv[2], sys.argv[3:]
b.open_model(Path(__file__).resolve().parent / f'out/{name}.bbmodel')
views = []
for sp in specs:
    p, t = sp.split(':')
    views.append({'position': [float(v) for v in p.split(',')], 'target': [float(v) for v in t.split(',')]})
res = b.call('screenshot_views', {'views': views, 'width': 600, 'height': 600})
ims = []
for shot in res['shots']:
    data = (shot.get('data_url') or shot.get('base64')).split(',', 1)[-1]
    p = Path(out).with_suffix(f'.{len(ims)}.png')
    p.write_bytes(base64.b64decode(data))
    ims.append(Image.open(p).convert('RGB'))
m = Image.new('RGB', (600 * len(ims), 600))
for i, im in enumerate(ims):
    m.paste(im, (600 * i, 0))
m.save(out)
