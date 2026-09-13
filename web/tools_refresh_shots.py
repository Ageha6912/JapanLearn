"""Rebuild web/assets/screenshots to a uniform phone canvas."""
from pathlib import Path
from PIL import Image
import numpy as np

SRC = Path(r"E:\JapanLearn\.screenshots")
DST = Path(r"E:\JapanLearn\web\assets\screenshots")
DST.mkdir(parents=True, exist_ok=True)

PICKS = {
    "home.png": "v131_release_home_fab.png",
    "learn.png": "v131_learn_tab.png",
    "course.png": "v131_course_unit.png",
    "word.png": "v100_02_word_session_unit1.png",
    "checkpoint.png": "v100_07_checkpoint.png",
    "listening.png": "v090_04_listening_quiz.png",
    "review.png": "v120_05_review_tab.png",
    "ai.png": "v131_release_popup.png",
}

CROP_BOTTOM = 130
# every export is exactly this canvas so the showcase phones align
OUT_W, OUT_H = 1080, 2263


def mean_brightness(img: Image.Image) -> float:
    small = img.resize((32, 32)).convert("L")
    px = list(small.getdata())
    return sum(px) / len(px)


def bottom_pad_color(img: Image.Image) -> tuple[int, int, int]:
    arr = np.asarray(img.convert("RGB"))
    row = arr[-1]
    return tuple(int(x) for x in np.median(row, axis=0))


def to_uniform(img: Image.Image) -> Image.Image:
    """Crop system bar, then pad/center-top onto OUT_W x OUT_H."""
    w, h = img.size
    # match width if needed
    if w != OUT_W:
        img = img.resize((OUT_W, int(h * OUT_W / w)), Image.LANCZOS)
        w, h = img.size
    if h > OUT_H:
        img = img.crop((0, 0, w, OUT_H))
        return img
    if h < OUT_H:
        pad = Image.new("RGB", (OUT_W, OUT_H), bottom_pad_color(img))
        pad.paste(img, (0, 0))
        return pad
    return img


for out_name, src_name in PICKS.items():
    src = SRC / src_name
    if not src.exists():
        raise SystemExit(f"missing source: {src}")
    img = Image.open(src).convert("RGB")
    w, h = img.size
    img = img.crop((0, 0, w, h - CROP_BOTTOM))
    img = to_uniform(img)
    img.save(DST / out_name, "PNG", optimize=True)
    print(f"{out_name:14} <- {src_name:20} {img.size} brightness={mean_brightness(img):.0f}")

for p in DST.iterdir():
    if p.name not in PICKS:
        print("remove unused", p.name)
        p.unlink()
