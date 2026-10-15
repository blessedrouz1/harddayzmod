"""Open a mod geo.json + texture in running Blockbench (new tab) and save screenshots.
usage: python3 bbgeo.py GEO PNG OUT_DIR [views...]"""
import json
import sys
from pathlib import Path

sys.path.insert(0, '/Users/danylokulibaba/Desktop/minecraft-mods-builder/blockbench-military-pixel/scripts')
import bb_bridge as b  # noqa: E402

geo, png, out = sys.argv[1:4]
views = sys.argv[4:] or ['front_right', 'back_left', 'left', 'front', 'top']
code = '''
const content = %s;
newProject(Formats.bedrock);
Codecs.bedrock.parse(content, %s);
const t = new Texture().fromPath(%s).add(false);
Cube.all.forEach(c => { for (let f in c.faces) c.faces[f].texture = t.uuid; });
Canvas.updateAll();
return Cube.all.length;
''' % (Path(geo).read_text(), json.dumps(str(Path(geo).resolve())), json.dumps(str(Path(png).resolve())))
print('cubes', b.call('execute_script', {'code': code}))
print(json.dumps(b.shots(out, views, 700), indent=0))
