"""Generate brand assets for Zombie Custom Drops using real vanilla textures.

Outputs:
  docs/logo.png    128x128 mod icon (also copied to src/main/resources/logo.png)
  docs/banner.png  1200x380 repo/curseforge banner
"""
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONT = str(ROOT / 'tools' / 'PressStart2P.ttf')
VAN = ROOT / 'tools' / 'vanilla'
random.seed(7)

BG    = (14, 16, 10)
BG2   = (20, 23, 14)
KHAKI = (211, 189, 122)
MUTE  = (122, 128, 104)
GREEN_L = (124, 186, 88)
GOLD  = (232, 196, 90)

ZFACE = Image.open(VAN / 'zface.png')          # 8x8 vanilla zombie face
ITEMS = {k: Image.open(VAN / f'{k}.png') for k in ('iron_ingot', 'bone', 'rotten_flesh', 'emerald')}
GRASS_SIDE = Image.open(VAN / 'grass_block_side.png')
DIRT = Image.open(VAN / 'dirt.png')
STONE = Image.open(VAN / 'stone.png')


def tile(img, tex, box):
    """Tile a 16x16 block texture across box (x0,y0,x1,y1), scaled px/tex-px."""
    pass  # unused


def down_arrow(g, ox, oy, px=1, color=GOLD):
    rows = ["X     X",
            "XX   XX",
            " XX XX ",
            "  XXX  ",
            "   X   "]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == 'X':
                g.rectangle([ox + x * px, oy + y * px,
                             ox + (x + 1) * px - 1, oy + (y + 1) * px - 1], fill=color)


def make_logo():
    S = 128
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    g = ImageDraw.Draw(im)
    # stone block bg
    stone = STONE.convert('RGB').resize((S, S), Image.NEAREST).dark if False else None
    st = STONE.convert('RGB').resize((S, S), Image.NEAREST)
    st = st.point(lambda p: int(p * 0.55))  # darken
    im.paste(st, (0, 0))
    g = ImageDraw.Draw(im)
    # grass cap strip (real grass_block_side texture, 4 rows of tiles)
    gs = GRASS_SIDE.convert('RGB').resize((S, 18), Image.NEAREST)
    top = gs.crop((0, 0, S, 8))
    im.paste(top, (0, 0))
    g.rectangle([0, 8, S - 1, 8], fill=(0, 0, 0, 90))
    # real vanilla zombie face, 8x8 -> 80px
    face = ZFACE.resize((80, 80), Image.NEAREST)
    im.paste(face, (24, 22), face)
    # drop arrow
    down_arrow(g, 45, 106, px=5)
    g.rectangle([0, 0, S - 1, S - 1], outline=(0, 0, 0), width=2)
    im.save(ROOT / 'docs' / 'logo.png')
    im.save(ROOT / 'src' / 'main' / 'resources' / 'logo.png')
    print('logo.png -> docs/ + resources/')


def make_banner():
    W, H = 1200, 380
    im = Image.new('RGB', (W, H), BG)
    g = ImageDraw.Draw(im)
    for _ in range(1600):
        x = random.randrange(W); y = random.randrange(H)
        g.point((x, y), fill=BG2 if random.random() < .6 else (24, 27, 16))

    # left: logo on a stone tile frame
    logo = Image.open(ROOT / 'docs' / 'logo.png').convert('RGBA').resize((160, 160), Image.NEAREST)
    g.rectangle([62, 92, 238, 268], fill=(30, 33, 22))
    im.paste(logo, (70, 100), logo)
    g.rectangle([62, 92, 238, 268], outline=(58, 62, 42), width=3)

    # title
    f_big = ImageFont.truetype(FONT, 44)
    f_sub = ImageFont.truetype(FONT, 16)
    f_tag = ImageFont.truetype(FONT, 12)
    g.text((272, 106), 'ZOMBIE', font=f_big, fill=GREEN_L)
    g.text((272, 162), 'CUSTOM DROPS', font=f_big, fill=KHAKI)
    g.text((274, 230), 'configurable item drops for zombies', font=f_sub, fill=(170, 172, 158))
    g.text((274, 256), 'forge 1.20.1  //  item ; chance ; amount', font=f_tag, fill=MUTE)

    # raining REAL item sprites
    kinds = ['iron_ingot', 'bone', 'rotten_flesh', 'emerald', 'iron_ingot', 'rotten_flesh', 'emerald', 'bone']
    xs   = [780, 870, 960, 1050, 825, 915, 1005, 1095]
    ys   = [44, 90, 36, 84, 180, 210, 176, 216]
    for kind, x, y in zip(kinds, xs, ys):
        ic = ITEMS[kind].convert('RGBA').resize((36, 36), Image.NEAREST)
        im.paste(ic, (x, y), ic)
        for ty in range(y - 14, y - 58, -13):
            if ty > 12:
                g.rectangle([x + 16, ty, x + 19, ty + 4], fill=(52, 58, 38))

    # ground strip: real grass_block_side tiles (top half of the tile)
    tile = GRASS_SIDE.convert('RGB').resize((38, 38), Image.NEAREST).crop((0, 0, 38, 24))
    for x in range(0, W, 38):
        im.paste(tile, (x, H - 24))
    g.rectangle([0, H - 24, W - 1, H - 24], fill=(0, 0, 0))

    im.save(ROOT / 'docs' / 'banner.png')
    print('banner.png -> docs/')


if __name__ == '__main__':
    make_logo()
    make_banner()
