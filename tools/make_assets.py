"""Generate pixel-art brand assets for Zombie Custom Drops.

Outputs:
  docs/logo.png    128x128 mod icon (also copied to src/main/resources/logo.png)
  docs/banner.png  1200x380 repo/curseforge banner
"""
import random
from PIL import Image, ImageDraw, ImageFont

ROOT = __import__('pathlib').Path(__file__).resolve().parent.parent
FONT = str(ROOT / 'tools' / 'PressStart2P.ttf')
random.seed(7)

# ---- palette -----------------------------------------------------------
BG      = (14, 16, 10)
BG2     = (20, 23, 14)
KHAKI   = (211, 189, 122)
MUTE    = (122, 128, 104)
GREEN   = (94, 156, 63)      # zombie skin
GREEN_D = (62, 110, 40)      # zombie shadow
GREEN_L = (124, 186, 88)     # zombie highlight
INK     = (18, 20, 14)       # eyes / dark detail
GOLD    = (232, 196, 90)
GRASS   = (95, 159, 53)
DIRT    = (121, 85, 58)
IRON    = (200, 200, 200)
BONE    = (232, 228, 210)
FLESH   = (156, 82, 60)
EMERALD = (80, 200, 120)


def put(g, x, y, c):
    g.point((x, y), fill=c)


def zombie_face(g, ox, oy, px=1):
    """16x16 zombie head drawn at (ox,oy) scaled by px."""
    rows = [
        " GGGGGGGGGGGGGG ",
        "GGGGGGGGGGGGGGGG",
        "GGGGGGGGGGGGGGGG",
        "GGGGGGGGGGGGGGGG",
        "GGGGGGGGGGGGGGGG",
        "GGGGGGGGGGGGGGGG",
        "GGGGGGGGGGGGGGGG",
        "GGGGGGGGGGGGGGGG",
        "GGGGGGGGGGGGGGGG",
        "GGGGGGGGGGGGGGGG",
        "GGGGGGGGGGGGGGGG",
        "GGGGGGGGGGGGGGGG",
        "GGGGGGGGGGGGGGGG",
        "GGGGGGGGGGGGGGGG",
        "GGGGGGGGGGGGGGGG",
        " GGGGGGGGGGGGGG ",
    ]
    C = {'G': GREEN, ' ': (0, 0, 0, 0)}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ' ':
                continue
            col = C[ch]
            for dy in range(px):
                for dx in range(px):
                    put(g, ox + x * px + dx, oy + y * px + dy, col)
    # eyes: 2x3 dark blocks
    for ey in range(5, 8):
        for ex in (3, 4, 11, 12):
            for dy in range(px):
                for dx in range(px):
                    put(g, ox + ex * px + dx, oy + ey * px + dy, INK)
    # brow shade above eyes
    for ex in (3, 4, 11, 12):
        for dy in range(px):
            for dx in range(px):
                put(g, ox + ex * px + dx, oy + 4 * px + dy, GREEN_D)
    # mouth: 4x2
    for my in range(11, 13):
        for mx in range(6, 10):
            for dy in range(px):
                for dx in range(px):
                    put(g, ox + mx * px + dx, oy + my * px + dy, INK)
    # shading: darker right/bottom edge
    for y in range(16):
        for dx in range(px):
            for dy in range(px):
                put(g, ox + 15 * px + dx, oy + y * px + dy, GREEN_D)
    for x in range(16):
        for dy in range(px):
            for dx in range(px):
                put(g, ox + x * px + dx, oy + 15 * px + dy, GREEN_D)


def down_arrow(g, ox, oy, px=1, color=GOLD):
    """7x5 down arrow."""
    rows = ["X     X",
            "XX   XX",
            " XX XX ",
            "  XXX  ",
            "   X   "]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == 'X':
                for dy in range(px):
                    for dx in range(px):
                        put(g, ox + x * px + dx, oy + y * px + dy, color)


def item_icon(kind, size=32):
    """Tiny pixel item sprites, returns RGBA image."""
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    g = ImageDraw.Draw(im)
    s = size / 16  # draw on a 16x16 grid
    def P(x, y, c):
        g.rectangle([x * s, y * s, (x + 1) * s - 1, (y + 1) * s - 1], fill=c)
    if kind == 'ingot':
        for x in range(3, 13):
            P(x, 5, IRON); P(x, 6, IRON)
        for x in range(4, 12):
            P(x, 7, IRON); P(x, 8, IRON)
        for x in range(3, 13):
            P(x, 9, IRON); P(x, 10, (170, 170, 170))
        for x in range(4, 12):
            P(x, 11, (140, 140, 140))
        for x in range(4, 8):
            P(x, 5, (240, 240, 240))
    elif kind == 'bone':
        for i in range(4, 12):
            P(i, 8, BONE); P(i, 9, BONE)
        for p in ((3, 7), (3, 8), (4, 7), (12, 9), (12, 10), (11, 10)):
            P(*p, BONE)
    elif kind == 'flesh':
        for y in range(4, 12):
            for x in range(4, 12):
                P(x, y, FLESH)
        for y in range(4, 6):
            for x in range(5, 11):
                P(x, y, (180, 104, 78))
        P(5, 6, (120, 60, 44)); P(9, 7, (120, 60, 44))
        P(6, 9, (120, 60, 44)); P(8, 10, (120, 60, 44))
    elif kind == 'emerald':
        rows = ["    XX    ",
                "   XXXX   ",
                "  XXXXXX  ",
                " XXXXXXXX ",
                "  XXXXXX  ",
                "   XXXX   ",
                "    XX    "]
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == 'X':
                    P(x + 3, y + 4, EMERALD)
        P(6, 5, (170, 255, 200)); P(5, 6, (170, 255, 200))
    return im


def make_logo():
    """128x128: dark block + zombie head + drop arrow."""
    S = 128
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    g = ImageDraw.Draw(im)
    # block bg: near-black with darker pixel dithering + grass-green rim
    g.rectangle([0, 0, S - 1, S - 1], fill=BG2)
    for _ in range(220):
        x = random.randrange(S); y = random.randrange(S)
        g.point((x, y), fill=(26, 29, 18))
    g.rectangle([0, 0, S - 1, 3], fill=GRASS)          # grass top edge
    for x in range(0, S, 8):                            # dirt teeth
        g.rectangle([x, 3, x + 3, 7], fill=GRASS)
    g.rectangle([0, S - 4, S - 1, S - 1], fill=DIRT)
    # zombie head centered upper
    zombie_face(g, 32, 26, px=4)
    # drop arrow under head
    down_arrow(g, 47, 100, px=5)
    im.save(ROOT / 'docs' / 'logo.png')
    im.save(ROOT / 'src' / 'main' / 'resources' / 'logo.png')
    print('logo.png -> docs/ + resources/')


def make_banner():
    W, H = 1200, 380
    im = Image.new('RGB', (W, H), BG)
    g = ImageDraw.Draw(im)
    # pixel noise
    for _ in range(1600):
        x = random.randrange(W); y = random.randrange(H)
        g.point((x, y), fill=BG2 if random.random() < .6 else (24, 27, 16))
    # vignette edges
    for x in range(W):
        for k in range(6):
            a = k / 6
            c = tuple(int(BG[i] + (BG2[i] - BG[i]) * a) for i in range(3))
            g.point((x, k), fill=c); g.point((x, H - 1 - k), fill=c)

    # left logo block
    logo = Image.open(ROOT / 'docs' / 'logo.png').resize((160, 160), Image.NEAREST)
    im.paste(logo, (70, 100), logo)
    g.rectangle([66, 96, 234, 264], outline=(50, 54, 36), width=3)

    # title
    f_big = ImageFont.truetype(FONT, 44)
    f_sub = ImageFont.truetype(FONT, 16)
    f_tag = ImageFont.truetype(FONT, 12)
    g.text((272, 108), 'ZOMBIE', font=f_big, fill=GREEN_L)
    g.text((272, 164), 'CUSTOM DROPS', font=f_big, fill=KHAKI)
    g.text((274, 232), 'configurable item drops for zombies', font=f_sub, fill=(170, 172, 158))
    g.text((274, 258), 'forge 1.20.1  //  item ; chance ; amount', font=f_tag, fill=MUTE)

    # raining items on the right — kept clear of the text block
    items = ['ingot', 'bone', 'flesh', 'emerald', 'ingot', 'flesh', 'emerald', 'bone']
    xs = [780, 870, 960, 1050, 825, 915, 1005, 1095]
    ys = [44, 90, 36, 84, 180, 210, 176, 216]
    for kind, x, y in zip(items, xs, ys):
        ic = item_icon(kind, size=36)
        im.paste(ic, (x, y), ic)
        for ty in range(y - 14, y - 58, -13):
            if ty > 12:
                g.rectangle([x + 16, ty, x + 19, ty + 4], fill=(52, 58, 38))

    # ground strip
    for x in range(0, W, 16):
        g.rectangle([x, H - 14, x + 15, H - 1], fill=DIRT)
        g.rectangle([x, H - 14, x + 15, H - 11], fill=GRASS)
    im.save(ROOT / 'docs' / 'banner.png')
    print('banner.png -> docs/')


if __name__ == '__main__':
    make_logo()
    make_banner()
