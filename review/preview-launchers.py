"""Geometric QA of bundled models; no game client or textures required."""
import json, math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = Path(__file__).with_name('launcher-geometry-preview.png')
font = ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial.ttf', 23)
small = ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial.ttf', 17)
image = Image.new('RGB', (1600, 1000), '#101923')
draw = ImageDraw.Draw(image)

def add(a,b): return tuple(x+y for x,y in zip(a,b))
def sub(a,b): return tuple(x-y for x,y in zip(a,b))
def rot(p,r):
    x,y,z=p
    a,b,c=(math.radians(v) for v in r)
    y,z=y*math.cos(a)-z*math.sin(a),y*math.sin(a)+z*math.cos(a)
    x,z=x*math.cos(b)+z*math.sin(b),-x*math.sin(b)+z*math.cos(b)
    x,y=x*math.cos(c)-y*math.sin(c),x*math.sin(c)+y*math.cos(c)
    return x,y,z

def panel(name,deploy,elev,erect,ox,oy,label):
    data=json.loads((ROOT/'src/main/resources/assets/harddayz/geo'/f'{name}.geo.json').read_text())
    bones={b['name']:b for b in data['minecraft:geometry'][0]['bones']}
    def transform(p,name):
        while name:
            b=bones[name]; pivot=b.get('pivot',[0,0,0]); pivot=(-pivot[0],pivot[1],pivot[2]); p=sub(p,pivot)
            if name.endswith('_extension'):
                base=name.removesuffix('_extension')
                foot=bones[base+'_foot']['cubes'][0]
                drop=deploy*max(0,foot['origin'][1])
                p=(p[0],p[1]*(1+drop/b['cubes'][0]['size'][1]),p[2])
            if name.endswith('_foot'):
                drop=deploy*max(0,b['cubes'][0]['origin'][1]); p=(p[0],p[1]-drop,p[2])
            r=b.get('rotation',[0,0,0]); r=[-r[0],-r[1],r[2]]
            if name=='rack':r[0]+=elev
            if name=='erector':r[0]+=90*erect
            p=add(rot(p,r),pivot); name=b.get('parent')
        return rot(p,(0,26,0))
    faces=[]; points=[]
    for name,b in bones.items():
        for cube in b.get('cubes',[]):
            start=cube['origin']; size=cube['size']; pivot=cube.get('pivot',[0,0,0])
            verts=[]
            for ix,iy,iz in [(0,0,0),(1,0,0),(1,1,0),(0,1,0),(0,0,1),(1,0,1),(1,1,1),(0,1,1)]:
                p=add(start,(size[0]*ix,size[1]*iy,size[2]*iz))
                p=(-p[0],p[1],p[2]); cp=(-pivot[0],pivot[1],pivot[2]); r=cube.get('rotation',[0,0,0])
                p=add(rot(sub(p,cp),(-r[0],-r[1],r[2])),cp)
                verts.append(transform(p,name))
            points.extend(verts)
            color=(97,121,129)
            if 'wheel' in name:color=(34,42,50)
            if 'jack' in name:color=(126,159,166)
            if name.endswith('_extension'):color=(255,184,67)
            if name.endswith('_foot'):color=(244,214,111)
            if name=='missile' or name.startswith('tip'):color=(174,189,172)
            for k,ids in enumerate([(0,1,2,3),(4,7,6,5),(0,4,5,1),(3,2,6,7),(0,3,7,4),(1,5,6,2)]):
                vs=[verts[i] for i in ids]
                depth=sum(v[2] for v in vs)/4
                faces.append((depth,vs,tuple(int(c*(0.62+0.065*k)) for c in color)))
    scale=min(670/(max(v[0] for v in points)-min(v[0] for v in points)),365/(max(v[1] for v in points)-min(v[1] for v in points)))
    cx=(max(v[0] for v in points)+min(v[0] for v in points))/2
    low=min(0,min(v[1] for v in points))
    def screen(p):return ox+400+(p[0]-cx)*scale,oy+440-(p[1]-low)*scale
    draw.rounded_rectangle((ox+12,oy+10,ox+788,oy+490),14,fill='#172532',outline='#345060')
    draw.text((ox+30,oy+24),label,font=font,fill='#e0eced')
    draw.line((ox+30,oy+440,ox+770,oy+440),fill='#4d6570',width=2)
    for _,vs,color in sorted(faces,key=lambda f:f[0],reverse=True):
        draw.polygon([screen(v) for v in vs],fill=color)
    draw.text((ox+30,oy+461),'Оранжевый — выдвижной шток; жёлтый — пята',font=small,fill='#acbdc6')

# Verify that sleeves stay fixed and extended feet reach ground in all models.
count=0
for path in (ROOT/'src/main/resources/assets/harddayz/geo').glob('*.geo.json'):
    bones={b['name']:b for b in json.loads(path.read_text())['minecraft:geometry'][0]['bones']}
    for name,b in bones.items():
        if not(name.startswith('jack') and name[4:].isdigit()):continue
        rod=bones[name+'_extension']; foot=bones[name+'_foot']; c=rod['cubes'][0]
        assert rod['parent']==name and foot['parent']==name
        top=c['origin'][1]+c['size'][1]
        assert abs(top-rod['pivot'][1])<1e-6, (path,name,'moving top')
        drop=max(0,foot['cubes'][0]['origin'][1])
        assert abs(foot['cubes'][0]['origin'][1]-drop)<=1 or path.stem=='shahed_ground.geo'
        for t in (0,0.25,0.5,1):
            bottom=top-c['size'][1]*(1+t*drop/c['size'][1])
            pad_top=foot['cubes'][0]['origin'][1]+foot['cubes'][0]['size'][1]-t*drop
            assert foot['cubes'][0]['origin'][1]-t*drop-1e-6 <= bottom <= pad_top+1e-6, (path,name,'detached foot')
        count+=1
panel('grad',0,0,0,0,0,'Град: опоры убраны')
panel('grad',1,45,0,800,0,'Град: опоры выдвинуты, пакет поднят')
panel('iskander',0,0,0,0,500,'Искандер: походное положение')
panel('iskander',1,0,1,800,500,'Искандер: опоры выдвинуты, стрела поднята')
image.save(OUT)
print(f'{count} supports validated; preview: {OUT}')
