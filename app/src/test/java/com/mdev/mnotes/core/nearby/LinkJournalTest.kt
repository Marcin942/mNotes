package com.mdev.mnotes.core.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Random

/** Dziennik łącza z anonimizacją (3b-B1): czyszczenie, skróty z solą, limit bufora, zapis i wpisy z maszyny. */
class LinkJournalTest {
    private var now = 1_700_000_000_000L
    private val env = LinkEnv("14", 34, "samsung", "SM-X820", "6.1", bluetooth = true, wifi = false, location = true,
        permissions = mapOf("BLUETOOTH_CONNECT" to true, "BLUETOOTH_SCAN" to false))

    private fun journal(max: Int = LinkJournal.MAX_BYTES) = LinkJournal(max, clock = { now })

    @Test
    fun scrubRemovesAddressesNamesPathsFilesAndCodes() {
        val names = listOf("Galaxy Tab S10+", "Pixel 8")
        fun c(t: String) = LinkScrub.clean(t, names)
        assertEquals("łączę z: ‹MAC›", c("łączę z: AA:1B:00:9c:FF:02"))
        assertEquals("łączę z: ‹MAC›", c("łączę z: aa-1b-00-9c-ff-02"))
        assertEquals("znaleziono: ‹urządzenie›", c("znaleziono: galaxy tab s10+"))
        assertEquals("rozłączono: ‹urządzenie›", c("rozłączono: Pixel 8"))
        assertEquals("plik ‹ścieżka›", c("plik /storage/emulated/0/DCIM/Camera"))
        assertEquals("brak ‹adres›", c("brak content://media/external/images/media/1234"))
        assertEquals("zapis ‹plik› nieudany", c("zapis IMG_20261028_101500.jpg nieudany"))
        assertEquals("zeszyt ‹plik›", c("zeszyt Analiza.mnotes"))
        assertEquals("tytuł ‹tekst›", c("tytuł „Wykład 3: całki”"))
        assertEquals("kod parowania ‹kod›", c("kod parowania 482 913"))
        assertEquals("cyfry: ‹kod›", c("cyfry: 482913"))
        assertEquals("punkt ‹skrót›", c("punkt mn-0123456789abcdef"))
        assertEquals("usługa ‹uuid›", c("usługa 8b5e2c4a-3f71-4d09-b6a2-71c9e04d5f18"))
        assertEquals("konto ‹e-mail›", c("konto jan.kowalski@gmail.com"))
        assertEquals("klucz ‹klucz›", c("klucz q83vEjRWeJq83vEjRWeJq83vEjRWeJ0="))
        assertFalse(c("a\nb|c").contains('\n')); assertFalse(c("a|b").contains('|'))
    }

    @Test
    fun scrubCatchesWhatSlippedThroughBefore() {
        // luki znalezione w audycie (docs/BEZPIECZENSTWO.md): spacja w ścieżce, MAC zamaskowany przez Androida, adresy IP,
        // nazwa Wi-Fi Direct, nazwa urządzenia z „|”
        fun c(t: String, names: List<String> = emptyList()) = LinkScrub.clean(t, names)
        val path = c("open failed: /storage/emulated/0/Download/Notatki Kowalskiego/scan.pdf (No such file or directory)")
        assertFalse(path, path.contains("Kowalskiego")); assertTrue(path, path.contains("No such file"))
        assertEquals("łączę z: ‹MAC›", c("łączę z: XX:XX:XX:XX:9C:FF"))
        assertEquals("grupa ‹IP›", c("grupa 192.168.49.1"))
        assertFalse(c("gniazdo fe80::1a2b:3c4d:5e6f%p2p0").contains("1a2b"))
        assertFalse(c("sieć DIRECT-xy-Tablet Ani").contains("xy-Tablet"))
        assertEquals("znaleziono: ‹urządzenie›", c("znaleziono: Tab|Ani", listOf("Tab|Ani")))
        // czasy i liczby zostają
        assertEquals("o 12:30:45 część 2/3", c("o 12:30:45 część 2/3"))
        assertEquals("wersja 14.0.1", c("wersja 14.0.1"))
    }

    @Test
    fun scrubKeepsWhatTheAgentNeeds() {
        for (t in listOf("część 2/3 wysłana: 256 KB", "potwierdzona po 1520 ms", "Nearby: błąd (kod 8007)", "start: send, szukam",
                "Bluetooth/Wi-Fi", "zdjęcie nie doszło po 4 próbach (runda 1)", "stan: Searching", "123456 ms od startu")) {
            assertEquals(t, LinkScrub.clean(t))
        }
    }

    @Test
    fun errorsBecomeClassAndCleanMessage() {
        val e = IOException("read failed, socket might closed or timeout, read ret: -1 (AA:BB:CC:DD:EE:FF)")
        assertEquals("IOException: read failed, socket might closed or timeout, read ret: -1 (‹MAC›)", LinkScrub.error(e))
        assertEquals("SecurityException", LinkScrub.error(SecurityException()))
    }

    @Test
    fun peerTokensAreSaltedAndExportGetsAFreshSalt() {
        val mac = "AA:00:00:00:00:01"
        val j = journal()
        j.begin("Bluetooth", LinkMode.SEND, SyncRole.CLIENT, env)
        val t = j.peer(mac)
        assertTrue(Regex("#[0-9a-f]{6}").matches(t))
        assertEquals("ten sam punkt w jednej sesji: ten sam skrót", t, j.peer(mac))
        assertNotEquals(t, journal().peer(mac))
        j.add("łączę z: $mac ${j.peer(mac)}")
        j.add("znowu ${j.peer(mac)}")
        val a = j.export(); val b = j.export()
        assertFalse(a.contains(mac)); assertFalse(a.contains("AA:00"))
        assertFalse("skrót z dziennika nie wychodzi w eksporcie", a.contains(t))
        val ta = Regex("#[0-9a-f]{6}").findAll(a).map { it.value }.toList()
        val tb = Regex("#[0-9a-f]{6}").findAll(b).map { it.value }.toList()
        assertEquals(2, ta.size)
        assertEquals("w jednym eksporcie spójnie", ta[0], ta[1])
        assertNotEquals("dwa eksporty nie dają się połączyć", ta[0], tb[0])
    }

    @Test
    fun sessionsKeepRelativeTimesAndHeader() {
        val j = journal()
        j.begin("Nearby", LinkMode.RECEIVE, SyncRole.HOST, env, names = listOf("Galaxy Tab S10+"))
        now += 1520
        j.add("połączono: Galaxy Tab S10+")
        val s = j.sessions.single()
        assertEquals(1520L, s.entries.single().ms)
        assertEquals("połączono: ‹urządzenie›", s.entries.single().text)
        assertEquals("Nearby · receive · tablet", s.title)
        val out = j.export()
        assertTrue(out, out.contains("+1520 ms  połączono: ‹urządzenie›"))
        assertTrue(out.contains("model: samsung SM-X820"))
        assertTrue(out.contains("one_ui: 6.1"))
        assertTrue(out.contains("wifi: nie"))
        assertTrue(out.contains("zgoda_BLUETOOTH_SCAN: nie"))
        assertTrue("dopisek, czego nie ma", out.contains("Nie ma: nazw urządzeń"))
    }

    @Test
    fun bufferDropsOldestSessionsAboveTheLimit() {
        val j = journal(max = 20_000)
        repeat(40) { i ->
            j.begin("Nearby", LinkMode.SEND, SyncRole.CLIENT, env)
            repeat(30) { j.add("sesja $i wpis $it: część 1/3 wysłana: 256 KB") }
        }
        assertTrue("rozmiar ${j.encode().length}", j.encode().length <= 22_000)
        assertTrue(j.sessions.size in 2..39)
        assertTrue("najnowsza zostaje", j.sessions.last().entries.last().text.startsWith("sesja 39"))
        // jedna sesja nie rośnie bez końca
        val k = journal()
        k.begin("Nearby", LinkMode.SEND, SyncRole.CLIENT, env)
        repeat(LinkJournal.SESSION_ENTRIES + 50) { k.add("w $it") }
        assertEquals(LinkJournal.SESSION_ENTRIES + 1, k.sessions.single().entries.size)
    }

    @Test
    fun encodeDecodeRoundTripAndSurvivesGarbage() {
        val j = journal()
        j.begin("Bluetooth", LinkMode.PAIR, SyncRole.HOST, env.copy(model = "SM;X=1%"))
        now += 10; j.add("start", 8007)
        now += 5; j.add("zdanie z | kreską")
        val text = j.encode()
        assertFalse(j.dirty)
        val d = LinkJournal.decode("śmieci\nE|x|y\n" + text + "S|zle\nE|1|2", clock = { now })
        assertEquals(1, d.sessions.size)
        val s = d.sessions.single()
        assertEquals(j.sessions.single().header, s.header)
        assertEquals(listOf(10L, 15L), s.entries.map { it.ms })
        assertEquals(8007, s.entries[0].code)
        assertNull(s.entries[1].code)
        assertEquals("zdanie z / kreską", s.entries[1].text)
        assertFalse(d.dirty)
    }

    @Test
    fun oneUiFromSamsungPlatformInt() {
        assertEquals("6.1", LinkEnv.oneUi(150100))
        assertEquals("5.5", LinkEnv.oneUi(140500))
        assertEquals("7.0", LinkEnv.oneUi(160000))
        assertNull(LinkEnv.oneUi(null))
        assertNull(LinkEnv.oneUi(0))
    }

    @Test
    fun machineWritesStagesPartsAndAcksWithoutNamesOrEndpointIds() {
        val air = FakeAir()
        val key = SyncCrypto.hkdf(ByteArray(32) { 1 }, ByteArray(0), "para", 32)
        val tj = journal(); val pj = journal()
        tj.begin("Nearby", LinkMode.RECEIVE, SyncRole.HOST, env); pj.begin("Nearby", LinkMode.SEND, SyncRole.CLIENT, env)
        val te = air.end("ENDT"); val pe = air.end("ENDP")
        var got = 0
        val tablet = LinkMachine(te, LinkMode.RECEIVE, SyncRole.HOST, "Galaxy Tab S10+", key, "Pixel 8", { now },
            object : LinkMachine.Listener { override fun photo(p: SyncMsg.Photo) { got++ } }, journal = tj).also { te.machine = it }
        val phone = LinkMachine(pe, LinkMode.SEND, SyncRole.CLIENT, "Pixel 8", key, "Galaxy Tab S10+", { now }, journal = pj).also { pe.machine = it }
        tablet.start(); phone.start()
        phone.sendPhoto("IMG_1.jpg", "image/jpeg", ByteArray(600_000).also { Random(1).nextBytes(it) })
        var i = 0
        air.pump()
        while (got == 0 && i++ < 200) { now += 250; tablet.tick(); phone.tick(); air.pump() }
        assertEquals(1, got)
        val p = pj.export(); val t = tj.export()
        assertTrue(p, p.contains("część 1/3 wysłana: 256 KB"))
        assertTrue(p, p.contains("część 3/3 potwierdzona"))
        assertTrue(p, p.contains("stan: Sending"))
        assertTrue(t, t.contains("odebrano część 2/3: 256 KB"))
        assertTrue(t, t.contains("odebrano plik: 585 KB"))
        for (x in listOf(p, t)) for (bad in listOf("Galaxy", "Pixel", "ENDT", "ENDP", "IMG_1", SyncCrypto.nearbyName(key))) assertFalse("$bad w:\n$x", x.contains(bad))
    }
}
