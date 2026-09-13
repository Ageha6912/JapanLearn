"""Consistency checks for the JapanLearn landing page.

Run: python web/tools_check_web.py  (from repo root or web/)

Guards against the page drifting out of sync with the app again:
1. cited content numbers match app/src/main/assets/content/*.json
2. cited version matches app/build.gradle.kts
3. every internal anchor has a matching id
4. every referenced local asset / script / stylesheet exists
5. no stale numbers or removed feature claims linger
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

WEB = Path(__file__).resolve().parent
ROOT = WEB.parent

html = (WEB / "index.html").read_text(encoding="utf-8")
failures: list[str] = []


def check(cond: bool, msg: str) -> None:
    if cond:
        print(f"  ok  {msg}")
    else:
        failures.append(msg)
        print(f"FAIL  {msg}")


def content_stats() -> dict:
    stats = {}
    base = ROOT / "app" / "src" / "main" / "assets" / "content"
    words = json.loads((base / "words.json").read_text(encoding="utf-8"))
    grammar = json.loads((base / "grammar.json").read_text(encoding="utf-8"))
    kana = json.loads((base / "kana.json").read_text(encoding="utf-8"))
    sentences = json.loads((base / "sentences.json").read_text(encoding="utf-8"))
    kanji = json.loads((base / "kanji.json").read_text(encoding="utf-8"))
    stats["words"] = len(words["words"])
    stats["grammar"] = len(grammar["grammar"])
    stats["kana"] = len(kana["kana"])
    stats["sentences"] = len(sentences["sentences"])
    stats["kanji"] = len(kanji["kanji"])
    stats["units"] = len({(w["level"], w["unit"]) for w in words["words"]})
    stats["words_version"] = words["version"]
    return stats


def app_version() -> str:
    gradle = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    return re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)


print("== content numbers cited on the page ==")
stats = content_stats()
version = app_version()
for token, key, label in [
    ("1104", "words", "单词数"),
    ("148", "grammar", "语法数"),
    ("300", "sentences", "每日一句数"),
    ("397", "kanji", "汉字专项数"),
    ("101", "kana", "五十音数"),
    ("22", "units", "课程单元数"),
]:
    check(token in html, f"页面引用了 {label} {token}")
    check(str(stats[key]) == token, f"内容库实际 {label} = {stats[key]}（与 {token} 一致）")

print("== app version ==")
check(f"v{version}" in html, f"页面引用 v{version}（build.gradle.kts versionName）")
check("v0.7.5" not in html and "v0.7" not in html, "旧版本号 v0.7.x 已清除")

print("== stale claims removed ==")
for stale in [">804<", ">87<", "×1.5", "熟练 ×2", "上限 60 天",
              "804 词", "语法 87", "每日一句 120", "语法 120", "120 语法",
              "180 条", "226 项", "v1.3.1",
              "word-card.png", "stats.png"]:
    check(stale not in html, f"旧表述「{stale}」已移除")
for fresh in ["FSRS", "ts-fsrs", "课程单元", "听力", "错题突击", "AI 助手", "流式",
              "汉字专项", "397", "300", "148", "275"]:
    check(fresh in html, "新能力「" + fresh + "」已上页")

print("== internal anchors ==")
ids = set(re.findall(r'id="([^"]+)"', html))
for href in re.findall(r'href="#([^"]+)"', html):
    check(href in ids, f"锚点 #{href} 存在")

print("== local assets ==")
for src in re.findall(r'(?:src|href)="(assets/[^"]+|styles\.css|main\.js)"', html):
    check((WEB / src).exists(), f"资源存在: {src}")

print("== external links ==")
allowed_non_repo = ("https://fonts.googleapis.com", "https://fonts.gstatic.com")
for href in set(re.findall(r'href="(https://[^"]+)"', html)):
    if href.startswith(allowed_non_repo):
        check(True, f"字体 CDN 外链（白名单）: {href.split('?')[0]}")
        continue
    check(href.startswith("https://github.com/Ageha6912/JapanLearn"),
          f"外链指向本项目仓库: {href}")

print()
if failures:
    print(f"{len(failures)} check(s) FAILED")
    sys.exit(1)
print("all web checks passed")
