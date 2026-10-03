package com.dexter.wear

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors

/**
 * The watch's end of the link to the phone: a Bluetooth RFCOMM connection to the paired phone, which
 * listens while its Watch setting is on. No Google services involved. Messages are single lines of
 * path, tab, payload. Keep the id and line format in sync with the phone's `WatchLink`.
 */
object PhoneLink {
    private val SERVICE_UUID: UUID = UUID.fromString("6f3c1a52-8d0e-4c8b-9a51-2f7d1e9b4c10")

    /** One thread for connecting, sending, and reading in order. */
    private val worker = Executors.newSingleThreadExecutor()

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var output: OutputStream? = null

    /** Bluetooth access. Watches on Android 11 have it from the manifest; Android 12 and later ask once. */
    fun hasPermission(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Sends one message to the phone, connecting first when needed. Failures leave [WatchState] as it was. */
    fun send(context: Context, path: String) {
        val app = context.applicationContext
        worker.execute {
            // A link the phone dropped only fails on the next write, so a failed send reconnects and
            // tries once more instead of losing the press.
            repeat(2) {
                val sent = runCatching {
                    val out = output ?: connect(app) ?: return@execute
                    out.write("$path\t\n".toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                if (sent.isSuccess) return@execute
                disconnect()
            }
        }
    }

    /** Tries each paired device, phones first, until one answers on the Dexter service. */
    @Suppress("MissingPermission")
    private fun connect(context: Context): OutputStream? {
        if (!hasPermission(context)) return null
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        val candidates = adapter.bondedDevices.sortedByDescending { it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PHONE }
        for (device in candidates) {
            val candidate = runCatching { device.createRfcommSocketToServiceRecord(SERVICE_UUID) }.getOrNull() ?: continue
            // A device that does not answer must have its socket closed, or each send leaks one per paired device.
            val attempt = runCatching { candidate.also { it.connect() } }.getOrElse {
                runCatching { candidate.close() }
                null
            } ?: continue
            socket = attempt
            output = attempt.outputStream
            Thread({ read(attempt) }, "phone-link").apply { isDaemon = true }.start()
            return output
        }
        return null
    }

    private fun read(connection: BluetoothSocket) {
        runCatching {
            connection.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val tab = line.indexOf('\t')
                    if (tab > 0 && line.substring(0, tab) == WearMainActivity.PROGRESS_REPLY) {
                        WatchState.onProgressReply(line.substring(tab + 1).toByteArray(Charsets.UTF_8))
                    }
                }
            }
        }
        if (socket === connection) disconnect()
    }

    private fun disconnect() {
        runCatching { socket?.close() }
        socket = null
        output = null
    }
}
