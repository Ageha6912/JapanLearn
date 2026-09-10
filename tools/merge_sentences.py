"""合并新每日一句到 sentences.json：按 ja 去重、分配 id、版本 +1。

用法: python tools/merge_sentences.py [new_sentences_b2.json]
"""
import json
import sys
from pathlib import Path

CONTENT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "content"
MAIN = CONTENT / "sentences.json"
BATCH = Path(__file__).resolve().parent / (sys.argv[1] if len(sys.argv) > 1 else "new_sentences_b2.json")

main = json.loads(MAIN.read_text(encoding="utf-8"))
batch = json.loads(BATCH.read_text(encoding="utf-8"))
existing_ja = {s["ja"] for s in main["sentences"]}
existing_ids = {int(s["id"][1:]) for s in main["sentences"]}

next_id = max(existing_ids) + 1
skipped = []
added = 0
for s in batch["items"]:
    if s["ja"] in existing_ja:
        skipped.append(s["ja"])
        continue
    main["sentences"].append({**s, "id": f"s{next_id:02d}"})
    existing_ja.add(s["ja"])
    next_id += 1
    added += 1

main["version"] += 1
MAIN.write_text(json.dumps(main, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"新增 {added} 条，跳过 {len(skipped)} 条，总句子 {len(main['sentences'])}，version {main['version']}")
if skipped:
    print("跳过:", skipped)
