"""合并批次词库到 words.json：去重、分配 id、版本 +1，输出统计。"""
import json
from pathlib import Path

CONTENT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "content"
MAIN = CONTENT / "words.json"

main = json.loads(MAIN.read_text(encoding="utf-8"))
existing_ja = {w["ja"] for w in main["words"]}
existing_kana_zh = {(w["kana"], w["zh"]) for w in main["words"]}
existing_ids = {int(w["id"][1:]) for w in main["words"]}

CAT_UNIT = {
    "人物": 1, "数字": 2, "时间": 3, "食物": 4, "地点": 5, "物品": 6,
    "动作": 7, "形容词": 8, "副词": 9, "自然": 10, "身体": 11,
}

next_id = max(existing_ids) + 1
added, skipped = [], []
for batch_file in sorted(Path(__file__).resolve().parent.glob("new_words_*.json")):
    batch = json.loads(batch_file.read_text(encoding="utf-8"))
    for w in batch["words"]:
        if w["ja"] in existing_ja or (w["kana"], w["zh"]) in existing_kana_zh:
            skipped.append(f"{w['ja']}({w['zh']})")
            continue
        if w["cat"] not in CAT_UNIT:
            raise SystemExit(f"{batch_file.name}: {w['ja']} 非法分类 {w['cat']}")
        main["words"].append({**w, "id": f"w{next_id:03d}", "unit": CAT_UNIT[w["cat"]]})
        existing_ja.add(w["ja"])
        existing_kana_zh.add((w["kana"], w["zh"]))
        next_id += 1
        added.append(w["ja"])

main["version"] += 1
MAIN.write_text(json.dumps(main, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print(f"新增 {len(added)} 条，跳过 {len(skipped)} 条: {skipped}")
print(f"总词数 {len(main['words'])}，version {main['version']}")
cats = {}
for w in main["words"]:
    cats[w["cat"]] = cats.get(w["cat"], 0) + 1
print("分类分布:", cats)
