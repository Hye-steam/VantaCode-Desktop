#!/usr/bin/env python3
"""用 GitHub Releases API 的数据同步仓库里的下载文档（幂等可重跑）。

同步目标（只改这三个文件里指定的表，不碰其他文字）：
  - README.md 的 `## 下载` 最新版表
  - DOWNLOADS.md 的 `## 最新版` 表
  - docs/repo-guide.md 第 4 节的 Release 对照表：只追加缺失的行，不改写历史行；
    追加行时顺带更新小节标题里的数量（如 `（3 个）`）和第 4 节标题里的总数。

产品归属按 Tag 判断（新 Tag 必须带前缀，见 docs/repo-guide.md 第 2 节）：
  - desktop-v*、以及无前缀历史 Tag v1.2.0 / v1.3.0 / v1.4.0 → VantaCode 桌面版
  - android-v*、以及无前缀历史 Tag v4.x / v5.x → VantaCode 安卓版
  - vantagate-v* → VantaGate 安卓版
  - vantagate-desktop-v* → VantaGate 桌面版
  无法归属的 Tag 会打印警告并跳过，不会静默丢进某个产品。

注意：2026-11-26 Feng 删除了 vantagate-desktop 分支和它的 Tag，但本脚本的
事实来源是 Releases API——只要 VantaGate 桌面版的 Release 对象还在 API 里，
它就会继续出现在下载表里，脚本不会因为分支删了就偷偷丢掉一个产品。
（分支删了之后这些 Release 的下载直链是否还有效，脚本无法保证；
如 Release 也被删了，它们会自然从 API 消失，表里随之消失。）

SHA-256：GitHub Releases API 不提供资产的 SHA-256，本脚本对需要 SHA 的
最新资产（目前只有 DOWNLOADS.md 里桌面版那一行带 SHA-256）流式下载后算哈希；
--no-hash 时保留文档里原有的 SHA 文字不动；--dry-run 时不下载、不写文件。

用法：
  python3 scripts/sync-downloads.py [--dry-run] [--no-hash] [--root DIR]
  GITHUB_TOKEN 环境变量存在时用它调 API（提高限额）；公开仓库无 token 也能跑。
  --from-file FILE 仅供测试：用本地 Releases JSON 代替 API。

依赖：仅 Python 3 标准库。
"""

import argparse
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO = "Hye-steam/VantaCode-Desktop"
API_URL = f"https://api.github.com/repos/{REPO}/releases"
UA = "vantacode-sync-downloads/1.0"
MIB = 1024 * 1024

LEGACY_DESKTOP_TAGS = {"v1.2.0", "v1.3.0", "v1.4.0"}

# name: 表格第一列的产品名（README 与 DOWNLOADS 两张表共用）
# version_style: "bare" 显示 1.4.0；"v" 显示 v5.9.4
# asset_ext: 该产品最新版表里要挑的资产后缀
# guide_section: repo-guide.md 第 4 节里该产品小节标题包含的文字
PRODUCTS = [
    {
        "key": "desktop",
        "name": "VantaCode 桌面版（Windows x64）",
        "version_style": "bare",
        "asset_ext": ".exe",
        "guide_section": "VantaCode 桌面版",
    },
    {
        "key": "android",
        "name": "VantaCode 安卓版",
        "version_style": "v",
        "asset_ext": ".apk",
        "guide_section": "VantaCode 安卓版",
    },
    {
        "key": "vantagate-android",
        "name": "VantaGate 安卓版",
        "version_style": "bare",
        "asset_ext": ".apk",
        "guide_section": "VantaGate 安卓版",
    },
    {
        "key": "vantagate-desktop",
        "name": "VantaGate 桌面版（Windows x64）",
        "version_style": "v",
        "asset_ext": ".exe",
        "guide_section": "VantaGate 桌面版",
    },
]
PRODUCT_BY_KEY = {p["key"]: p for p in PRODUCTS}


def warn(msg):
    print(f"warning: {msg}", file=sys.stderr)


def classify_tag(tag):
    """Tag → 产品 key；无法归属返回 None（调用方打印警告并跳过）。"""
    if tag.startswith("desktop-v"):
        return "desktop"
    if tag in LEGACY_DESKTOP_TAGS:
        return "desktop"
    if tag.startswith("vantagate-desktop-v"):
        return "vantagate-desktop"
    if tag.startswith("vantagate-v"):
        return "vantagate-android"
    if tag.startswith("android-v"):
        return "android"
    if re.match(r"^v[45]\.\d+", tag):
        return "android"
    # v1.x 但不是已知的三个桌面历史 Tag：归属不明，不猜。
    return None


def raw_version(tag, product_key):
    """去掉产品前缀和开头的 v，得到裸版本号，如 desktop-v1.5.0 → 1.5.0。"""
    prefixes = {
        "desktop": "desktop-v",
        "android": "android-v",
        "vantagate-android": "vantagate-v",
        "vantagate-desktop": "vantagate-desktop-v",
    }
    v = tag
    prefix = prefixes[product_key]
    if v.startswith(prefix):
        v = v[len(prefix):]
    elif v.startswith("v"):
        v = v[1:]
    return v


def display_version(tag, product_key):
    v = raw_version(tag, product_key)
    if PRODUCT_BY_KEY[product_key]["version_style"] == "v" and not v.startswith("v"):
        return "v" + v
    return v


def parse_semver(version):
    """裸版本号 → (主, 次, 修订, ...) 元组，用于最新版的合理性检查；解析失败返回 None。"""
    m = re.match(r"^(\d+(?:\.\d+)*)", version)
    if not m:
        return None
    try:
        return tuple(int(x) for x in m.group(1).split("."))
    except ValueError:
        return None


def fmt_mib(size_bytes):
    return f"{size_bytes / MIB:.1f} MiB"


def fmt_bytes(size_bytes):
    return f"{size_bytes:,}"


def download_url(tag, asset_name):
    return f"https://github.com/{REPO}/releases/download/{tag}/{asset_name}"


def fetch_releases():
    """一遍分页拉取全部 Release（draft 跳过）。失败时直接报错退出。"""
    token = os.environ.get("GITHUB_TOKEN", "")
    releases = []
    page = 1
    while True:
        url = f"{API_URL}?per_page=100&page={page}"
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        if token:
            req.add_header("Authorization", f"Bearer {token}")
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                batch = json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", "replace")[:500]
            if e.code in (403, 429):
                print(
                    f"error: GitHub API 请求被拒（HTTP {e.code}），很可能是限额用完。"
                    "请设置 GITHUB_TOKEN 环境变量后重试，不要反复无 token 请求。",
                    file=sys.stderr,
                )
            else:
                print(f"error: GitHub API 请求失败（HTTP {e.code}）：{body}", file=sys.stderr)
            sys.exit(1)
        except urllib.error.URLError as e:
            print(f"error: 无法访问 GitHub API：{e}", file=sys.stderr)
            sys.exit(1)
        if not batch:
            break
        releases.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    return [r for r in releases if not r.get("draft")]


def load_releases_from_file(path):
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    if isinstance(data, dict):
        data = data.get("releases", [])
    return [r for r in data if not r.get("draft")]


def primary_asset(release, product_key):
    """该 Release 里最新版表要用的主资产：先按产品后缀挑，取最大的；
    没有符合后缀的就取最大的资产并警告。"""
    assets = release.get("assets") or []
    if not assets:
        return None
    ext = PRODUCT_BY_KEY[product_key]["asset_ext"]
    candidates = [a for a in assets if a["name"].endswith(ext)]
    if not candidates:
        warn(
            f"{release.get('tag_name')}: 没有找到 {ext} 资产，"
            f"改用最大的资产 {max(assets, key=lambda a: a['size'])['name']}"
        )
        candidates = assets
    return max(candidates, key=lambda a: a["size"])


def sha256_of_url(url):
    """流式下载并算 SHA-256（不把整个文件读进内存）。"""
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    token = os.environ.get("GITHUB_TOKEN", "")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    h = hashlib.sha256()
    with urllib.request.urlopen(req, timeout=600) as resp:
        while True:
            chunk = resp.read(MIB)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest().upper()


def group_releases(releases):
    """按产品分组；返回 {key: [release, ...]}（每组按发布日期升序）。"""
    grouped = {p["key"]: [] for p in PRODUCTS}
    for rel in releases:
        tag = rel.get("tag_name") or ""
        key = classify_tag(tag)
        if key is None:
            warn(f"跳过无法归属的 Tag：{tag}")
            continue
        grouped[key].append(rel)
    for key in grouped:
        grouped[key].sort(key=lambda r: r.get("published_at") or "")
    return grouped


def latest_per_product(grouped):
    """每产品最新版：按发布日期取最新；再用 semver 核对一次，不一致只警告。"""
    latest = {}
    for key, rels in grouped.items():
        if not rels:
            warn(f"{PRODUCT_BY_KEY[key]['name']}：API 里没有 Release，该行保持不动")
            continue
        by_date = rels[-1]
        scored = []
        for r in rels:
            v = parse_semver(raw_version(r["tag_name"], key))
            if v is not None:
                scored.append((v, r))
        if scored:
            by_ver = max(scored, key=lambda t: t[0])[1]
            if by_ver["tag_name"] != by_date["tag_name"]:
                warn(
                    f"{PRODUCT_BY_KEY[key]['name']}：按日期最新是 {by_date['tag_name']}，"
                    f"按版本号最新是 {by_ver['tag_name']}，以日期为准"
                )
        latest[key] = by_date
    return latest


# ---------------------------------------------------------------- 表格读写

def split_cells(row_line):
    """Markdown 表格行 → 单元格列表（去掉首尾竖线与空白）。"""
    return [c.strip() for c in row_line.strip().strip("|").split("|")]


def find_heading(lines, heading):
    for i, line in enumerate(lines):
        if line.rstrip("\n") == heading:
            return i
    raise ValueError(f"找不到标题行：{heading}")


def table_span(lines, start):
    """从 start 往下找连续的表格行，返回 (first, last)，左闭右开。"""
    i = start
    while i < len(lines) and not lines[i].startswith("|"):
        i += 1
    j = i
    while j < len(lines) and lines[j].startswith("|"):
        j += 1
    if i == j:
        raise ValueError("该标题下找不到表格")
    return i, j


def extract_sha(cell):
    m = re.search(r"SHA-256 `([0-9A-Fa-f]+)`", cell)
    return m.group(1).upper() if m else None


def readme_row(product, release, asset):
    tag = release["tag_name"]
    date = (release.get("published_at") or "")[:10]
    return (
        f"| {product['name']} | {display_version(tag, product['key'])}（{date}） | "
        f"[{asset['name']}" + f"]({download_url(tag, asset['name'])})（{fmt_mib(asset['size'])}） |"
    )


def downloads_row(product, release, asset, sha=None, old_sha=None):
    tag = release["tag_name"]
    date = (release.get("published_at") or "")[:10]
    size_cell = f"{fmt_mib(asset['size'])}（{fmt_bytes(asset['size'])} 字节）"
    use_sha = sha if sha is not None else old_sha
    if use_sha:
        size_cell += f"，SHA-256 `{use_sha}`"
    return (
        f"| {product['name']} | {display_version(tag, product['key'])} | "
        f"[{asset['name']}" + f"]({download_url(tag, asset['name'])}) | {date} | {size_cell} |"
    )


def guide_row(release):
    tag = release["tag_name"]
    date = (release.get("published_at") or "")[:10]
    assets = release.get("assets") or []
    names = " + ".join(f"`{a['name']}`" for a in assets)
    sizes = " + ".join(fmt_mib(a["size"]) for a in assets)
    return f"| `{tag}` | {date} | {names} | {sizes} |"


def rewrite_latest_table(lines, heading, row_builder):
    """重写 heading 下的最新版表。row_builder(product, release, asset) -> 新行。
    返回 (新 lines, 实际改动行数)。只替换按产品名匹配到的行；缺失的产品行追加。"""
    h = find_heading(lines, heading)
    first, last = table_span(lines, h + 1)
    # 旧行按产品名索引（第 0 行表头，第 1 行分隔线，第 2 行起是数据）
    old_by_name = {}
    for idx in range(first + 2, last):
        cells = split_cells(lines[idx])
        if cells:
            old_by_name[cells[0]] = idx
    new_lines = list(lines)
    changed = 0
    # 从后往前替换，避免行号漂移
    for product in PRODUCTS:
        key = product["key"]
        if key not in row_builder:
            continue
        new_row = row_builder[key]
        name = product["name"]
        if name in old_by_name:
            idx = old_by_name[name]
            if new_lines[idx].rstrip("\n") != new_row:
                new_lines[idx] = new_row + "\n"
                changed += 1
        else:
            new_lines.insert(last, new_row + "\n")
            last += 1
            changed += 1
    return new_lines, changed


def append_guide_rows(lines, grouped):
    """往 repo-guide.md 第 4 节各产品小节追加缺失的 Release 行。
    返回 (新 lines, 追加行数)。同时更新小节标题和第 4 节标题里的数量。"""
    new_lines = list(lines)
    appended_total = 0
    for product in PRODUCTS:
        key = product["key"]
        rels = grouped.get(key, [])
        if not rels:
            continue
        # 找到该产品的小节标题：以 "### 4." 开头且包含产品小节名
        sec = None
        for i, line in enumerate(new_lines):
            if line.startswith("### 4.") and product["guide_section"] in line:
                sec = i
                break
        if sec is None:
            warn(f"repo-guide.md 里找不到 {product['name']} 的小节，跳过追加")
            continue
        first, last = table_span(new_lines, sec + 1)
        existing_tags = set()
        for idx in range(first + 2, last):
            cells = split_cells(new_lines[idx])
            if cells:
                existing_tags.add(cells[0].strip("`"))
        missing = [r for r in rels if r["tag_name"] not in existing_tags]
        if not missing:
            continue
        # 追加到表尾（表内按日期升序，missing 本身已按日期排好）
        insert_at = last
        for r in missing:
            new_lines.insert(insert_at, guide_row(r) + "\n")
            insert_at += 1
            appended_total += 1
        # 更新小节标题里的数量，如 "### 4.1 VantaCode 桌面版（3 个）"
        old_heading = new_lines[sec]
        m = re.search(r"（(\d+) 个）", old_heading)
        if m:
            total = int(m.group(1)) + len(missing)
            new_lines[sec] = old_heading[:m.start()] + f"（{total} 个）" + old_heading[m.end():]
    if appended_total:
        # 更新第 4 节标题里的总数，如 "## 4. Release 对照表（43 个，..."
        for i, line in enumerate(new_lines):
            if line.startswith("## 4. "):
                m = re.search(r"（(\d+) 个", line)
                if m:
                    total = int(m.group(1)) + appended_total
                    new_lines[i] = line[:m.start()] + f"（{total} 个" + line[m.end():]
                break
    return new_lines, appended_total


def current_downloads_shas(lines):
    """从当前 DOWNLOADS.md 最新版表里读出每产品旧的 SHA-256（有才有）。"""
    h = find_heading(lines, "## 最新版")
    first, last = table_span(lines, h + 1)
    shas = {}
    for idx in range(first + 2, last):
        cells = split_cells(lines[idx])
        if len(cells) >= 5:
            for product in PRODUCTS:
                if cells[0] == product["name"]:
                    sha = extract_sha(cells[4])
                    if sha:
                        shas[product["key"]] = sha
    return shas


def main(argv=None):
    ap = argparse.ArgumentParser(description="用 GitHub Releases API 同步下载文档")
    ap.add_argument("--dry-run", action="store_true", help="只打印会改成什么样，不写文件、不下载资产")
    ap.add_argument("--no-hash", action="store_true", help="不下载资产算 SHA，保留文档里原有的 SHA 文字")
    ap.add_argument("--root", default=None, help="仓库根目录（默认按脚本位置推导）")
    ap.add_argument("--from-file", default=None, help="仅供测试：用本地 Releases JSON 代替 API")
    args = ap.parse_args(argv)

    root = Path(args.root) if args.root else Path(__file__).resolve().parent.parent
    readme_path = root / "README.md"
    downloads_path = root / "DOWNLOADS.md"
    guide_path = root / "docs" / "repo-guide.md"
    for p in (readme_path, downloads_path, guide_path):
        if not p.is_file():
            print(f"error: 找不到文件：{p}", file=sys.stderr)
            return 1

    if args.from_file:
        releases = load_releases_from_file(args.from_file)
    else:
        releases = fetch_releases()
    print(f"拉到 {len(releases)} 个 Release（已跳过 draft）", file=sys.stderr)

    grouped = group_releases(releases)
    latest = latest_per_product(grouped)

    readme_lines = readme_path.read_text(encoding="utf-8").splitlines(keepends=True)
    downloads_lines = downloads_path.read_text(encoding="utf-8").splitlines(keepends=True)
    guide_lines = guide_path.read_text(encoding="utf-8").splitlines(keepends=True)
    old_sha_by_key = current_downloads_shas(downloads_lines)

    # SHA：只对当前表里本来就带 SHA-256 的产品行算哈希；dry-run / --no-hash 时不下载
    shas = {}
    if not args.dry_run and not args.no_hash:
        for product in PRODUCTS:
            key = product["key"]
            if key in latest and key in old_sha_by_key:
                rel = latest[key]
                asset = primary_asset(rel, key)
                if asset is None:
                    continue
                url = download_url(rel["tag_name"], asset["name"])
                print(f"正在下载 {asset['name']} 算 SHA-256 …", file=sys.stderr)
                try:
                    shas[key] = sha256_of_url(url)
                except Exception as e:
                    warn(f"下载 {asset['name']} 算 SHA 失败（{e}），保留旧 SHA")

    readme_builder = {}
    downloads_builder = {}
    for product in PRODUCTS:
        key = product["key"]
        if key not in latest:
            continue
        rel = latest[key]
        asset = primary_asset(rel, key)
        if asset is None:
            warn(f"{rel['tag_name']} 没有资产，最新版表里该行保持不动")
            continue
        readme_builder[key] = readme_row(product, rel, asset)
        downloads_builder[key] = downloads_row(
            product, rel, asset, sha=shas.get(key), old_sha=old_sha_by_key.get(key)
        )

    new_readme, c1 = rewrite_latest_table(readme_lines, "## 下载", readme_builder)
    new_downloads, c2 = rewrite_latest_table(downloads_lines, "## 最新版", downloads_builder)
    new_guide, c3 = append_guide_rows(guide_lines, grouped)

    if args.dry_run:
        h = find_heading(new_readme, "## 下载")
        f, l = table_span(new_readme, h + 1)
        print("=== README.md ## 下载（将生成的表）===")
        print("".join(new_readme[f:l]), end="")
        h = find_heading(new_downloads, "## 最新版")
        f, l = table_span(new_downloads, h + 1)
        print("=== DOWNLOADS.md ## 最新版（将生成的表）===")
        print("".join(new_downloads[f:l]), end="")
        print("=== docs/repo-guide.md 第 4 节（将追加的行）===")
        old_set = set(guide_lines)
        appended_rows = [ln for ln in new_guide if ln not in old_set]
        if appended_rows:
            print("".join(appended_rows), end="")
        else:
            print("（无新增行）")
        print("=== 各产品最新版 ===")
        for product in PRODUCTS:
            key = product["key"]
            if key in latest:
                print(f"{product['name']}：{latest[key]['tag_name']}")
        print(
            f"改动统计：README {c1} 行，DOWNLOADS {c2} 行，repo-guide 追加 {c3} 行"
            "（dry-run，未写文件）"
        )
        return 0

    wrote = []
    if new_readme != readme_lines:
        readme_path.write_text("".join(new_readme), encoding="utf-8")
        wrote.append(f"README.md（{c1} 行）")
    if new_downloads != downloads_lines:
        downloads_path.write_text("".join(new_downloads), encoding="utf-8")
        wrote.append(f"DOWNLOADS.md（{c2} 行）")
    if new_guide != guide_lines:
        guide_path.write_text("".join(new_guide), encoding="utf-8")
        wrote.append(f"docs/repo-guide.md（追加 {c3} 行）")
    if wrote:
        print("已更新：" + "、".join(wrote))
    else:
        print("没有变化，三个文件都未改动")
    return 0


if __name__ == "__main__":
    sys.exit(main())
