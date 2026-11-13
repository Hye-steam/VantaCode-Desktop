# VantaCode Desktop (Windows)

VantaCode 是 [AiCode](https://github.com/jieapi/AiCode) 的独立复刻版本，承载同一套 AI 编程代理能力，
以 Kotlin + Compose Multiplatform 桌面应用形式提供（Windows 优先）。

- 上游项目：**AiCode — Android 上的通用 AI Coding Agent**
- 本仓库是 AiCode 的桌面端移植与复刻，许可证完全一致：**GPL-3.0**（见 [LICENSE](LICENSE)）
- 版权声明与第三方组件说明见 [NOTICE](NOTICE)
- 上游说明与原版 README：[README.md](README.md) / [README.en.md](README.en.md)

## 许可与源代码获取（GPL-3.0）

本程序是自由软件：你可以重新分发和修改它，条件是遵守 GPL-3.0 的全部条款。分发本程序时
必须保留 LICENSE 与 NOTICE 文件，并按 GPL-3.0 向接收者提供对应源代码。

- 完整许可证文本：[LICENSE](LICENSE)
- 第三方组件、字体、库及其许可：[NOTICE](NOTICE)
- 源代码获取：本仓库即为 VantaCode Desktop 对应的源代码树；构建产物（exe / app image）
  与源码同源，欢迎依照 [LICENSE](LICENSE) 第六章的形式履行对应源码义务。

## 构建（Windows x64，JDK 21）

要求：JDK 21、Git、8 GB 以上内存（编译时关闭占用大量内存的应用可显著加快构建）。

```powershell
# 设置 JDK（仓库内 .jdk 为构建用 JDK 21）
$env:JAVA_HOME = "$PWD\.jdk"

# 编译并生成 app image（免 WiX；MSI/EXE 安装包见下方）
.\gradlew.bat :desktop:createDistributable --console=plain

# 产物目录
# desktop\build\compose\binaries\main\app\VantaCode\VantaCode.exe
```

生成 MSI / EXE 安装包（需先安装 WiX 3.11）：

```powershell
# 需要 WiX toolset（candle.exe / light.exe）
.\gradlew.bat :desktop:packageReleaseExe    # 安装包 (.exe)
.\gradlew.bat :desktop:packageReleaseMsi    # 安装包 (.msi)
```

若网络环境受限，可将 `wix311-binaries.zip` 放到 `%GRADLE_USER_HOME%\compose-jb\wix311.zip`
（`%USERPROFILE%\.gradle\compose-jb\wix311.zip`），Gradle 任务会跳过下载直接解包。

## 运行与数据目录

- 应用数据：`%USERPROFILE%\.vantacode`（日志、数据库、缓存）
- 代理配置：`%USERPROFILE%\.aicode`（agents、prompts、scripts、prompts 等）

本地模式直接调用宿主机 Git Bash / PowerShell / cmd；远程模式（SSH）通过 sshj 连接用户自己的服务器。

## 已移植与未移植（MVP）

已移植：AI 对话主界面、代理/会话、Git 视图、工作区/远程 SSH、设置（模型、MCP、主题、背景）、
备份/还原（本地 S3/WebDAV/SSH 目标）、凭据管理、Latex 渲染、终端（PTY，pty4j）。

未移植 / 受限：
- Android 专有能力（Proot、Shizuku、APK 内升级、唤醒锁、通知栏）已删除，不计划回移植；
- 移动端布局相关的设置已裁剪；
- 安装包需要 WiX 环境，默认 MVP 分发为 app image（便携目录）。

## 贡献与来源

VantaCode 复用 AiCode（<https://github.com/jieapi/AiCode>）的代码，并以 GPL-3.0 发布。
任何分发衍生作品必须同样遵守 GPL-3.0，并保留版权与许可声明。
