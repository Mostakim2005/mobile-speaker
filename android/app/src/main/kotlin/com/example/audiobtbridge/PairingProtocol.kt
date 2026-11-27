package com.example.audiobtbridge

data class QrPairingPayload(
    val ips: List<String>,
    val port: Int,
    val token: String,
    val matchCode: String?,
    val hostName: String
)

data class PairChallenge(
    val hostName: String,
    val challengeId: String,
    val choices: List<String>,
    val serverIp: String
)

sealed class PairResult {
    data class Success(val serverIp: String, val hostName: String, val token: String) : PairResult()
    data class Failure(val reason: String) : PairResult()
}

object PairingProtocol {
    const val DEFAULT_PORT = 12345
    const val WINDOWS_HOTSPOT_IP = "192.168.137.1"

    fun isValidIpv4(ip: String): Boolean {
        val parts = ip.trim().split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            if (part.isEmpty() || part.length > 3) return false
            val num = part.toIntOrNull() ?: return false
            num in 0..255
        }
    }

    fun parseQrPayload(raw: String): QrPairingPayload? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("as2p://pair?")) return null
        val query = trimmed.substringAfter("as2p://pair?", "")
        if (query.isEmpty()) return null

        val params = mutableMapOf<String, String>()
        for (pair in query.split('&')) {
            val eqIdx = pair.indexOf('=')
            if (eqIdx <= 0) continue
            val key = pair.substring(0, eqIdx).trim()
            val value = pair.substring(eqIdx + 1).trim()
            params[key] = value
        }

        val ips = params["ips"]
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { isValidIpv4(it) }
            ?.distinct()
            ?: emptyList()
        if (ips.isEmpty()) return null

        val port = params["p"]?.toIntOrNull() ?: DEFAULT_PORT
        if (port !in 1..65535) return null

        val token = params["t"]?.trim().orEmpty()
        if (token.isEmpty()) return null

        val matchCode = params["c"]?.trim()?.takeIf { it.isNotEmpty() }
        val hostName = params["n"]?.trim()?.takeIf { it.isNotEmpty() } ?: "PC"
        return QrPairingPayload(
            ips = ips,
            port = port,
            token = token,
            matchCode = matchCode,
            hostName = hostName
        )
    }

    fun parseChallengePacket(message: String, serverIp: String): PairChallenge? {
        val trimmed = message.trim()
        if (!trimmed.startsWith("AS2P_PAIR_CHALLENGE|")) return null
        val parts = trimmed.split('|')
        if (parts.size != 4) return null
        val host = parts[1].trim().ifEmpty { serverIp }
        val challengeId = parts[2].trim()
        if (challengeId.isEmpty()) return null
        val choices = parts[3]
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (choices.size != 3) return null
        return PairChallenge(
            hostName = host,
            challengeId = challengeId,
            choices = choices,
            serverIp = serverIp
        )
    }

    fun parseAckPacket(message: String): Pair<String, String>? {
        val trimmed = message.trim()
        if (!trimmed.startsWith("AS2P_PAIR_ACK|")) return null
        val parts = trimmed.split('|')
        if (parts.size < 2) return null
        val host = parts[1].trim().ifEmpty { "PC" }
        val token = parts.getOrNull(2)?.trim().orEmpty()
        return host to token
    }

    fun buildQrPairMessage(token: String): ByteArray =
        "AS2P_QR_PAIR|${token.trim()}".toByteArray(Charsets.UTF_8)

    fun buildPairProbeMessage(): ByteArray =
        "AS2P_PAIR_PROBE".toByteArray(Charsets.UTF_8)

    fun buildPairVerifyMessage(challengeId: String, choice: String): ByteArray =
        "AS2P_PAIR_VERIFY|${challengeId.trim()}|${choice.trim()}".toByteArray(Charsets.UTF_8)

    fun buildProbeTargets(
        broadcasts: List<String>,
        gatewayIps: List<String>,
        lastServerIp: String?,
        discoveredIps: List<String> = emptyList()
    ): List<String> {
        val out = LinkedHashSet<String>()
        lastServerIp?.trim()?.takeIf { isValidIpv4(it) }?.let { out.add(it) }
        discoveredIps.map { it.trim() }.filter { isValidIpv4(it) }.forEach { out.add(it) }
        gatewayIps.map { it.trim() }.filter { isValidIpv4(it) }.forEach { out.add(it) }
        out.add(WINDOWS_HOTSPOT_IP)
        broadcasts.map { it.trim() }.filter { isValidIpv4(it) }.forEach { out.add(it) }
        out.add("255.255.255.255")
        return out.toList()
    }
}
