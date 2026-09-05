"""Build assets/readme/showcase.png: 6 real app screenshots on a washi board."""
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import os

ROOT = r"E:\JapanLearn"
SRC = os.path.join(ROOT, ".screenshots")
OUT = os.path.join(ROOT, "assets", "readme", "showcase.png")

WASHI = (247, 245, 240)
INK = (87, 82, 75)
BLUE = (27, 58, 92)

PICKS = [
    ("01_home.png", "首页 · 今日学习"),
    ("03_kana.png", "五十音"),
    ("04_word_card.png", "单词学习卡"),
    ("05_quiz.png", "即时练习"),
    ("07_review.png", "SRS 复习"),
    ("10_stats.png", "学习统计"),
]

PHONE_W, PHONE_H = 360, 800
COLS, ROWS = 3, 2
MARGIN_X, MARGIN_Y = 80, 80
GAP_X, GAP_Y = 62, 96
LABEL_H = 46
CORNER = 30

def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius=radius, fill=255)
    return m

def phone_tile(path, rotation):
    img = Image.open(path).convert("RGB").resize((PHONE_W, PHONE_H), Image.LANCZOS)
    # 圆角 + 1px 描边底板
    mask = rounded_mask(img.size, CORNER)
    tile = Image.new("RGBA", img.size, (0, 0, 0, 0))
    tile.paste(img, (0, 0), mask)
    stroke = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(stroke).rounded_rectangle(
        [0, 0, PHONE_W - 1, PHONE_H - 1], radius=CORNER, outline=(27, 58, 92, 60), width=2
    )
    tile = Image.alpha_composite(tile, stroke)
    return tile.rotate(rotation, expand=True, resample=Image.BICUBIC)

def shadow_for(tile):
    pad = 40
    sh = Image.new("RGBA", (tile.width + pad * 2, tile.height + pad * 2), (0, 0, 0, 0))
    alpha = tile.split()[3]
    black = Image.new("RGBA", tile.size, (27, 45, 70, 90))
    black.putalpha(alpha.point(lambda a: int(a * 0.38)))
    sh.paste(black, (pad, pad + 16), black)
    return sh.filter(ImageFilter.GaussianBlur(14)), pad

font = None
for cand in [r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\segoeui.ttf", r"C:\Windows\Fonts\arial.ttf"]:
    if os.path.exists(cand):
        try:
            font = ImageFont.truetype(cand, 30)
            break
        except OSError:
            continue

canvas_w = MARGIN_X * 2 + COLS * PHONE_W + (COLS - 1) * GAP_X + 40
row_h = PHONE_H + LABEL_H
canvas_h = MARGIN_Y * 2 + ROWS * row_h + (ROWS - 1) * (GAP_Y - LABEL_H) + 60
canvas = Image.new("RGB", (canvas_w, canvas_h), WASHI)
draw = ImageDraw.Draw(canvas)

rotations = [-1.4, 1.2, -1.2, 1.4, -1.2, 1.2]
centers_x = [MARGIN_X + PHONE_W // 2 + 20,
            MARGIN_X + PHONE_W // 2 + 20 + PHONE_W + GAP_X,
            MARGIN_X + PHONE_W // 2 + 20 + 2 * (PHONE_W + GAP_X)]
centers_y = [MARGIN_Y + PHONE_H // 2 + 20,
             MARGIN_Y + PHONE_H // 2 + 20 + row_h + (GAP_Y - LABEL_H) + 20]

for i, (fname, label) in enumerate(PICKS):
    tile = phone_tile(os.path.join(SRC, fname), rotations[i])
    sh, pad = shadow_for(tile)
    cx = centers_x[i % COLS]
    cy = centers_y[i // COLS]
    pos = (cx - tile.width // 2, cy - tile.height // 2)
    canvas.paste(sh, (pos[0] - pad + 40, pos[1] - pad + 40), sh)
    canvas.paste(tile, pos, tile)
    if font:
        tw = draw.textlength(label, font=font)
        draw.text((cx - tw / 2, cy + PHONE_H // 2 + 34), label, fill=INK, font=font)

canvas.save(OUT, "PNG", optimize=True)
print("saved", OUT, canvas.size)
