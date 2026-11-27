package com.example.audiobtbridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList

object PairingClient {
    private const val TAG = "AS2P_Pairing"

    fun collectGatewayAndBroadcastTargets(context: Context): Pair<List<String>, List<String>> {
        val broadcasts = LinkedHashSet<String>()
        val gateways = LinkedHashSet<String>()

        try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (ia in nif.interfaceAddresses) {
                    ia.broadcast?.hostAddress?.let { addr ->
                        if (PairingProtocol.isValidIpv4(addr)) broadcasts.add(addr)
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                @Suppress("DEPRECATION")
                for (net in cm.allNetworks) {
                    val lp = cm.getLinkProperties(net) ?: continue
                    for (route in lp.routes) {
                        val gw = route.gateway
                        if (gw is Inet4Address) {
                            val host = gw.hostAddress
                            if (host != null && PairingProtocol.isValidIpv4(host) && host != "0.0.0.0") {
                                gateways.add(host)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return broadcasts.toList() to gateways.toList()
    }

    private suspend fun discoverMdnsHosts(context: Context, waitMs: Long = 900L): List<String> {
        val discovered = CopyOnWriteArrayList<String>()
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return emptyList()
        var listener: NsdManager.DiscoveryListener? = null
        try {
            val dl = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (!serviceInfo.serviceType.contains("_as2p")) return
                    @Suppress("DEPRECATION")
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(si: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val host = si.host
                            if (host is Inet4Address) {
                                host.hostAddress?.let { discovered.add(it) }
                            }
                        }
                    })
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            listener = dl
            nsd.discoverServices("_as2p._udp.", NsdManager.PROTOCOL_DNS_SD, dl)
            delay(waitMs)
        } catch (e: Exception) {
            Log.w(TAG, "mDNS scan skipped: ${e.message}")
        } finally {
            try {
                listener?.let { nsd.stopServiceDiscovery(it) }
            } catch (_: Exception) {}
        }
        return discovered.distinct()
    }

    suspend fun pairViaQr(payload: QrPairingPayload, timeoutMs: Long = 3000L): PairResult =
        withContext(Dispatchers.IO) {
            val msg = PairingProtocol.buildQrPairMessage(payload.token)
            val socket = DatagramSocket()
            socket.soTimeout = 350
            try {
                val deadline = System.currentTimeMillis() + timeoutMs
                val buf = ByteArray(1024)
                val packet = DatagramPacket(buf, buf.size)
                var wavesSent = 0

                while (System.currentTimeMillis() < deadline) {
                    if (wavesSent < 3) {
                        for (ip in payload.ips) {
                            try {
                                val target = InetAddress.getByName(ip)
                                socket.send(DatagramPacket(msg, msg.size, target, payload.port))
                            } catch (_: Exception) {}
                        }
                        wavesSent++
                    }
                    try {
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val ack = PairingProtocol.parseAckPacket(text)
                        if (ack != null) {
                            val ip = packet.address.hostAddress ?: payload.ips.first()
                            return@withContext PairResult.Success(
                                serverIp = ip,
                                hostName = ack.first,
                                token = ack.second.ifEmpty { payload.token }
                            )
                        }
                        if (text.startsWith("AS2P_PAIR_DENY")) {
                            return@withContext PairResult.Failure("QR code expired — tap 'New Code / QR' on PC and scan again.")
                        }
                    } catch (_: SocketTimeoutException) {
                        // Retry burst wave on next iteration until deadline.
                    }
                }
                PairResult.Failure("No response from PC (${payload.ips.joinToString()}). Make sure PC server is listening and firewall allows UDP ${payload.port}.")
            } catch (e: Exception) {
                PairResult.Failure("QR pairing error: ${e.message}")
            } finally {
                socket.close()
            }
        }

    suspend fun discoverChallenges(
        context: Context,
        lastServerIp: String?,
        timeoutMs: Long = 2400L
    ): List<PairChallenge> = withContext(Dispatchers.IO) {
        val (broadcasts, gateways) = collectGatewayAndBroadcastTargets(context)
        val mdnsIps = discoverMdnsHosts(context, waitMs = 800L)
        val targets = PairingProtocol.buildProbeTargets(
            broadcasts = broadcasts,
            gatewayIps = gateways,
            lastServerIp = lastServerIp,
            discoveredIps = mdnsIps
        )
        val msg = PairingProtocol.buildPairProbeMessage()
        val found = LinkedHashMap<String, PairChallenge>()
        val socket = DatagramSocket()
        socket.broadcast = true
        socket.soTimeout = 350

        try {
            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(1024)
            val packet = DatagramPacket(buf, buf.size)
            var wavesSent = 0

            while (System.currentTimeMillis() < deadline) {
                if (wavesSent < 3) {
                    for (ip in targets) {
                        try {
                            socket.send(
                                DatagramPacket(
                                    msg,
                                    msg.size,
                                    InetAddress.getByName(ip),
                                    PairingProtocol.DEFAULT_PORT
                                )
                            )
                        } catch (_: Exception) {}
                    }
                    wavesSent++
                }
                try {
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val serverIp = packet.address.hostAddress ?: continue
                    val challenge = PairingProtocol.parseChallengePacket(text, serverIp)
                    if (challenge != null) {
                        found["${challenge.serverIp}|${challenge.challengeId}"] = challenge
                    }
                } catch (_: SocketTimeoutException) {
                    if (found.isNotEmpty() && wavesSent >= 2) break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Probe error: ${e.message}")
        } finally {
            socket.close()
        }
        found.values.toList()
    }

    suspend fun verifyChallenge(
        challenge: PairChallenge,
        choice: String,
        timeoutMs: Long = 2200L
    ): PairResult = withContext(Dispatchers.IO) {
        val msg = PairingProtocol.buildPairVerifyMessage(challenge.challengeId, choice)
        val socket = DatagramSocket()
        socket.soTimeout = 450
        try {
            val target = InetAddress.getByName(challenge.serverIp)
            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(1024)
            val packet = DatagramPacket(buf, buf.size)
            var sent = 0

            while (System.currentTimeMillis() < deadline) {
                if (sent < 2) {
                    socket.send(DatagramPacket(msg, msg.size, target, PairingProtocol.DEFAULT_PORT))
                    sent++
                }
                try {
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val ack = PairingProtocol.parseAckPacket(text)
                    if (ack != null) {
                        return@withContext PairResult.Success(
                            serverIp = challenge.serverIp,
                            hostName = ack.first,
                            token = ack.second
                        )
                    }
                    if (text.startsWith("AS2P_PAIR_DENY")) {
                        return@withContext PairResult.Failure("Number did not match — check the new number on your PC.")
                    }
                } catch (_: SocketTimeoutException) {}
            }
            PairResult.Failure("Timed out verifying code with ${challenge.hostName}.")
        } catch (e: Exception) {
            PairResult.Failure("Verification error: ${e.message}")
        } finally {
            socket.close()
        }
    }
}
