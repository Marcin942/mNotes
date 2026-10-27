package com.mdev.mnotes.lab.nearby

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.mdev.mnotes.core.nearby.ChunkJoiner
import com.mdev.mnotes.core.nearby.LinkChunks
import com.mdev.mnotes.core.nearby.Pairing
import com.mdev.mnotes.core.nearby.PairingSession
import com.mdev.mnotes.core.nearby.SyncCodec
import com.mdev.mnotes.core.nearby.SyncCrypto
import com.mdev.mnotes.core.nearby.SyncMsg
import com.mdev.mnotes.core.nearby.SyncRole

/** Zdarzenia łącza; przychodzą na głównym wątku. */
interface SyncListener {
    /** Parowanie: policzono klucz pary, [sas] trzeba porównać z drugim ekranem. */
    fun onPairCode(sas: String, peerName: String, pairKey: ByteArray) {}
    fun onUp() {}
    fun onDown() {}
    fun onMessage(msg: SyncMsg) {}
    /** Nearby nie wystartowało ([code] z ConnectionsStatusCodes); [permission] = brak uprawnienia albo radia. */
    fun onError(code: Int, permission: Boolean) {}
}

/**
 * Łącze telefon ↔ tablet przez Nearby Connections (Bluetooth albo Wi-Fi Direct, bez wspólnej sieci), wzorowane na
 * klawiaturze mChochlika (`docs/nearby/`). Tablet ([SyncRole.HOST]) się ogłasza, telefon szuka. Dwa tryby:
 * parowanie (stała nazwa, [PairingSession]: zobowiązania, potem oferty z kluczami, potem sześć cyfr na obu ekranach)
 * i praca po parowaniu (nazwa z klucza pary, każda ramka szyfrowana [SyncCodec]). Wszystko na głównym wątku.
 */
class NearbyLink(context: Context, private val listener: SyncListener) {
    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private var session: Session? = null

    /** Parowanie: [deviceName] pokaże się na drugim urządzeniu. */
    fun pair(role: SyncRole, deviceName: String) = restart(Session(role, null, deviceName))

    /** Praca z zapisanym kluczem pary. */
    fun connect(role: SyncRole, pairKey: ByteArray) = restart(Session(role, pairKey, ""))

    fun stop() { session?.close(); session = null }

    fun send(msg: SyncMsg): Boolean = session?.send(msg) ?: false

    private fun restart(s: Session) { stop(); session = s; s.search() }

    private inner class Session(val role: SyncRole, val pairKey: ByteArray?, val deviceName: String) {
        private val name = pairKey?.let { SyncCrypto.nearbyName(it) } ?: Pairing.PAIRING_NAME
        private var pairing = if (pairKey == null) PairingSession(deviceName) else null
        private var searching = false
        private var endpoint: String? = null
        private var connected = false
        private var codec: SyncCodec? = null
        private var joiner = ChunkJoiner()
        private var up = false
        private val alive get() = session === this

        private val payloads = object : PayloadCallback() {
            override fun onPayloadReceived(id: String, payload: Payload) {
                if (!alive || id != endpoint || payload.type != Payload.Type.BYTES) return
                val part = payload.asBytes() ?: return
                try {
                    val raw = joiner.feed(part) ?: return
                    if (pairKey == null) return pairMessage(raw)
                    val c = codec ?: return
                    if (!c.ready) {
                        c.acceptHello(raw)
                        if (connected) linkUp()
                    } else {
                        c.open(raw)?.let(listener::onMessage)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "zła ramka od $id: ${e.message}")
                    client.disconnectFromEndpoint(id)
                    lost(id)
                }
            }

            override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) = Unit
        }

        private val lifecycle = object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                if (!alive || info.endpointName != name || (endpoint != null && endpoint != id)) { client.rejectConnection(id); return }
                endpoint = id
                joiner = ChunkJoiner()
                if (pairKey != null) codec = SyncCodec(pairKey, role) else pairing = PairingSession(deviceName)
                client.acceptConnection(id, payloads).addOnFailureListener { if (alive && endpoint == id) failed(id) }
            }

            override fun onConnectionResult(id: String, resolution: ConnectionResolution) {
                if (!alive || id != endpoint) return
                if (!resolution.status.isSuccess) return failed(id)
                connected = true
                stopSearch()
                if (pairKey == null) { pairing?.let { sendRaw(it.start()) }; return }
                val c = codec ?: SyncCodec(pairKey!!, role).also { codec = it }
                sendRaw(c.hello())
                if (c.ready) linkUp()
            }

            override fun onDisconnected(id: String) { if (alive && id == endpoint) lost(id) }
        }

        private val discovery = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                if (!alive || info.endpointName != name || endpoint != null) return
                endpoint = id
                client.requestConnection(name, id, lifecycle).addOnFailureListener { e ->
                    if (!alive || endpoint != id) return@addOnFailureListener
                    if ((e as? ApiException)?.statusCode == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) return@addOnFailureListener
                    failed(id)
                }
            }

            override fun onEndpointLost(id: String) = Unit
        }

        /**
         * Wiadomość parowania: zobowiązanie drugiej strony (odpowiadamy ofertą) albo jej oferta (sprawdzona względem
         * zobowiązania; niezgodna rzuca i zrywa połączenie). Cyfry porównuje potem użytkownik.
         */
        private fun pairMessage(raw: ByteArray) {
            val p = pairing ?: return
            val step = p.receive(raw)
            for (m in step.send) sendRaw(m)
            step.result?.let { listener.onPairCode(it.sas, it.peerName, it.pairKey) }
        }

        fun search() {
            if (!alive || searching || connected) return
            searching = true
            val task = if (role == SyncRole.HOST) client.startAdvertising(name, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
            else client.startDiscovery(SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
            task.addOnFailureListener { e ->
                val code = (e as? ApiException)?.statusCode ?: ConnectionsStatusCodes.STATUS_ERROR
                if (code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING || code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING || !alive) return@addOnFailureListener
                searching = false
                listener.onError(code, isPermissionProblem(code))
                if (!isPermissionProblem(code)) retrySearch()
            }
        }

        fun send(msg: SyncMsg): Boolean {
            val c = codec ?: return false
            if (!up) return false
            return runCatching { sendRaw(c.seal(msg)) }.isSuccess
        }

        fun close() {
            handler.removeCallbacksAndMessages(this)
            stopSearch()
            endpoint?.let { client.disconnectFromEndpoint(it) }
            client.stopAllEndpoints()
            endpoint = null; connected = false; up = false; codec = null
        }

        private fun linkUp() { if (!up) { up = true; listener.onUp() } }

        private fun sendRaw(data: ByteArray) {
            val id = endpoint ?: return
            for (part in LinkChunks.split(data)) client.sendPayload(id, Payload.fromBytes(part))
        }

        private fun failed(id: String) {
            if (endpoint != id) return
            endpoint = null; connected = false; codec = null
            if (role == SyncRole.CLIENT) { stopSearch(); retrySearch() } else search()
        }

        private fun lost(id: String) {
            if (endpoint != id) return
            val wasUp = up
            endpoint = null; connected = false; up = false; codec = null
            search()
            if (wasUp) listener.onDown()
        }

        private fun stopSearch() {
            if (!searching) return
            searching = false
            if (role == SyncRole.HOST) client.stopAdvertising() else client.stopDiscovery()
        }

        private fun retrySearch() {
            handler.removeCallbacksAndMessages(this)
            handler.postDelayed({ search() }, this, RETRY_MS)
        }
    }

    companion object {
        const val SERVICE_ID = "com.mdev.mnotes.sync"
        private val STRATEGY = Strategy.P2P_POINT_TO_POINT
        private const val RETRY_MS = 2_000L
        private const val TAG = "MnotesNearby"

        fun isPermissionProblem(code: Int?): Boolean = code in setOf(
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADMIN,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_SCAN,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADVERTISE,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_CONNECT,
            ConnectionsStatusCodes.MISSING_PERMISSION_NEARBY_WIFI_DEVICES,
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_FINE_LOCATION,
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_COARSE_LOCATION,
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_WIFI_STATE,
            ConnectionsStatusCodes.MISSING_PERMISSION_CHANGE_WIFI_STATE,
        )

        /**
         * Czy wynik prośby o uprawnienia pozwala próbować: wszystko poza lokalizacją przyznane, a z lokalizacji wystarczy
         * przybliżona (Android 12+ daje wybór; odmowa dokładnej to nie odmowa łącza).
         */
        fun granted(result: Map<String, Boolean>): Boolean = com.mdev.mnotes.core.nearby.LinkPermissions.granted(result)

        /** Uprawnienia do poproszenia przed startem (zależnie od wersji Androida). */
        fun permissions(sdk: Int): Array<String> = buildList {
            if (sdk >= 31) { add("android.permission.BLUETOOTH_ADVERTISE"); add("android.permission.BLUETOOTH_CONNECT"); add("android.permission.BLUETOOTH_SCAN") }
            if (sdk >= 33) add("android.permission.NEARBY_WIFI_DEVICES")
            add("android.permission.ACCESS_FINE_LOCATION"); add("android.permission.ACCESS_COARSE_LOCATION")
        }.toTypedArray()
    }
}
