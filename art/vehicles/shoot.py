"""Open built bbmodels in Blockbench, shoot views, glue into one sheet. usage: shoot.py OUT.png view name..."""
import sys
sys.path.insert(0, '/Users/danylokulibaba/Desktop/minecraft-mods-builder/blockbench-military-pixel/scripts')
import bb_bridge as b  # noqa: E402
from pathlib import Path
from PIL import Image

out, view, names = sys.argv[1], sys.argv[2], sys.argv[3:]
here = Path(__file__).resolve().parent
ims = []
for n in names:
    b.open_model(here / f'out/{n}.bbmodel')
    f = b.shots(here / f'out/shots/{n}', [view], 700)[0]['file']
    ims.append(Image.open(f).convert('RGB'))
w, h = ims[0].size
cols = min(4, len(ims))
rows = (len(ims) + cols - 1) // cols
m = Image.new('RGB', (w * cols, h * rows), (20, 20, 20))
for i, im in enumerate(ims):
    m.paste(im, ((i % cols) * w, (i // cols) * h))
m = m.resize((m.width * 1400 // m.width, m.height * 1400 // m.width))
m.save(out)
print(out, m.size)
