"""Hi-res cut-out render -> pixel-art item icon: fit, hard alpha, limited palette, 1 px dark outline, light from the top-left."""
from PIL import Image, ImageFilter, ImageEnhance
import colorsys


def fit(im, size, margin=1, angle=0):
    if angle:
        im = im.rotate(angle, resample=Image.BICUBIC, expand=True)
        bx = im.getchannel('A').point(lambda a: 255 if a > 20 else 0).getbbox()
        im = im.crop(bx)
    inner = size - 2 * margin - 2  # room for the outline
    k = inner / max(im.size)
    w, h = max(1, round(im.width * k)), max(1, round(im.height * k))
    return im.resize((w, h), Image.LANCZOS)


def tone(rgb, gamma=0.62, contrast=1.18, color=1.08):
    """Military models are dark on purpose; an inventory icon needs to read on a dark slot background: lift shadows, add saturation."""
    lut = [round(255 * (v / 255) ** gamma) for v in range(256)]
    rgb = rgb.point(lut * 3)
    rgb = ImageEnhance.Contrast(rgb).enhance(contrast)
    return ImageEnhance.Color(rgb).enhance(color)


def quantize(im, colors=16, **kw):
    a = im.getchannel('A').point(lambda v: 255 if v > 150 else 0)
    rgb = Image.new('RGB', im.size, (0, 0, 0))
    rgb.paste(tone(im.convert('RGB'), **kw), mask=a)
    q = rgb.quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert('RGB')
    out = Image.new('RGBA', im.size, (0, 0, 0, 0))
    out.paste(q, mask=a)
    return out


def outline(im, strength=0.42):
    w, h = im.size
    px = im.load()
    out = Image.new('RGBA', (w + 2, h + 2), (0, 0, 0, 0))
    out.paste(im, (1, 1))
    po = out.load()
    src = out.copy().load()
    for y in range(h + 2):
        for x in range(w + 2):
            if src[x, y][3]:
                continue
            ns = [src[x + dx, y + dy] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)) if 0 <= x + dx < w + 2 and 0 <= y + dy < h + 2 and src[x + dx, y + dy][3]]
            if ns:
                r = sum(n[0] for n in ns) // len(ns)
                g = sum(n[1] for n in ns) // len(ns)
                b = sum(n[2] for n in ns) // len(ns)
                hh, ll, ss = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
                rr, gg, bb = colorsys.hls_to_rgb(hh, max(0.03, ll * strength), min(1, ss * 0.45))
                po[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), 255)
    return out


def icon(big, size=32, colors=16, angle=0, align='center', **kw):
    im = fit(big, size, angle=angle)
    im = quantize(im, colors, **kw)
    im = outline(im)
    canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    ox = (size - im.width) // 2
    oy = (size - im.height) // 2 if align == 'center' else size - im.height - 1
    canvas.alpha_composite(im, (ox, oy))
    return canvas


def sheet(icons, scale=6, cols=8, bg=(70, 70, 78, 255)):
    rows = (len(icons) + cols - 1) // cols
    cell = max(i.width for i in icons) * scale + 8
    m = Image.new('RGBA', (cols * cell, rows * cell), bg)
    for k, im in enumerate(icons):
        t = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
        m.alpha_composite(t, ((k % cols) * cell + 4, (k // cols) * cell + 4))
    return m
