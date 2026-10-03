package com.dexter.cast

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException

/** A TV the reader can cast to: a Chromecast found over mDNS, or a DLNA renderer found over SSDP. */
sealed interface CastDevice {
    val id: String
    val name: String

    data class Chromecast(override val id: String, override val name: String, val host: InetAddress, val port: Int) : CastDevice

    data class Dlna(override val id: String, override val name: String, val controlUrl: String) : CastDevice
}

private const val TAG = "DexterCast"
private const val SSDP_ADDRESS = "239.255.255.250"
private const val SSDP_PORT = 1900
private const val SSDP_LISTEN_MS = 3_000
private const val CHROMECAST_SERVICE = "_googlecast._tcp"

/**
 * Casts reader pages to a TV with open-source code only: Chromecasts through Cast v2 ([ChromecastSession]),
 * other smart TVs through DLNA. Discovery runs while the device picker is open. Every call no-ops
 * gracefully when no TV is connected.
 */
class CastManager private constructor(private val context: Context, private val http: OkHttpClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Casting needs no Google services, so it is always available on Wi-Fi. */
    val isAvailable: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    private val _isCasting = MutableStateFlow(false)

    /** True while a TV is connected. */
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())

    /** TVs found on the network, while the picker is open. */
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private val _connected = MutableStateFlow<CastDevice?>(null)

    /** The TV being cast to, or null. */
    val connected: StateFlow<CastDevice?> = _connected.asStateFlow()

    @Volatile private var chromecast: ChromecastSession? = null
    private var discovery: Job? = null
    private var nsdListener: NsdManager.DiscoveryListener? = null

    /** The chapter being cast, so a page turn can send the next page. */
    private var chapterPages: List<String> = emptyList()
    private var chapterTitle = ""
    private var chapterSubtitle = ""
    private var shownIndex = -1

    /** Starts looking for TVs. Call when the picker opens; [stopDiscovery] when it closes. */
    fun startDiscovery() {
        if (discovery?.isActive == true) return
        _devices.value = emptyList()
        discovery = scope.launch {
            launch { runCatching { discoverChromecasts() }.onFailure { Log.w(TAG, "mDNS discovery failed", it) } }
            while (isActive) {
                runCatching { discoverDlna() }.onFailure { Log.w(TAG, "SSDP discovery failed", it) }
                delay(10_000)
            }
        }
    }

    fun stopDiscovery() {
        discovery?.cancel()
        discovery = null
        nsdListener?.let { listener -> runCatching { context.getSystemService(NsdManager::class.java).stopServiceDiscovery(listener) } }
        nsdListener = null
    }

    private fun addDevice(device: CastDevice) = _devices.update { list -> (list.filterNot { it.id == device.id } + device).sortedBy { it.name.lowercase() } }

    /** Chromecasts announce themselves over mDNS as _googlecast._tcp, with their name in the "fn" attribute. */
    private fun discoverChromecasts() {
        val nsd = context.getSystemService(NsdManager::class.java)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                nsd.registerServiceInfoCallback(
                    service,
                    { it.run() },
                    object : NsdManager.ServiceInfoCallback {
                        override fun onServiceUpdated(info: NsdServiceInfo) {
                            val host = info.hostAddresses.firstOrNull() ?: return
                            val name = info.attributes["fn"]?.decodeToString() ?: info.serviceName
                            addDevice(CastDevice.Chromecast("cc:${info.serviceName}", name, host, info.port))
                            runCatching { nsd.unregisterServiceInfoCallback(this) }
                        }

                        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                        override fun onServiceLost() = Unit
                        override fun onServiceInfoCallbackUnregistered() = Unit
                    },
                )
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                _devices.update { list -> list.filterNot { it.id == "cc:${service.serviceName}" } }
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        nsdListener = listener
        nsd.discoverServices(CHROMECAST_SERVICE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    /** Asks every DLNA renderer to answer over SSDP, then reads each one's description file. */
    @SuppressLint("WifiManagerLeak")
    private suspend fun discoverDlna() {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        // Without a multicast lock, many phones drop the answers to save power.
        val lock = wifi.createMulticastLock("dexter-ssdp").apply { setReferenceCounted(false); acquire() }
        val locations = HashSet<String>()
        try {
            MulticastSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(0))
                socket.soTimeout = 500
                val search = SSDP_SEARCH.toByteArray()
                socket.send(DatagramPacket(search, search.size, InetAddress.getByName(SSDP_ADDRESS), SSDP_PORT))
                val buffer = ByteArray(4096)
                val until = System.currentTimeMillis() + SSDP_LISTEN_MS
                while (System.currentTimeMillis() < until) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    ssdpLocation(String(packet.data, 0, packet.length))?.let(locations::add)
                }
            }
        } finally {
            lock.release()
        }
        for (location in locations) {
            val xml = runCatching { http.newCall(Request.Builder().url(location).build()).execute().use { it.body.string() } }.getOrNull() ?: continue
            parseDlnaDescription(xml, location)?.let { addDevice(CastDevice.Dlna("dlna:$location", it.name, it.controlUrl)) }
        }
    }

    /** Connects to [device], ending any session already running. */
    fun connect(device: CastDevice) {
        scope.launch {
            disconnect()
            val ok = runCatching {
                when (device) {
                    is CastDevice.Chromecast -> chromecast = ChromecastSession.open(device.host, device.port) { endedRemotely() }
                    is CastDevice.Dlna -> Unit
                }
            }.onFailure { Log.w(TAG, "Could not connect to ${device.name}", it) }.isSuccess
            if (ok) {
                _connected.value = device
                _isCasting.value = true
            }
        }
    }

    private fun endedRemotely() {
        chromecast = null
        _connected.value = null
        _isCasting.value = false
        shownIndex = -1
    }

    /** Casts one page image as a photo. Its title shows on the TV as "Series - Ep. N" where the TV supports it. */
    fun castPage(url: String, title: String, subtitle: String) {
        if (!url.startsWith("http")) return
        val device = _connected.value ?: return
        scope.launch {
            runCatching {
                when (device) {
                    is CastDevice.Chromecast -> chromecast?.showImage(url, imageTypeFor(url), title, subtitle)
                    is CastDevice.Dlna -> dlnaShow(device, url, "$title - $subtitle")
                }
            }.onFailure { Log.w(TAG, "Could not cast the page", it) }
        }
    }

    /** Casts the chapter starting at [startIndex]. Page turns then follow through [seekToPage]. */
    fun startChapter(pages: List<String>, startIndex: Int, title: String, subtitle: String) {
        if (pages.isEmpty()) return
        chapterPages = pages
        chapterTitle = title
        chapterSubtitle = subtitle
        shownIndex = -1
        seekToPage(startIndex.coerceIn(pages.indices))
    }

    /** Shows page [index] of the chapter being cast. */
    fun seekToPage(index: Int) {
        if (index !in chapterPages.indices || index == shownIndex) return
        shownIndex = index
        castPage(chapterPages[index], chapterTitle, "$chapterSubtitle, page ${index + 1}")
    }

    /** Stops casting and disconnects. */
    fun endSession() {
        scope.launch { disconnect() }
    }

    private suspend fun disconnect() {
        val device = _connected.value
        runCatching { chromecast?.close() }
        chromecast = null
        if (device is CastDevice.Dlna) runCatching { dlnaCall(device, "Stop", listOf("InstanceID" to "0")) }
        _connected.value = null
        _isCasting.value = false
        shownIndex = -1
    }

    private suspend fun dlnaShow(device: CastDevice.Dlna, url: String, title: String) {
        dlnaCall(
            device,
            "SetAVTransportURI",
            listOf("InstanceID" to "0", "CurrentURI" to url, "CurrentURIMetaData" to dlnaPhotoMetadata(url, title)),
        )
        dlnaCall(device, "Play", listOf("InstanceID" to "0", "Speed" to "1"))
    }

    private suspend fun dlnaCall(device: CastDevice.Dlna, action: String, arguments: List<Pair<String, String>>) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(device.controlUrl)
            .header("SOAPACTION", "\"$AV_TRANSPORT#$action\"")
            .post(dlnaSoapBody(action, arguments).toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "DLNA $action failed: HTTP ${response.code}" }
        }
    }

    companion object {
        /** Creates the manager. Discovery and connections start only when asked. */
        fun create(context: Context, http: OkHttpClient): CastManager = CastManager(context.applicationContext, http)
    }
}
