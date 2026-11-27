package com.vantacode.feature.plugin.runtime

import com.vantacode.core.util.FileLogger
import com.vantacode.feature.agent.domain.container.ConnectionState
import com.vantacode.feature.agent.domain.container.RemoteConnectionConfig
import com.vantacode.feature.agent.domain.container.RemoteSshConnection
import com.vantacode.feature.settings.data.repository.ExecutionMode
import com.vantacode.feature.settings.data.repository.ExecutionModeHolder
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder
import net.schmizz.sshj.connection.channel.direct.Parameters

private const val TAG = "PortForward"

/** 端口转发失败原因；[code] 供调用方（插件 host API）透出稳定错误码。 */
class PortForwardException(val code: String, message: String) : Exception(message)

/**
 * 一条端口映射：本机 `127.0.0.1:[localPort]` 对应目标 `[remoteHost]:[remotePort]`。
 * 远程 SSH 模式下目标地址在服务器上解析，因此也能映射服务器内网的其他主机；
 * 本地模式下目标就是本机或本机可达的地址。
 */
data class PortForwardInfo(
    val id: String,
    val owner: String,
    val localPort: Int,
    val remoteHost: String,
    val remotePort: Int,
    /** 本地直通：无需隧道，直接可用，没有监听 socket。 */
    val direct: Boolean
) {
    /** 便于直接打开的 HTTP 地址；非 HTTP 服务请用 `127.0.0.1:[localPort]` 自行拼接。 */
    val url: String get() = "http://127.0.0.1:$localPort"
}

/**
 * 插件端口转发管理：`host.ports.*` 的宿主实现，对照上游 AiCode 的 PortForwardManager。
 *
 * - 本地模式（[ExecutionMode.LOCAL_PROOT]）：目标在本机上。回环目标且不要求换端口时
 *   直接返回 `direct = true`（与 Android 本地模式一致，不占监听端口）；目标是非回环
 *   地址、或插件明确要求换一个本地端口时，起本地 TCP 代理（ServerSocket + Socket 双向泵）。
 * - 远程 SSH 模式：复用独立的 SSH transport（与 exec/SFTP 隔离，见
 *   [RemoteSshConnection.newDedicatedClient]），用 sshj LocalPortForwarder 建隧道；
 *   建隧道前先探测目标，未监听或服务器禁用 TCP 转发时立即报 TARGET_UNREACHABLE。
 *
 * 只绑定 `127.0.0.1`——不对外网或同一局域网暴露端口。SSH 断开时全部隧道统一回收，
 * 映射不会跨连接残留；插件停用/卸载时由 [PluginRuntimeManager.closePlugin] 回收该插件的映射。
 */
@Singleton
class PluginPortForwardManager @Inject constructor(
    private val sshConnection: RemoteSshConnection,
    private val executionModeHolder: ExecutionModeHolder
) {

    private class Tunnel(
        val info: PortForwardInfo,
        val serverSocket: ServerSocket?,
        val forwarder: LocalPortForwarder?,
        val clientSockets: MutableSet<Socket> = Collections.synchronizedSet(LinkedHashSet())
    ) {
        var acceptJob: Job? = null
        var listenJob: Job? = null
    }

    private val lock = Any()
    private val tunnels = LinkedHashMap<String, Tunnel>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var tunnelClient: SSHClient? = null

    @Volatile
    private var tunnelConfig: RemoteConnectionConfig? = null

    init {
        scope.launch {
            sshConnection.connectionState.collect { state ->
                if (state != ConnectionState.CONNECTED) closeAll()
            }
        }
    }

    /** 当前映射列表；只返回 [owner] 这个插件的映射。 */
    fun list(owner: String): List<PortForwardInfo> = synchronized(lock) {
        tunnels.values.map { it.info }.filter { it.owner == owner }
    }

    /**
     * 建立映射。[requestedLocalPort] 为空时优先占用与 [remotePort] 相同的本地端口，
     * 被占用则顺延；返回的 [PortForwardInfo.localPort] 是**实际**监听端口。
     * 同一插件重复映射同一目标会复用原映射。
     */
    suspend fun forward(
        owner: String,
        remoteHost: String = "127.0.0.1",
        remotePort: Int,
        requestedLocalPort: Int? = null
    ): PortForwardInfo {
        if (remotePort !in 1..65535) throw PortForwardException("PARAM_INVALID", "port 超出范围: $remotePort")
        if (requestedLocalPort != null && requestedLocalPort !in 1..65535) {
            throw PortForwardException("PARAM_INVALID", "localPort 超出范围: $requestedLocalPort")
        }
        val host = remoteHost.trim().ifEmpty { "127.0.0.1" }
        if (host.any { it.isWhitespace() }) throw PortForwardException("PARAM_INVALID", "host 非法: $remoteHost")

        val remote = executionModeHolder.currentMode() == ExecutionMode.REMOTE_SSH
        // 本地回环目标且不要求换端口：与目标直通，无需监听（与 Android 本地模式一致）。
        val wantDirect = !remote && isLoopback(host) &&
            (requestedLocalPort == null || requestedLocalPort == remotePort)

        synchronized(lock) {
            checkLimits(owner)
            tunnels.values.firstOrNull { sameTarget(it.info, owner, host, remotePort, wantDirect) }
                ?.let { return it.info }
        }

        val tunnel: Tunnel
        val info: PortForwardInfo
        if (wantDirect) {
            info = PortForwardInfo(
                id = UUID.randomUUID().toString(),
                owner = owner,
                localPort = requestedLocalPort ?: remotePort,
                remoteHost = host,
                remotePort = remotePort,
                direct = true
            )
            tunnel = Tunnel(info, null, null)
        } else if (remote) {
            val client = ensureTunnelClient()
            probeSshTarget(client, host, remotePort)
            val socket = withContext(Dispatchers.IO) { bindLoopback(requestedLocalPort ?: remotePort) }
            val forwarder = try {
                withContext(Dispatchers.IO) {
                    client.newLocalPortForwarder(
                        Parameters("127.0.0.1", socket.localPort, host, remotePort),
                        socket
                    )
                }
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw PortForwardException("FORWARD_FAILED", "建立转发失败：${e.message.orEmpty()}")
            }
            info = PortForwardInfo(
                id = UUID.randomUUID().toString(),
                owner = owner,
                localPort = socket.localPort,
                remoteHost = host,
                remotePort = remotePort,
                direct = false
            )
            tunnel = Tunnel(info, socket, forwarder)
            tunnel.listenJob = scope.launch {
                try {
                    forwarder.listen()
                } catch (e: Exception) {
                    if (!socket.isClosed) {
                        FileLogger.w(TAG, "端口转发监听中断 ${info.localPort} -> $host:$remotePort", e)
                    }
                }
            }
        } else {
            // 本地 TCP 代理：非回环目标、或插件要求换一个本地端口时才需要真实监听。
            probeTcpTarget(host, remotePort)
            val socket = withContext(Dispatchers.IO) { bindLoopback(requestedLocalPort ?: remotePort) }
            info = PortForwardInfo(
                id = UUID.randomUUID().toString(),
                owner = owner,
                localPort = socket.localPort,
                remoteHost = host,
                remotePort = remotePort,
                direct = false
            )
            tunnel = Tunnel(info, socket, null)
            tunnel.acceptJob = scope.launch { acceptLoop(tunnel) }
        }

        // 二次检查：建隧道期间可能已有并发调用建好同一目标的映射，复用它并丢弃刚建好的。
        val duplicate = try {
            synchronized(lock) {
                val dup = tunnels.values
                    .firstOrNull { sameTarget(it.info, owner, host, remotePort, wantDirect) }?.info
                if (dup == null) {
                    checkLimits(owner)
                    tunnels[info.id] = tunnel
                }
                dup
            }
        } catch (e: PortForwardException) {
            closeTunnel(tunnel)
            throw e
        }
        if (duplicate != null) {
            closeTunnel(tunnel)
            return duplicate
        }
        return info
    }

    /** 关闭一条映射；不属于 [owner] 时拒绝。返回是否命中。 */
    fun close(owner: String, id: String): Boolean {
        val tunnel = synchronized(lock) { tunnels[id] } ?: return false
        if (tunnel.info.owner != owner) {
            throw PortForwardException("FORBIDDEN", "该端口映射不属于当前插件")
        }
        synchronized(lock) { tunnels.remove(id) }
        closeTunnel(tunnel)
        return true
    }

    /** 关闭 [owner] 这个插件的全部映射（插件停用/卸载时调用）。 */
    fun closeAll(owner: String) {
        val snapshot = synchronized(lock) {
            val mine = tunnels.values.filter { it.info.owner == owner }
            mine.forEach { tunnels.remove(it.info.id) }
            mine
        }
        snapshot.forEach { closeTunnel(it) }
    }

    /** 关闭全部映射并丢弃隧道用 SSH 连接（SSH 断开时调用）。 */
    fun closeAll() {
        val snapshot = synchronized(lock) {
            val all = tunnels.values.toList()
            tunnels.clear()
            all
        }
        if (snapshot.isEmpty()) return
        snapshot.forEach { closeTunnel(it) }
        runCatching { tunnelClient?.close() }
        tunnelClient = null
        tunnelConfig = null
        FileLogger.i(TAG, "SSH 连接状态变化，已关闭 ${snapshot.size} 条端口映射")
    }

    private fun sameTarget(
        info: PortForwardInfo,
        owner: String,
        host: String,
        port: Int,
        direct: Boolean
    ): Boolean = info.owner == owner && info.remoteHost == host &&
        info.remotePort == port && info.direct == direct

    private fun checkLimits(owner: String) {
        if (tunnels.size >= MAX_TOTAL) {
            throw PortForwardException("PORT_LIMIT", "端口映射总数已达上限 $MAX_TOTAL")
        }
        if (tunnels.values.count { it.info.owner == owner } >= MAX_PER_OWNER) {
            throw PortForwardException("PORT_LIMIT", "同一插件最多保留 $MAX_PER_OWNER 条端口映射")
        }
    }

    /** 回环目标：本机自己。只做字面判断，不做 DNS 解析。 */
    private fun isLoopback(host: String): Boolean {
        val h = host.lowercase()
        return h == "localhost" || h == "127.0.0.1" || h == "::1" || h == "[::1]" ||
            (h.startsWith("127.") && h.substring(4).all { it.isDigit() || it == '.' })
    }

    /** 转发用的独立 transport：只在与当前连接配置一致且仍连通时复用。 */
    private suspend fun ensureTunnelClient(): SSHClient = withContext(Dispatchers.IO) {
        val config = sshConnection.config
            ?: throw PortForwardException("SSH_NOT_CONFIGURED", "未配置 SSH 连接")
        if (!sshConnection.isConnected()) {
            throw PortForwardException("SSH_NOT_CONNECTED", "SSH 未连接，请等待连接恢复后重试")
        }
        tunnelClient?.takeIf { tunnelConfig == config && it.isConnected && it.isAuthenticated }
            ?: run {
                runCatching { tunnelClient?.close() }
                sshConnection.newDedicatedClient(config).also {
                    tunnelClient = it
                    tunnelConfig = config
                }
            }
    }

    /**
     * 先开一条到目标的 direct-tcpip 通道再返回：目标未监听或服务器禁用 TCP 转发时
     * 立即报错，而不是等到用户打开页面才发现打不开。
     */
    private suspend fun probeSshTarget(client: SSHClient, host: String, port: Int) =
        withContext(Dispatchers.IO) {
            try {
                client.newDirectConnection(host, port).close()
            } catch (e: Exception) {
                val raw = e.message.orEmpty()
                val message = when {
                    "administratively prohibited" in raw ->
                        "服务器禁止 TCP 转发（sshd 的 AllowTcpForwarding 为 no），无法映射端口"
                    else -> "无法从服务器连接 $host:$port（${raw.ifEmpty { e.javaClass.simpleName }}）"
                }
                throw PortForwardException("TARGET_UNREACHABLE", message)
            }
        }

    /** 本地 TCP 代理建监听前先探测目标是否可达，不可达立即报错。 */
    private suspend fun probeTcpTarget(host: String, port: Int) = withContext(Dispatchers.IO) {
        try {
            Socket().use { it.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS) }
        } catch (e: Exception) {
            throw PortForwardException(
                "TARGET_UNREACHABLE",
                "无法连接 $host:$port（${e.message.orEmpty()}）"
            )
        }
    }

    private fun bindLoopback(preferred: Int): ServerSocket {
        for (offset in 0 until PORT_SCAN_ATTEMPTS) {
            val port = preferred + offset
            if (port > 65535) break
            val socket = ServerSocket()
            try {
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
                return socket
            } catch (_: BindException) {
                runCatching { socket.close() }
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw PortForwardException("PORT_UNAVAILABLE", "无法监听 127.0.0.1:$port（${e.message.orEmpty()}）")
            }
        }
        throw PortForwardException("PORT_UNAVAILABLE", "从 $preferred 起没有可用的本地端口")
    }

    /** 本地 TCP 代理的接受循环：每个连接起一对双向泵。 */
    private suspend fun acceptLoop(tunnel: Tunnel) {
        val server = tunnel.serverSocket ?: return
        // accept() 在 socket 关闭时抛 SocketException 退出循环；acceptJob 的 cancel 只做辅助。
        while (!server.isClosed) {
            val client = try {
                withContext(Dispatchers.IO) { server.accept() }
            } catch (_: SocketException) {
                return
            } catch (e: Exception) {
                if (server.isClosed) return
                FileLogger.w(TAG, "端口转发接受连接失败 ${tunnel.info.localPort}", e)
                return
            }
            tunnel.clientSockets.add(client)
            scope.launch { pumpConnection(tunnel, client) }
        }
    }

    /** 一条代理连接：本地 socket 与目标 socket 双向转发，任一方向结束即双向断开。 */
    private suspend fun pumpConnection(tunnel: Tunnel, client: Socket) {
        val remote = try {
            withContext(Dispatchers.IO) {
                Socket().apply {
                    connect(
                        InetSocketAddress(tunnel.info.remoteHost, tunnel.info.remotePort),
                        CONNECT_TIMEOUT_MS
                    )
                }
            }
        } catch (e: Exception) {
            FileLogger.w(TAG, "端口转发连接目标失败 ${tunnel.info.remoteHost}:${tunnel.info.remotePort}", e)
            runCatching { client.close() }
            tunnel.clientSockets.remove(client)
            return
        }
        tunnel.clientSockets.add(remote)
        val up = scope.async(Dispatchers.IO) {
            runCatching { client.getInputStream().copyTo(remote.getOutputStream()) }
        }
        val down = scope.async(Dispatchers.IO) {
            runCatching { remote.getInputStream().copyTo(client.getOutputStream()) }
        }
        try {
            // 任一方向先结束就返回；finally 里关 socket 会打断另一方向阻塞的 copyTo。
            select<Unit> {
                up.onJoin { }
                down.onJoin { }
            }
        } finally {
            runCatching { client.close() }
            runCatching { remote.close() }
        }
        runCatching { up.await() }
        runCatching { down.await() }
        tunnel.clientSockets.remove(client)
        tunnel.clientSockets.remove(remote)
    }

    private fun closeTunnel(tunnel: Tunnel) {
        runCatching { tunnel.acceptJob?.cancel() }
        runCatching { tunnel.listenJob?.cancel() }
        runCatching { tunnel.forwarder?.close() }
        runCatching { tunnel.serverSocket?.close() }
        val clients = synchronized(tunnel.clientSockets) { tunnel.clientSockets.toList() }
        clients.forEach { runCatching { it.close() } }
    }

    private companion object {
        /** 每个归属（插件）最多同时保留的映射数。 */
        const val MAX_PER_OWNER = 8

        /** 全局映射数上限。 */
        const val MAX_TOTAL = 32

        /** 本地端口被占用时向后顺延尝试的次数。 */
        const val PORT_SCAN_ATTEMPTS = 20

        /** 本地 TCP 代理建监听前探测目标的超时。 */
        const val PROBE_TIMEOUT_MS = 5_000

        /** 代理连接建目标连接的超时。 */
        const val CONNECT_TIMEOUT_MS = 10_000
    }
}
