package com.vantacode.feature.plugin.runtime

import com.vantacode.feature.agent.domain.container.ConnectionState
import com.vantacode.feature.agent.domain.container.RemoteSshConnection
import com.vantacode.feature.settings.data.repository.ExecutionModeHolder
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * 插件端口转发（本地模式）：直通、TCP 代理、复用、限流与清理。
 * 只覆盖不依赖 SSH 的本地路径；SSH 隧道路径需真实服务器，由人工/Windows 烟测覆盖。
 */
class PluginPortForwardManagerTest {

    private lateinit var echoServer: ServerSocket
    private var echoPort: Int = 0
    private lateinit var manager: PluginPortForwardManager

    @Before
    fun setUp() {
        echoServer = startEchoServer()
        echoPort = echoServer.localPort
        val sshConnection = mockk<RemoteSshConnection>(relaxed = true)
        every { sshConnection.connectionState } returns MutableStateFlow(ConnectionState.DISCONNECTED)
        // ExecutionModeHolder 默认 LOCAL_PROOT：本地模式。
        manager = PluginPortForwardManager(sshConnection, ExecutionModeHolder())
    }

    @After
    fun tearDown() {
        runCatching { manager.closeAll() }
        runCatching { echoServer.close() }
    }

    @Test
    fun forwardLoopbackTarget_isDirect() = runTest {
        val info = manager.forward("test.plugin", "127.0.0.1", echoPort)
        assertTrue(info.direct)
        assertEquals(echoPort, info.localPort)
        assertEquals("127.0.0.1", info.remoteHost)
        assertEquals(echoPort, info.remotePort)
        assertEquals("http://127.0.0.1:$echoPort", info.url)
        // 直通即目标本身：连上去能通。
        assertEquals("ping", echoThrough(info.localPort, "ping"))
        assertEquals(1, manager.list("test.plugin").size)
        assertTrue(manager.list("other.plugin").isEmpty())
    }

    @Test
    fun forwardWithDifferentLocalPort_proxiesTcp() = runTest {
        val proxyPort = freePort()
        val info = manager.forward("test.plugin", "127.0.0.1", echoPort, proxyPort)
        assertFalse(info.direct)
        assertEquals(proxyPort, info.localPort)
        assertEquals("hello", echoThrough(info.localPort, "hello"))
        assertTrue(manager.close("test.plugin", info.id))
        assertTrue(manager.list("test.plugin").isEmpty())
        // 端口已释放：再连应被拒绝。
        try {
            Socket("127.0.0.1", proxyPort).use { }
            fail("端口应已释放")
        } catch (_: IOException) {
            // expected
        }
    }

    @Test
    fun forwardSameTarget_reusesMapping() = runTest {
        val first = manager.forward("test.plugin", "127.0.0.1", echoPort)
        val second = manager.forward("test.plugin", "127.0.0.1", echoPort)
        assertEquals(first.id, second.id)
        assertEquals(1, manager.list("test.plugin").size)
    }

    @Test
    fun forwardOccupiedLocalPort_bumpsForward() = runTest {
        // 占一个临时端口；要求它 < 65534，否则顺延目标会越界。
        var blocker = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        while (blocker.localPort >= 65534) {
            blocker.close()
            blocker = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        }
        try {
            val occupied = blocker.localPort
            val info = manager.forward("test.plugin", "127.0.0.1", echoPort, occupied)
            assertFalse(info.direct)
            assertEquals(occupied + 1, info.localPort)
            assertEquals("hi", echoThrough(info.localPort, "hi"))
        } finally {
            blocker.close()
        }
    }

    @Test
    fun forwardInvalidPort_throwsParamInvalid() = runTest {
        val badRemote = try {
            manager.forward("test.plugin", "127.0.0.1", 0)
            fail("port 0 应抛 PARAM_INVALID")
        } catch (e: PortForwardException) {
            e
        }
        assertEquals("PARAM_INVALID", badRemote.code)
        val tooBig = try {
            manager.forward("test.plugin", "127.0.0.1", 70000)
            fail("port 70000 应抛 PARAM_INVALID")
        } catch (e: PortForwardException) {
            e
        }
        assertEquals("PARAM_INVALID", tooBig.code)
        val badLocal = try {
            manager.forward("test.plugin", "127.0.0.1", echoPort, 70000)
            fail("localPort 70000 应抛 PARAM_INVALID")
        } catch (e: PortForwardException) {
            e
        }
        assertEquals("PARAM_INVALID", badLocal.code)
    }

    @Test
    fun forwardUnreachableTarget_throwsTargetUnreachable() = runTest {
        // 目标端口无监听：本地代理建监听前先探测，不可达立即报错且不留记录。
        // 注：回环目标默认走 direct（不探测，与 Android 本地模式一致），
        // 这里用 localPort != remotePort 强制走代理路径以覆盖探测分支；
        // localPort 取 deadPort 相邻端口（必不相等，避免两次取临时端口撞号）。
        val deadPort = freePort()
        val proxyPort = if (deadPort < 65535) deadPort + 1 else deadPort - 1
        val error = try {
            manager.forward("test.plugin", "127.0.0.1", deadPort, proxyPort)
            fail("不可达目标应抛 TARGET_UNREACHABLE")
        } catch (e: PortForwardException) {
            e
        }
        assertEquals("TARGET_UNREACHABLE", error.code)
        assertTrue(manager.list("test.plugin").isEmpty())
    }

    @Test
    fun closeUnknownId_returnsFalseAndForeignId_forbidden() = runTest {
        assertFalse(manager.close("test.plugin", "no-such-id"))
        val info = manager.forward("test.plugin", "127.0.0.1", echoPort)
        val error = try {
            manager.close("other.plugin", info.id)
            fail("关别人的映射应抛 FORBIDDEN")
        } catch (e: PortForwardException) {
            e
        }
        assertEquals("FORBIDDEN", error.code)
        assertTrue(manager.close("test.plugin", info.id))
    }

    @Test
    fun closeAll_releasesOnlyOwnMappings() = runTest {
        val proxyPort = freePort()
        manager.forward("test.plugin", "127.0.0.1", echoPort, proxyPort)
        manager.forward("other.plugin", "127.0.0.1", echoPort)
        manager.closeAll("test.plugin")
        assertTrue(manager.list("test.plugin").isEmpty())
        assertEquals(1, manager.list("other.plugin").size)
        try {
            Socket("127.0.0.1", proxyPort).use { }
            fail("代理端口应已释放")
        } catch (_: IOException) {
            // expected
        }
    }

    /** 在 127.0.0.1 上起一个回显服务器（收到什么原样发回），端口由系统分配。 */
    private fun startEchoServer(): ServerSocket {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val thread = Thread {
            while (!server.isClosed) {
                try {
                    val socket = server.accept()
                    Thread {
                        try {
                            socket.getInputStream().copyTo(socket.getOutputStream())
                        } catch (_: Exception) {
                            // 客户端断开即结束
                        } finally {
                            runCatching { socket.close() }
                        }
                    }.apply { isDaemon = true }.start()
                } catch (_: Exception) {
                    break
                }
            }
        }.apply { isDaemon = true }
        thread.start()
        return server
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    /** 连 [port] 发 [payload] 并读回同样长度的回显。 */
    private fun echoThrough(port: Int, payload: String): String {
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 5_000
            val bytes = payload.toByteArray(Charsets.UTF_8)
            socket.getOutputStream().write(bytes)
            socket.getOutputStream().flush()
            val buf = ByteArray(bytes.size)
            var read = 0
            while (read < buf.size) {
                val n = socket.getInputStream().read(buf, read, buf.size - read)
                assertTrue("回显中断", n > 0)
                read += n
            }
            return String(buf, Charsets.UTF_8)
        }
    }
}
