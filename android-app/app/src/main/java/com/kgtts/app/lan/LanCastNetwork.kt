package com.lhtstudio.kigtts.app.lan

import java.net.Inet4Address
import java.net.NetworkInterface

internal object LanCastNetwork {
    fun addresses(): List<LanCastAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .asSequence()
            .filter { it.isUp && !it.isLoopback && !isCellularInterface(it.name) }
            .flatMap { network ->
                network.inetAddresses.toList().asSequence()
                    .filterIsInstance<Inet4Address>()
                    .filter { address ->
                        !address.isLoopbackAddress &&
                            !address.isLinkLocalAddress &&
                            address.isSiteLocalAddress
                    }
                    .map { address ->
                        LanCastAddress(
                            id = "${network.name}:${address.hostAddress}",
                            interfaceName = friendlyName(network.name, network.displayName),
                            address = address.hostAddress.orEmpty()
                        )
                    }
            }
            .distinctBy { it.address }
            .sortedWith(
                compareBy<LanCastAddress> { addressPriority(it.interfaceName) }
                    .thenBy { it.interfaceName }
                    .thenBy { it.address }
            )
            .toList()
    }.getOrDefault(emptyList())

    private fun friendlyName(name: String, displayName: String?): String {
        val normalized = name.lowercase()
        return when {
            normalized.startsWith("wlan") || normalized.startsWith("wifi") -> "Wi-Fi"
            normalized.startsWith("ap") || normalized.contains("softap") -> "个人热点"
            normalized.startsWith("eth") -> "有线网络"
            normalized.startsWith("rndis") || normalized.startsWith("usb") -> "USB 网络"
            else -> displayName?.takeIf { it.isNotBlank() } ?: name
        }
    }

    private fun addressPriority(label: String): Int = when (label) {
        "Wi-Fi" -> 0
        "个人热点" -> 1
        "有线网络" -> 2
        "USB 网络" -> 3
        else -> 4
    }

    private fun isCellularInterface(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized.startsWith("rmnet") ||
            normalized.startsWith("ccmni") ||
            normalized.startsWith("pdp") ||
            normalized.startsWith("radio")
    }
}
