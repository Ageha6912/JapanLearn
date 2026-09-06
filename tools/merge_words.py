"""合并批次词库到 words_n5.json：去重、分配 id、版本 +1，输出统计。"""
import json
from pathlib import Path

CONTENT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "content"
MAIN = CONTENT / "words_n5.json"

main = json.loads(MAIN.read_text(encoding="utf-8"))
existing_ja = {w["ja"] for w in main["words"]}
existing_kana_zh = {(w["kana"], w["zh"]) for w in main["words"]}
existing_ids = {int(w["id"][1:]) for w in main["words"]}

next_id = max(existing_ids) + 1
added, skipped = [], []
for batch_file in sorted(Path(__file__).resolve().parent.glob("new_words_b*.json")):
    batch = json.loads(batch_file.read_text(encoding="utf-8"))
    for w in batch["words"]:
        if w["ja"] in existing_ja or (w["kana"], w["zh"]) in existing_kana_zh:
            skipped.append(f"{w['ja']}({w['zh']})")
            continue
        main["words"].append({**w, "id": f"w{next_id:03d}"})
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
