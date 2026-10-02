package com.dexter.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The "data saver on metered connections" choice. While [ReaderUi.dataSaverAutoMetered] is on and
 * the active network is metered, [MangaDexRepository.meteredSaver] stays true and page URLs come
 * from the data-saver set. The manual [Settings.dataSaver] toggle keeps working on its own; either
 * one is enough for saver images.
 *
 * Wired in the DI graph and started from the application; see the integration snippet in the report.
 */
class MeteredDataSaver(
    private val context: Context,
    private val repository: MangaDexRepository,
    private val prefs: ReaderUiPrefs,
) {
    /** Starts watching the preference and the network. Call once, from an application-wide scope. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(prefs.ui, meteredFlow()) { ui, metered -> ui.dataSaverAutoMetered && metered }
                .distinctUntilChanged()
                .collect { repository.meteredSaver = it }
        }
    }

    /** True while the active network is metered, following connects, drops, and capability changes. */
    private fun meteredFlow() = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isMetered(manager))
            }

            override fun onLost(network: Network) {
                trySend(isMetered(manager))
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(isMetered(manager))
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        trySend(isMetered(manager))
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }

    private fun isMetered(manager: ConnectivityManager): Boolean {
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}

/** True when the active network is metered. False with no connection or no network information. */
fun isMeteredConnection(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}
