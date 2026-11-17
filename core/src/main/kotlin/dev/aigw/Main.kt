package dev.aigw

import dev.aigw.gateway.Engine
import dev.aigw.gateway.GatewayHttpServer
import dev.aigw.gateway.ProxySettings
import dev.aigw.gateway.ProxyType
import dev.aigw.gateway.Settings
import dev.aigw.provider.DeviceAuthPoll
import dev.aigw.provider.ProviderAccount
import dev.aigw.util.Log
import java.io.File

fun main(args: Array<String>) {
    val cmd = args.firstOrNull() ?: "serve"
    val rest = args.drop(1).toMutableList()
    val dataDir = argValue(rest, "--data")?.let(::File) ?: File(System.getProperty("user.home"), ".aigw")
    dataDir.mkdirs()

    when (cmd) {
        "serve" -> serve(rest, dataDir)
        "add" -> add(rest, dataDir)
        "accounts" -> accounts(dataDir)
        "models" -> models(dataDir)
        "credits" -> credits(dataDir)
        "logs" -> logs(dataDir)
        "providers" -> providers()
        "workbuddy-login" -> workbuddyLogin(rest, dataDir)
        else -> usage()
    }
}

private fun serve(args: MutableList<String>, dataDir: File) {
    val engine = Engine(dataDir)
    val s = engine.settings
    argValue(args, "--port")?.toIntOrNull()?.let { s.port = it }
    if (args.contains("--lan")) s.bindLan = true
    argValue(args, "--api-key")?.let { s.apiKey = it; s.allowNoKey = false }
    if (args.contains("--no-key")) s.allowNoKey = true
    argValue(args, "--proxy")?.let { hp ->
        val host = hp.substringBefore(':')
        val port = hp.substringAfter(':', "8080").toIntOrNull() ?: 8080
        val type = when ((argValue(args, "--proxy-type") ?: "http").lowercase()) {
            "socks", "socks5" -> ProxyType.SOCKS
            else -> ProxyType.HTTP
        }
        engine.updateProxySettings(
            ProxySettings(
                enabled = true,
                type = type,
                host = host,
                port = port,
                username = argValue(args, "--proxy-user").orEmpty(),
                password = argValue(args, "--proxy-pass").orEmpty(),
            ),
        )
    }
    Settings.save(engine.dataDir, engine.settings)

    val server = GatewayHttpServer(engine)
    server.start()
    Runtime.getRuntime().addShutdownHook(Thread { server.stop() })

    val host = if (s.bindLan) "0.0.0.0" else "127.0.0.1"
    println("""
        |
        |LoopAPI 已启动
        |  Base URL : http://$host:${s.port}/v1
        |  模型列表 : http://$host:${s.port}/v1/models
        |  健康检查 : http://$host:${s.port}/healthz
        |  鉴权     : ${if (s.allowNoKey) "无 Key 调用（客户端可随意填写）" else "API Key: ${s.apiKey}"}
        |  数据目录 : $dataDir
        |
        |示例: curl http://127.0.0.1:${s.port}/v1/chat/completions \
        |  -H "Content-Type: application/json" \
        |  -d '{"model":"custom:<uid>/gpt-4o","messages":[{"role":"user","content":"你好"}]}'
        |
        |按 Ctrl+C 停止。
    """.trimMargin())
    Thread.currentThread().join()
}

private fun add(args: MutableList<String>, dataDir: File) {
    val name = argValue(args, "--name") ?: error("缺少 --name")
    val baseUrl = argValue(args, "--base-url") ?: error("缺少 --base-url")
    val key = argValue(args, "--key") ?: error("缺少 --key")
    val uid = argValue(args, "--uid") ?: (name + baseUrl).hashCode().toUInt().toString(16)
    val engine = Engine(dataDir)
    engine.pool.add(
        ProviderAccount(
            providerId = "custom",
            uid = uid,
            displayName = name,
            apiKey = key,
            baseUrl = baseUrl,
        ),
    )
    println("已添加账号 custom:$uid（$name）")
    println("调用时模型名写: custom:$uid/<模型名>")
}

private fun accounts(dataDir: File) {
    val engine = Engine(dataDir)
    val all = engine.pool.all()
    if (all.isEmpty()) {
        println("暂无账号。用 add 子命令添加：")
        println("  java -jar aigw.jar add --name my --base-url https://api.openai.com/v1 --key sk-xxx")
        return
    }
    all.forEach { e ->
        println(
            "${e.account.providerId}:${e.account.uid}  ${e.account.displayName}  " +
                "启用=${e.state.enabled} 余额=${e.state.balance ?: "?"}  " +
                "base=${e.account.baseUrl}" +
                (e.state.disabledReason?.let { "  已禁用原因: $it" } ?: ""),
        )
    }
}

private fun models(dataDir: File) {
    val engine = Engine(dataDir)
    val json = engine.listModels(forceRefresh = true)
    val ids = json.getAsJsonArray("data").map { it.asJsonObject.get("id").asString }
    if (ids.isEmpty()) println("暂无模型（没有可用账号）")
    else ids.forEach { println(it) }
}

private fun credits(dataDir: File) {
    val engine = Engine(dataDir)
    engine.pool.all().forEach { e ->
        println("${e.account.providerId}:${e.account.uid}  ${e.account.displayName}  余额=${e.state.balance ?: "未知"}")
    }
}

private fun logs(dataDir: File) {
    val engine = Engine(dataDir)
    val records = engine.callLog.recent(50)
    if (records.isEmpty()) {
        println("暂无调用记录")
        return
    }
    records.forEach { r ->
        val t = java.time.Instant.ofEpochMilli(r.time).toString().substring(11, 19)
        println("$t  ${if (r.ok) "OK " else "ERR"}  ${r.model}  via=${r.via}  ${r.latencyMs}ms  ${r.detail}")
    }
}

private fun providers() {
    println("custom      自定义 OpenAI 兼容中转站（一个 key = 一个账号）")
    println("codebuddy   WorkBuddy（国内/国际，设备授权登录）")
}

/** 设备授权登录：打印链接，用户在浏览器完成登录后自动保存账号。 */
private fun workbuddyLogin(args: MutableList<String>, dataDir: File) {
    val region = when (val r = argValue(args, "--region") ?: "cn") {
        "global", "intl" -> "global"
        else -> "cn"
    }
    val engine = Engine(dataDir)
    val ticket = try {
        engine.startDeviceAuth("codebuddy", region)
    } catch (e: Exception) {
        println("获取授权链接失败：${e.message}")
        return
    }
    println("区域：${if (region == "global") "国际版" else "国内版"}")
    println("请在浏览器打开以下链接完成登录：")
    println(ticket.loginUrl)
    println("等待授权（Ctrl+C 取消）…")
    var attempts = 0
    while (attempts < 300) {
        Thread.sleep(2000)
        when (val poll = engine.pollDeviceAuth("codebuddy", ticket.state, region)) {
            is DeviceAuthPoll.Pending -> print(".")
            is DeviceAuthPoll.Success -> {
                engine.saveAccount(poll.account)
                println("\n登录成功：${poll.account.displayName}（${poll.account.uid}）")
                return
            }
            is DeviceAuthPoll.Failed -> {
                println("\n登录失败：${poll.error}")
                return
            }
        }
        attempts++
    }
    println("\n等待超时，请重试")
}

private fun usage() {
    println(
        """
        |用法: java -jar aigw.jar <命令> [选项]
        |
        |  serve   启动网关（默认端口 8790，--port 修改；--lan 监听局域网；--api-key 设置密钥并关闭无 Key）
        |          --proxy host:port [--proxy-type http|socks5] [--proxy-user u --proxy-pass p]
        |  add     添加自定义账号  --name <显示名> --base-url <上游BaseUrl> --key <API Key> [--uid <id>]
        |  workbuddy-login  WorkBuddy 设备授权登录（--region cn|global）
        |  accounts  列出账号
        |  models    拉取并列出各账号的模型
        |  credits   列出账号额度
        |  logs      最近调用记录
        |  providers 支持的供应商
        |
        |通用: --data <目录>（默认 ~/.aigw）
        """.trimMargin(),
    )
}

private fun argValue(args: MutableList<String>, name: String): String? {
    val i = args.indexOf(name)
    if (i < 0 || i + 1 >= args.size) return null
    return args[i + 1]
}
