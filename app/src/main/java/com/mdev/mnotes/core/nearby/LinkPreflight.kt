package com.mdev.mnotes.core.nearby

/**
 * Sprawdzenie radia przed łączeniem i w trakcie (3b-B3): jedno zdanie, co włączyć, zanim łącze zacznie się kręcić
 * w kółko. [blocking]: bez tego nic nie zadziała (Bluetooth wyłączony); pozostałe to ostrzeżenia (Nearby bez Wi-Fi
 * działa, ale zdjęcie idzie samym Bluetooth; bez lokalizacji szukanie Nearby na wielu telefonach nic nie znajduje).
 */
enum class LinkIssue(val blocking: Boolean, val pl: String, val en: String) {
    BT_OFF(true, "Bluetooth jest wyłączony. Włącz go na obu urządzeniach.", "Bluetooth is off. Turn it on on both devices."),
    LOCATION_OFF(false, "Lokalizacja jest wyłączona: Nearby może nie znaleźć drugiego urządzenia. Włącz ją w szybkich ustawieniach.", "Location is off: Nearby may not find the other device. Turn it on in quick settings."),
    WIFI_OFF(false, "Wi-Fi jest wyłączone: zdjęcia pójdą wolno, samym Bluetooth. Włącz Wi-Fi (sieć nie jest potrzebna).", "Wi-Fi is off: photos will go slowly over Bluetooth only. Turn Wi-Fi on (no network needed)."),
}

object LinkPreflight {
    /** [bluetooth]: transport RFCOMM (bez Wi-Fi i lokalizacji); null w stanie = nie wiadomo, wtedy bez ostrzeżenia. */
    fun check(bluetooth: Boolean, btOn: Boolean?, locationOn: Boolean?, wifiOn: Boolean?): List<LinkIssue> = buildList {
        if (btOn == false) add(LinkIssue.BT_OFF)
        if (!bluetooth) {
            if (locationOn == false) add(LinkIssue.LOCATION_OFF)
            if (wifiOn == false) add(LinkIssue.WIFI_OFF)
        }
    }
}
