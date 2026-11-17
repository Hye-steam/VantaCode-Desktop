package dev.aigw.app.data

import java.net.Inet4Address
import java.net.NetworkInterface

/** 取当前网络的局域网 IPv4 地址，用于「局域网」那条接入地址。 */
object LanAddresses {

    fun current(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isLoopbackAddress }
            .mapNotNull { it.hostAddress }
            .filter { it.isNotEmpty() }
            .distinct()
    } catch (_: Exception) {
        emptyList()
    }
}
