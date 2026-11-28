# 仓库地图：分支、Tag、Release 对照

> 一个仓库，四个产品：VantaCode 桌面版、VantaCode 安卓版、VantaGate 安卓版、VantaGate 桌面版。
> 本文档是本仓库的唯一事实地图：新人先看这里，再去翻分支、Tag 和 Releases。
> 最后更新：2026-11-28（核实时点，北京时间约 14:15；Release 总数 43、Tag 总数 43）。
> 注：VantaGate 桌面版曾于 2026-11-26 移除（分支、4 个 Tag 和 4 个 Release v1.0.0、v2.0.0、v2.0.1、v2.0.2 均已删除），后于 2026-11-27 按安卓版 2.17 全功能重建并正式发布 v2.17.0；下表含现存的四个产品。
> 另见：[DOWNLOADS.md](../DOWNLOADS.md)（只列各产品最新版下载）。

## 1. 分支地图

分支名本身已经能看懂产品，**不改名**。产品分支只收合并，临时工作一律开 `产品/事项` 格式的新分支，例如 `vantagate-android/fix-icon`。

| 分支 | 产品 | 版本线 | 当前版本 | HEAD（核实日） | 顶层目录 | 构建 |
|---|---|---|---|---|---|---|
| `main`（默认分支） | VantaCode 桌面版 | 1.x | 1.4.0（2026-11-21） | `a1a6619` | `desktop/`、`terminal-emulator/`、`docs-site/`、`docs/`、`scripts/`、`data/` | `.\gradlew.bat :desktop:createDistributable`（便携目录）；`.\gradlew.bat :desktop:packageExe`（需 WiX 3.11） |
| `vantacode-android` | VantaCode 安卓版 | 5.x | v5.9.7（2026-11-27） | `d5496521` | `app/`、`terminal-emulator/`、`terminal-view/`、`skills-registry.json`、`build-flavor.sh`、`build-release.sh` | 见该分支的 `build-release.sh` / `build-flavor.sh` |
| `vantagate-android` | VantaGate 安卓版 | 2.x | 2.17（2026-11-25） | `9d1bc25f` | `app/`、`core/`、`aigw-admin/`、`libs/`、`build.sh`、`test/` | 见该分支的 `build.sh` |
| `vantagate-desktop` | VantaGate 桌面版 | 2.x | 2.17.0（2026-11-27） | `db282756` | `desktop/`、`core/`、`docs/` | `.\gradlew.bat :desktop:packageExeZh`（中文 Inno 安装包） |

结构事实（已核实）：`main`、`vantacode-android`、`vantagate-android` 三个分支之间**没有共同历史**，各是独立的根历史；`vantagate-desktop` 由 `vantagate-android` 的 2.17 提交（`9d1bc25f`）分出，与安卓分支共享历史。`main` 只有 `desktop/`、没有安卓代码，`vantacode-android` 只有 `app/`、没有桌面代码。

## 2. 三条版本线

| 产品 | 分支 | 当前版本 | 新 Tag 前缀（以后强制） | Release 标题前缀 |
|---|---|---|---|---|
| VantaCode 桌面版（Windows） | `main` | 1.4.0 | `desktop-v`，如 `desktop-v1.5.0` | `[桌面]` |
| VantaCode 安卓版 | `vantacode-android` | v5.9.7 | `android-v`，如 `android-v5.9.6` | `[安卓]` |
| VantaGate 安卓版 | `vantagate-android` | 2.17 | `vantagate-v`，如 `vantagate-v2.18` | `[VantaGate]` |
| VantaGate 桌面版（Windows） | `vantagate-desktop` | 2.17.0 | `vantagate-desktop-v`，如 `vantagate-desktop-v2.17.0` | `VantaGate Desktop` |

## 3. Tag 对照表（43 个，全部）

发版前核对一次：Tag 名、指向的提交、分支，三者一致才推。

状态说明：✅ 正确；❌ 指向错误（不动它，只标注）；⚠️ 近似（借用他版提交）。

### 3.1 VantaCode 桌面版（3 个，全部正确）

| Tag | 指向 | 日期 | 状态 |
|---|---|---|---|
| `v1.2.0` | `322f2a45` | 2026-11-13 | ✅ |
| `v1.3.0` | `6c4b32b7` | 2026-11-14 | ✅ |
| `v1.4.0` | `d6c077d0` | 2026-11-21 | ✅ |

### 3.2 VantaCode 安卓版（25 个，14 个打错）

| Tag | 指向 | 日期（Release 发布日） | 状态 | 备注 |
|---|---|---|---|---|
| `v4.1` | `6c4b32b7` | 2026-11-14 | ❌ | 指向桌面版 1.3.0 的提交，不是安卓分支的祖先；正确提交已被 squash，**已丢失**，只剩 APK |
| `v4.2` | `6c4b32b7` | 2026-11-14 | ❌ | 同上 |
| `v4.3` | `6c4b32b7` | 2026-11-14 | ❌ | 同上 |
| `v4.4` | `6c4b32b7` | 2026-11-14 | ❌ | 同上 |
| `v4.5` | `6c4b32b7` | 2026-11-14 | ❌ | 同上 |
| `v4.6` | `6c4b32b7` | 2026-11-14 | ❌ | 同上 |
| `v4.7` | `6c4b32b7` | 2026-11-14 | ❌ | 同上 |
| `v4.8` | `6c4b32b7` | 2026-11-15 | ❌ | 同上 |
| `v4.9` | `6c4b32b7` | 2026-11-15 | ❌ | 同上 |
| `v5.0` | `6c4b32b7` | 2026-11-15 | ❌ | 同上 |
| `v5.1` | `6c4b32b7` | 2026-11-15 | ❌ | 同上 |
| `v5.2` | `6c4b32b7` | 2026-11-15 | ❌ | 指向错误；正确提交是 `74724b9`（分支日志 "v5.2: pet overlay permission check…"），仅作记录，**不重打** |
| `v5.3` | `4ebccefe` | 2026-11-17 | ✅ | 分支祖先，日志 "v5.3: 移除 DSH…" |
| `v5.4` | `d43de077` | 2026-11-18 | ✅ | |
| `v5.5` | `d43de077` | 2026-11-19 | ⚠️ | 与 v5.4 共用同一提交，v5.5 没有自己的提交 |
| `v5.6` | `6c4b32b7` | 2026-11-20 | ❌ | 指向错误；正确提交是 `669d41b`（分支日志 "v5.6: 定时任务、语音输入…"），仅作记录，**不重打** |
| `v5.7` | `6c4b32b7` | 2026-11-20 | ❌ | 指向错误；正确提交是 `9459252`（分支日志 "v5.7: 插件模型同步 API…"），仅作记录，**不重打** |
| `v5.8` | `7cbcda6d` | 2026-11-22 | ✅ | |
| `v5.9` | `ba6c01e5` | 2026-11-23 | ✅ | |
| `v5.9.1` | `3bfe2825` | 2026-11-23 | ✅ | |
| `v5.9.3` | `305836a2` | 2026-11-24 | ✅ | 曾是安卓分支 HEAD |
| `v5.9.4` | `305836a2` | 2026-11-25 | ✅ | 曾是安卓分支 HEAD |
| `v5.9.5` | `305836a2` | 2026-11-27 | ✅ | Tag 指向 305836a2；分支 HEAD 已是 333c4a7d |
| `v5.9.6` | `333c4a7d` | 2026-11-27 | ✅ | Tag 指向 333c4a7d；分支 HEAD 已是 88417076 |
| `v5.9.7` | `d5496521` | 2026-11-27 | ✅（= 安卓分支 HEAD） | 可选 Root 权限（默认关闭）；这次 Tag 与分支 HEAD 一致 |

> 打错的 Tag 一律不动：每个背后都有真实 Release 和真实 APK，挪了 Release 就对不上了。`v5.9.2` 没有 Tag 也没有 Release，是跳过的版本号，无需处理。

### 3.3 VantaGate 安卓版（14 个，全部正确）

| Tag | 指向 | 日期（Release 发布日） | 状态 |
|---|---|---|---|
| `vantagate-v1.0.0` | `f681b32a` | 2026-11-17 | ✅ |
| `vantagate-v2.2` | `06b961d9` | 2026-11-18 | ✅ |
| `vantagate-v2.3` | `2dd5f26f` | 2026-11-18 | ✅ |
| `vantagate-v2.6` | `ce36d596` | 2026-11-18 | ✅ |
| `vantagate-v2.7` | `dacdda7f` | 2026-11-18 | ✅ |
| `vantagate-v2.9` | `1891af94` | 2026-11-19 | ✅ |
| `vantagate-v2.10` | `d0c44a4d` | 2026-11-19 | ✅ |
| `vantagate-v2.11` | `e52f5e53` | 2026-11-19 | ✅ |
| `vantagate-v2.12` | `d625c636` | 2026-11-20 | ✅ |
| `vantagate-v2.13` | `7670539b` | 2026-11-20 | ✅ |
| `vantagate-v2.14` | `d8382918` | 2026-11-20 | ✅ |
| `vantagate-v2.15` | `57cf739f` | 2026-11-23 | ✅ |
| `vantagate-v2.16` | `ffeef988` | 2026-11-23 | ✅ |
| `vantagate-v2.17` | `9d1bc25f` | 2026-11-25 | ✅（= 安卓分支 HEAD） |

跳过的版本（无 Tag、无 Release）：`v2.0`、`v2.1`、`v2.4`、`v2.5`、`v2.8`。

### 3.4 VantaGate 桌面版（1 个，正确）

| Tag | 指向 | 日期 | 状态 |
|---|---|---|---|
| `vantagate-desktop-v2.17.0` | `db282756` | 2026-11-27 | ✅（= 桌面分支 HEAD） |

## 4. Release 对照表（46 个，按产品分组，不按时间混排）

大小按 MiB 计（1 MiB = 1,048,576 字节）。最新版见 [DOWNLOADS.md](../DOWNLOADS.md)。

### 4.1 VantaCode 桌面版（6 个）

| Tag | 发布日期 | 资产 | 大小 |
|---|---|---|---|
| `v1.2.0` | 2026-11-13 | `VantaCode-1.2.0.exe` | 233.4 MiB |
| `v1.3.0` | 2026-11-14 | `VantaCode-1.3.0.exe` | 234.3 MiB |
| `v1.4.0` | 2026-11-21 | `VantaCode-1.4.0.exe` | 402.3 MiB，SHA-256 `E18BF0962440FBDB68360F197C646504D1B43FD5F31F23ACDD58EE102B03F244` |
| `desktop-v1.5.0` | 2026-11-28 | `VantaCode-1.5.0.exe` | 402.8 MiB |
| `desktop-v1.5.1` | 2026-11-28 |  |  |
| `desktop-v1.5.2` | 2026-11-28 |  |  |

### 4.2 VantaCode 安卓版（25 个）

| Tag | 发布日期 | 资产 | 大小 |
|---|---|---|---|
| `v4.1` | 2026-11-14 | `VantaCode-4.1-public.apk` | 52.1 MiB |
| `v4.2` | 2026-11-14 | `VantaCode-4.2-public.apk` | 52.8 MiB |
| `v4.3` | 2026-11-14 | `VantaCode-4.3-public.apk` | 52.8 MiB |
| `v4.4` | 2026-11-14 | `VantaCode-4.4-public.apk` | 52.8 MiB |
| `v4.5` | 2026-11-14 | `VantaCode-4.5-public.apk` | 55.3 MiB |
| `v4.6` | 2026-11-14 | `VantaCode-4.6-public.apk` | 55.3 MiB |
| `v4.7` | 2026-11-14 | `VantaCode-4.7-public.apk` | 55.3 MiB |
| `v4.8` | 2026-11-15 | `VantaCode-4.8-public.apk` | 55.3 MiB |
| `v4.9` | 2026-11-15 | `VantaCode-4.9-public.apk` | 55.3 MiB |
| `v5.0` | 2026-11-15 | `VantaCode-5.0-public.apk` | 55.3 MiB |
| `v5.1` | 2026-11-15 | `VantaCode-5.1-public.apk` | 55.3 MiB |
| `v5.2` | 2026-11-15 | `VantaCode-5.2-public.apk` | 55.3 MiB |
| `v5.3` | 2026-11-17 | `VantaCode-5.3-public.apk` | 36.1 MiB |
| `v5.4` | 2026-11-18 | `VantaCode-5.4-public.apk` | 34.5 MiB |
| `v5.5` | 2026-11-19 | `VantaCode-5.5.apk` | 34.5 MiB |
| `v5.6` | 2026-11-20 | `VantaCode-5.6.apk` | 34.5 MiB |
| `v5.7` | 2026-11-20 | `VantaCode-5.7.apk` | 34.5 MiB |
| `v5.8` | 2026-11-22 | `VantaCode-5.8.apk` | 34.5 MiB |
| `v5.9` | 2026-11-23 | `VantaCode-5.9.apk` | 34.5 MiB |
| `v5.9.1` | 2026-11-23 | `VantaCode-5.9.1.apk` | 34.5 MiB |
| `v5.9.3` | 2026-11-24 | `VantaCode-5.9.3.apk` | 34.5 MiB |
| `v5.9.4` | 2026-11-25 | `VantaCode-5.9.4.apk` | 34.6 MiB |
| `v5.9.5` | 2026-11-27 | `VantaCode-5.9.5.apk` | 34.4 MiB |
| `v5.9.6` | 2026-11-27 | `VantaCode-5.9.6.apk` | 34.8 MiB |
| `v5.9.7` | 2026-11-27 | `VantaCode-5.9.7.apk` | 34.8 MiB |

### 4.3 VantaGate 安卓版（14 个）

| Tag | 发布日期 | 资产 | 大小 |
|---|---|---|---|
| `vantagate-v1.0.0` | 2026-11-17 | `VantaGate-v1.0.0.apk` | 22.8 MiB |
| `vantagate-v2.2` | 2026-11-18 | `VantaGate-2.2.apk` | 22.8 MiB |
| `vantagate-v2.3` | 2026-11-18 | `VantaGate-2.3.apk` | 22.8 MiB |
| `vantagate-v2.6` | 2026-11-18 | `VantaGate-2.6.apk` | 22.8 MiB |
| `vantagate-v2.7` | 2026-11-18 | `VantaGate-2.7.apk` | 22.9 MiB |
| `vantagate-v2.9` | 2026-11-19 | `VantaGate-2.9.apk` | 22.9 MiB |
| `vantagate-v2.10` | 2026-11-19 | `VantaGate-2.10.apk` | 22.9 MiB |
| `vantagate-v2.11` | 2026-11-19 | `VantaGate-2.11.apk` | 22.9 MiB |
| `vantagate-v2.12` | 2026-11-20 | `VantaGate-2.12.apk` | 23.0 MiB |
| `vantagate-v2.13` | 2026-11-20 | `VantaGate-2.13.apk` | 23.0 MiB |
| `vantagate-v2.14` | 2026-11-20 | `VantaGate-2.14.apk` | 23.0 MiB |
| `vantagate-v2.15` | 2026-11-23 | `VantaGate-2.15.apk` | 23.0 MiB |
| `vantagate-v2.16` | 2026-11-23 | `VantaGate-2.16.apk` | 23.0 MiB |
| `vantagate-v2.17` | 2026-11-25 | `VantaGate-2.17.apk` | 23.0 MiB |

### 4.4 VantaGate 桌面版（1 个）

| Tag | 发布日期 | 资产 | 大小 |
|---|---|---|---|
| `vantagate-desktop-v2.17.0` | 2026-11-27 | `VantaGate-2.17.0-win64.zip` | 302.1 MiB，SHA-256 `097d2d9192145717f03ea57c846d426bd9a20640b8a10ef3e4b397daf5cc60c2` |

## 5. 以后发版的规则

1. **Tag**：按第 2 节的前缀打，发版前核对 Tag 名、提交、分支三者一致。
2. **Release 标题**：`[产品] 名字 版本 · 一句话`，例如 `[桌面] VantaCode Desktop 1.4.0`、`[安卓] VantaCode v5.9.4`、`[VantaGate] VantaGate 2.17`。
3. **Release 正文**固定四段：这次改了什么 / 适用平台 / 下载哪个文件 / SHA-256。
4. **资产名**统一带版本号：桌面 `VantaCode-1.4.0.exe`、安卓 `VantaCode-5.9.4.apk`、VantaGate 同理。
5. **发版后同步更新**：README 顶部的下载表和 [DOWNLOADS.md](../DOWNLOADS.md)，不要只依赖 Releases 列表。

## 6. 已知引用与待办（不属于本次整理）

- README 有两处指向本仓库 Releases 的链接；`AboutSection.kt` 写死了仓库 / Release / Issue 地址；`UpdateCheckService.kt` 写死了应用内更新检查 API（`api.github.com/repos/Hye-steam/VantaCode-Desktop/releases`）。单仓库下地址本身不用换，但**更新检查要按 Tag 前缀过滤**，否则桌面版会收到安卓版的更新提示——记为代码待办。
- `CLAUDE.md` 仍是安卓版内容（SCHEMA_VERSION 写 63、实际 64），重写为桌面版是另一件独立工作，不混在这里。
- 安卓分支与 VantaGate 分支的应用内更新检查地址、姊妹仓库 `Vanta-Plugins-Store` 是否写死本仓库地址、文档站 `docs-site/` 的交叉引用，尚未逐项核实，发版前按需核查。
