"""Generate brand assets for Zombie Custom Drops using real vanilla textures.

Outputs:
  docs/logo.png    128x128 mod icon (also copied to src/main/resources/logo.png)
  docs/banner.png  1200x380 repo/curseforge banner
"""
import math
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONT = str(ROOT / 'tools' / 'PressStart2P.ttf')
VAN = ROOT / 'tools' / 'vanilla'
random.seed(7)

BG      = (12, 14, 9)
BG2     = (19, 22, 13)
PANEL   = (30, 33, 22)
EDGE    = (58, 62, 42)
KHAKI   = (211, 189, 122)
MUTE    = (122, 128, 104)
GREEN_L = (124, 186, 88)
GREEN_D = (60, 92, 44)
GOLD    = (232, 196, 90)
BONE    = (198, 198, 190)

ZFACE = Image.open(VAN / 'zface.png')          # 8x8 vanilla zombie face
ZSKIN = Image.open(VAN / 'zombie.png')         # 64x64 vanilla zombie skin
ITEMS = {k: Image.open(VAN / f'{k}.png') for k in ('iron_ingot', 'bone', 'rotten_flesh', 'emerald')}
GRASS_SIDE = Image.open(VAN / 'grass_block_side.png')
STONE = Image.open(VAN / 'stone.png')


def zombie_head(scale):
    """Flat 2D vanilla zombie face, nearest-neighbour scaled."""
    return ZFACE.resize((ZFACE.width * scale, ZFACE.height * scale), Image.NEAREST)


def vignette(im, strength=90):
    """Darken corners via a radial alpha mask."""
    w, h = im.size
    mask = Image.new('L', (w, h), 0)
    mg = ImageDraw.Draw(mask)
    mg.ellipse([-w * 0.35, -h * 0.45, w * 1.35, h * 1.45], fill=strength)
    mask = mask.filter(ImageFilter.GaussianBlur(60))
    dark = Image.new('RGB', (w, h), (0, 0, 0))
    im.paste(dark, (0, 0), mask.point(lambda p: strength - p))


def noise(im, n=1400, colors=(BG2, (24, 27, 16))):
    g = ImageDraw.Draw(im)
    for _ in range(n):
        g.point((random.randrange(im.width), random.randrange(im.height)),
                fill=colors[0] if random.random() < .6 else colors[1])


def shadow_text(g, xy, text, font, fill, shadow=(0, 0, 0), off=3):
    x, y = xy
    g.text((x + off, y + off), text, font=font, fill=shadow)
    g.text((x, y), text, font=font, fill=fill)


def chip(g, x, y, text, font, fg=KHAKI, edge=EDGE, bg=PANEL):
    """Small outlined tag box; returns x after the chip."""
    w = g.textlength(text, font=font) + 14
    g.rectangle([x, y, x + w, y + 18], fill=bg, outline=edge, width=2)
    g.text((x + 7, y + 4), text, font=font, fill=fg)
    return x + w + 8


def rain_item(im, g, icon, x, y, size=36, trail=(56, 62, 40)):
    """Item sprite with a fading dashed motion trail above it."""
    ic = icon.convert('RGBA').resize((size, size), Image.NEAREST)
    for i, ty in enumerate(range(y - 16, y - size - 30, -14)):
        a = 200 - i * 55
        if a > 0 and ty > 8:
            seg = Image.new('RGBA', (4, 9), trail + (a,))
            im.paste(seg, (x + size // 2 - 2, ty), seg)
    im.paste(ic, (x, y), ic)


def make_logo():
    S = 128
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    # darkened stone block face
    # full-bleed grass-block face: grass band on top, dirt below - reads
    # as an actual Minecraft block, not a framed tile
    block = GRASS_SIDE.convert('RGB').resize((S, S), Image.NEAREST)
    block = block.point(lambda p: int(p * 0.92))
    im.paste(block, (0, 0))
    g = ImageDraw.Draw(im)
    # soft dark pocket in the dirt so the face pops
    halo = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    hg = ImageDraw.Draw(halo)
    hg.rounded_rectangle([22, 30, S - 22, 102], radius=10, fill=(0, 0, 0, 110))
    halo = halo.filter(ImageFilter.GaussianBlur(8))
    im.paste(halo, (0, 0), halo)
    # flat 2D zombie face sunk into the block
    zed = zombie_head(8)   # 8x8 -> 64x64
    im.paste(zed, ((S - zed.width) // 2, 30), zed)
    # drops spilling out of the block bottom edge
    for i, (kind, x) in enumerate(zip(('bone', 'iron_ingot', 'emerald'), (16, 56, 94))):
        ic = ITEMS[kind].convert('RGBA').resize((18, 18), Image.NEAREST)
        im.paste(ic, (x, 102 - (i % 2) * 6), ic)
    # frame: dark outer, thin green inner accent
    g.rectangle([0, 0, S - 1, S - 1], outline=(8, 9, 6), width=3)
    g.rectangle([3, 3, S - 4, S - 4], outline=GREEN_D, width=1)
    im.save(ROOT / 'docs' / 'logo.png')
    im.save(ROOT / 'src' / 'main' / 'resources' / 'logo.png')
    print('logo.png -> docs/ + resources/')


def make_banner():
    W, H = 1200, 380
    im = Image.new('RGB', (W, H), BG)
    noise(im)
    g = ImageDraw.Draw(im)

    # faint giant zombie face watermark far right
    big = ZFACE.convert('RGBA').resize((300, 300), Image.NEAREST)
    big.putalpha(big.split()[3].point(lambda p: int(p * 0.12) if p else 0))
    im.paste(big, (880, 26), big)

    # (logo plaque carries the zombie; right side is just raining drops)

    # logo plaque
    logo = Image.open(ROOT / 'docs' / 'logo.png').convert('RGBA').resize((176, 176), Image.NEAREST)
    g.rectangle([54, 84, 250, 288], fill=PANEL)
    g.rectangle([58, 88, 246, 284], fill=(0, 0, 0, 0), outline=(44, 47, 32), width=2)
    im.paste(logo, (64, 98), logo)
    g.rectangle([54, 84, 250, 288], outline=EDGE, width=3)
    g.rectangle([54, 84, 250, 84], outline=GOLD, width=3)   # gold top edge accent

    # title with drop shadow
    f_big = ImageFont.truetype(FONT, 46)
    f_sub = ImageFont.truetype(FONT, 15)
    f_tag = ImageFont.truetype(FONT, 11)
    shadow_text(g, (282, 92), 'ZOMBIE', f_big, GREEN_L, off=4)
    shadow_text(g, (282, 150), 'CUSTOM DROPS', f_big, KHAKI, off=4)
    g.text((286, 224), 'configurable item drops for zombies', font=f_sub, fill=(168, 170, 156))
    g.text((286, 256), 'forge 1.20.1  //  item ; chance ; amount', font=f_tag, fill=MUTE)

    # raining items on the right, over the watermark face
    kinds = ['iron_ingot', 'bone', 'rotten_flesh', 'emerald',
             'emerald', 'iron_ingot', 'rotten_flesh', 'bone']
    xs = [800, 880, 960, 1040, 840, 920, 1000, 1090]
    ys = [46, 88, 34, 80, 186, 214, 176, 208]
    for kind, x0, y0 in zip(kinds, xs, ys):
        rain_item(im, g, ITEMS[kind], x0, y0, size=36)

    # a few stragglers low over the grass line
    for kind, x0 in zip(('bone', 'emerald', 'iron_ingot'), (620, 660, 700)):
        ic = ITEMS[kind].convert('RGBA').resize((24, 24), Image.NEAREST)
        im.paste(ic, (x0, H - 52), ic)

    # ground strip: real grass_block_side tiles
    tile = GRASS_SIDE.convert('RGB').resize((38, 38), Image.NEAREST).crop((0, 0, 38, 24))
    for x0 in range(0, W, 38):
        im.paste(tile, (x0, H - 24))
    g.rectangle([0, H - 25, W - 1, H - 25], fill=(0, 0, 0))

    vignette(im)
    im.save(ROOT / 'docs' / 'banner.png')
    print('banner.png -> docs/')


if __name__ == '__main__':
    make_logo()
    make_banner()
