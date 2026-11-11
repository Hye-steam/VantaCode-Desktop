import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization") version "2.3.10"
    id("com.google.devtools.ksp") version "2.3.10"
}

// Build-time asset manifest so DesktopAssets.list() can enumerate packaged
// resources (classpath resources are not listable at runtime).
val genAssetManifest = tasks.register("genAssetManifest") {
    val assetsDir = layout.projectDirectory.dir("src/main/resources/assets")
    val outDir = layout.buildDirectory.dir("generated/assets")
    inputs.dir(assetsDir)
    outputs.dir(outDir)
    doLast {
        val base = assetsDir.get().asFile
        val lines = mutableListOf<String>()
        base.walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = f.relativeTo(base).invariantSeparatorsPath
            val idx = rel.lastIndexOf('/')
            val dir = if (idx < 0) "" else rel.substring(0, idx)
            val name = if (idx < 0) rel else rel.substring(idx + 1)
            lines += "$dir\t$name"
        }
        outDir.get().asFile.resolve("assets/manifest.txt").apply {
            parentFile.mkdirs()
            writeText(lines.joinToString("\n") + "\n")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

compose.resources {
    packageOfResClass = "com.vantacode.resources"
}

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("generated/assets"))
        }
    }
}

tasks.named("processResources") {
    dependsOn(genAssetManifest)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)

    // Kotlin & coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // Lifecycle (KMP, JVM/desktop target)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

    // Room persistence (KMP: Android + iOS + JVM desktop)
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.sqlite:sqlite-bundled:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // Datastore preferences (KMP)
    implementation("androidx.datastore:datastore-preferences:1.1.4")

    // Navigation Compose (KMP, desktop target)
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.1")

    // Dagger annotations used by the ported @Inject constructors (DI graph is manual/lazy).
    implementation("javax.inject:javax.inject:1")

    // SSH / SFTP / FTP (remote workspaces)
    implementation("com.hierynomus:sshj:0.38.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("commons-net:commons-net:3.10.0")
    implementation("org.apache.ftpserver:ftpserver-core:1.2.0")

    // Archives for user-imported images
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.tukaani:xz:1.10")

    // YAML (prompt fragments, skills frontmatter)
    implementation("org.yaml:snakeyaml:2.2")

    // Terminal emulator core (this project, adapted from upstream)
    implementation(project(":terminal-emulator"))
    // Local PTY backend for the terminal panel
    implementation("org.jetbrains.pty4j:pty4j:0.13.13")

    // Charts (usage dashboard)
    implementation("com.patrykandpatrick.vico:compose-m3:3.2.2")

    // Compose icons — feather (same set as Android app)
    implementation("br.com.devsrsouza.compose.icons:feather:1.1.1")

    // Markdown rendering (multiplatform, desktop target)
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.41.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-code:0.41.0")
    implementation("dev.snipme:highlights-jvm:1.1.0")

    // Drag & drop reorder (settings lists)
    implementation("sh.calvin.reorderable:reorderable:3.1.0")

    // LaTeX math rendering — pure JVM build of jlatexmath
    implementation("org.scilab.forge:jlatexmath:1.0.7")
    implementation("org.scilab.forge:jlatexmath-font-greek:1.0.7")
    implementation("org.scilab.forge:jlatexmath-font-cyrillic:1.0.7")

    implementation("org.slf4j:slf4j-simple:2.0.9")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.mockk:mockk:1.14.11")
}

compose.desktop {
    application {
        mainClass = "com.vantacode.MainKt"
        jvmArgs += listOf("-Xmx1024m")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "VantaCode"
            packageVersion = "1.5.0"
            description = "VantaCode — AI coding agent for desktop (GPL-3.0)"
            copyright = "GPL-3.0. Contains code from AiCode (https://github.com/jieapi/AiCode)."
            vendor = "VantaCode"

            windows {
                menuGroup = "VantaCode"
                perUserInstall = true
                shortcut = true
                upgradeUuid = "3f6a1c2e-7b4d-4c2a-9a9c-9f2a3e5b6c01"
                iconFile.set(project.file("icons/icon.ico"))
            }
        }
    }
}
