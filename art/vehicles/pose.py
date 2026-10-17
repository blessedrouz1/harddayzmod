"""Pose preview: pose.py NAME ANGLE [rack|erector]  -> out/pose_NAME.png. Rotates the swinging bone and drives ram bones like LauncherRenderer."""
import base64, json, math, re, sys
sys.path.insert(0, '/Users/danylokulibaba/Desktop/minecraft-mods-builder/blockbench-military-pixel/scripts')
import bb_bridge as b
from pathlib import Path
here = Path(__file__).resolve().parent
name, ang = sys.argv[1], float(sys.argv[2])
swing = sys.argv[3] if len(sys.argv) > 3 else 'rack'
bb = json.loads((here / f'out/{name}.bbmodel').read_text())
els = {e['uuid']: e for e in bb['elements']}
groups = {}
def walk(n):
    if isinstance(n, dict):
        groups[n['name']] = n
        for c in n.get('children', []): walk(c)
for g in bb['outliner']: walk(g)
a = math.radians(ang)
hinge = groups[swing]['origin']
groups[swing]['rotation'] = [ang, 0, 0]
for gn, g in list(groups.items()):
    if not re.fullmatch(r'e?ram\d+', gn) or (gn.startswith('eram') != (swing == 'erector')):
        continue
    foot = g['origin']
    pin = groups[gn + '_mount']['origin']
    def kids(n): return [els[u] for u in groups[n]['children'] if isinstance(u, str) and u in els]
    len0 = foot[2] - pin[2]                      # rest distance barrel pivot -> pin (HydraulicLift.restLength)
    dy0, dz0 = pin[1] - hinge[1], pin[2] - hinge[2]
    vy = hinge[1] + dy0 * math.cos(a) - dz0 * math.sin(a) - foot[1]
    vz = hinge[2] + dy0 * math.sin(a) + dz0 * math.cos(a) - foot[2]
    d = math.degrees(math.atan2(vy, -vz)); ext = math.hypot(vy, vz) - len0
    print(gn, 'delta', round(d, 1), 'extend', round(ext, 1), 'rest', round(len0, 1))
    g['rotation'] = [d, 0, 0]
    # like HydraulicLift: stage slides by half the stroke, the rod (child of the stage) by half again
    for e in kids(gn + '_stage'):
        e['from'][2] -= ext / 2; e['to'][2] -= ext / 2
    for e in kids(gn + '_rod'):
        e['from'][2] -= ext; e['to'][2] -= ext
p = here / f'out/pose_{name}.bbmodel'
p.write_text(json.dumps(bb))
b.open_model(p)
import os
if os.environ.get('VIEWS'):
    views = [{'position': [float(v) for v in a.split(':')[0].split(',')], 'target': [float(v) for v in a.split(':')[1].split(',')]} for a in os.environ['VIEWS'].split(';')]
elif os.environ.get('ZOOM'):
    views = [{'position': [150, 45, 36], 'target': [10, 45, 36]}, {'position': [40, 70, 110], 'target': [10, 42, 38]}]
else:
    views = [{'position': [140, 60, 40], 'target': [0, 40, 10]}, {'position': [-120, 90, -90], 'target': [0, 40, 10]}]
res = b.call('screenshot_views', {'views': views, 'width': 700, 'height': 600})
from PIL import Image
ims = []
for k, shot in enumerate(res['shots']):
    data = (shot.get('data_url') or shot.get('base64')).split(',', 1)[-1]
    q = here / f'out/pose_{name}_{k}.png'; q.write_bytes(base64.b64decode(data)); ims.append(Image.open(q).convert('RGB'))
m = Image.new('RGB', (700 * len(ims), 600))
for i, im in enumerate(ims): m.paste(im, (700 * i, 0))
m.save(here / f'out/pose_{name}.png')
