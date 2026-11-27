#!/usr/bin/env python3
"""生成官网的 website/data.json（幂等可重跑）。

数据来源（只读公开 API，不改仓库其他文件）：
  - Hye-steam/VantaCode-Desktop 的 Releases：四个产品的最新版、下载链接、大小、发布日期
  - Hye-steam/Vanta-Plugins-Store 的 index.json：插件总数
分类规则（Tag → 产品）复用 scripts/sync-downloads.py，和下载文档的口径保持一致。

SHA-256：只有桌面版两个产品在站上展示 SHA。版本号没变时沿用 data.json 里已有的
SHA，不重新下载几百 MB 的安装包；只有新版本出现时才下载一次算哈希。
下载失败时保留旧 SHA 并警告，不让整站同步因此失败（SHA 为空时页面自动隐藏该行）。

桌面版 1.5.0：在 desktop_in_progress 里体现——最新桌面版 < 1.5.0 时为 true，
页面自动显示“1.5.0 插件兼容升级进行中”；1.5.0 一发布就自动变成 false，相关文案隐藏。

用法：
  python3 scripts/sync-website.py [--dry-run] [--no-hash] [--root DIR]
  GITHUB_TOKEN 存在时用它调 API（提高限额）；公开仓库无 token 也能跑。
"""

import argparse
import hashlib
import importlib.util
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

REPO = "Hye-steam/VantaCode-Desktop"
PLUGIN_INDEX_URL = (
    "https://raw.githubusercontent.com/Hye-steam/Vanta-Plugins-Store/main/index.json"
)
UA = "vantacode-sync-website/1.0"
MIB = 1024 * 1024

# 产品 key → 官网 data.json 里的展示字段。asset_ext 是最新版表里要挑的资产后缀。
PRODUCTS = {
    "android": {
        "name": "VantaCode 安卓版",
        "version_style": "v",
        "asset_ext": ".apk",
        "asset_label": "APK",
        "show_sha": False,
    },
    "desktop": {
        "name": "VantaCode 桌面版",
        "version_style": "bare",
        "asset_ext": ".exe",
        "asset_label": "EXE",
        "show_sha": True,
    },
    "gate-android": {
        "name": "VantaGate 安卓版",
        "version_style": "bare",
        "asset_ext": ".apk",
        "asset_label": "APK",
        "show_sha": False,
    },
    "gate-desktop": {
        "name": "VantaGate 桌面版",
        "version_style": "v",
        "asset_ext": ".zip",
        "asset_label": "ZIP",
        "show_sha": True,
    },
}

# 桌面版 1.5.0 未发布前，页面显示“插件兼容升级进行中”
DESKTOP_IN_PROGRESS_UNTIL = (1, 5, 0)


# sync-downloads.py 里的产品 key（分类/版本函数都按这个 key 查表）与
# 官网 data.json / index.html 里用的短 key 之间的映射。
SD_KEY = {
    "android": "android",
    "desktop": "desktop",
    "gate-android": "vantagate-android",
    "gate-desktop": "vantagate-desktop",
}


def load_sync_downloads(root):
    """复用 scripts/sync-downloads.py 的分类/版本/API 逻辑，不另起一套口径。"""
    path = root / "scripts" / "sync-downloads.py"
    spec = importlib.util.spec_from_file_location("sync_downloads", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def warn(msg):
    print(f"warning: {msg}", file=sys.stderr)


def primary_asset(release, product_key):
    """该 Release 的主资产：按后缀挑最大的；没有符合后缀的取最大的并警告。"""
    assets = release.get("assets") or []
    if not assets:
        return None
    ext = PRODUCTS[product_key]["asset_ext"]
    candidates = [a for a in assets if a["name"].endswith(ext)]
    if not candidates:
        warn(f"{release.get('tag_name')}: 没有找到 {ext} 资产，"
             f"改用最大的资产 {max(assets, key=lambda a: a['size'])['name']}")
        candidates = assets
    return max(candidates, key=lambda a: a["size"])


def sha256_of_url(url, token):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
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


def fetch_plugin_count(token):
    req = urllib.request.Request(PLUGIN_INDEX_URL, headers={"User-Agent": UA})
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        warn(f"插件商店 index.json 拉取失败：{e}，插件总数保持旧值")
        return None
    plugins = data.get("plugins") if isinstance(data, dict) else None
    if not isinstance(plugins, list):
        warn("插件商店 index.json 结构异常（没有 plugins 列表），插件总数保持旧值")
        return None
    return len(plugins)


def fmt_mib(size_bytes):
    return f"{size_bytes / MIB:.1f} MiB"


def download_url(tag, asset_name):
    return f"https://github.com/{REPO}/releases/download/{tag}/{asset_name}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--no-hash", action="store_true")
    ap.add_argument("--root", default=".")
    args = ap.parse_args()
    root = Path(args.root)
    token = os.environ.get("GITHUB_TOKEN") or ""

    sd = load_sync_downloads(root)
    releases = sd.fetch_releases()
    grouped = sd.group_releases(releases)

    data_path = root / "website" / "data.json"
    old = {}
    if data_path.exists():
        try:
            old = json.loads(data_path.read_text(encoding="utf-8"))
        except Exception as e:
            warn(f"旧 data.json 读失败（{e}），按空处理")
    old_products = (old.get("products") or {}) if isinstance(old, dict) else {}

    now = datetime.now(timezone.utc)
    products = {}
    for key, meta in PRODUCTS.items():
        sd_key = SD_KEY[key]
        rels = grouped.get(sd_key) or []
        if not rels:
            warn(f"{meta['name']}：API 里没有 Release，该产品保持旧值")
            if "version_display" in (old_products.get(key) or {}):
                products[key] = old_products[key]
            continue
        latest = sd.latest_per_product({sd_key: rels})[sd_key]
        asset = primary_asset(latest, key)
        if asset is None:
            warn(f"{meta['name']} {latest.get('tag_name')}：没有资产，该产品保持旧值")
            if "version_display" in (old_products.get(key) or {}):
                products[key] = old_products[key]
            continue
        tag = latest["tag_name"]
        version_display = sd.display_version(tag, sd_key)
        old_entry = old_products.get(key) or {}
        sha = None
        if meta["show_sha"]:
            if old_entry.get("tag") == tag and old_entry.get("sha256"):
                sha = old_entry["sha256"]  # 版本没变，不重新下载算哈希
            elif not args.no_hash:
                print(f"{meta['name']} 新版本 {version_display}：下载 {asset['name']} 算 SHA-256…")
                try:
                    sha = sha256_of_url(download_url(tag, asset["name"]), token)
                except Exception as e:
                    warn(f"{meta['name']} SHA 下载失败：{e}，SHA 置空（页面自动隐藏该行）")
                    sha = None
        published = (latest.get("published_at") or "")[:10]
        products[key] = {
            "name": meta["name"],
            "version_display": version_display,
            "tag": tag,
            "asset_name": asset["name"],
            "asset_ext": meta["asset_label"],
            "size_bytes": asset["size"],
            "size_mib": fmt_mib(asset["size"]),
            "published_date": published,
            "url": download_url(tag, asset["name"]),
            "sha256": sha,
            "release_count": len(rels),
        }

    plugin_count = fetch_plugin_count(token)
    if plugin_count is None:
        plugin_count = old.get("plugin_count", 0)

    desktop_latest = products.get("desktop") or {}
    desktop_ver = sd.parse_semver(
        sd.raw_version(desktop_latest.get("tag", ""), "desktop")) if desktop_latest.get("tag") else None
    desktop_in_progress = bool(desktop_ver and desktop_ver < DESKTOP_IN_PROGRESS_UNTIL)

    data = {
        "generated_at": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "generated_date": now.strftime("%Y-%m-%d"),
        "plugin_count": plugin_count,
        "desktop_in_progress": desktop_in_progress,
        "products": products,
    }

    if args.dry_run:
        print(json.dumps(data, ensure_ascii=False, indent=2))
        return 0
    data_path.parent.mkdir(parents=True, exist_ok=True)
    data_path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n",
                         encoding="utf-8")
    for key, p in products.items():
        print(f"{p['name']}: {p['version_display']} · {p['asset_name']} · "
              f"{p['size_mib']} · {p['published_date']} · {p['release_count']} 个 Release")
    print(f"插件总数: {plugin_count} · 桌面 1.5.0 进行中: {desktop_in_progress}")
    print(f"写出 {data_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
