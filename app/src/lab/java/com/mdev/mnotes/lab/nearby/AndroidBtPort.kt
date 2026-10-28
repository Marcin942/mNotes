package com.mdev.mnotes.lab.nearby

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.mdev.mnotes.core.nearby.BtDevice
import com.mdev.mnotes.core.nearby.BtPipe
import com.mdev.mnotes.core.nearby.BtPort
import com.mdev.mnotes.core.nearby.BtServer
import java.io.IOException
import java.util.UUID

/**
 * [BtPort] na `BluetoothAdapter` (2e-B1): gniazda „insecure” bez systemowego bondingu (szyfruje i uwierzytelnia
 * `SyncCodec` z kluczem pary), lista sparowanych w systemie, wyszukiwanie przez `ACTION_FOUND`. Zgody sprawdza okno
 * łącza przed startem; brak zgody to [SecurityException], który [com.mdev.mnotes.core.nearby.RfcommTransport] zamienia
 * na kod łącza. Logika łącza i wątki są w `core` (testy JVM), tu tylko wywołania systemu.
 */
@SuppressLint("MissingPermission")
class AndroidBtPort(context: Context) : BtPort {
    private val ctx = context.applicationContext
    private val adapter: BluetoothAdapter? = ctx.getSystemService(BluetoothManager::class.java)?.adapter
    private var receiver: BroadcastReceiver? = null

    private fun need(): BluetoothAdapter = adapter ?: throw IOException("brak Bluetooth")

    override fun available() = adapter != null

    override fun enabled() = adapter?.isEnabled == true

    override fun bonded(): List<BtDevice> = adapter?.bondedDevices.orEmpty().map(::dev)

    override fun listen(uuid: UUID, name: String): BtServer {
        val s = need().listenUsingInsecureRfcommWithServiceRecord(name, uuid)
        return object : BtServer {
            override fun accept(): BtPipe = pipe(s.accept())
            override fun close() = s.close()
        }
    }

    override fun connect(address: String, uuid: UUID): BtPipe {
        val a = need()
        val sock = a.getRemoteDevice(address).createInsecureRfcommSocketToServiceRecord(uuid)
        try { sock.connect() } catch (e: IOException) { runCatching { sock.close() }; throw e }
        return pipe(sock)
    }

    override fun startDiscovery(found: (BtDevice) -> Unit): Boolean {
        val a = adapter ?: return false
        cancelDiscovery()
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val d = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                d?.let { found(dev(it)) }
            }
        }
        ContextCompat.registerReceiver(ctx, r, IntentFilter(BluetoothDevice.ACTION_FOUND), ContextCompat.RECEIVER_EXPORTED)
        receiver = r
        return a.startDiscovery()
    }

    override fun cancelDiscovery() {
        receiver?.let { runCatching { ctx.unregisterReceiver(it) }; receiver = null }
        runCatching { adapter?.cancelDiscovery() }
    }

    private fun dev(d: BluetoothDevice) = BtDevice(d.address, runCatching { d.name }.getOrNull().orEmpty())

    private fun pipe(s: BluetoothSocket): BtPipe = object : BtPipe {
        override val peer = dev(s.remoteDevice)
        override val input = s.inputStream
        override val output = s.outputStream
        override fun close() = s.close()
    }
}
