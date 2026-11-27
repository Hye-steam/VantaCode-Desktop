import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Base64

plugins {
    kotlin("multiplatform") version "2.3.10"
    id("org.jetbrains.compose") version "1.12.2"
    kotlin("plugin.compose") version "2.3.10"
}

// ---------------------------------------------------------------------------
// 生成式资源（构建期产出，不进 git，见 docs/vantagate-desktop.md）：
//
// 图标：desktop/icons/*.b64 是图标文件的 base64 文本（push_files 只能传
// UTF-8 文本，二进制图标无法直接进分支）。decodeIcons 在构建期解码成
// build/generated/assets/icon.{ico,png}：.ico 供安装器打包，.png 进 classpath
// 做窗口图标。
// （桌宠已移除：2026-11-27 删 PetWindow 及 syncPetAssets，不再打包 pet 资源。）
// ---------------------------------------------------------------------------

// 安装器版本（与下面 nativeDistributions.packageVersion、Inno 脚本共用）。
val vantagateVersion = "2.17.0"
val decodeIcons = tasks.register("decodeIcons") {
    val iconsDir = layout.projectDirectory.dir("icons")
    val outDir = layout.buildDirectory.dir("generated/assets")
    inputs.files(iconsDir.file("icon.ico.b64"), iconsDir.file("icon.png.b64"))
    outputs.dir(outDir)
    doLast {
        val out = outDir.get().asFile.apply { mkdirs() }
        val decoder = Base64.getMimeDecoder()
        for ((b64Name, outName) in listOf("icon.ico.b64" to "icon.ico", "icon.png.b64" to "icon.png")) {
            val b64File = iconsDir.file(b64Name).asFile
            require(b64File.isFile) { "缺少图标源文件：$b64File" }
            out.resolve(outName).writeBytes(decoder.decode(b64File.readText()))
        }
        println("[decodeIcons] 图标已解码到 $out")
    }
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation("org.jetbrains.compose.components:components-resources:1.12.2")

            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

            // :core 用 implementation 引 gson/nanohttpd，不传递；desktop 直接用 Gson 解析。
            implementation("com.google.code.gson:gson:2.11.0")

            implementation(project(":core"))

            // JCEF — 网页登录 + WebTransport（ChatGLM/Muse 必须 live page）。
            // natives jar（~163MiB）随 app image 进 lib/，首次运行由 jcefmaven
            // 解包到 ~/.vantagate/jcef（installDir 见 GateCef）。
            val jcefVersion = "152.0.6"
            implementation("me.friwi:jcefmaven:$jcefVersion")
            implementation(
                "me.friwi:jcef-natives-windows-amd64:" +
                    "jcef-17e805a+cef-152.0.6+g708dc14+chromium-152.0.7977.83"
            )

            // JNA：DWM 深色标题栏（Windows 原生标题栏与深色主题统一）。
            implementation("net.java.dev.jna:jna:5.14.0")
            implementation("net.java.dev.jna:jna-platform:5.14.0")
        }
        jvmTest.dependencies {
            implementation("junit:junit:4.13.2")
        }
        jvmMain {
            resources.srcDir(layout.buildDirectory.dir("generated/assets"))
        }
    }
}

tasks.named("jvmProcessResources") {
    dependsOn(decodeIcons)
}

compose.desktop {
    application {
        mainClass = "dev.aigw.desktop.MainKt"
        jvmArgs += listOf(
            "-Xmx1536m",
            // JCEF / OSR 离屏渲染所需模块开放（fat jlink 已含全部模块）
            "--add-exports=java.base/java.lang=ALL-UNNAMED",
            "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED"
        )

        nativeDistributions {
            // 正式分发 = 绿色 app image + EXE 安装包（Inno Setup 中文向导，见 packageExeZh）。
            targetFormats(TargetFormat.Exe)
            // 安装时显示许可证接受对话框（GPL 分发必须）：LICENSE 全文。
            licenseFile.set(rootProject.file("LICENSE"))
            packageName = "VantaGate"
            packageVersion = vantagateVersion
            description = "VantaGate — AI gateway for desktop (GPL-3.0)"
            copyright = "GPL-3.0. VantaGate Android 2.17 desktop port."
            vendor = "VantaGate"

            windows {
                menuGroup = "VantaGate"
                perUserInstall = true
                shortcut = true
                // 全新 UUID，绝不复用 VantaCode 的 upgradeUuid。
                upgradeUuid = "107c79cb-4652-471c-9f84-4fdae0e08743"
                iconFile.set(layout.buildDirectory.file("generated/assets/icon.ico"))
            }
        }
    }
}

// Compose 插件会把运行时镜像裁成 ~4 模块（自动分析），含 jdk.crypto.ec 但缺 jdk.unsupported
// （protobuf/Unsafe、JCEF 相关路径需要）。其模块属性被 finalize 后无法在 doFirst
// 里改、配置阶段的设置也会被插件覆盖，所以走强行替换：在 createRuntimeImage 之后
// 删掉它产出的默认镜像，用 jlink 直接打出全模块镜像（构建 JDK 全部 70 模块），
// 再让后续打包任务照常 copy 这个完整镜像。
val ALL_JDK_MODULES = listOf(
    "java.base", "java.compiler", "java.datatransfer", "java.desktop", "java.instrument",
    "java.logging", "java.management", "java.management.rmi", "java.naming", "java.net.http",
    "java.prefs", "java.rmi", "java.scripting", "java.se", "java.security.jgss",
    "java.security.sasl", "java.smartcardio", "java.sql", "java.sql.rowset", "java.transaction.xa",
    "java.xml", "java.xml.crypto",
    "jdk.accessibility", "jdk.attach", "jdk.charsets", "jdk.compiler", "jdk.crypto.cryptoki",
    "jdk.crypto.ec", "jdk.crypto.mscapi", "jdk.dynalink", "jdk.editpad", "jdk.hotspot.agent",
    "jdk.httpserver", "jdk.incubator.vector", "jdk.internal.ed", "jdk.internal.jvmstat",
    "jdk.internal.le", "jdk.internal.opt", "jdk.internal.vm.ci", "jdk.internal.vm.compiler",
    "jdk.internal.vm.compiler.management", "jdk.jartool", "jdk.javadoc", "jdk.jcmd", "jdk.jconsole",
    "jdk.jdeps", "jdk.jdi", "jdk.jdwp.agent", "jdk.jfr", "jdk.jlink", "jdk.jpackage", "jdk.jshell",
    "jdk.jsobject", "jdk.jstatd", "jdk.localedata", "jdk.management", "jdk.management.agent",
    "jdk.management.jfr", "jdk.naming.dns", "jdk.naming.rmi", "jdk.net", "jdk.nio.mapmode",
    "jdk.random", "jdk.sctp", "jdk.security.auth", "jdk.security.jgss", "jdk.unsupported",
    "jdk.unsupported.desktop", "jdk.xml.dom", "jdk.zipfs",
)

val fatRuntimeImage = tasks.register("fatRuntimeImage") {
    dependsOn("createRuntimeImage")
    outputs.upToDateWhen { false }
    doLast {
        val runtimeDir = layout.buildDirectory.dir("compose/tmp/main/runtime").get().asFile
        println("[fatRuntimeImage] 替换 Compose 默认镜像为全模块镜像：$runtimeDir")
        if (runtimeDir.exists()) runtimeDir.deleteRecursively()
        // jlink 要求 --output 目录不存在，不能先 mkdirs；内存紧张时限制 jlink 自身堆
        val jlink = "${System.getProperty("java.home")}/bin/jlink"
        val cmd = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            listOf("${System.getProperty("java.home")}\\bin\\jlink.exe",
                "-J-Xmx256m", "-J-XX:MaxMetaspaceSize=96m", "-J-XX:+UseSerialGC",
                "--no-header-files", "--no-man-pages", "--strip-debug",
                "--compress", "2", "--add-modules", ALL_JDK_MODULES.joinToString(","), "--output", runtimeDir.absolutePath)
        } else {
            listOf(jlink, "-J-Xmx256m", "-J-XX:MaxMetaspaceSize=96m", "-J-XX:+UseSerialGC",
                "--no-header-files", "--no-man-pages", "--strip-debug",
                "--compress", "2", "--add-modules", ALL_JDK_MODULES.joinToString(","), "--output", runtimeDir.absolutePath)
        }
        println("[fatRuntimeImage] 执行: jlink --add-modules <70 模块> --output $runtimeDir")
        exec { commandLine(cmd) }
        println("[fatRuntimeImage] 完成。modules=${ALL_JDK_MODULES.size}")
    }
}

// 让所有会用到临时镜像的打包任务在 fatRuntimeImage 之后执行（createRuntimeImage 先产出默认镜像，我们再覆盖）。
tasks.matching { it.name.contains("unpackDefaultComposeDesktopJvmApplicationResources", ignoreCase = true) }.configureEach {
    dependsOn(fatRuntimeImage)
}

// 打包前图标必须已解码（iconFile 指向 generated/assets/icon.ico）。
tasks.matching { it.name.contains("package", ignoreCase = true) }.configureEach {
    dependsOn(decodeIcons)
}

// 中文安装器：Compose 的 packageExe（jpackage）不向 Exe 步骤传 --resource-dir，
// WiX 向导语言改不成中文；改用 Inno Setup 打 .exe（自带简体中文向导）。
// 依赖 createDistributable 产出的 app image + decodeIcons 解出的图标；
// CI 需先装 Inno Setup（choco install innosetup），ISCC.exe 在 PATH。
// 产物与原来同名同目录：build/compose/binaries/main/exe/VantaGate-<版本>-win64.exe。
val packageExeZh = tasks.register<Exec>("packageExeZh") {
    dependsOn("createDistributable", decodeIcons)
    group = "distribution"
    description = "使用 Inno Setup 打包中文安装向导的 .exe（替代 packageExe）"
    val issFile = layout.projectDirectory.file("installer/vantagate.iss")
    val exeOutDir = layout.buildDirectory.dir("compose/binaries/main/exe")
    inputs.file(issFile)
    inputs.dir(layout.buildDirectory.dir("compose/binaries/main/app"))
    inputs.file(layout.buildDirectory.file("generated/assets/icon.ico"))
    outputs.dir(exeOutDir)
    doFirst {
        exeOutDir.get().asFile.mkdirs()
    }
    commandLine(
        "ISCC.exe",
        "/Q",
        "/DAppVersion=$vantagesVersion",
        issFile.asFile.absolutePath,
    )
}

// 开发期 gradlew run 也继承 JCEF/OSR 所需的模块开放参数（打包时上面 jvmArgs 已含）。
val jcefJvmArgs = listOf(
    "--add-exports=java.base/java.lang=ALL-UNNAMED",
    "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
    "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED"
)
tasks.withType<JavaExec>().configureEach {
    jvmArgs(jcefJvmArgs)
}
