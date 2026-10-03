package com.dexter.platform

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArraySet

private const val TAG = "DexterWatch"

/**
 * The phone's end of the link to the Dexter watch app: a Bluetooth RFCOMM service the paired watch
 * connects to directly, with no Google services in between. Each message is one line, see
 * [watchLine]. The watch sends page turns and progress requests, and the phone answers with
 * [WearPaths.PROGRESS_REPLY]. Runs while the setting is on and Bluetooth permission is granted.
 */
object WatchLink {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var server: BluetoothServerSocket? = null
    private var job: Job? = null
    private val clients = CopyOnWriteArraySet<OutputStream>()

    /** Answers a progress request with the payload for what is being read, or "" for nothing. */
    @Volatile var progress: suspend () -> String = { "" }

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Restarts listening when Bluetooth comes back on, since the listener ends when Bluetooth turns off. */
    private var bluetoothWatcher: BroadcastReceiver? = null

    /** Starts listening for the watch. Does nothing without Bluetooth permission or when already listening. */
    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext
        if (bluetoothWatcher == null) {
            val watcher = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) start(app)
                }
            }
            // Not exported: the Bluetooth state broadcast comes from the system, which still reaches it.
            ContextCompat.registerReceiver(app, watcher, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            bluetoothWatcher = watcher
        }
        if (job?.isActive == true || !hasPermission(app)) return
        val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        job = scope.launch {
            var socket: BluetoothServerSocket? = null
            try {
                @Suppress("MissingPermission")
                val listening = adapter.listenUsingRfcommWithServiceRecord("Dexter", WATCH_LINK_UUID)
                socket = listening
                server = listening
                while (isActive) {
                    val client = listening.accept()
                    launch { serve(client) }
                }
            } catch (e: Exception) {
                // Bluetooth off, or the server closed by stop(). Turning the setting on again restarts it.
                Log.i(TAG, "Watch link stopped: ${e.message}")
            } finally {
                // Release the RFCOMM channel when Bluetooth turns off, so the next start can claim it.
                runCatching { socket?.close() }
            }
        }
    }

    /** Stops listening and drops every connected watch. */
    @Synchronized
    fun stop(context: Context? = null) {
        bluetoothWatcher?.let { watcher -> context?.applicationContext?.let { runCatching { it.unregisterReceiver(watcher) } } }
        if (context != null) bluetoothWatcher = null
        runCatching { server?.close() }
        server = null
        job?.cancel()
        job = null
        clients.forEach { runCatching { it.close() } }
        clients.clear()
    }

    private suspend fun serve(socket: BluetoothSocket) {
        val output = socket.outputStream
        clients += output
        try {
            socket.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val (path, _) = parseWatchLine(line) ?: continue
                    when (path) {
                        WearPaths.PAGE_NEXT -> WearBridge.requestTurn(WearBridge.PageTurn.Next)
                        WearPaths.PAGE_PREVIOUS -> WearBridge.requestTurn(WearBridge.PageTurn.Previous)
                        WearPaths.PROGRESS_REQUEST -> send(output, watchLine(WearPaths.PROGRESS_REPLY, runCatching { progress() }.getOrDefault("")))
                    }
                }
            }
        } catch (e: Exception) {
            // The watch went out of range or closed its app.
        } finally {
            clients -= output
            runCatching { socket.close() }
        }
    }

    private fun send(output: OutputStream, line: String) {
        synchronized(output) {
            output.write(line.toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    /** Sends [payload] on [path] to every connected watch. Never blocks the caller. */
    fun broadcast(path: String, payload: String) {
        if (clients.isEmpty()) return
        val line = watchLine(path, payload)
        scope.launch { clients.forEach { output -> runCatching { send(output, line) }.onFailure { clients -= output } } }
    }
}
