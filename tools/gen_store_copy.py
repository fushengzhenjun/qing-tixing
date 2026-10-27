# -*- coding: utf-8 -*-
"""从上架指南.html 的 DATA 数组抽出商店表单文案，生成 发布/文档/商店文案.txt"""
import os, re, json, io

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUIDE = os.path.join(BASE, "发布", "上架指南.html")
OUT = os.path.join(BASE, "发布", "文档", "商店文案.txt")

s = open(GUIDE, encoding="utf-8").read()
start = s.find("var DATA = [")
end = s.find("\n  ];", start)
body = s[start + len("var DATA = ["):end].strip()
if body.endswith(","):
    body = body[:-1]

# 逐条解析 {t:"..", h:"..", v:".."}
item_re = re.compile(r'\{t:"(.*?)", h:"(.*?)", v:"(.*?)"\}', re.S)
items = item_re.findall(body)


def unescape(x):
    return x.replace("\\n", "\n").replace('\\"', '"').replace("\\\\", "\\")


lines = []
lines.append("轻提醒 · 商店表单文案（直接复制粘贴）")
lines.append("版本 1.0.1 · 生成自 上架指南.html")
lines.append("=" * 64)
for t, h, v in items:
    lines.append("")
    lines.append("-" * 64)
    lines.append("【" + unescape(t) + "】")
    lines.append("说明：" + unescape(h))
    lines.append("-" * 64)
    lines.append(unescape(v))

text = "\r\n".join(lines) + "\r\n"
open(OUT, "w", encoding="utf-8-sig", newline="").write(text)
print("字段数:", len(items), "->", OUT, len(text), "字符")
for t, _, _ in items:
    print("  ·", unescape(t))
