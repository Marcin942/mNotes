package com.mdev.mnotes.lab.nearby

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.BandwidthInfo
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.mdev.mnotes.core.nearby.LinkErrors
import com.mdev.mnotes.core.nearby.LinkMachine
import com.mdev.mnotes.core.nearby.LinkScrub
import com.mdev.mnotes.core.nearby.LinkTransport

/**
 * Nearby Connections jako [LinkTransport] (2b-B1): wywołania `ConnectionsClient` 1:1 i zdarzenia z powrotem do
 * [machine], bez żadnej logiki (ta jest w `core/nearby/LinkMachine`, z testami). Wywołania i zwrotne na głównym wątku.
 * Ta sama strategia i `serviceId` po obu stronach; błędy startu idą jako kody `ConnectionsStatusCodes` do maszyny.
 */
class NearbyTransport(context: Context) : LinkTransport {
    lateinit var machine: LinkMachine
    private val client = Nearby.getConnectionsClient(context.applicationContext)

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) payload.asBytes()?.let { machine.onReceived(id, it) }
        }

        /** Do dziennika (3b-B1) tylko porażki: ładunek, który Nearby porzuciło, to „zdjęcie nie dochodzi”. */
        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.FAILURE -> machine.note("Nearby: ładunek nieudany (${update.bytesTransferred / 1024}/${update.totalBytes / 1024} KB)")
                PayloadTransferUpdate.Status.CANCELED -> machine.note("Nearby: ładunek anulowany (${update.bytesTransferred / 1024}/${update.totalBytes / 1024} KB)")
            }
        }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) = machine.onInitiated(id, info.endpointName)

        override fun onConnectionResult(id: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) machine.onConnected(id) else machine.onConnectFailed(id, resolution.status.statusCode)
        }

        override fun onDisconnected(id: String) = machine.onDisconnected(id)

        /** Medium po przejściu z Bluetooth na Wi-Fi (albo z powrotem): LOW to sam Bluetooth, czyli wolne zdjęcia. */
        override fun onBandwidthChanged(id: String, info: BandwidthInfo) {
            val q = when (info.quality) { BandwidthInfo.Quality.LOW -> "niskie (Bluetooth)"; BandwidthInfo.Quality.MEDIUM -> "średnie"; BandwidthInfo.Quality.HIGH -> "wysokie (Wi-Fi)"; else -> "nieznane" }
            machine.note("Nearby: pasmo $q")
        }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) = machine.onFound(id, info.endpointName)
        override fun onEndpointLost(id: String) = machine.onLost(id)
    }

    private fun code(e: Exception) = (e as? ApiException)?.statusCode ?: LinkErrors.STATUS_ERROR

    private fun failed(what: String, e: Exception) { machine.note("Nearby: $what nieudane: ${LinkScrub.error(e)}") }

    override fun advertise(name: String) {
        client.startAdvertising(name, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { failed("ogłaszanie", it); machine.onFailed(code(it)) }
    }

    override fun discover(name: String) {
        client.startDiscovery(SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { failed("szukanie", it); machine.onFailed(code(it)) }
    }

    override fun stopSearch() {
        client.stopAdvertising()
        client.stopDiscovery()
    }

    override fun request(endpoint: String, name: String) {
        client.requestConnection(name, endpoint, lifecycle).addOnFailureListener {
            val c = code(it)
            failed("łączenie", it)
            if (c != LinkErrors.ALREADY_CONNECTED) machine.onConnectFailed(endpoint, c)
        }
    }

    override fun accept(endpoint: String) {
        client.acceptConnection(endpoint, payloads).addOnFailureListener { failed("akceptacja", it); machine.onConnectFailed(endpoint, code(it)) }
    }

    override fun reject(endpoint: String) { client.rejectConnection(endpoint) }

    override fun send(endpoint: String, bytes: ByteArray) { client.sendPayload(endpoint, Payload.fromBytes(bytes)) }

    override fun disconnect(endpoint: String) { client.disconnectFromEndpoint(endpoint) }

    /** Koniec okna: zamyka wszystko, co Nearby mogło jeszcze trzymać (inaczej następny start dostaje 8008). */
    fun close() { client.stopAllEndpoints() }

    companion object {
        const val SERVICE_ID = "com.mdev.mnotes.sync"
        private val STRATEGY = Strategy.P2P_POINT_TO_POINT
    }
}
