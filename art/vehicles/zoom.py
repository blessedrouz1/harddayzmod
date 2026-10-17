"""Close-up shots of the rams: zoom.py NAME ANGLE  (rack angle; ram bones driven like pose.py)"""
import base64, json, math, re, sys
sys.path.insert(0, '/Users/danylokulibaba/Desktop/minecraft-mods-builder/blockbench-military-pixel/scripts')
import bb_bridge as b
from pathlib import Path
here = Path(__file__).resolve().parent
name, ang = sys.argv[1], float(sys.argv[2])
bb = json.loads((here / f'out/{name}.bbmodel').read_text())
b.open_model(here / f'out/{name}.bbmodel')
views = [{'position': [60, 45, 20], 'target': [10, 32, 20]}, {'position': [-60, 45, 20], 'target': [-10, 32, 20]}, {'position': [0, 20, 80], 'target': [0, 32, 30]}]
res = b.call('screenshot_views', {'views': views, 'width': 700, 'height': 600})
from PIL import Image
ims = []
for k, shot in enumerate(res['shots']):
    data = (shot.get('data_url') or shot.get('base64')).split(',', 1)[-1]
    q = here / f'out/zoom_{k}.png'; q.write_bytes(base64.b64decode(data)); ims.append(Image.open(q).convert('RGB'))
m = Image.new('RGB', (700 * len(ims), 600))
for i, im in enumerate(ims): m.paste(im, (700 * i, 0))
m.save(here / f'out/zoom_{name}.png')
