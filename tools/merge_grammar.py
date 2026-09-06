"""合并新语法到 grammar.json：分配 id、版本 +1。"""
import json
from pathlib import Path

CONTENT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "content"
MAIN = CONTENT / "grammar.json"
import sys
BATCH = Path(__file__).resolve().parent / (sys.argv[1] if len(sys.argv) > 1 else "new_grammar.json")

main = json.loads(MAIN.read_text(encoding="utf-8"))
batch = json.loads(BATCH.read_text(encoding="utf-8"))
existing_titles = {g["title"] for g in main["grammar"]}
existing_ids = {int(g["id"][1:]) for g in main["grammar"]}

next_id = max(existing_ids) + 1
skipped = []
for g in batch["items"]:
    if g["title"] in existing_titles:
        skipped.append(g["title"])
        continue
    main["grammar"].append({**g, "id": f"g{next_id:02d}"})
    existing_titles.add(g["title"])
    next_id += 1

main["version"] += 1
MAIN.write_text(json.dumps(main, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"新增 {len(batch['items']) - len(skipped)} 条，跳过 {skipped}，总语法 {len(main['grammar'])}，version {main['version']}")
