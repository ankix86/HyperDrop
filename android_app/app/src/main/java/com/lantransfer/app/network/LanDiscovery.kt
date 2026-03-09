package com.lantransfer.app.network

import com.lantransfer.app.core.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.Collections

data class DiscoveredPeer(
    val deviceId: String,
    val deviceName: String,
    val ip: String,
    val port: Int,
    val platform: String
)

class LanDiscovery(
    private val deviceIdProvider: () -> String,
    private val deviceNameProvider: () -> String,
    private val tcpPortProvider: () -> Int,
    private val platform: String
) {
    suspend fun discover(timeoutMs: Int = 1400): List<DiscoveredPeer> = withContext(Dispatchers.IO) {
        val localDeviceId = deviceIdProvider().trim()
        if (localDeviceId.isBlank()) return@withContext emptyList()

        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(0))
            broadcast = true
            soTimeout = 180
        }
        val found = linkedMapOf<String, DiscoveredPeer>()
        val targets = discoveryTargets()
        try {
            val probe = buildPacket("discover_request", localDeviceId)
            val deadline = System.currentTimeMillis() + timeoutMs
            var nextProbeAt = 0L
            while (System.currentTimeMillis() < deadline) {
                val now = System.currentTimeMillis()
                if (now >= nextProbeAt) {
                    targets.forEach { target ->
                        runCatching {
                            socket.send(DatagramPacket(probe, probe.size, target, AppConstants.DISCOVERY_PORT))
                        }
                    }
                    nextProbeAt = now + 320L
                }

                val buf = ByteArray(2048)
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val peer = parsePeer(packet, localDeviceId) ?: continue
                found[peer.deviceId] = peer
            }
        } finally {
            socket.close()
        }
        return@withContext found.values.sortedBy { it.deviceName.lowercase() }
    }

    suspend fun sendBye() = withContext(Dispatchers.IO) {
        val localId = deviceIdProvider().trim()
        if (localId.isBlank()) return@withContext
        runCatching {
            val bye = buildPacket("bye", localId)
            DatagramSocket().use { socket ->
                socket.broadcast = true
                discoveryTargets().forEach { target ->
                    runCatching {
                        socket.send(DatagramPacket(bye, bye.size, target, AppConstants.DISCOVERY_PORT))
                    }
                }
            }
        }
    }

    private fun buildPacket(type: String, localDeviceId: String): ByteArray {
        return JSONObject()
            .put("magic", AppConstants.DISCOVERY_MAGIC)
            .put("type", type)
            .put("device_id", localDeviceId)
            .put("device_name", deviceNameProvider())
            .put("port", tcpPortProvider())
            .put("platform", platform)
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    private fun parsePeer(packet: DatagramPacket, localDeviceId: String): DiscoveredPeer? {
        val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
        val obj = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (obj.optString("magic") != AppConstants.DISCOVERY_MAGIC) return null
        val type = obj.optString("type")
        if (type != "discover_response" && type != "announce") return null

        val peerId = obj.optString("device_id").trim()
        if (peerId.isBlank() || peerId == localDeviceId) return null

        val port = obj.optInt("port", 0)
        if (port <= 0) return null

        return DiscoveredPeer(
            deviceId = peerId,
            deviceName = obj.optString("device_name", "Unknown"),
            ip = packet.address.hostAddress ?: return null,
            port = port,
            platform = obj.optString("platform", "unknown")
        )
    }

    private fun discoveryTargets(): List<InetAddress> {
        val targets = linkedMapOf<String, InetAddress>()

        fun add(address: InetAddress?) {
            val ipv4 = address as? Inet4Address ?: return
            if (ipv4.isLoopbackAddress) return
            val host = ipv4.hostAddress ?: return
            targets.putIfAbsent(host, ipv4)
        }

        add(runCatching { InetAddress.getByName("255.255.255.255") }.getOrNull())
        runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces()).forEach { network ->
                if (!network.isUp || network.isLoopback || network.isVirtual) return@forEach
                network.interfaceAddresses.forEach { iface ->
                    add(iface.broadcast)
                }
            }
        }
        return targets.values.toList()
    }
}

class DiscoveryResponder(
    private val scope: CoroutineScope,
    private val deviceIdProvider: () -> String,
    private val deviceNameProvider: () -> String,
    private val tcpPortProvider: () -> Int,
    private val platform: String,
    private val onPeerOffline: ((deviceId: String) -> Unit)? = null
) {
    private var job: Job? = null
    var acceptingConnections: Boolean = true

    fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val socket = runCatching {
                    DatagramSocket(null).apply {
                        reuseAddress = true
                        bind(InetSocketAddress(AppConstants.DISCOVERY_PORT))
                        broadcast = true
                        soTimeout = 260
                    }
                }.getOrNull()

                if (socket == null) {
                    delay(1500)
                    continue
                }

                try {
                    var lastAnnounceAt = 0L
                    while (isActive) {
                        ensureActive()
                        val now = System.currentTimeMillis()
                        if (acceptingConnections && now - lastAnnounceAt >= 2200L) {
                            announcePresence(socket)
                            lastAnnounceAt = now
                        }

                        val buf = ByteArray(2048)
                        val packet = DatagramPacket(buf, buf.size)
                        try {
                            socket.receive(packet)
                        } catch (_: SocketTimeoutException) {
                            continue
                        }

                        val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                        val obj = runCatching { JSONObject(text) }.getOrNull() ?: continue
                        if (obj.optString("magic") != AppConstants.DISCOVERY_MAGIC) continue
                        val msgType = obj.optString("type")
                        val peerId = obj.optString("device_id").trim()

                        if (msgType == "bye" && peerId.isNotBlank() && peerId != deviceIdProvider()) {
                            onPeerOffline?.invoke(peerId)
                            continue
                        }

                        if (msgType != "discover_request") continue
                        if (peerId == deviceIdProvider()) continue
                        if (!acceptingConnections) continue

                        val response = buildPresencePacket("discover_response")
                        if (response.isNotEmpty()) {
                            socket.send(DatagramPacket(response, response.size, packet.address, packet.port))
                        }
                    }
                } finally {
                    socket.close()
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun announcePresence(socket: DatagramSocket) {
        val announce = buildPresencePacket("announce")
        if (announce.isEmpty()) return
        discoveryTargets().forEach { target ->
            runCatching {
                socket.send(DatagramPacket(announce, announce.size, target, AppConstants.DISCOVERY_PORT))
            }
        }
    }

    private fun buildPresencePacket(type: String): ByteArray {
        val localId = deviceIdProvider().trim()
        if (localId.isBlank()) return ByteArray(0)
        return JSONObject()
            .put("magic", AppConstants.DISCOVERY_MAGIC)
            .put("type", type)
            .put("device_id", localId)
            .put("device_name", deviceNameProvider())
            .put("port", tcpPortProvider())
            .put("platform", platform)
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    private fun discoveryTargets(): List<InetAddress> {
        val targets = linkedMapOf<String, InetAddress>()

        fun add(address: InetAddress?) {
            val ipv4 = address as? Inet4Address ?: return
            if (ipv4.isLoopbackAddress) return
            val host = ipv4.hostAddress ?: return
            targets.putIfAbsent(host, ipv4)
        }

        add(runCatching { InetAddress.getByName("255.255.255.255") }.getOrNull())
        runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces()).forEach { network ->
                if (!network.isUp || network.isLoopback || network.isVirtual) return@forEach
                network.interfaceAddresses.forEach { iface ->
                    add(iface.broadcast)
                }
            }
        }
        return targets.values.toList()
    }
}
