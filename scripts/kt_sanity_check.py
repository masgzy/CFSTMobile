#!/usr/bin/env python3
"""CFSTMobile Kotlin 静态自检：括号平衡 + 残留已删符号 + 常见笔误"""
import re
import sys
from pathlib import Path

ROOT = Path("/home/z/my-project/repo/CFSTMobile/app/src")
FILES = sorted(ROOT.rglob("*.kt"))

# 已删除/已改名符号：出现即报错（定义文件已删，引用即悬空）
DEAD_SYMBOLS = [
    "StatusCard", "ControlButtons", "SettingsDialog", "DcTestDialog",
    "SectionLabel", "IntField", "DoubleField", "settingsSummary",
    "OkGreenSemantic", "BannerStateAlias", "TestVmSources", "updateFlags",
]
# 新文件应存在的符号（漏写定义自查）
REQUIRED = {
    "CfWidgets.kt": ["fun StatusBanner", "fun ModeSwitch", "fun ParamsCard", "fun OptionItem",
                     "fun CfDropdown", "fun CfTextField", "fun GreenButton", "fun PrimaryButton",
                     "fun CountBadge", "fun CfTabs", "fun LogPanel", "fun CfTable", "data class CfOption"],
    "ParamsSection.kt": ["fun ParamsSection", "SegmentsDialog("],
    "ResultsSection.kt": ["fun ResultsSection"],
    "DcTestSection.kt": ["fun DcTestSection"],
    "AppRoot.kt": ["fun AppRoot", "ConfigMenu", "SettingsMenu", "bannerInfo"],
    "UiSettings.kt": ["BUILT_IN_IPS_V6", "object SpeedUrlPresets", "effectiveSegments"],
    "ConfigBackup.kt": ["fun save", "fun load", "fun delete", "fun exists"],
}

def strip_kotlin(text: str) -> str:
    """单遍状态机：移除注释与字符串/字符字面量，模板插值 ${} 内代码保留计括号"""
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if c == "/" and nxt == "/":          # 行注释
            while i < n and text[i] != "\n":
                i += 1
        elif c == "/" and nxt == "*":        # 块注释
            i += 2
            while i + 1 < n and not (text[i] == "*" and text[i + 1] == "/"):
                i += 1
            i += 2
        elif c == '"':                       # 字符串（含模板插值）
            i += 1
            depth = 0
            while i < n:
                ch = text[i]
                if ch == "\\":
                    i += 2
                    continue
                if depth == 0 and ch == '"':
                    i += 1
                    break
                if ch == "$" and i + 1 < n and text[i + 1] == "{":
                    depth += 1
                    i += 2
                    continue
                if depth > 0 and ch == "{":
                    depth += 1
                elif depth > 0 and ch == "}":
                    depth -= 1
                i += 1
        elif c == "'":                       # 字符字面量
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == "'":
                    i += 1
                    break
                i += 1
        else:
            out.append(c)
            i += 1
    return "".join(out)


fails = []
for f in FILES:
    text = f.read_text(encoding="utf-8")
    rel = f.relative_to(ROOT)
    # 1) 括号平衡（状态机去注释/字符串后计数）
    cleaned = strip_kotlin(text)
    for open_c, close_c in [("{", "}"), ("(", ")")]:
        o, c = cleaned.count(open_c), cleaned.count(close_c)
        if o != c:
            fails.append(f"{rel}: {open_c} 不平衡 ({o} vs {c})")
    # 2) 已删符号残留
    for sym in DEAD_SYMBOLS:
        if re.search(r'\b' + sym + r'\b', text):
            fails.append(f"{rel}: 残留已删符号 {sym}")
    # 3) 必需符号存在性
    name = f.name
    if name in REQUIRED:
        for sym in REQUIRED[name]:
            if sym not in text:
                fails.append(f"{rel}: 缺少必需符号 {sym}")
    # 4) 用了 MaterialTheme 却没导入
    if re.search(r'\bMaterialTheme\b', text) and 'import androidx.compose.material3.MaterialTheme' not in text:
        fails.append(f"{rel}: 用了 MaterialTheme 但未导入")
    # 5) 常用 Compose 符号使用但未导入（同包定义除外）
    COMMON_IMPORTS = {
        "Box": "androidx.compose.foundation.layout.Box",
        "Column": "androidx.compose.foundation.layout.Column",
        "Row": "androidx.compose.foundation.layout.Row",
        "Spacer": "androidx.compose.foundation.layout.Spacer",
        "Text": "androidx.compose.material3.Text",
        "Icon": "androidx.compose.material3.Icon",
        "Button": "androidx.compose.material3.Button",
        "Switch": "androidx.compose.material3.Switch",
        "LinearProgressIndicator": "androidx.compose.material3.LinearProgressIndicator",
        "CircularProgressIndicator": "androidx.compose.material3.CircularProgressIndicator",
        "FilterChip": "androidx.compose.material3.FilterChip",
        "DropdownMenu": "androidx.compose.material3.DropdownMenu",
        "Surface": "androidx.compose.material3.Surface",
        "Card": "androidx.compose.material3.Card",
        "LazyColumn": "androidx.compose.foundation.lazy.LazyColumn",
    }
    code_only = strip_kotlin(text)
    pkg = re.search(r'^package\s+([\w.]+)', text, re.M)
    pkg = pkg.group(1) if pkg else ""
    for sym, imp in COMMON_IMPORTS.items():
        if not re.search(r'\b' + sym + r'\b', code_only):
            continue
        if f'import {imp}' in text:
            continue
        # 同包可见（同包内定义的类/函数）：查同目录下文件是否定义
        defined_same_pkg = False
        for other in FILES:
            if other == f:
                if re.search(r'^(abstract )?(fun|class|object|val|data class|enum class)\s+' + sym + r'\b', text, re.M):
                    defined_same_pkg = True
                continue
            if f'package {pkg}' in other.read_text(encoding='utf-8')[:400] and re.search(r'\b(fun|class|object|val)\s+' + sym + r'\b', other.read_text(encoding='utf-8')):
                defined_same_pkg = True
                break
        if not defined_same_pkg:
            fails.append(f"{rel}: 使用 {sym} 但未导入 {imp}.{sym} 且同包无定义")

if fails:
    print("FAIL")
    for x in fails:
        print(" -", x)
    sys.exit(1)
print(f"OK: {len(FILES)} 个 Kotlin 文件通过静态自检")
