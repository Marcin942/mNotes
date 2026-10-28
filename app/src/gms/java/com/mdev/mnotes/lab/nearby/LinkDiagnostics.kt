package com.mdev.mnotes.lab.nearby

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.mdev.mnotes.core.nearby.LinkEvent
import com.mdev.mnotes.core.nearby.LinkPermissions
import com.mdev.mnotes.editor.T
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Jeden wiersz „Diagnostyki”: co sprawdzono i czy jest dobrze (null = nie da się sprawdzić). */
class Check(val label: String, val ok: Boolean?)

/**
 * „Diagnostyka” łącza (`docs/NEARBY-PRZEBUDOWA.md` §3.6): uprawnienia, Bluetooth, Wi-Fi, usługa lokalizacji, Usługi Google
 * Play i wersja Androida, plus ostatnie zdarzenia maszyny. Każde sprawdzenie w `runCatching`: diagnostyka nie może się wywrócić.
 */
object LinkDiagnostics {
    fun has(ctx: Context, permission: String) = ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    fun locationOn(ctx: Context): Boolean? = runCatching {
        LocationManagerCompat.isLocationEnabled(ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
    }.getOrNull()

    private fun bluetooth(ctx: Context) = com.mdev.mnotes.lab.LinkSettings.of(ctx).bluetooth

    /** Bluetooth RFCOMM (2e-B1): zgody tego transportu, Bluetooth i zapamiętany adres; bez wierszy o Wi-Fi i Usługach Google. */
    fun checks(ctx: Context): List<Check> {
        val out = ArrayList<Check>()
        val bt = bluetooth(ctx)
        for (p in if (bt) LinkPermissions.rfcomm(Build.VERSION.SDK_INT) else LinkPermissions.required(Build.VERSION.SDK_INT)) {
            if (p == LinkPermissions.COARSE) continue
            out += Check(permissionName(p), has(ctx, p))
        }
        out += Check(T.t("Bluetooth włączony", "Bluetooth on"), runCatching { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled }.getOrNull())
        if (bt) {
            val store = PairStore(ctx)
            out += Check(T.t("Adres drugiego urządzenia zapamiętany", "Other device's address remembered"), if (store.paired) store.peerAddress.isNotEmpty() else null)
            return out
        }
        out += Check(T.t("Wi-Fi włączone (szybsze przesyłanie)", "Wi-Fi on (faster transfer)"), runCatching { (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled }.getOrNull())
        out += Check(T.t("Lokalizacja (usługa) włączona", "Location (service) on"), locationOn(ctx))
        out += Check(T.t("Usługi Google Play", "Google Play services"), runCatching { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) == ConnectionResult.SUCCESS }.getOrNull())
        return out
    }

    fun permissionName(p: String): String = when {
        p.endsWith("BLUETOOTH_SCAN") || p.endsWith("BLUETOOTH_ADVERTISE") || p.endsWith("BLUETOOTH_CONNECT") ->
            T.t("Urządzenia w pobliżu (${p.substringAfterLast('.')})", "Nearby devices (${p.substringAfterLast('.')})")
        p.endsWith("NEARBY_WIFI_DEVICES") -> T.t("Urządzenia Wi-Fi w pobliżu", "Nearby Wi-Fi devices")
        p == LinkPermissions.FINE -> T.t("Lokalizacja dokładna", "Precise location")
        else -> p.substringAfterLast('.')
    }

    /** Zdanie „dlaczego” do ekranu przed prośbą systemową (jedno na uprawnienie). */
    fun why(p: String): String = when {
        p.endsWith("NEARBY_WIFI_DEVICES") -> T.t("Wi-Fi w pobliżu: zdjęcie leci szybciej niż przez sam Bluetooth.", "Nearby Wi-Fi: the photo travels faster than over Bluetooth alone.")
        p.contains("BLUETOOTH") -> T.t("Urządzenia w pobliżu: żeby tablet i telefon się znalazły.", "Nearby devices: so the tablet and the phone can find each other.")
        p == LinkPermissions.FINE -> T.t("Lokalizacja dokładna: Android wymaga jej do szukania przez Bluetooth. mNotes nie odczytuje położenia.", "Precise location: Android requires it to search over Bluetooth. mNotes never reads your location.")
        else -> p.substringAfterLast('.')
    }

    /** Raport do skopiowania (bez kluczy i treści zdjęć). */
    fun report(ctx: Context, log: List<LinkEvent>): String = buildString {
        appendLine("mNotes lab — ${if (bluetooth(ctx)) "Bluetooth" else "Nearby"} — Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        for (c in checks(ctx)) appendLine("${mark(c.ok)} ${c.label}")
        if (log.isNotEmpty()) appendLine(T.t("Ostatnie zdarzenia:", "Last events:"))
        for (e in log) appendLine(event(e))
    }

    fun mark(ok: Boolean?) = when (ok) { true -> "✓"; false -> "✗"; null -> "?" }

    fun event(e: LinkEvent): String = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(e.at)) + "  " + e.text + (e.code?.let { "  (kod $it)" } ?: "")
}
