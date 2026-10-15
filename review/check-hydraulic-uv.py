"""All hydraulic faces must sample opaque, in-bounds texels in every damage variant."""
import json
import math
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/harddayz'
faces = pixels = models = 0
for path in (ROOT / 'geo').glob('*.geo.json'):
    bones = json.loads(path.read_text())['minecraft:geometry'][0]['bones']
    hydraulic = [c for b in bones if b['name'].startswith(('ram0', 'eram0')) for c in b.get('cubes', [])]
    if not hydraulic:
        continue
    models += 1
    for suffix in ('', '_damaged', '_burnt'):
        texture = ROOT / 'textures/entity' / (path.name.removesuffix('.geo.json') + suffix + '.png')
        image = Image.open(texture).convert('RGBA')
        for cube in hydraulic:
            uv = cube['uv']
            if isinstance(uv, list):
                u, v = uv
                w, h, d = map(math.floor, cube['size'])
                regions = [(u,v+d,d,h), (u+d,v+d,w,h), (u+d+w,v+d,d,h),
                           (u+2*d+w,v+d,w,h), (u+d,v,w,d), (u+d+w,v,w,d)]
            else:
                assert len(uv) == 6, (path, 'missing faces')
                regions = [(*face['uv'], *face['uv_size']) for face in uv.values()]
            for u, v, w, h in regions:
                assert w and h, (path, 'empty UV')
                x0, x1 = sorted((u, u+w))
                y0, y1 = sorted((v, v+h))
                assert 0 <= x0 < x1 <= image.width and 0 <= y0 < y1 <= image.height, (texture, 'UV outside atlas')
                for x in range(math.floor(x0), math.ceil(x1)):
                    for y in range(math.floor(y0), math.ceil(y1)):
                        assert image.getpixel((x, y))[3] == 255, (texture, x, y, 'transparent hydraulic texel')
                        pixels += 1
                faces += 1
print(f'PASS {models} launcher models, {faces} faces across 36 texture variants, {pixels} opaque texel samples')
