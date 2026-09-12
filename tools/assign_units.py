"""给 words/grammar 分配课程单元（PRD §19.8）。

- 词按分类映射 1..11（单元号全级别统一，N5/N4 各 11 个单元）
- 语法在每个级别内部按现有难度顺序均分到 11 个单元
- 两个文件各自 version +1，触发装机重装（进度按内容 id 关联，不丢）

只跑一次；重跑无害但会再把版本 +1 触发无谓重装。
用法: python tools/assign_units.py
"""
import json
from pathlib import Path

CONTENT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "content"

CAT_UNIT = {
    "人物": 1, "数字": 2, "时间": 3, "食物": 4, "地点": 5, "物品": 6,
    "动作": 7, "形容词": 8, "副词": 9, "自然": 10, "身体": 11,
}
UNITS_PER_LEVEL = 11

# ---- words ----
wp = CONTENT / "words.json"
w = json.loads(wp.read_text(encoding="utf-8"))
missing = sorted({x["cat"] for x in w["words"]} - set(CAT_UNIT))
assert not missing, f"未映射的分类: {missing}"
for x in w["words"]:
    x["unit"] = CAT_UNIT[x["cat"]]
w["version"] += 1
wp.write_text(json.dumps(w, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"words version {w['version']}")
for lv in ("N5", "N4"):
    sizes = [sum(1 for x in w["words"] if x["level"] == lv and x["unit"] == u) for u in range(1, UNITS_PER_LEVEL + 1)]
    print(f"  {lv} 各单元词数: {sizes}")

# ---- grammar：级别内按现有顺序均分 ----
gp = CONTENT / "grammar.json"
g = json.loads(gp.read_text(encoding="utf-8"))
for lv in ("N5", "N4"):
    items = [x for x in g["grammar"] if x["level"] == lv]
    n = len(items)
    assert n > 0, f"{lv} 无语法条目"
    for i, x in enumerate(items):
        x["unit"] = i * UNITS_PER_LEVEL // n + 1
g["version"] += 1
gp.write_text(json.dumps(g, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"grammar version {g['version']}")
for lv in ("N5", "N4"):
    items = [x for x in g["grammar"] if x["level"] == lv]
    sizes = [sum(1 for x in items if x["unit"] == u) for u in range(1, UNITS_PER_LEVEL + 1)]
    print(f"  {lv} 各单元语法数: {sizes}")
