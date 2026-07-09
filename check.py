"""planet.html 代码逻辑检查脚本 — python check.py"""

import os
import re
import sys

PLANET = r"D:\work\ai_code\SolarSystem3D\app\src\main\assets\planet.html"
MODELS = r"D:\work\ai_code\SolarSystem3D\app\src\main\assets\models"


def main():
    with open(PLANET, "r", encoding="utf-8") as f:
        html = f.read()

    errors, ok = [], []

    # 1. HTML 标签平衡
    for tag in ["script", "html", "body"]:
        o = html.count(f"<{tag}")
        c = html.count(f"</{tag}>")
        if o != c:
            errors.append(f"<{tag}>: {o}开 vs {c}关")
        else:
            ok.append(f"<{tag}>: {o} 平衡")

    # 2. 关键函数定义
    for kw in [
        "const data = {",
        "function parseSTL",
        "function createSTLMesh",
        "function showPlanet",
        "function onSelectPlanet",
    ]:
        c = html.count(kw)
        if c == 0:
            errors.append(f"缺少: {kw}")
        elif c > 1:
            errors.append(f"重复 {c}x: {kw}")
        else:
            ok.append(f"定义: {kw}")

    # 3. const 语法
    for m in re.finditer(r"\bconst\s+(\w+)\s*;", html):
        errors.append(
            f"const '{m.group(1)}' 无初始化 L{html[:m.start()].count(chr(10))+1}"
        )

    # 4. 碎片检测
    if re.search(r"}\s*,info:", html):
        pass  # 合法JS: features:{...},info:'...'
    for pat in [r",\s*,\s*cn:", r":\s*,\s*cn:"]:
        if re.search(pat, html):
            errors.append(f"可疑碎片: {pat}")

    # 5. STL 文件
    for name in ["bennu.stl", "kleopatra.stl"]:
        fp = os.path.join(MODELS, name)
        if os.path.exists(fp):
            sz = os.path.getsize(fp)
            with open(fp, "rb") as f:
                tri = int.from_bytes(f.read(84)[80:84], "little")
            if sz == 84 + tri * 50:
                ok.append(f"{name}: {sz}B {tri}tri")
            else:
                errors.append(f"{name}: {sz}B != {84+tri*50}B")
        else:
            errors.append(f"缺失: {name}")

    # 6. showPlanet 完整性
    sp = html[
        html.find("function showPlanet") : html.find(
            "\nfunction ", html.find("function showPlanet") + 50
        )
    ]
    for kw, desc in [
        ("d.model", "STL检测"),
        ("parseSTL", "解析"),
        ("scene.remove(currentMesh)", "替换球体"),
        ("scene.add(stlMesh)", "添加STL"),
    ]:
        (ok if kw in sp else errors).append(
            f"showPlanet: {desc} {'[OK]' if kw in sp else '缺失'}"
        )

    # 输出
    print(f"planet.html: {len(html)/1024:.0f}KB, {html.count(chr(10))} 行\n")
    for e in errors:
        print(f"  X {e}")
    for o in ok:
        print(f"  V {o}")
    print(f"\n{'ALL CLEAN' if not errors else f'{len(errors)} ISSUES'}")


if __name__ == "__main__":
    main()
