#!/usr/bin/env python3
"""Рисует 16x16 текстуры блоков мода в стиле ванильного Minecraft 1.20.1.

Раскладка пикселей своя. Палитры берутся из ванильных текстур (client.jar из кеша ForgeGradle),
чтобы тона, контраст и зернистость совпадали с игрой.

Запуск из корня проекта: python3 art/blocks/gen_block_textures.py [путь/к/client.jar]
Нужен Pillow. Результат пишется в src/main/resources/assets/harddayz/textures/block.
"""
import io
import os
import random
import sys
import zipfile
from collections import Counter

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "harddayz", "textures", "block")
JAR = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser(
    "~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client.jar")

rng = random.Random(2024)
_zip = zipfile.ZipFile(JAR)


def vanilla(name):
    return Image.open(io.BytesIO(_zip.read(f"assets/minecraft/textures/block/{name}.png"))).convert("RGBA").crop((0, 0, 16, 16))


def lum(c):
    return 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]


def pal(name, n=5, tint=None):
    """n самых частых цветов ванильной текстуры, от тёмного к светлому."""
    cnt = Counter(p[:3] for p in vanilla(name).getdata() if p[3] == 255)
    cols = sorted((c for c, _ in cnt.most_common(n)), key=lum)
    if tint:
        cols = [tuple(int(v * t) for v, t in zip(c, tint)) for c in cols]
    return cols


def new():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def px(im, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        im.putpixel((x, y), tuple(c)[:3] + (255,))


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(im, x, y, c)


def clumpy(im, cols, box=(0, 0, 15, 15), bias=0.55):
    """Зернистость как у камня и земли: случайные тона, затем соседи подтягиваются друг к другу."""
    x0, y0, x1, y1 = box
    grid = {(x, y): rng.randrange(len(cols)) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)}
    for (x, y) in list(grid):
        if rng.random() < bias:
            nx, ny = x + rng.choice((-1, 0, 1)), y + rng.choice((-1, 0, 1))
            if (nx, ny) in grid:
                grid[(x, y)] = grid[(nx, ny)]
    for (x, y), i in grid.items():
        px(im, x, y, cols[i])


def bevel(im, light, dark, box=(0, 0, 15, 15)):
    x0, y0, x1, y1 = box
    for i in range(x0, x1 + 1):
        px(im, i, y0, light)
        px(im, i, y1, dark)
    for j in range(y0, y1 + 1):
        px(im, x0, j, light)
        px(im, x1, j, dark)
    px(im, x1, y0, dark)
    px(im, x0, y1, dark)


def frame(im, c, box=(0, 0, 15, 15)):
    x0, y0, x1, y1 = box
    rect(im, x0, y0, x1, y0, c)
    rect(im, x0, y1, x1, y1, c)
    rect(im, x0, y0, x0, y1, c)
    rect(im, x1, y0, x1, y1, c)


def save(name, im):
    im.save(os.path.join(OUT, name + ".png"))


# ---------------------------------------------------------------- дерево

def planks_base(im, cols, seam, rows, box=(0, 0, 15, 15)):
    """Доски в стиле ванили: горизонтальные швы, тёмные стыки, пятна волокон."""
    x0, y0, x1, y1 = box
    for y in range(y0, y1 + 1):
        base = cols[(y // 2 + rng.randrange(2)) % len(cols)]
        for x in range(x0, x1 + 1):
            c = cols[rng.randrange(len(cols))] if rng.random() < 0.55 else base
            px(im, x, y, c)
    for i, y in enumerate(rows):
        rect(im, x0, y, x1, y, seam)
        top = rows[i - 1] + 1 if i else y0
        jx = x0 + (4, 10, 7, 12, 3)[i % 5]
        rect(im, jx, top, jx, y - 1, seam)


def ammo_crate():
    sp = pal("spruce_planks", 5)
    iron = pal("iron_block", 4, tint=(0.6, 0.6, 0.62))
    im = new()
    planks_base(im, sp, sp[0], (3, 7, 11, 15))
    dark = pal("blast_furnace_side", 3)
    # железная обвязка и угловые накладки
    for x in (1, 14):
        rect(im, x, 0, x, 15, dark[0])
    for x in (2, 13):
        rect(im, x, 0, x, 15, dark[1])
    for y in (0, 15):
        rect(im, 0, y, 15, y, dark[0])
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14), (1, 8), (14, 8)):
        px(im, x, y, iron[1])
    # крышка: светлая метка посередине
    rect(im, 6, 6, 9, 9, sp[-1])
    frame(im, sp[0], (6, 6, 9, 9))
    save("ammo_crate", im)


def warehouse(top):
    oak = pal("oak_planks", 5)
    dk = pal("dark_oak_planks", 3)
    im = new()
    planks_base(im, oak, oak[0], (3, 7, 11, 15))
    frame(im, dk[0])
    frame(im, dk[1], (1, 1, 14, 14))
    if not top:
        for i in range(2, 14):
            px(im, i, i, dk[1])
            px(im, 15 - i, i, dk[1])
    save("warehouse_top" if top else "warehouse_side", im)


def log_roof():
    cols = pal("spruce_log", 5)
    im = new()
    for x in range(16):
        base = cols[rng.randrange(len(cols))]
        for y in range(16):
            px(im, x, y, base if rng.random() < 0.7 else cols[rng.randrange(len(cols))])
    for x in range(16):
        if rng.random() < 0.5:
            y0 = rng.randrange(0, 11)
            for y in range(y0, y0 + rng.randrange(3, 7)):
                px(im, x, min(15, y), cols[0])
    save("log_roof", im)


def log_roof_end():
    bark = pal("spruce_log", 5)
    wood = pal("spruce_log_top", 5)
    im = new()
    clumpy(im, bark, bias=0.3)
    rect(im, 1, 1, 14, 14, wood[3])
    clumpy(im, [wood[2], wood[3], wood[4]], (1, 1, 14, 14), 0.4)
    frame(im, wood[1], (2, 2, 13, 13))
    frame(im, wood[1], (4, 4, 11, 11))
    rect(im, 6, 6, 9, 9, wood[2])
    px(im, 7, 7, wood[0])
    px(im, 8, 8, wood[0])
    save("log_roof_end", im)


def revetment():
    """Плетень: ветви переплетены, нечётные ряды идут «под» вертикальными кольями."""
    cols = pal("dark_oak_planks", 4)
    stake = pal("spruce_log", 4)
    im = new()
    for y in range(16):
        row = y // 2
        for x in range(16):
            over = ((x // 4) + row) % 2 == 0
            base = cols[2] if over else cols[1]
            c = base if rng.random() < 0.7 else cols[rng.randrange(len(cols))]
            if y % 2 == 1:
                c = cols[0]
            px(im, x, y, c)
    for x in (0, 8):
        for y in range(16):
            px(im, x, y, stake[3] if y % 2 == 0 else stake[2])
            px(im, x + 1, y, stake[1])
    save("revetment", im)


# ---------------------------------------------------------------- камень, бетон, металл

def bunker_concrete():
    im = new()
    clumpy(im, pal("stone", 5), bias=0.5)
    save("bunker_concrete", im)


def concrete_fort():
    cols = pal("andesite", 4)
    dark = pal("stone", 2)
    im = new()
    clumpy(im, cols, bias=0.4)
    # две большие плиты со швом посередине
    rect(im, 0, 7, 15, 7, dark[0])
    for x, y0, y1 in ((4, 0, 6), (11, 8, 15)):
        rect(im, x, y0, x, y1, dark[0])
    rect(im, 0, 0, 15, 0, cols[-1])
    rect(im, 0, 8, 15, 8, cols[-1])
    save("concrete_fort", im)


def gabion():
    stones = pal("cobblestone", 5)
    wire = [(52, 52, 54), (84, 84, 86), (120, 120, 122)]
    im = new()
    # камни в клетках 5x5, сверху сетка проволоки
    for gy in range(3):
        for gx in range(3):
            base = stones[rng.randrange(1, 5)]
            for y in range(gy * 5 + 1, gy * 5 + 6):
                for x in range(gx * 5 + 1, gx * 5 + 6):
                    inner = gx * 5 + 2 <= x <= gx * 5 + 4 and gy * 5 + 2 <= y <= gy * 5 + 4
                    px(im, x, y, base if inner else stones[1])
            px(im, gx * 5 + 2, gy * 5 + 2, stones[-1])
    for i in range(16):
        for c in (0, 5, 10, 15):
            px(im, i, c, wire[1])
            px(im, c, i, wire[1])
    rect(im, 0, 0, 15, 0, wire[2])
    save("gabion", im)


def armor_plate():
    cols = pal("iron_block", 4, tint=(0.58, 0.58, 0.6))
    im = new()
    clumpy(im, cols, bias=0.7)
    bevel(im, cols[-1], cols[0])
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px(im, x, y, cols[0])
        px(im, x + 1, y + 1, cols[-1])
    rect(im, 3, 7, 12, 7, cols[0])
    rect(im, 3, 8, 12, 8, cols[-1])
    save("armor_plate", im)


def rust_steel():
    iron = pal("iron_block", 3, tint=(0.62, 0.6, 0.6))
    rust = pal("raw_iron_block", 5, tint=(0.8, 0.7, 0.62))
    im = new()
    clumpy(im, iron, bias=0.6)
    # пятна ржавчины растут от нескольких центров
    for _ in range(5):
        cx, cy = rng.randrange(16), rng.randrange(16)
        for _ in range(rng.randrange(6, 13)):
            x, y = cx + rng.randint(-2, 2), cy + rng.randint(-2, 2)
            px(im, x, y, rust[rng.randrange(1, 5)])
    bevel(im, iron[-1], iron[0])
    save("rust_steel", im)


def bunker_door(top):
    cols = pal("iron_door_top" if top else "iron_door_bottom", 5, tint=(0.68, 0.68, 0.7))
    im = new()
    clumpy(im, cols[1:4], bias=0.6)
    bevel(im, cols[-1], cols[0])
    frame(im, cols[1], (2, 2, 13, 13))
    if top:
        # узкая смотровая щель
        rect(im, 4, 5, 11, 8, cols[0])
        rect(im, 5, 6, 10, 7, (46, 54, 62))
        rect(im, 4, 9, 11, 9, cols[-1])
        px(im, 6, 6, (110, 128, 140))
    else:
        rect(im, 11, 4, 12, 6, cols[0])
        px(im, 11, 4, cols[-1])
        rect(im, 4, 9, 11, 9, cols[0])
        rect(im, 4, 10, 11, 10, cols[-1])
    save("bunker_door_top" if top else "bunker_door_bottom", im)


def sandbags():
    cols = pal("sand", 5)
    im = new()
    for row in range(4):
        y0 = row * 4
        off = 0 if row % 2 == 0 else 4
        for x in range(16):
            for y in range(y0, y0 + 4):
                px(im, x, y, cols[2 + rng.randrange(2)] if rng.random() < 0.6 else cols[rng.randrange(1, 5)])
        for x in range(16):
            px(im, x, y0, cols[4])          # светлый верх мешка
            px(im, x, y0 + 3, cols[0])      # тёмный низ
        for bx in range(-4 + off, 16, 8):
            for y in range(y0, y0 + 4):
                px(im, bx % 16, y, cols[0])  # шов между мешками
            px(im, (bx + 1) % 16, y0, cols[1])
    save("sandbags", im)


def camo_net():
    green = pal("spruce_leaves", 5, tint=(0.55, 0.95, 0.5))
    brown = pal("oak_log", 3, tint=(1.0, 0.95, 0.8))
    im = new()
    for y in range(16):
        for x in range(16):
            r = rng.random()
            if r < 0.12:
                continue
            px(im, x, y, brown[1] if r < 0.2 else green[rng.randrange(len(green))])
    save("camo_net", im)


def barbed_wire():
    wire = pal("iron_bars", 3)
    im = new()
    # две провисающие нити с шипами в ванильной палитре прутьев
    for x in range(16):
        px(im, x, 4 + (1 if x % 4 in (1, 2) else 0), wire[1])
        px(im, x, 10 + (1 if x % 4 in (3, 0) else 0), wire[1])
    for x in (1, 5, 9, 13):
        px(im, x, 3, wire[2])
        px(im, x, 6, wire[2])
        px(im, x + 1, 5, wire[2])
    for x in (3, 7, 11, 15):
        px(im, x, 9, wire[2])
        px(im, x, 12, wire[2])
        px(im, x - 1, 11, wire[2])
    save("barbed_wire", im)


# ---------------------------------------------------------------- прочее

def frontline_side():
    red = pal("red_terracotta", 4)
    white = pal("white_concrete", 3)
    im = new()
    clumpy(im, red, bias=0.6)
    rect(im, 0, 6, 15, 9, white[1])
    clumpy(im, white, (0, 6, 15, 9), 0.5)
    rect(im, 0, 5, 15, 5, red[0])
    rect(im, 0, 10, 15, 10, red[0])
    bevel(im, red[-1], red[0])
    save("frontline_side", im)


def frontline_top():
    red = pal("red_terracotta", 4)
    white = pal("white_concrete", 3)
    im = new()
    clumpy(im, red, bias=0.6)
    frame(im, red[0])
    # ромб-метка
    for i in range(5):
        rect(im, 8 - i, 3 + i, 7 + i, 3 + i, white[1])
        rect(im, 8 - i, 12 - i, 7 + i, 12 - i, white[1])
    rect(im, 7, 7, 8, 8, red[1])
    save("frontline_top", im)


def hq(top):
    stone = pal("stone_bricks", 5)
    screen = (34, 40, 40)
    im = new()
    clumpy(im, stone, bias=0.5)
    bevel(im, stone[-1], stone[0])
    if top:
        frame(im, stone[0], (3, 3, 12, 12))
        rect(im, 6, 6, 9, 9, screen)
        rect(im, 7, 7, 8, 8, (96, 200, 108))
    else:
        rect(im, 2, 2, 13, 9, stone[0])
        rect(im, 3, 3, 12, 8, screen)
        for x, y in ((4, 7), (5, 6), (6, 5), (7, 6), (8, 7), (9, 6), (10, 5), (11, 6)):
            px(im, x, y, (96, 200, 108))
        red = pal("red_terracotta", 3)
        rect(im, 3, 11, 12, 12, red[1])
        rect(im, 3, 11, 12, 11, red[2])
    save("hq_top" if top else "hq_side", im)


def siren(top):
    cols = pal("stone", 5)
    iron = pal("iron_block", 3, tint=(0.75, 0.75, 0.78))
    im = new()
    clumpy(im, cols, bias=0.5)
    bevel(im, cols[-1], cols[0])
    if top:
        rect(im, 5, 5, 10, 10, cols[0])
        rect(im, 6, 6, 9, 9, (170, 38, 30))
        rect(im, 7, 7, 8, 8, (226, 84, 62))
    else:
        # решётка динамика: кольца отверстий
        for (x, y) in ((7, 3), (8, 3), (5, 4), (10, 4), (4, 5), (11, 5), (3, 7), (3, 8), (12, 7), (12, 8),
                       (4, 10), (11, 10), (5, 11), (10, 11), (7, 12), (8, 12)):
            px(im, x, y, (38, 38, 40))
        rect(im, 6, 6, 9, 9, (38, 38, 40))
        rect(im, 7, 7, 8, 8, iron[0])
    save("siren_top" if top else "siren_side", im)


def main():
    os.makedirs(OUT, exist_ok=True)
    ammo_crate()
    armor_plate()
    barbed_wire()
    bunker_concrete()
    bunker_door(top=False)
    bunker_door(top=True)
    camo_net()
    concrete_fort()
    frontline_side()
    frontline_top()
    gabion()
    hq(top=False)
    hq(top=True)
    log_roof()
    log_roof_end()
    revetment()
    rust_steel()
    sandbags()
    siren(top=False)
    siren(top=True)
    warehouse(top=False)
    warehouse(top=True)


if __name__ == "__main__":
    main()
