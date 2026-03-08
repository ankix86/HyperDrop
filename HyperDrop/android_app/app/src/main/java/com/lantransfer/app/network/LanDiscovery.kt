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
import java.net.InetAddress
import java.net.SocketTimeoutException

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

        val socket = DatagramSocket().apply {
            broadcast = true
            soTimeout = 220
        }
        val found = linkedMapOf<String, DiscoveredPeer>()
        try {
            val probe = buildPacket("discover_request", localDeviceId)
            socket.send(
                DatagramPacket(
                    probe,
                    probe.size,
                    InetAddress.getByName("255.255.255.255"),
                    AppConstants.DISCOVERY_PORT
                )
            )

            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
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

    private fun buildPacket(type: String, localDeviceId: String): ByteArray {
        val json = JSONObject()
            .put("magic", AppConstants.DISCOVERY_MAGIC)
            .put("type", type)
            .put("device_id", localDeviceId)
            .put("device_name", deviceNameProvider())
            .put("port", tcpPortProvider())
            .put("platform", platform)
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private fun parsePeer(packet: DatagramPacket, localDeviceId: String): DiscoveredPeer? {
        val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
        val obj = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (obj.optString("magic") != AppConstants.DISCOVERY_MAGIC) return null
        if (obj.optString("type") != "discover_response") return null

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
}

class DiscoveryResponder(
    private val scope: CoroutineScope,
    private val deviceIdProvider: () -> String,
    private val deviceNameProvider: () -> String,
    private val tcpPortProvider: () -> Int,
    private val platform: String
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val socket = runCatching {
                    DatagramSocket(null).apply {
                        reuseAddress = true
                        bind(java.net.InetSocketAddress(AppConstants.DISCOVERY_PORT))
                        broadcast = true
                        soTimeout = 300
                    }
                }.getOrNull()

                if (socket == null) {
                    delay(1500)
                    continue
                }

                try {
                    while (isActive) {
                        ensureActive()
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
                        if (obj.optString("type") != "discover_request") continue
                        if (obj.optString("device_id") == deviceIdProvider()) continue

                        val localId = deviceIdProvider().trim()
                        if (localId.isBlank()) continue

                        val response = JSONObject()
                            .put("magic", AppConstants.DISCOVERY_MAGIC)
                            .put("type", "discover_response")
                            .put("device_id", localId)
                            .put("device_name", deviceNameProvider())
                            .put("port", tcpPortProvider())
                            .put("platform", platform)
                            .toString()
                            .toByteArray(Charsets.UTF_8)

                        socket.send(DatagramPacket(response, response.size, packet.address, packet.port))
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
}
