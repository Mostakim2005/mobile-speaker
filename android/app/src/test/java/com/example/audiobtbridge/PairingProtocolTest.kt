package com.example.audiobtbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingProtocolTest {

    @Test
    fun `parses compact multi-IP QR URI`() {
        val raw = "as2p://pair?v=1&ips=192.168.137.1,192.168.1.50&p=12345&t=abcd1234ef56&c=47&n=WorkPC"
        val parsed = PairingProtocol.parseQrPayload(raw)
        assertNotNull(parsed)
        assertEquals(listOf("192.168.137.1", "192.168.1.50"), parsed!!.ips)
        assertEquals(12345, parsed.port)
        assertEquals("abcd1234ef56", parsed.token)
        assertEquals("47", parsed.matchCode)
        assertEquals("WorkPC", parsed.hostName)
    }

    @Test
    fun `rejects invalid or empty QR URIs`() {
        assertNull(PairingProtocol.parseQrPayload("https://example.com"))
        assertNull(PairingProtocol.parseQrPayload("as2p://pair?v=1&ips=not-an-ip&p=12345&t=abc"))
        assertNull(PairingProtocol.parseQrPayload("as2p://pair?v=1&ips=192.168.1.5&p=12345&t="))
        assertNull(PairingProtocol.parseQrPayload("as2p://pair?v=1&ips=192.168.1.5&p=99999&t=abc"))
    }

    @Test
    fun `parses number-match challenge and ack packets`() {
        val challenge = PairingProtocol.parseChallengePacket(
            "AS2P_PAIR_CHALLENGE|Desktop|01020304|19,47,83",
            "192.168.137.1"
        )
        assertNotNull(challenge)
        assertEquals("Desktop", challenge!!.hostName)
        assertEquals("01020304", challenge.challengeId)
        assertEquals(listOf("19", "47", "83"), challenge.choices)
        assertEquals("192.168.137.1", challenge.serverIp)

        val ack = PairingProtocol.parseAckPacket("AS2P_PAIR_ACK|Desktop|tok999")
        assertNotNull(ack)
        assertEquals("Desktop", ack!!.first)
        assertEquals("tok999", ack.second)
    }

    @Test
    fun `buildProbeTargets prioritizes saved IP, gateways, and Windows hotspot`() {
        val targets = PairingProtocol.buildProbeTargets(
            broadcasts = listOf("192.168.1.255", "192.168.137.255"),
            gatewayIps = listOf("192.168.43.1"),
            lastServerIp = "192.168.1.50",
            discoveredIps = listOf("192.168.43.120")
        )
        assertEquals("192.168.1.50", targets[0])
        assertTrue(targets.contains("192.168.43.120"))
        assertTrue(targets.contains("192.168.43.1"))
        assertTrue(targets.contains(PairingProtocol.WINDOWS_HOTSPOT_IP))
        assertEquals("255.255.255.255", targets.last())
    }
}
