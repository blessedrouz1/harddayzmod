#!/usr/bin/env python3
"""Рисует 16x16 иконки предметов мода в стиле ванильного Minecraft.

Приёмы ванили: 16x16, свет сверху-слева, 3-4 тона на материал, тёмный контур цвета самого предмета,
диагональ снизу-слева вверх-вправо для длинных предметов.

Запуск из корня проекта: python3 art/items/gen_item_textures.py
Нужен Pillow. Результат пишется в src/main/resources/assets/harddayz/textures/item.
gun_palette.png не трогаем: это палитра для моделей оружия, а не иконка.
"""
import math
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets",
                   "harddayz", "textures", "item")

# палитры: от тёмного к светлому
OLV = [(44, 56, 24), (66, 82, 34), (90, 110, 48), (118, 140, 66)]
SAND = [(128, 106, 68), (166, 142, 94), (196, 172, 120), (222, 202, 152)]
STEEL = [(84, 86, 92), (128, 130, 136), (172, 174, 180), (214, 216, 220)]
DARK = [(22, 22, 26), (40, 40, 46), (62, 62, 70), (90, 90, 100)]
RED = [(96, 22, 20), (150, 34, 30), (192, 52, 44), (228, 92, 74)]
WOOD = [(52, 36, 20), (84, 60, 34), (116, 86, 50), (146, 112, 68)]
BRASS = [(110, 76, 14), (168, 120, 22), (216, 168, 36), (246, 214, 92)]
WHITE = [(138, 140, 146), (182, 184, 190), (222, 224, 228), (250, 250, 252)]
GLASS = [(48, 84, 112), (80, 130, 166), (120, 172, 204), (186, 222, 240)]
ORANGE = [(140, 62, 14), (196, 96, 22), (236, 136, 38), (255, 188, 88)]
GREEN = [(26, 70, 40), (40, 110, 60), (72, 168, 92), (130, 220, 140)]
BROWN = [(80, 56, 32), (120, 86, 50), (156, 118, 70), (188, 148, 94)]


def new():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def px(im, x, y, c):
    x, y = int(x), int(y)
    if 0 <= x < 16 and 0 <= y < 16:
        im.putpixel((x, y), tuple(c)[:3] + (255,))


def box(im, x0, y0, x1, y1, pal):
    """Прямоугольник со светом сверху-слева, тенью снизу-справа."""
    if isinstance(pal[0], int):  # одиночный цвет — плоская заливка
        pal = [pal] * 4
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            t = 2
            if y == y0:
                t = 3
            elif y == y1:
                t = 1
            elif x == x0:
                t = 3 if y1 - y0 > 2 else 2
            elif x == x1:
                t = 1
            if y == y0 and x == x1 or y == y1 and x == x0:
                t = 2
            if y0 == y1:
                t = 2
            px(im, x, y, pal[t])


def disc(im, cx, cy, r, pal):
    ellipse(im, cx, cy, r, r, pal)


def ellipse(im, cx, cy, rx, ry, pal):
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            d = dx * dx + dy * dy
            if d <= 1.0:
                s = (-dx - dy) / 1.6
                t = 3 if s > 0.45 else 2 if s > -0.15 else 1 if s > -0.7 else 0
                px(im, x, y, pal[t])


def tube(im, p0, p1, r0, r1, pal):
    """Толстая линия с переменным радиусом; светлая сторона — верх-лево."""
    (x0, y0), (x1, y1) = p0, p1
    vx, vy = x1 - x0, y1 - y0
    ln = math.hypot(vx, vy) or 1
    ux, uy = vx / ln, vy / ln
    nx, ny = uy, -ux
    if nx + ny > 0:
        nx, ny = -nx, -ny
    for y in range(16):
        for x in range(16):
            qx, qy = x + 0.5 - x0, y + 0.5 - y0
            t = (qx * ux + qy * uy) / ln
            if t < -0.02 or t > 1.02:
                continue
            tc = min(1, max(0, t))
            r = r0 + (r1 - r0) * tc
            d = qx * nx + qy * ny
            if abs(d) <= r + 0.01:
                rel = d / max(r, 0.5)
                if r < 0.9:
                    tone = 2
                else:
                    tone = 3 if rel > 0.4 else 2 if rel > -0.4 else 1
                px(im, x, y, pal[tone])


def line(im, p0, p1, c):
    n = int(max(abs(p1[0] - p0[0]), abs(p1[1] - p0[1]))) * 2 + 1
    for i in range(n + 1):
        t = i / n
        px(im, p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t, c)


def outline(im):
    """Тёмный контур цвета самого предмета, как у ванильных иконок."""
    src = im.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3]:
                continue
            ns = []
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16:
                    p = src.getpixel((nx, ny))
                    if p[3]:
                        ns.append(p)
            if ns:
                r = sum(p[0] for p in ns) // len(ns)
                g = sum(p[1] for p in ns) // len(ns)
                b = sum(p[2] for p in ns) // len(ns)
                im.putpixel((x, y), (int(r * 0.32), int(g * 0.32), int(b * 0.32), 255))


def save(name, im, outlined=True):
    if outlined:
        outline(im)
    im.save(os.path.join(OUT, name + ".png"))


# ---------------------------------------------------------------- ракеты

def missile(name, body, nose, fin, w=1.5, length=1.0, band=None, flame=False):
    im = new()
    tail = (2.0, 14.0)
    tip = (2.0 + 11.5 * length, 14.0 - 11.5 * length)
    sx, sy = tip[0] - tail[0], tip[1] - tail[1]
    cut = (tail[0] + sx * 0.68, tail[1] + sy * 0.68)
    ln = math.hypot(sx, sy)
    ux, uy = sx / ln, sy / ln
    # оперение сзади
    for k in (0.6, 1.6):
        for sign in (-1, 1):
            fx = tail[0] + ux * k + sign * (-uy) * (w + 0.4)
            fy = tail[1] + uy * k + sign * ux * (w + 0.4)
            px(im, fx, fy, fin[1 if sign > 0 else 2])
    tube(im, tail, cut, w, w, body)
    tube(im, cut, tip, w, 0.35, nose)
    if band:
        bx, by = tail[0] + sx * 0.4, tail[1] + sy * 0.4
        tube(im, (bx, by), (bx + ux * 1.2, by + uy * 1.2), w, w, band)
    if flame:
        px(im, tail[0] - 0.2, tail[1] + 0.2, ORANGE[3])
        px(im, tail[0] - 1.2, tail[1] + 1.2, ORANGE[2])
    save(name, im)


def missiles():
    missile("ballistic_missile", OLV, DARK, ORANGE, w=2.0, band=RED)
    missile("grad_rocket", OLV, RED, DARK, w=1.3)
    missile("himars_rocket", WHITE, DARK, OLV, w=1.7, band=OLV)
    missile("javelin_missile", OLV, DARK, SAND, w=1.2)
    missile("kalibr_missile", WHITE, STEEL, ORANGE, w=1.4, flame=True)
    missile("nasams_missile", WHITE, SAND, DARK, w=1.3, band=RED)
    missile("patriot_missile", WHITE, STEEL, RED, w=1.5, band=RED)
    missile("s400_missile", WHITE, STEEL, DARK, w=1.8, band=DARK)
    missile("sam_missile", WHITE, DARK, RED, w=1.2)
    missile("smerch_rocket", OLV, STEEL, DARK, w=1.9, length=1.0, band=SAND)
    missile("tochka_missile", WHITE, DARK, OLV, w=1.8)
    missile("tos_rocket", STEEL, SAND, DARK, w=2.0, length=0.8, band=ORANGE)
    missile("uragan_rocket", OLV, STEEL, DARK, w=1.5)


# ---------------------------------------------------------------- техника (вид сбоку, смотрит вправо)

def wheel(im, cx, cy=12.5):
    disc(im, cx, cy, 1.9, DARK)
    px(im, cx - 0.5, cy - 0.5, STEEL[1])


def truck(im, body, axles=(3.5, 7.5, 12.0), cab=True, glass=True):
    box(im, 1, 9, 14, 11, body)
    if cab:
        box(im, 10, 5, 14, 9, body)
        if glass:
            box(im, 12, 6, 14, 7, GLASS)
    for ax in axles:
        wheel(im, ax)


def rack(im, x0, y0, n=3, pal=STEEL):
    """Пакет труб под углом, как у РСЗО."""
    for i in range(n):
        tube(im, (x0 + i * 0.5, y0 - i * 0.9 + 3), (x0 + 6 + i * 0.5, y0 - i * 0.9 - 2.5), 1, 1, pal)


def vehicles():
    # РСЗО «Град»: пакет направляющих
    im = new(); truck(im, OLV); box(im, 2, 8, 9, 8, DARK)
    for i in range(3):
        tube(im, (2.5 + i * 1.0, 8.4 - i * 0.2), (8.5 + i * 1.0, 3.0 - i * 0.2), 0.8, 0.8, STEEL if i != 1 else WHITE)
    save("grad_launcher", im)
    # Град-П: одна труба на треноге
    im = new()
    tube(im, (3, 12), (12, 3), 1.2, 1.2, OLV)
    tube(im, (11, 4), (13, 2), 1.5, 1.5, DARK)
    line(im, (6, 9), (3, 14), DARK[2]); line(im, (8, 8), (9, 14), DARK[2])
    box(im, 2, 13, 4, 14, DARK)
    save("grad_p_launcher", im)
    # Искандер: большая ракета над кузовом
    im = new(); truck(im, OLV)
    tube(im, (2.5, 8.5), (10.5, 1.5), 1.6, 1.6, WHITE)
    tube(im, (8.5, 3.0), (11.5, 0.8), 1.6, 0.4, DARK)
    save("iskander_launcher", im)
    # HIMARS: два пакета-контейнера
    im = new(); truck(im, SAND, axles=(4.0, 8.5, 12.5))
    box(im, 2, 5, 9, 8, STEEL); px(im, 4, 6, DARK[0]); px(im, 6, 6, DARK[0]); px(im, 8, 6, DARK[0])
    px(im, 3, 7, DARK[0]); px(im, 5, 7, DARK[0]); px(im, 7, 7, DARK[0])
    save("himars_launcher", im)
    # РЭБ: антенна-тарелка
    im = new(); truck(im, OLV)
    box(im, 4, 7, 7, 8, DARK)
    tube(im, (5.5, 7.5), (5.5, 3), 0.6, 0.6, STEEL)
    ellipse(im, 5.5, 2.5, 3.5, 1.5, STEEL)
    px(im, 5, 1, RED[2])
    save("ew_station", im)
    # РЛС: решётка антенны
    im = new(); truck(im, OLV)
    box(im, 2, 1, 8, 7, STEEL)
    for x in (3, 5, 7):
        for y in range(2, 7):
            px(im, x, y, STEEL[0])
    box(im, 4, 7, 6, 8, DARK)
    save("radar_truck", im)
    # Калибр: контейнеры под углом
    im = new(); truck(im, OLV, glass=True)
    box(im, 2, 7, 9, 8, DARK)
    tube(im, (2.5, 8), (9, 2.5), 1.0, 1.0, WHITE)
    tube(im, (4.5, 8), (11, 2.5), 1.0, 1.0, STEEL)
    save("kalibr_launcher", im)
    # Смерч: блок труб
    im = new(); truck(im, OLV, axles=(3.5, 7.5, 12.0))
    box(im, 2, 3, 9, 8, DARK)
    for x in (3, 5, 7):
        for y in (4, 6):
            px(im, x, y, STEEL[1]); px(im, x + 1, y, STEEL[3])
    save("smerch_launcher", im)
    # Ураган: пакет труб
    im = new(); truck(im, OLV)
    box(im, 2, 4, 9, 8, STEEL)
    for x in (3, 5, 7):
        for y in (5, 7):
            px(im, x, y, DARK[0])
    save("uragan_launcher", im)
    # Точка-У: ракета лежит на пусковой
    im = new(); truck(im, OLV)
    tube(im, (2.5, 8), (10.5, 2), 1.3, 1.3, WHITE)
    tube(im, (9, 3.2), (11.5, 1.5), 1.3, 0.3, DARK)
    box(im, 2, 8, 8, 8, DARK)
    save("tochka_launcher", im)
    # Patriot: четыре контейнера под углом
    im = new(); truck(im, SAND, axles=(4.0, 8.5, 12.5))
    for i in range(3):
        tube(im, (2.5 + i * 1.6, 8.5), (8 + i * 1.6, 3.3 - i * 0.2), 1.0, 1.0, STEEL if i % 2 == 0 else WHITE)
    save("patriot_launcher", im)
    # С-400: контейнеры и антенна
    im = new(); truck(im, SAND, axles=(4.0, 8.5, 12.5))
    tube(im, (2.5, 8.5), (8, 3.3), 1.0, 1.0, WHITE)
    tube(im, (4.5, 8.5), (10, 3.3), 1.0, 1.0, STEEL)
    box(im, 3, 7, 8, 8, DARK)
    save("s400_launcher", im)
    # NASAMS: ящик-пусковая и ноги
    im = new()
    box(im, 2, 4, 13, 9, OLV)
    box(im, 3, 5, 12, 6, STEEL)
    box(im, 3, 7, 12, 8, WHITE)
    box(im, 3, 10, 5, 14, DARK); box(im, 10, 10, 12, 14, DARK)
    save("nasams_launcher", im)
    # ЗРК (ЗУР на кузове)
    im = new(); truck(im, SAND)
    tube(im, (2.5, 8.5), (9.5, 2), 1.0, 1.0, OLV)
    tube(im, (4, 8.5), (11, 2), 1.0, 1.0, STEEL)
    save("sam_launcher", im)
    # Шахеды: пусковая с самолётиком на рельсе
    im = new(); truck(im, SAND)
    box(im, 2, 8, 9, 8, DARK)
    tube(im, (2, 7.5), (9.5, 4), 0.7, 0.7, WHITE)
    px(im, 3, 4, WHITE[2]); px(im, 4, 5, WHITE[1]); px(im, 7, 3, WHITE[2])
    save("shahed_launcher", im)
    # Пикап с пулемётом
    im = new()
    box(im, 1, 9, 14, 11, SAND)
    box(im, 9, 5, 14, 9, SAND); box(im, 11, 6, 14, 7, GLASS)
    box(im, 1, 8, 8, 8, SAND)
    tube(im, (4.5, 7), (8.5, 4.5), 0.7, 0.7, DARK)
    tube(im, (8, 5), (10.5, 3), 0.6, 0.6, DARK)
    wheel(im, 4.0); wheel(im, 11.5)
    save("pickup_mg", im)
    # Пикап с ПЗРК
    im = new()
    box(im, 1, 9, 14, 11, OLV)
    box(im, 9, 5, 14, 9, OLV); box(im, 11, 6, 14, 7, GLASS)
    box(im, 1, 8, 8, 8, OLV)
    tube(im, (2.5, 7.5), (8, 2), 1.0, 1.0, SAND)
    tube(im, (7.5, 3), (9.5, 1.5), 1.0, 0.4, DARK)
    wheel(im, 4.0); wheel(im, 11.5)
    save("pickup_sam", im)
    # БМП-2 / Bradley: гусеницы, башня, пушка
    for name, pal in (("bmp2", OLV), ("bradley", SAND)):
        im = new()
        box(im, 1, 8, 14, 11, pal)
        box(im, 2, 11, 14, 13, DARK)
        for x in (3, 6, 9, 12):
            px(im, x, 12, STEEL[2])
        box(im, 4, 4, 10, 8, pal)
        px(im, 6, 5, GLASS[2]); px(im, 7, 5, GLASS[2])
        tube(im, (10, 5.5), (14.5, 5.5), 0.6, 0.6, DARK)
        save(name, im)
    # ТОС-1А: гусеницы, пакет труб
    im = new()
    box(im, 1, 9, 14, 11, OLV)
    box(im, 2, 11, 14, 13, DARK)
    for x in (3, 6, 9, 12):
        px(im, x, 12, STEEL[2])
    box(im, 2, 3, 10, 8, DARK)
    for x in (3, 5, 7, 9):
        px(im, x, 4, STEEL[3]); px(im, x, 6, STEEL[3])
    box(im, 11, 6, 14, 9, OLV); px(im, 13, 7, GLASS[2])
    save("tos_launcher", im)
    # Рампа: наклонная направляющая
    im = new()
    tube(im, (2, 12), (13, 3), 1.2, 1.2, STEEL)
    box(im, 1, 12, 9, 14, DARK)
    tube(im, (6, 9.5), (9, 7), 0.6, 0.6, WHITE)
    save("ramp_launcher", im)
    # Пост ЗУР
    im = new()
    box(im, 4, 10, 11, 14, OLV)
    tube(im, (5, 10), (12, 3), 1.6, 1.6, STEEL)
    tube(im, (9.5, 5.5), (12.5, 2.5), 1.6, 0.4, WHITE)
    save("sam_post", im)
    # Ручной ЗРК: труба на плечо
    im = new()
    tube(im, (2, 12), (13, 3), 1.7, 1.7, OLV)
    tube(im, (11, 5), (14, 2), 2.0, 2.0, DARK)
    box(im, 6, 9, 7, 12, DARK)
    px(im, 9, 4, STEEL[3])
    save("sam_manual", im)
    # Пулемётное гнездо: ствол и треножник
    im = new()
    box(im, 3, 4, 7, 8, OLV)
    tube(im, (7, 5.5), (14, 5.5), 0.7, 0.7, DARK)
    px(im, 5, 3, STEEL[2])
    line(im, (5, 9), (2, 14), DARK[2]); line(im, (5, 9), (8, 14), DARK[2]); line(im, (5, 9), (5, 14), DARK[2])
    save("mg_nest", im)
    # Миномёт: труба под углом, плита, сошки
    im = new()
    tube(im, (4, 11), (10, 3), 1.4, 1.4, OLV)
    box(im, 1, 13, 7, 14, DARK)
    line(im, (7, 7), (11, 13), DARK[2])
    px(im, 10, 2, DARK[0])
    save("mortar", im)
    # Ретранслятор: мачта
    im = new()
    tube(im, (8, 14), (8, 3), 0.9, 0.9, DARK)
    box(im, 4, 13, 11, 14, OLV)
    for y, hw in ((5, 3), (8, 4)):
        line(im, (8 - hw, y), (8 + hw, y), STEEL[1])
    px(im, 8, 1, RED[3]); px(im, 8, 2, RED[2])
    save("relay", im)


# ---------------------------------------------------------------- дроны (вид сверху)

def quad(name, body, rotor=STEEL, accent=None, size=1, payload=False):
    im = new()
    for cx, cy in ((3.2, 3.2), (12.8, 3.2), (3.2, 12.8), (12.8, 12.8)):
        disc(im, cx, cy, 2.6 if size else 2.0, rotor)
        px(im, cx - 0.5, cy - 0.5, DARK[0]); px(im, cx + 0.5, cy + 0.5, DARK[0])
        px(im, cx - 0.5, cy + 0.5, DARK[0]); px(im, cx + 0.5, cy - 0.5, DARK[0])
    line(im, (4, 4), (12, 12), body[1]); line(im, (12, 4), (4, 12), body[1])
    box(im, 5, 5, 10, 10, body)
    if accent:
        for ax in (7, 8):
            for ay in (7, 8):
                px(im, ax, ay, accent)
    if payload:
        box(im, 6, 11, 9, 13, DARK)
    save(name, im)


def delta(name, wing, tip_y=1, base_y=11, hw=6, tail=DARK, flame=False):
    im = new()
    cx = 8
    for y in range(tip_y, base_y + 1):
        half = int(round((y - tip_y) / max(1, base_y - tip_y) * hw))
        for x in range(cx - half, cx + half):
            t = 3 if x < cx - 1 else 2 if x < cx else 1
            if y == tip_y or (x == cx - half):
                t = 3
            px(im, x, y, wing[t])
    px(im, cx, base_y, wing[1])
    tube(im, (cx - 0.1, tip_y), (cx - 0.1, base_y + 2), 0.9, 0.9, tail)
    box(im, cx - 2, base_y + 2, cx + 1, base_y + 2, tail)
    if flame:
        px(im, cx - 1, base_y + 3, ORANGE[3]); px(im, cx, base_y + 3, ORANGE[2])
    save(name, im)


def drones():
    quad("fpv_drone", DARK, accent=RED[2], size=0)
    quad("interceptor_drone", DARK, accent=ORANGE[2], size=0, rotor=DARK)
    quad("recon_drone", WHITE, accent=GLASS[2], rotor=STEEL)
    quad("baba_yaga", OLV, accent=RED[2], payload=True)
    delta("shahed_drone", WHITE, 1, 10, 6)
    delta("shahed_131", WHITE, 2, 10, 5, tail=STEEL)
    delta("shahed_238", DARK, 1, 10, 6, tail=DARK, flame=True)
    delta("geran5", STEEL, 1, 10, 5, tail=DARK, flame=True)
    delta("gerbera", SAND, 2, 11, 6, tail=SAND)
    # Куб: малый «летающее крыло» с крестом
    im = new()
    tube(im, (8, 2), (8, 13), 1.0, 1.0, STEEL)
    box(im, 2, 5, 13, 6, WHITE)
    box(im, 4, 11, 11, 12, WHITE)
    px(im, 8, 1, RED[2])
    save("kub_drone", im)
    # Ланцет: X-образные крылья
    im = new()
    tube(im, (8, 1.5), (8, 14), 1.0, 1.0, SAND)
    box(im, 2, 3, 13, 4, SAND)
    box(im, 3, 11, 12, 12, SAND)
    px(im, 8, 0, DARK[1])
    px(im, 8, 14, DARK[0])
    save("lancet", im)
    # Молния: деревянный самолёт
    im = new()
    tube(im, (8, 1.5), (8, 13.5), 1.0, 0.8, SAND)
    box(im, 1, 5, 14, 7, SAND)
    box(im, 5, 11, 10, 12, SAND)
    px(im, 7, 3, DARK[1])
    save("molniya", im)
    # Самолётный разведчик: белый самолёт, синий фонарь
    im = new()
    tube(im, (8, 1), (8, 14), 1.2, 0.9, WHITE)
    box(im, 1, 6, 14, 8, WHITE)
    box(im, 5, 11, 10, 12, WHITE)
    px(im, 7, 3, GLASS[2]); px(im, 8, 3, GLASS[1])
    save("wing_recon", im)


# ---------------------------------------------------------------- прочее

def misc():
    # Бинт: рулон
    im = new()
    disc(im, 7, 8, 5, WHITE)
    disc(im, 7, 8, 1.6, SAND)
    px(im, 7, 8, SAND[0])
    box(im, 11, 10, 14, 13, WHITE)
    save("bandage", im)
    # Бандероль: картонная посылка
    im = new()
    box(im, 2, 4, 13, 13, BROWN)
    box(im, 7, 4, 8, 13, SAND)
    box(im, 2, 8, 13, 9, SAND)
    box(im, 10, 5, 12, 7, WHITE)
    save("banderol", im)
    # Гермодверь: как ванильная железная дверь
    im = new()
    box(im, 4, 1, 11, 14, STEEL)
    box(im, 5, 2, 10, 5, DARK)
    px(im, 6, 3, GLASS[1]); px(im, 7, 3, GLASS[1]); px(im, 8, 4, GLASS[1])
    box(im, 5, 7, 10, 7, STEEL[:3] + [STEEL[3]])
    px(im, 10, 9, DARK[2]); px(im, 9, 9, BRASS[2])
    px(im, 5, 11, STEEL[3]); px(im, 6, 11, STEEL[3])
    save("bunker_door", im)
    # Снаряды автопушки: гильзы
    im = new()
    for x in (2, 6, 10):
        box(im, x, 6, x + 2, 13, BRASS)
        box(im, x, 3, x + 2, 5, STEEL)
        px(im, x + 1, 2, STEEL[3])
        box(im, x, 13, x + 2, 13, BRASS[:2] + [BRASS[1], BRASS[1]])
    save("cannon_shells", im)
    # Лазерный целеуказатель: ручной компактный прибор, как дальномер — корпус, объектив, окуляр, экран, ремешок
    im = new()
    box(im, 3, 6, 10, 12, OLV)                            # корпус
    box(im, 10, 7, 13, 11, STEEL)                         # объектив
    px(im, 13, 8, RED[2]); px(im, 13, 9, RED[3]); px(im, 13, 10, RED[2])
    box(im, 4, 4, 7, 5, DARK)                             # окуляр
    box(im, 4, 8, 6, 9, DARK); px(im, 5, 8, GREEN[3])     # экран
    px(im, 8, 8, RED[2]); px(im, 8, 10, DARK[1])          # кнопка
    box(im, 4, 12, 5, 14, DARK)                           # ремешок
    px(im, 14, 9, RED[3]); px(im, 15, 9, RED[1])          # луч
    save("designator", im)
    # Канистры
    for name, pal in (("jerrycan", RED), ("empty_jerrycan", OLV)):
        im = new()
        box(im, 3, 4, 12, 14, pal)
        box(im, 3, 4, 12, 4, pal[:3] + [pal[3]])
        box(im, 4, 2, 7, 3, DARK)
        box(im, 9, 2, 11, 4, DARK)
        for i in range(5):
            px(im, 5 + i, 6 + i, pal[0]); px(im, 10 - i, 6 + i, pal[0])
        save(name, im)
    # Сапёрная лопата
    im = new()
    tube(im, (2.5, 13.5), (10, 6), 0.8, 0.8, WOOD)
    tube(im, (9.5, 6.5), (13.5, 2.5), 2.2, 2.2, STEEL)
    px(im, 13, 2, STEEL[3])
    save("field_shovel", im)
    # Осколочная граната
    im = new()
    disc(im, 7.5, 9, 4.8, OLV)
    for k in (-2, 0, 2):
        line(im, (3 + k, 6), (12 + k, 13), OLV[0]) if False else None
    for x in (5, 8, 11):
        for y in range(5, 13):
            if (x + y) % 2 == 0:
                px(im, x - 1, y, OLV[0])
    box(im, 6, 3, 9, 4, STEEL)
    tube(im, (9, 3.5), (12.5, 6), 0.7, 0.7, STEEL)
    px(im, 5, 2, STEEL[3])
    save("frag_grenade", im)
    # Дымовая граната
    im = new()
    box(im, 5, 4, 10, 13, STEEL)
    box(im, 5, 8, 10, 10, WHITE)
    box(im, 5, 3, 10, 4, DARK)
    tube(im, (10, 3), (13, 5), 0.6, 0.6, STEEL)
    save("smoke_grenade", im)
    # Мины
    im = new(); ellipse(im, 7.5, 10, 5, 3.4, OLV); ellipse(im, 7.5, 9, 2, 1.2, DARK); px(im, 12, 8, RED[2])
    save("mine_ap", im)
    im = new(); ellipse(im, 8, 9.5, 7, 4.5, OLV); ellipse(im, 8, 8.5, 3, 1.8, STEEL); px(im, 8, 8, DARK[0])
    save("mine_at", im)
    # Пулемётная лента
    im = new()
    box(im, 1, 8, 14, 9, DARK)
    for i in range(7):
        x = 1 + i * 2
        box(im, x, 4, x + 1, 8, BRASS)
        px(im, x, 3, STEEL[2]); px(im, x + 1, 3, STEEL[1])
        px(im, x, 11, BRASS[1]); px(im, x + 1, 11, BRASS[0])
        box(im, x, 9, x + 1, 11, BRASS)
    save("mg_belt", im)
    # Мина 82 мм
    im = new()
    tube(im, (8, 13), (8, 2), 2.0, 0.5, OLV)
    box(im, 5, 12, 6, 14, DARK); box(im, 9, 12, 10, 14, DARK)
    px(im, 8, 1, STEEL[2])
    save("mortar_shell", im)
    # Пульт оператора
    im = new()
    box(im, 1, 5, 13, 12, DARK)
    box(im, 2, 6, 8, 11, GREEN[:2] + [GREEN[1], GREEN[1]])
    px(im, 4, 9, GREEN[3]); px(im, 5, 8, GREEN[3]); px(im, 6, 8, GREEN[3])
    px(im, 11, 8, RED[2]); px(im, 11, 10, STEEL[2])
    tube(im, (12, 5), (14, 1), 0.5, 0.5, STEEL)
    save("operator_terminal", im)
    # Радарный экран
    im = new()
    disc(im, 8, 8, 6.5, DARK)
    disc(im, 8, 8, 5.2, [GREEN[0], GREEN[0], GREEN[0], GREEN[0]])
    for r in (2, 3.6):
        for a in range(0, 360, 12):
            px(im, 8 + r * math.cos(math.radians(a)), 8 + r * math.sin(math.radians(a)), GREEN[1])
    line(im, (8, 8), (12, 4), GREEN[3])
    px(im, 6, 11, RED[3]); px(im, 11, 9, ORANGE[3])
    save("radar_display", im)
    # Планшет снабжения
    im = new()
    box(im, 2, 1, 13, 14, DARK)
    box(im, 3, 2, 12, 12, GREEN[:2] + [GREEN[1], GREEN[1]])
    for y in (4, 7, 10):
        box(im, 4, y, 5, y, GREEN[3]); box(im, 7, y, 11, y, GREEN[2])
    px(im, 7, 13, STEEL[2]); px(im, 8, 13, STEEL[2])
    save("supply_tablet", im)
    # Журнал целей: книга с прицелом
    im = new()
    box(im, 2, 1, 13, 14, WOOD)
    box(im, 4, 2, 12, 13, WHITE)
    box(im, 2, 1, 3, 14, WOOD[:3] + [WOOD[2]])
    px(im, 8, 6, RED[2]); px(im, 8, 8, RED[2]); px(im, 7, 7, RED[2]); px(im, 9, 7, RED[2])
    box(im, 5, 11, 11, 11, STEEL)
    save("target_log", im)
    # Аптечка
    im = new()
    box(im, 2, 4, 13, 13, WHITE)
    box(im, 6, 2, 9, 3, DARK)
    box(im, 7, 6, 8, 11, RED); box(im, 5, 8, 10, 9, RED)
    save("medkit", im)
    # Обезболивающее: флакон
    im = new()
    box(im, 4, 5, 11, 14, ORANGE)
    box(im, 5, 2, 10, 4, WHITE)
    box(im, 5, 8, 10, 11, WHITE)
    px(im, 7, 9, RED[2]); px(im, 8, 9, RED[2])
    save("painkiller", im)
    # Жгут
    im = new()
    box(im, 1, 9, 14, 11, DARK)
    box(im, 6, 8, 9, 12, STEEL)
    tube(im, (3.5, 5), (11.5, 12.5), 0.7, 0.7, RED)
    tube(im, (11, 3), (13, 5), 0.7, 0.7, SAND)
    save("tourniquet", im)


def main():
    os.makedirs(OUT, exist_ok=True)
    missiles()
    vehicles()
    drones()
    misc()


if __name__ == "__main__":
    main()
