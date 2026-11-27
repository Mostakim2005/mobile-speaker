package com.example.audiobtbridge

import android.content.Context

data class SavedPc(
    val hostName: String,
    val ip: String,
    val token: String,
    val lastConnectedMs: Long
)

object SavedPcStore {
    private const val PREFS_NAME = "AS2P_Prefs"
    private const val KEY_SAVED_PCS = "saved_pcs_tsv"
    private const val DEFAULT_MAX_ITEMS = 5

    private fun cleanField(raw: String): String =
        raw.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()

    fun upsert(existing: List<SavedPc>, entry: SavedPc, maxItems: Int = DEFAULT_MAX_ITEMS): List<SavedPc> {
        val cleanIp = cleanField(entry.ip)
        if (!PairingProtocol.isValidIpv4(cleanIp)) return existing
        val normalized = SavedPc(
            hostName = cleanField(entry.hostName).ifEmpty { "PC" },
            ip = cleanIp,
            token = cleanField(entry.token),
            lastConnectedMs = entry.lastConnectedMs
        )
        val filtered = existing.filterNot { it.ip == normalized.ip }
        return (listOf(normalized) + filtered).take(maxItems.coerceAtLeast(1))
    }

    fun removeByIp(existing: List<SavedPc>, ip: String): List<SavedPc> =
        existing.filterNot { it.ip == ip.trim() }

    fun serialize(list: List<SavedPc>): String =
        list.joinToString("\n") { item ->
            "${cleanField(item.ip)}\t${cleanField(item.hostName)}\t${cleanField(item.token)}\t${item.lastConnectedMs}"
        }

    fun deserialize(raw: String?): List<SavedPc> {
        if (raw.isNullOrBlank()) return emptyList()
        val seen = HashSet<String>()
        val out = mutableListOf<SavedPc>()
        for (line in raw.lines()) {
            if (line.isBlank()) continue
            val parts = line.split('\t')
            if (parts.size < 4) continue
            val ip = parts[0].trim()
            if (!PairingProtocol.isValidIpv4(ip) || !seen.add(ip)) continue
            val host = parts[1].trim().ifEmpty { "PC" }
            val token = parts[2].trim()
            val ts = parts[3].trim().toLongOrNull() ?: 0L
            out.add(SavedPc(hostName = host, ip = ip, token = token, lastConnectedMs = ts))
        }
        return out
    }

    fun loadSavedPcs(context: Context): List<SavedPc> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = deserialize(prefs.getString(KEY_SAVED_PCS, null))
        if (stored.isNotEmpty()) return stored

        // Migrate single legacy saved PC if present.
        val legacyIp = prefs.getString("last_server_ip", null)?.trim().orEmpty()
        if (!PairingProtocol.isValidIpv4(legacyIp)) return emptyList()
        val legacyName = prefs.getString("last_server_name", null)?.trim().orEmpty().ifEmpty { "PC" }
        val legacyToken = prefs.getString("last_pair_token", null)?.trim().orEmpty()
        return listOf(SavedPc(legacyName, legacyIp, legacyToken, System.currentTimeMillis()))
    }

    fun savePairedPc(context: Context, ip: String, hostName: String, token: String): List<SavedPc> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = upsert(
            loadSavedPcs(context),
            SavedPc(hostName = hostName, ip = ip, token = token, lastConnectedMs = System.currentTimeMillis())
        )
        prefs.edit()
            .putString(KEY_SAVED_PCS, serialize(updated))
            .putString("last_server_ip", ip)
            .putString("last_server_name", hostName)
            .putString("last_pair_token", token)
            .apply()
        return updated
    }

    fun deleteSavedPc(context: Context, ip: String): List<SavedPc> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = removeByIp(loadSavedPcs(context), ip)
        val editor = prefs.edit().putString(KEY_SAVED_PCS, serialize(updated))
        val activeFallback = updated.firstOrNull()
        if (activeFallback != null) {
            editor
                .putString("last_server_ip", activeFallback.ip)
                .putString("last_server_name", activeFallback.hostName)
                .putString("last_pair_token", activeFallback.token)
        } else {
            editor
                .remove("last_server_ip")
                .remove("last_server_name")
                .remove("last_pair_token")
        }
        editor.apply()
        return updated
    }
}
