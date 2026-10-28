package com.mdev.mnotes.core.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sprawdzenie radia przed łączeniem (3b-B3). */
class LinkPreflightTest {
    @Test
    fun nearbyWarnsAboutEveryRadioBluetoothOnlyAboutBluetooth() {
        assertEquals(listOf(LinkIssue.BT_OFF, LinkIssue.LOCATION_OFF, LinkIssue.WIFI_OFF), LinkPreflight.check(false, btOn = false, locationOn = false, wifiOn = false))
        assertEquals(listOf(LinkIssue.BT_OFF), LinkPreflight.check(true, btOn = false, locationOn = false, wifiOn = false))
        assertEquals(emptyList<LinkIssue>(), LinkPreflight.check(false, btOn = true, locationOn = true, wifiOn = true))
        assertEquals("nie wiadomo → bez ostrzeżeń", emptyList<LinkIssue>(), LinkPreflight.check(false, null, null, null))
        assertTrue(LinkIssue.BT_OFF.blocking)
        assertTrue(LinkIssue.entries.filter { it.blocking } == listOf(LinkIssue.BT_OFF))
    }
}
