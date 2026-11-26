# CLAUDE.md

本仓库的 AI 协作规则（Claude Code、AiCode 等 AI 编程助手均读取本文件），优先级高于默认通用规则。

> **本文描述的是 `main` 分支上的 VantaCode 桌面版（Windows）。** 同一个仓库里还有安卓版和 VantaGate 的分支，各自是独立的历史（见 [docs/repo-guide.md](docs/repo-guide.md)）；不要把本文的桌面版规则套用到其他分支上，也不要从其他分支的旧文本里把安卓版内容搬回来。

## 基本约定

- **永远使用中文回复。**
- 文件读写用已有的文件工具，不用 `cat` / `sed` / `echo >` 代替。
- 桌面应用：Kotlin + Compose Multiplatform Desktop，模块 `:desktop` / `:terminal-emulator`，包名 `com.vantacode.desktop`（注意：与安卓版的 `com.vantacode` 不同）。
- 当前版本 1.4.0（VERSION_CODE 10400），见〈版本〉。
- **已移除的功能，不要再加回来**：桌宠与 DSH 已于 2026-11-20 移除（代码、资源、文案、设置入口均已删除），不要从旧文档或安卓分支里把它们复活。插件市场 `registry.json` 里残留的 `dsh-coding-workflow` 条目是待确认的历史遗留，见〈已知问题〉。
- **优先使用项目自定义组件（硬规则）**：
  - **输入框**：优先使用 `core/ui/AppTextField.kt`（及其配套配色 `appTextFieldColors()`、弹窗专用 `dialogTextFieldColors()`），禁止在业务页面无故裸写带有长下划线的原生 `TextField`，禁止在各模块私自重复包装 OutlinedTextField。
  - **开关**：一律使用 `core/ui/AppSwitch.kt`，禁止使用原生 M3 `Switch`。
  - **模态底栏**：一律使用 `core/ui/AdaptiveModalBottomSheet.kt`，禁止直接调用原生 `ModalBottomSheet`。
  - **分组卡片与列表行**：设置与表单页面优先复用 `feature/settings/presentation/component/SettingsGroupComponents.kt` 中的 `SettingsGroup`、`SettingsRow`、`SettingsDivider`、`SettingsGroupHeader`。
  - **左滑删除**：一律使用 `core/ui/SwipeToDeleteRow.kt`。
  - **主题与设计规范**：严格遵循 `core/theme/AIEditorTheme.kt` 的 `Spacing`、`Radius`（含 `Radius.mdLarge = 12.dp`）与 `MaterialTheme.semanticColors`，禁止随意硬编码非标魔数与色彩。
  - 以上组件路径以 `desktop/src/jvmMain/kotlin/com/vantacode/` 为根；若某组件在桌面端已被重命名或删除，以实际代码为准，不要凭旧文档臆测。

## 构建与验证

**改完编译型代码（`.kt` / `.gradle.kts`）→ 提交前必跑冒烟构建；`git push` 前必跑单元测试，并跑 `check_migrations.py` 迁移对账。** 只改文档 / 资源文案 / 纯 `.md` 时这些都跳过。

构建环境：Windows x64、JDK 21、Git、8 GB 以上内存。`gradle.properties` 已按 8 GB 内存裁剪（`workers.max=1`、`parallel=false`、`daemon=false`），不要擅自放宽。

| 用途 | 命令 |
| --- | --- |
| 冒烟构建（日常默认） | `.\gradlew.bat :desktop:createDistributable`（产物：便携目录 `desktop\build\compose\binaries\main\app\VantaCode\VantaCode.exe`） |
| 推送前单测 | `.\gradlew.bat :desktop:jvmTest` |
| 推送前迁移对账 | `python3 scripts/check_migrations.py` |
| 发版构建 EXE 安装包 | `.\gradlew.bat :desktop:packageExe`（需先装 WiX 3.11；安装时弹 GPL 许可接受页） |

- **没有 APK / AAB，没有 flavor，没有 `assembleUniversalDebug`。** 看到这类安卓构建命令一律视为过时文本，不要执行。
- **没有 CI**：`.github/` 只有 ISSUE_TEMPLATE，没有 workflows；`check_migrations.py` 全靠人工在推送前跑，不要假设有自动化兜底。
- 构建特殊点：Compose 插件会把运行时镜像裁成约 4 个模块，项目在 `createRuntimeImage` 后用 jlink 强行打出全模块镜像（70 个模块，含 `jdk.unsupported`，因 DataStore protobuf 需要 `sun.misc.Unsafe`）；JCEF natives 约 163 MiB，首次运行解包到 `%USERPROFILE%\.vantacode\jcef`。

## 版本

版本号**不是**从 Git Tag 推导的，而是三处显式同步，缺一不可：

- `PlatformPaths.kt` 的 `VERSION_NAME` / `VERSION_CODE`
- `desktop/build.gradle.kts` 的 `packageVersion`
- 同步工具：`node scripts/bump-version.js`（自增 minor，三处一起改）

改版本只用该脚本，不要手改其中一处；README 里的版本号、SHA-256、文件大小仍需手工同步，发版时一并核对（见〈发版〉）。

## 架构地图

feature-based 分层 + DDD。入口 `MainKt`（`compose.desktop.application.mainClass = "com.vantacode.MainKt"`，见 `DesktopApp.kt` / `Main.kt`）。**DI 是手动 / 懒加载**（`di/AppGraph.kt`），**没有 Hilt**——不要引入 Hilt 或其他 DI 框架。

- **`core/`**：跨 feature 基础设施 —— `assets/`、`data/`、`datastore/`、`db/`（含 `MigrationLoader.kt`）、`media/`、`net/`、`platform/`、`security/`、`theme/`、`ui/`、`util/`、`watch/`。
- **`feature/`**（`desktop/src/jvmMain/kotlin/com/vantacode/feature/`）：
  - `agent`：AI agent 核心 —— 提示词、工具注册与权限、MCP、provider 适配；检查点（checkpoint）与回放、LLM 调用记录。
  - `terminal`：终端与会话。本地 PTY（pty4j）+ `:terminal-emulator` 模块；远程模式 sshj。
  - `workspace`：工作区、远程 SSH（sshj）、SFTP/FTP、远程服务器与挂载。
  - `editor`：内置代码编辑器（BasicTextField + highlights-jvm 语法高亮）。`git`：Git 操作（提交历史、分支管理）。`settings`：provider、模型、MCP、主题、背景等设置。
  - `backup`：备份恢复（本地 / S3 / WebDAV / SSH 目标）。`credentials`：凭据管理（含 `LegacyCredentialMigrator`）。
  - `plugin`：插件系统，见下。`teams`：多成员 AI 团队协作（含团队聊天消息）。`onboarding` / `agreement`：引导与协议。
- **远程 SSH 链路**：共享 sshj client + 执行引擎 + SFTP 文件访问 + 远程终端会话管理。
- **工具系统**：`feature/agent/domain/tool/` 下各工具经 `ToolRegistry` 注册，执行权限由 `ToolPermissionManager` 与 `ToolPermissionPolicyEngine` 管控。
- **MCP**：`feature/agent/domain/mcp/`，连接远端 server 并动态注册其工具。
- **插件系统**（`feature/plugin`，cef / data / domain / runtime / tool）：ZIP 包解析与严格校验、原子安装与崩溃恢复、在线插件市场索引（`assets/plugin-market/registry.json`）、NDJSON JSON-RPC 子进程运行时、host API、hook / tool 系统。进程模型是 NativeCommandEngine 本机进程（**不是容器**）。插件 HTML 面板用 JCEF 152.0.6 渲染。插件市场支持一键更新、版本检查、同步模型到供应商（`host.provider.syncModels`）。
- **VantaGate**：网关插件把模型同步进宿主供应商，供应商不存在时自动创建，指向插件常驻本地网关 `127.0.0.1:8888/v1`（见 `PluginRuntimeManager.kt`）。
- **动态背景**：GIF / MP4（jcodec 解码；首次启动自动解包内置 ffmpeg 到 `%USERPROFILE%\.vantacode\tools\ffmpeg`）。
- **技能市场 / 更新检查**：技能安装到 `%USERPROFILE%\.aicode\skills`；更新检查拉 GitHub Releases。

### 数据库与迁移

Room（`feature/agent/data/local/database/AgentDatabase.kt` + 各 DAO），当前 `SCHEMA_VERSION = 64`。

- 迁移文件在 `desktop/src/jvmMain/resources/assets/migrations/`，命名 `{VERSION}_description.sql`（如 `64_add_team_chat_messages.sql`），共 57 个文件，**编号必须连续**；`core/db/MigrationLoader.kt` 读取并按语句切分（识别注释与字符串字面量，**字符串里可放心写 `;`**）；整段迁移包事务、任一条失败整体回滚，成功记入 `migration_history` 表。
- `desktop/schemas/` 下同时有 `com.aicode.*` 与 `com.vantacode.*` 两套 schema 目录，是迁移遗留，**不要擅自删除旧的一套**，先确认是否还被引用。

改 schema 三步：

1. 递增 `AgentDatabase.kt` 的 `SCHEMA_VERSION`。
2. 在 `desktop/src/jvmMain/resources/assets/migrations/` 新建 `{VERSION}_description.sql`，编号连续。
3. 写入 DDL/SQL，启动时自动执行并记入 `migration_history` 表。

**跨分支冲突硬规则**（RC/hotfix 与 `main` 并行推进时）

- **发布即冻结**：已打 Tag 的迁移文件内容与编号不可再改，补丁只能靠新增迁移修正。
- **合流后移**：RC/hotfix 若带数据库迁移，其迁移号一旦随 tag 发布即占用；合回 `main` 时，`main` 上所有编号 ≤ 已发布最大版本号的未发布迁移必须整体重编号到该上限之后（内容一字不改、编号连续），并同步递增 `SCHEMA_VERSION`。已发布号一律不可复用。
- **对账脚本**：`python3 scripts/check_migrations.py` 校验编号连续、SCHEMA_VERSION 一致、已发布迁移未被篡改/复用，**推送前必跑**（桌面端无 CI 兜底）。

## 资产同步（硬规则）

`desktop/src/jvmMain/resources/assets/prompts/`（AI 提示词）与 `docs-site/docs/`（用户文档）是 AI Agent 的知识来源，必须与代码同步：

- **AI 工作流改动 → 同步 `prompts/`**：工具增删改名、参数签名变化、agent 行为或提示词逻辑调整，都要更新 `prompts/` 下对应文件（自行查找，不存在则新建）。
- **功能或工具变化 → 检查 `docs-site/docs/`** 是否有使用文档要更新。
- **UI 变化（新增页面、改交互、调布局、改文案）→ 必须更新 `docs-site/docs/`**；新增文档页同步加进 `docs-site/.vitepress/config.ts` 侧栏与 `docs-site/docs/guide/overview.md` 索引。
- **用户可见中文文案 → 必须进双语 strings**：桌面端用 Compose `stringResource`，中英文案分别维护，**禁止在 `.kt` 中硬编码中文 UI 文案**。命名用语义化英文小写下划线，跨页面复用的加 `common_` 前缀。
- **提供商预设只在仓库数据文件维护**：新增或调整内置 AI 供应商，**只改 `data/providers.json`**（App 启动时从仓库动态拉取的源）；**不要**同步改内置兜底快照（随发版固化）与 `docs-site/` 文档。
- **容器镜像目录由脚本生成，禁止手改**：`data/container-images.json` 由 `scripts/sync-container-images.py` 生成；内置兜底快照**发版前用同一脚本刷新**——它承载离线可用的版本号与 SHA-256 校验和。要增删发行版或改镜像源，改脚本里的配置或 `sources` 表，不要改生成结果。

**文档目录约定**：`docs-site/docs/` 是文档唯一事实源，`guide/` 放功能说明、`advanced/` 放环境搭建与进阶教程。**面向用户书写**：讲清怎么做、会看到什么、出错怎么办；变量名、错误码、内部实现路径属于 `prompts/`，别写进用户文档。仓库结构、分支、Tag、Release 的对照见 [docs/repo-guide.md](docs/repo-guide.md)，下载入口见 [DOWNLOADS.md](DOWNLOADS.md)。

## Git 提交规范

Conventional Commits（`.githooks/commit-msg` 本地校验），格式 `<type>(<scope>): <subject>`，正文空行隔开。

- **type** ∈ `feat | fix | refactor | docs | style | chore | ci | build | perf | test`
- **scope** 可选，用功能模块：`agent | settings | terminal | workspace | git | ui | mcp | db | core | plugin | teams | desktop | docs | build | deps`
- **subject** 一行简述，中英文均可，句末不加句号。例：`fix(settings): 修复 provider 保存时校验失败`
- 仅紧急情况可用 `git commit --no-verify` 跳过校验。

## 分支工作流

本仓库一个仓库三个产品，分支与 Tag 规则见 [docs/repo-guide.md](docs/repo-guide.md)，这里只写 `main`（桌面版）的规矩：

- **新功能 / 复杂多文件改动 / 架构重构** → 开 `feat/xxx` 或 `refactor/xxx`，动手前先定好分支名，验证通过后合回 `main`。
- **日常 bug 修复 / 补单测 / CI 与构建配置 / 纯文档 / 资源文案** → 直接提交 `main`。
- **预览版热修复** → 从该 RC Tag 拉 `hotfix/xxx`，见〈发版〉。
- **合并后清理分支**：`git branch --merged main` 确认后 `git branch -d <branch>`；推送过的同步 `git push origin --delete <branch>`。
- **不要在 `feat/*` / `refactor/*` 上打 Tag 发版**，必须先合入 `main`。桌面版新 Tag 强制加前缀 `desktop-v`（如 `desktop-v1.5.0`）；安卓与 VantaGate 的分支、Tag 前缀见 repo-guide。

## PR 合并流程

仓库至今没有 PR，全部工作都是直接提交；以下流程只在未来出现 PR 时用。**在本地验证完再推送，不要在远端直接合**（绕过本地构建验证，且与本地在途提交分叉）。

1. **摸状态**：`git fetch origin` → 查看 PR 状态与 CI（如有）→ `git rev-list --left-right --count origin/main...main`。
2. **拉到本地**：`git fetch origin pull/<N>/head:pr-<N>`。勿用 `gh pr checkout`，它会改当前分支的跟踪关系。
3. **审查**：通读 diff。**PR 描述与评审结论都不算证据**，逐项落地核实：
   - 新引入的库在 `desktop/build.gradle.kts` 里是否已有
   - 新用的字符串资源在中英文案里是否都存在
   - 调用的既有组件签名是否匹配；被改了签名的组件用 `rg` 找出全部调用点确认都改到
   - 新交互是否复用既有实现
   - 安全面：可疑网络请求、凭据外发、命令拼接
   - 〈资产同步〉各项；含数据库迁移的 PR 必须跑 `python3 scripts/check_migrations.py` 对账
   - 超出 PR 标题范围的改动记下来交用户定夺
4. **预演冲突**：`git merge-tree --write-tree --name-only main pr-<N>`。只输出一个 tree hash = 可干净合并；列出文件名 = 有冲突。
5. **临时分支合并**：`git switch -c merge/pr-<N> main` → `git merge --no-ff pr-<N>`。
6. **冒烟验证**：动了 UI 或交互的 PR 必须跑通冒烟构建，**通过才继续**。
7. **单测 + 推送**：`git switch main && git merge --ff-only merge/pr-<N> && git push origin main`。
8. **清理**：`git branch -d merge/pr-<N> pr-<N>`。不动 stash 与未跟踪文件。

**必须停下来问用户**：本地 `main` 有未推提交（推 `main` 会把在途提交一并带上去）／预演有冲突／PR 含数据库迁移或构建、CI、签名改动／审查发现超范围改动或质量问题／编译或单测失败。

## Issue 审查

收到待评估的 issue（新功能 / bug 报告 / 改进建议）时，按以下顺序评估并输出审查报告：

1. **通读 issue**：提炼核心诉求、验收标准、所属模块（对照〈架构地图〉）与优先级；关联的讨论、PR、历史 commit 一并核实。**issue 里提出的实现方案只是作者的建议，不代表最优解，不得直接采纳**，优劣须在第 2~4 步结合代码与查证独立判断。
2. **阅读相关代码**：按 issue 提到的模块定位并阅读真实代码，确认描述是否属实、影响面多大。**陈述事实以核实过的代码为准，issue 作者的描述不算证据**。做法拿不准或需要业界对照时，查官方文档、同类实现与社区实践。
3. **评估是否合适**：结合项目约束判断该改动该不该做——
   - 是否符合 feature-based 分层 + DDD 现有结构，是否重复造已有轮子；
   - 是否触碰硬约束（迁移编号连续、提供商预设只改 `data/providers.json`、双语 strings、`prompts/` / 文档同步等〈资产同步〉各项）；
   - 改动范围与风险：是否触及启动 / 插件 / JCEF / 签名 / 构建等敏感面（此类改动发版需按〈发版〉先发 RC）。
4. **平替方案**：不受 issue 提案的局限，以核实过的代码与查证资料为依据，给出至少一个替代实现并对比（实现成本、风险、维护性、对既有用户的影响），给出推荐结论；需要用户取舍时用选择题征询，别替用户拍板。
5. **输出报告**：以对话回复形式输出报告，包含：结论（采纳 / 拒绝 / 待定）、理由、相关代码位置（带行号链接）、方案对比、建议的下一步（是否进入 PLAN、是否开分支按〈分支工作流〉）。报告不落盘，除非用户明确要求写文件。

## 发版

**版本号唯一事实源是 `bump-version.js` 同步的三处显式版本（见〈版本〉），不要从 Git Tag 推导，不要手改其中一处。**

### 是否先发 RC

靠 GitHub Release 分发且无灰度，发出去即终态，RC 是主要兜底：

- **必须先发 RC**：含新功能或行为变化（定档 `x.Y.0`）；构建链路 / WiX / JCEF 改动；插件系统改动。
- **可直接发正式**：仅纯文档 / typo / 资源文案（定档 `x.y.Z`）。
- **看改动面**：仅纯 bug 修复（定档 `x.y.Z`）小改直接正式，触碰启动或插件的仍先发 RC。

### 步骤

0. **更新内置模型数据（仅打 Tag 前执行）**：`python3 scripts/update-models-dev-assets.py` 从 models.dev 刷新内置模型快照（只保留内置官方 provider、不引入新 provider，现有 provider 下可扩充模型与单价）。**该脚本只在打 Tag 发版前手动执行；日常提交 / 推 main / 其它开发流程一律不跑**。**失败时脚本非零退出且不改快照——直接跳过此步发版，不要重试或手改文件**；成功则把快照改动一并提交到即将打 Tag 的分支。
1. **版本同步**：`node scripts/bump-version.js`，核对三处版本与 README 里的版本号、SHA-256、文件大小，提交。
2. **在 `main` 最新提交上打 Tag 并推送**：桌面版用前缀 Tag，如 `git tag desktop-v1.5.0 && git push origin desktop-v1.5.0`。**严禁在 `feat/*` / `refactor/*` 上打 Tag 发版**，必须先合入 `main`。
3. **构建 EXE 并发布 Release**：`.\gradlew.bat :desktop:packageExe`，Release 标题用 `[桌面] VantaCode Desktop x.y.z`，正文写改动、适用平台、下载文件、SHA-256；发版后同步更新 README 顶部的下载表与 [DOWNLOADS.md](DOWNLOADS.md)。
4. **验证**：安装 EXE，至少跑通 AI 对话 + 终端 + 插件加载三条主线。
5. **有问题**：从该 RC Tag 拉 `hotfix/xxx` 修复（**勿从最新 `main` 或功能分支拉**，否则会把已合入的未发版功能带进修复包）→ 升 rc 序号打 Tag 重发 → 修复合回 `main` 并推送 → 删 hotfix 分支。这是允许在非 `main` 分支打 Tag 的唯一例外。**无问题**：直接打正式 Tag 转正。
   - 若 hotfix 带数据库迁移：迁移号随 tag 发布即冻结（发布即冻结），合回 `main` 时 `main` 上编号更小的未发布迁移必须后移（见〈数据库与迁移〉合流后移），push 前先跑 `check_migrations.py`。

## 已知问题（写代码前先看）

- 桌宠与 DSH 已移除（2026-11-20），不要复活；`plugin-market/registry.json` 里残留的 `dsh-coding-workflow` 条目尚未核实能否安装，不能用就下架。
- 插件系统 2026-11-20/21 刚从安卓移植过来，仍在稳定期；改动插件相关代码时先回归 11-21 修过的点（AppGraph 初始化顺序、插件更新时 Windows 目录占用、同步模型、启停文案）。
- `desktop/schemas/` 两套 schema 目录并存，旧的一套是否还被引用未确认，不要擅自删除。
- 更新检查（`UpdateCheckService.kt`）目前拉全量 Releases，未按 Tag 前缀过滤——桌面版可能收到安卓版的更新提示，这是已知代码待办，改动它时按 `desktop-v` 前缀过滤。
- README 英文段仍写着 "a draggable desktop pet"，与现状不符，是文档待改项，不要照着它实现功能。
