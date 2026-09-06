"""内容校验管线（PRD §17.6 / v0.2 决策）：校验 assets/content 下四个 JSON 的质量。

用法: python tools/validate_content.py
退出码 0 = 全部通过；1 = 存在错误。
"""
import json
import re
import sys
from pathlib import Path

CONTENT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "content"

KANA_RE = re.compile(r"^[\u3040-\u309F\u30FC\u30A0-\u30FF\u301C]+$")  # 平/片假名 + 长音符 + 波浪线(量词前缀)
ROMAJI_RE = re.compile(r"^[a-zāīūēōâîûêô'-]+( [a-zāīūēōâîûêô'-]+)*$", re.IGNORECASE)
JP_SENTENCE_RE = re.compile(r"^[\u3040-\u309F\u30A0-\u30FF\u3005\u4E00-\u9FFF。、！？\s]+$")  # 假名+汉字+标点

CATEGORIES = {"人物", "数字", "时间", "食物", "地点", "物品", "动作", "形容词", "副词", "自然", "身体"}
KANA_GROUPS = {"seion", "dakuon", "youon"}
SCENES = {"日常聊天", "餐厅", "便利店", "旅游", "学校", "工作", "动漫 / 娱乐"}

errors: list[str] = []


def err(msg: str) -> None:
    errors.append(msg)


def load(name: str):
    path = CONTENT / name
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        err(f"{name}: JSON 解析失败 {e}")
        return None


def non_empty(items, idx_key, fields, label):
    for it in items:
        for f in fields:
            if not str(it.get(f, "")).strip():
                err(f"{label} {it.get(idx_key, '?')}: 字段 {f} 为空")


def validate_kana(data):
    if not data:
        return
    seen_ids, seen_hira = set(), {}
    for k in data["kana"]:
        if k["id"] in seen_ids:
            err(f"kana 重复 id: {k['id']}")
        seen_ids.add(k["id"])
        if k["group"] not in KANA_GROUPS:
            err(f"kana {k['id']}: 非法分组 {k['group']}")
        if not (1 <= len(k["h"]) <= 2) or not KANA_RE.match(k["h"]):
            err(f"kana {k['id']}: 平假名异常 {k['h']!r}")
        if not (1 <= len(k["k"]) <= 2) or not KANA_RE.match(k["k"]):
            err(f"kana {k['id']}: 片假名异常 {k['k']!r}")
        if not ROMAJI_RE.match(k["r"]):
            err(f"kana {k['id']}: 罗马音异常 {k['r']!r}")
        if k["h"] in seen_hira:
            err(f"kana 平假名重复: {k['h']} ({seen_hira[k['h']]} / {k['id']})")
        seen_hira[k["h"]] = k["id"]
    non_empty(data["kana"], "id", ["exJa", "exZh"], "kana")
    print(f"kana: {len(data['kana'])} 条 ✓")


def validate_words(data, kana_set):
    if not data:
        return
    seen_ids, seen_pairs = set(), {}
    for w in data["words"]:
        if w["id"] in seen_ids:
            err(f"word 重复 id: {w['id']}")
        seen_ids.add(w["id"])
        if w["cat"] not in CATEGORIES:
            err(f"word {w['id']} {w['ja']}: 非法分类 {w['cat']}")
        if not KANA_RE.match(w["kana"]):
            err(f"word {w['id']} {w['ja']}: 假名字符异常 {w['kana']!r}")
        if not ROMAJI_RE.match(w["romaji"]):
            err(f"word {w['id']} {w['ja']}: 罗马音异常 {w['romaji']!r}")
        pair = (w["ja"], w["zh"])
        if pair in seen_pairs:
            err(f"word 重复词条: {w['ja']}/{w['zh']} ({seen_pairs[pair]} / {w['id']})")
        seen_pairs[pair] = w["id"]
        if not JP_SENTENCE_RE.match(w["example"]):
            err(f"word {w['id']}: 例句含异常字符 {w['example']!r}")
    non_empty(data["words"], "id", ["ja", "kana", "romaji", "zh", "pos", "cat", "example", "exampleZh"], "word")
    cats = {}
    for w in data["words"]:
        cats[w["cat"]] = cats.get(w["cat"], 0) + 1
    print(f"words: {len(data['words'])} 条 ✓  分布 {cats}")


def validate_grammar(data):
    if not data:
        return
    seen = set()
    for g in data["grammar"]:
        if g["id"] in seen:
            err(f"grammar 重复 id: {g['id']}")
        seen.add(g["id"])
        if not g["examples"]:
            err(f"grammar {g['id']}: 无例句")
        if not g["exercises"]:
            err(f"grammar {g['id']}: 无练习题")
        for i, ex in enumerate(g["exercises"]):
            if len(ex["options"]) != 4:
                err(f"grammar {g['id']} 练习{i}: 选项数 {len(ex['options'])} != 4")
            if not 0 <= ex["answer"] < len(ex["options"]):
                err(f"grammar {g['id']} 练习{i}: answer 越界")
            if len(set(ex["options"])) != len(ex["options"]):
                err(f"grammar {g['id']} 练习{i}: 选项重复")
    non_empty(data["grammar"], "id", ["title", "meaning", "connection", "explanation"], "grammar")
    print(f"grammar: {len(data['grammar'])} 条 ✓")


def validate_sentences(data):
    if not data:
        return
    seen = set()
    for s in data["sentences"]:
        if s["id"] in seen:
            err(f"sentence 重复 id: {s['id']}")
        seen.add(s["id"])
        if s["scene"] not in SCENES:
            err(f"sentence {s['id']}: 非法场景 {s['scene']}")
        if not s["breakdown"]:
            err(f"sentence {s['id']}: 无词汇拆解")
    non_empty(data["sentences"], "id", ["ja", "zh"], "sentence")
    print(f"sentences: {len(data['sentences'])} 条 ✓")


def main():
    kana = load("kana.json")
    words = load("words_n5.json")
    grammar = load("grammar_n5.json")
    sentences = load("sentences.json")

    versions = {}
    for name, d in [("kana", kana), ("words", words), ("grammar", grammar), ("sentences", sentences)]:
        if d is not None:
            v = d.get("version")
            if not isinstance(v, int):
                err(f"{name}: version 缺失或非整数")
            versions[name] = v

    validate_kana(kana)
    validate_words(words, kana)
    validate_grammar(grammar)
    validate_sentences(sentences)

    if errors:
        print(f"\n✗ {len(errors)} 个问题：")
        for e in errors:
            print("  -", e)
        sys.exit(1)
    print(f"\n全部通过。版本: {versions}")


if __name__ == "__main__":
    main()
