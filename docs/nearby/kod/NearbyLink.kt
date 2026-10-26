package com.mdev.mchochlik.feature.keyboard

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Zdarzenia łącza dla silnika; przychodzą na głównym wątku. */
interface KbdLinkListener {
    fun onLinkUp()
    fun onLinkDown()
    fun onLinkMessage(msg: LocalMsg)
}

/**
 * Łącze lokalne przez Nearby Connections (D-033): Bluetooth albo Wi‑Fi Direct, bez wspólnej
 * sieci. Ekran się ogłasza, klawiatura szuka; obie strony używają tej samej nazwy z klucza
 * pary, więc obce urządzenia odpadają już przy odkrywaniu. Nearby nie uwierzytelnia, dlatego
 * pierwszy ładunek to `hello` z [LocalCodec] — dopiero on podnosi łącze.
 *
 * Wywołania i wywołania zwrotne Nearby idą głównym wątkiem, więc stan nie potrzebuje blokad.
 */
class NearbyLink(context: Context, private val listener: KbdLinkListener) : KbdLink {
    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())

    /** Ostatni błąd uruchomienia Nearby (kod [ConnectionsStatusCodes]); `null`, gdy działa. */
    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error.asStateFlow()

    private var session: Session? = null

    override fun start(pairing: Pairing, role: KbdRole) {
        val s = session
        if (s != null && s.pairing.id == pairing.id && s.role == role) return
        stop()
        // Najpierw przypisanie: `search()` sprawdza `alive`, czyli `session === this`.
        val fresh = Session(pairing, role)
        session = fresh
        fresh.search()
    }

    override fun stop() {
        val s = session ?: return
        session = null
        s.close()
    }

    override fun send(msg: LocalMsg): Boolean = session?.send(msg) ?: false

    private inner class Session(val pairing: Pairing, val role: KbdRole) {
        private val name = KeyboardCrypto.nearbyName(pairing.key)
        private var searching = false
        /** Punkt, z którym się łączymy albo jesteśmy połączeni. */
        private var endpoint: String? = null
        private var connected = false
        private var codec: LocalCodec? = null
        private var joiner = ChunkJoiner()
        private var up = false
        private val alive get() = session === this

        private val payloads = object : PayloadCallback() {
            override fun onPayloadReceived(id: String, payload: Payload) {
                if (!alive || id != endpoint || payload.type != Payload.Type.BYTES) {
                    Log.d(TAG, "ładunek od $id pominięty (punkt=$endpoint, typ=${payload.type})")
                    return
                }
                val part = payload.asBytes() ?: return
                try {
                    val raw = joiner.feed(part) ?: return
                    val c = codec ?: return
                    if (!c.ready) {
                        c.acceptHello(raw)
                        Log.i(TAG, "hello od $id przyjęte")
                        if (connected) linkUp()
                    } else {
                        val msg = c.open(raw)
                        if (msg == null) Log.w(TAG, "ramka od $id pominięta (nieznana albo za duża)")
                        else listener.onLinkMessage(msg)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "zła ramka od $id: ${e.message}")
                    // Zły klucz, powtórka albo uszkodzona ramka: to połączenie jest bezużyteczne.
                    client.disconnectFromEndpoint(id)
                    lost(id)
                }
            }

            override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) = Unit
        }

        private val lifecycle = object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                val busy = endpoint != null && endpoint != id
                Log.i(TAG, "połączenie z $id zainicjowane (${info.endpointName}, nasz=${info.endpointName == name}, zajęty=$busy)")
                if (!alive || info.endpointName != name || busy) {
                    client.rejectConnection(id)
                    return
                }
                endpoint = id
                // Hello partnera bywa szybsze niż nasze `onConnectionResult` — kodek musi już czekać.
                joiner = ChunkJoiner()
                codec = LocalCodec(pairing.key, role)
                client.acceptConnection(id, payloads).addOnFailureListener { if (alive && endpoint == id) failed(id) }
            }

            override fun onConnectionResult(id: String, resolution: ConnectionResolution) {
                if (!alive || id != endpoint) return
                Log.i(TAG, "wynik połączenia z $id: ${resolution.status.statusCode}")
                if (!resolution.status.isSuccess) return failed(id)
                connected = true
                stopSearch()
                val c = codec ?: LocalCodec(pairing.key, role).also { codec = it }
                sendRaw(c.hello())
                // Hello partnera przyszło wcześniej; teraz, po naszym, można już wysyłać ramki.
                if (c.ready) linkUp()
            }

            override fun onDisconnected(id: String) {
                Log.i(TAG, "rozłączono $id")
                if (alive && id == endpoint) lost(id)
            }
        }

        private val discovery = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                Log.i(TAG, "znaleziono $id (${info.endpointName}, nasz=${info.endpointName == name}, punkt=$endpoint)")
                if (!alive || info.endpointName != name || endpoint != null) return
                endpoint = id
                client.requestConnection(name, id, lifecycle).addOnFailureListener { e ->
                    Log.w(TAG, "prośba o połączenie z $id nieudana: ${(e as? ApiException)?.statusCode} ${e.message}")
                    if (!alive || endpoint != id) return@addOnFailureListener
                    if ((e as? ApiException)?.statusCode == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) return@addOnFailureListener
                    failed(id)
                }
            }

            override fun onEndpointLost(id: String) = Unit
        }

        fun search() {
            if (!alive || searching || connected) return
            searching = true
            Log.i(TAG, "start ${if (role == KbdRole.SCREEN) "ogłaszania" else "szukania"} jako $name")
            val task = if (role == KbdRole.SCREEN) {
                client.startAdvertising(name, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
            } else {
                client.startDiscovery(SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
            }
            task.addOnSuccessListener {
                Log.i(TAG, "${role.name}: Nearby wystartowało")
                if (alive) _error.value = null
            }
            task.addOnFailureListener { e ->
                val code = (e as? ApiException)?.statusCode
                Log.w(TAG, "${role.name}: Nearby nie wystartowało: $code ${e.message}")
                if (code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING || code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) return@addOnFailureListener
                if (!alive) return@addOnFailureListener
                searching = false
                _error.value = code ?: ConnectionsStatusCodes.STATUS_ERROR
                // Radio bywa chwilowo zajęte (np. po wyłączeniu Bluetooth); próbujemy dalej, póki tryb trwa.
                retrySearch()
            }
        }

        fun send(msg: LocalMsg): Boolean {
            val c = codec ?: return false
            if (!up) return false
            return try {
                sendRaw(c.seal(msg))
                true
            } catch (_: Exception) {
                false
            }
        }

        fun close() {
            Log.i(TAG, "koniec sesji łącza (punkt=$endpoint, gotowe=$up)")
            handler.removeCallbacksAndMessages(this)
            stopSearch()
            endpoint?.let { client.disconnectFromEndpoint(it) }
            client.stopAllEndpoints()
            endpoint = null
            connected = false
            up = false
            codec = null
        }

        private fun linkUp() {
            if (up) return
            Log.i(TAG, "łącze gotowe ($endpoint)")
            up = true
            _error.value = null
            listener.onLinkUp()
        }

        private fun sendRaw(data: ByteArray) {
            val id = endpoint ?: return
            for (part in LinkChunks.split(data)) client.sendPayload(id, Payload.fromBytes(part))
        }

        /** Nieudane łączenie: wracamy do szukania, a klawiatura zaczyna odkrywanie od nowa. */
        private fun failed(id: String) {
            if (endpoint != id) return
            endpoint = null
            connected = false
            codec = null
            if (role == KbdRole.KEYS) {
                stopSearch()
                retrySearch()
            } else {
                search()
            }
        }

        /** Zerwane połączenie (także lokalnie — Nearby wtedy nie woła `onDisconnected`). */
        private fun lost(id: String) {
            if (endpoint != id) return
            val wasUp = up
            endpoint = null
            connected = false
            up = false
            codec = null
            search()
            if (wasUp) listener.onLinkDown()
        }

        private fun stopSearch() {
            if (!searching) return
            searching = false
            if (role == KbdRole.SCREEN) client.stopAdvertising() else client.stopDiscovery()
        }

        private fun retrySearch() {
            handler.removeCallbacksAndMessages(this)
            handler.postDelayed({ search() }, this, RETRY_MS)
        }
    }

    companion object {
        const val SERVICE_ID = "com.mdev.mchochlik.kbd"
        private val STRATEGY = Strategy.P2P_POINT_TO_POINT
        private const val RETRY_MS = 2_000L
        private const val TAG = "ChochlikKbd"

        /** Kody, przy których brakuje uprawnienia albo włączonego radia — UI pokazuje je osobno. */
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
    }
}
