# Vanta 官网（GitHub Pages）

官网源码在这里，不在别处。部署方式：GitHub Actions（`.github/workflows/website.yml`）
在发版 / 手动触发 / 每 6 小时定时检查时跑 `scripts/sync-website.py` 更新
`website/data.json`，然后把整个 `website/` 目录发布到 GitHub Pages。

线上地址：https://hye-steam.github.io/VantaCode-Desktop/
（仓库 Settings → Pages → Build and deployment 选 GitHub Actions 后生效）

## 文件

| 文件 | 说明 |
|---|---|
| `website/index.html` | 官网页面本身。图片已内联 base64，单文件 4.9 MB；版本、大小、日期、下载链接等会变的内容用 `data-*` 标记，加载时由 `data.json` 填充，`data.json` 加载失败时保留打包进来的当前版本文字 |
| `website/data.json` | 四个产品的最新版 + 插件总数 + 桌面 1.5.0 是否进行中。**不要手改**，由 CI 生成 |
| `scripts/sync-website.py` | 生成 `data.json` 的脚本：读 Releases API + 插件商店 index.json。Tag 分类规则复用 `scripts/sync-downloads.py`，和 README/DOWNLOADS 的口径一致 |
| `.github/workflows/website.yml` | 同步 + 发布流程 |

## 自动同步是怎么工作的

1. 触发：新 Release 发布（`release: published`）**或**每 6 小时定时检查**或**手动 `workflow_dispatch`；改 `website/` 或 `scripts/sync-website.py` 推送到 main 也会重新发布。
2. `sync-website.py` 从 Releases API 取每个产品的最新版（按发布日期，semver 复核）、
   主资产、大小、发布日期；从插件商店 index.json 取插件总数。
3. SHA-256 只有桌面版两个产品展示；版本号没变时沿用旧 SHA，不重新下载几百 MB；
   只有新版本出现时才下载一次算哈希。下载失败时 SHA 置空，页面自动隐藏该行。
4. `data.json` 有变化就由 bot 提交回 main（同一 run 内继续发布，不会触发别的流程），
   然后 `upload-pages-artifact` + `deploy-pages` 发布。

## 为什么有定时检查：release 触发漏过 3 次

`sync-downloads.yml` 的 `release: published` 触发在 v5.9.5、VantaGate 桌面 2.17.0、
v5.9.6 三次发版时一次都没跑（4 次运行记录全是手动触发）。查到的共同点：

- 这三次 Release 的 Tag 指向的提交**都不在 main 分支历史上**（安卓 / VantaGate 分支
  与 main 没有共同祖先，是孤儿分支历史）；而 `release` 事件的流程跑在默认分支上。
- VantaGate 桌面 2.17.0 还是先以 pre-release 发布、之后才翻成正式版——翻状态
  发的是 `edited` 事件，不是 `published`，触发器本来就收不到。

所以不要只靠 `release` 触发：本流程保留它，再加每 6 小时定时兜底和手动触发。
下一次桌面版发版（Tag 在 main 上，如 `desktop-v1.5.0`）是第一次验证 `release`
触发是否对 main 上 Tag 生效的机会；如果那次也没触发，就只剩定时兜底在干活，
届时再看要不要改成纯定时。

## 手动同步（一键）

仓库 Actions → “Vanta 官网” → Run workflow。跑完后线上站即更新，无需改代码。

## 改官网内容（文案、样式、结构）

不要直接改 `website/index.html` 里带 `data-*` 的那几行——那是同步的槽位。
大改版时：用官网 artifact 重新导出，在 `~/workspace/vanta-website-build/transform.py`
里跑一遍转换（它会校验每个替换的出现次数），再把新 `index.html` 推上来；
`data.json` 不用动。

## 已知的不同步项（有意为之）

- 版本线里的历史行（如 `1.3.0 · 11-14 / 1.2.0 · 11-13`）是静态文字，新版本发版后
  “当前”那行会自动变，历史行需要人顺手补一句。
- 插件列表（32 个插件的名字、版本、桌面兼容状态）是人工核对的内容，只自动同步总数；
  插件本身有增减时，由人更新 `index.html` 里的插件数组。
- 赞赏码二维码和 Muse 邀请码不上站（Feng 定过）。
- 桌面版 1.5.0 未发布前，站上只写“插件兼容升级进行中”，不写日期；1.5.0 一发布，
  `desktop_in_progress` 自动变 false，相关文案自动隐藏。
- 1.5.0 的 Tag 必须带 `desktop-v` 前缀（如 `desktop-v1.5.0`）。如果打成裸 `v1.5.0`，
  分类脚本会认不出（和 `sync-downloads.py` 同一个口径），网站和 README 都同步不到，
  只能手动补——发版时注意。

## docs-site/ 是什么

`docs-site/` 是上游 AiCode 的 VitePress 文档站（标题还是 AiCode，链接指向上游仓库），
和 Vanta 官网无关，这次没动它。以后如果要把文档站也换成 Vanta 的，另起一件事。
