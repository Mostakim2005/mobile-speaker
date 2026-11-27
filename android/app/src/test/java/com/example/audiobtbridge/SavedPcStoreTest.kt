package com.example.audiobtbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPcStoreTest {

    @Test
    fun `upsert moves latest IP to top, deduplicates, and caps list`() {
        var list = emptyList<SavedPc>()
        for (i in 1..6) {
            list = SavedPcStore.upsert(
                list,
                SavedPc("PC-$i", "192.168.1.$i", "tok$i", i.toLong()),
                maxItems = 5
            )
        }
        assertEquals(5, list.size)
        assertEquals("192.168.1.6", list[0].ip)

        // Updating an older IP promotes it to index 0 with fresh token.
        list = SavedPcStore.upsert(
            list,
            SavedPc("PC-2-Renamed", "192.168.1.2", "newTok", 99L),
            maxItems = 5
        )
        assertEquals(5, list.size)
        assertEquals("192.168.1.2", list[0].ip)
        assertEquals("PC-2-Renamed", list[0].hostName)
        assertEquals("newTok", list[0].token)
    }

    @Test
    fun `serialize and deserialize round-trip safely and strip control chars`() {
        val items = listOf(
            SavedPc("Living\tRoom\nPC", "192.168.137.1", "tok\t123", 1000L),
            SavedPc("DeskPC", "192.168.1.44", "tok456", 2000L)
        )
        val encoded = SavedPcStore.serialize(items)
        val decoded = SavedPcStore.deserialize(encoded)
        assertEquals(2, decoded.size)
        assertEquals("Living Room PC", decoded[0].hostName)
        assertEquals("192.168.137.1", decoded[0].ip)
        assertEquals("tok 123", decoded[0].token)
        assertEquals(1000L, decoded[0].lastConnectedMs)

        val removed = SavedPcStore.removeByIp(decoded, "192.168.137.1")
        assertEquals(1, removed.size)
        assertTrue(removed.none { it.ip == "192.168.137.1" })
    }
}
