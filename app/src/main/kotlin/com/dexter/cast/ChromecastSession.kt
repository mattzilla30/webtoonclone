package com.dexter.cast

import android.annotation.SuppressLint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

private const val SENDER = "sender-0"
private const val RECEIVER = "receiver-0"
private const val HEARTBEAT_MS = 5_000L
private const val CONNECT_TIMEOUT_MS = 5_000
private const val LAUNCH_TIMEOUT_MS = 15_000L
private const val MAX_FRAME = 1 shl 20

/**
 * One Cast v2 connection to a Chromecast: TLS on port 8009, a heartbeat every few seconds, and the
 * Default Media Receiver launched to show photos. Built on plain sockets and the framing in CastProtocol.kt.
 */
class ChromecastSession private constructor(
    private val socket: SSLSocket,
    private val onEnded: () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val output: OutputStream = socket.outputStream
    private val requests = AtomicInteger(1)
    private val json = Json { ignoreUnknownKeys = true }

    /** The launched receiver's transport id and session id, once it is running. */
    @Volatile private var transportId: String? = null

    @Volatile private var sessionId: String? = null

    @Volatile private var launched = CompletableDeferred<Unit>()

    @Volatile private var closed = false

    private fun send(destination: String, namespace: String, payload: JsonObject) {
        val frame = frameCastMessage(CastMessage(SENDER, destination, namespace, payload.toString()))
        synchronized(output) {
            output.write(frame)
            output.flush()
        }
    }

    private fun start() {
        send(RECEIVER, NS_CONNECTION, buildJsonObject { put("type", "CONNECT") })
        scope.launch { readLoop() }
        scope.launch {
            while (isActive) {
                runCatching { send(RECEIVER, NS_HEARTBEAT, buildJsonObject { put("type", "PING") }) }
                delay(HEARTBEAT_MS)
            }
        }
    }

    private fun readLoop() {
        val input = DataInputStream(socket.inputStream)
        try {
            while (!closed) {
                val size = input.readInt()
                require(size in 1..MAX_FRAME) { "Bad frame size $size" }
                val bytes = ByteArray(size).also(input::readFully)
                val message = decodeCastMessage(bytes) ?: continue
                handle(message)
            }
        } catch (e: Exception) {
            if (!closed) end()
        }
    }

    private fun handle(message: CastMessage) {
        val body = runCatching { json.parseToJsonElement(message.payload).jsonObject }.getOrNull() ?: return
        when (body["type"]?.jsonPrimitive?.contentOrNull) {
            "PING" -> send(message.source, NS_HEARTBEAT, buildJsonObject { put("type", "PONG") })
            "CLOSE" -> if (message.source == RECEIVER || message.source == transportId) end()
            "RECEIVER_STATUS" -> {
                val app = body["status"]?.jsonObject?.get("applications")?.jsonArray
                    ?.map { it.jsonObject }
                    ?.firstOrNull { it["appId"]?.jsonPrimitive?.contentOrNull == DEFAULT_MEDIA_RECEIVER }
                if (app == null) {
                    // The receiver was closed on the TV, so this session is over.
                    if (transportId != null) end()
                    return
                }
                val transport = app["transportId"]?.jsonPrimitive?.contentOrNull ?: return
                if (transport != transportId) {
                    transportId = transport
                    sessionId = app["sessionId"]?.jsonPrimitive?.contentOrNull
                    send(transport, NS_CONNECTION, buildJsonObject { put("type", "CONNECT") })
                }
                launched.complete(Unit)
            }
        }
    }

    /** Launches the Default Media Receiver on the TV, or joins it when it already runs. */
    private suspend fun launchReceiver() {
        if (transportId != null) return
        launched = CompletableDeferred()
        send(
            RECEIVER,
            NS_RECEIVER,
            buildJsonObject {
                put("type", "LAUNCH")
                put("appId", DEFAULT_MEDIA_RECEIVER)
                put("requestId", requests.getAndIncrement())
            },
        )
        withTimeout(LAUNCH_TIMEOUT_MS) { launched.await() }
    }

    /** Shows the image at [url] on the TV as a photo titled [title] and [subtitle]. */
    suspend fun showImage(url: String, contentType: String, title: String, subtitle: String) {
        launchReceiver()
        val transport = transportId ?: error("The TV did not start its receiver")
        send(
            transport,
            NS_MEDIA,
            buildJsonObject {
                put("type", "LOAD")
                put("requestId", requests.getAndIncrement())
                sessionId?.let { put("sessionId", it) }
                put("autoplay", true)
                putJsonObject("media") {
                    put("contentId", url)
                    put("contentType", contentType)
                    put("streamType", "NONE")
                    putJsonObject("metadata") {
                        put("metadataType", 4) // PHOTO
                        put("title", title)
                        put("subtitle", subtitle)
                    }
                }
            },
        )
    }

    /** Stops the receiver on the TV and closes the connection. */
    fun close() {
        if (closed) return
        runCatching {
            sessionId?.let { id ->
                send(
                    RECEIVER, NS_RECEIVER,
                    buildJsonObject {
                        put("type", "STOP")
                        put("sessionId", id)
                        put("requestId", requests.getAndIncrement())
                    },
                )
            }
            send(RECEIVER, NS_CONNECTION, buildJsonObject { put("type", "CLOSE") })
        }
        shutdown()
    }

    private fun end() {
        if (closed) return
        shutdown()
        onEnded()
    }

    private fun shutdown() {
        closed = true
        runCatching { socket.close() }
        scope.cancel()
    }

    companion object {
        /**
         * Chromecasts present a certificate signed by Google's own device authority, which no public
         * trust store holds. The connection is still encrypted; it only skips checking who signed it,
         * the same trade every open-source Cast client makes. Only this socket uses it.
         */
        @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
        private val trustChromecast = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        /** Connects to the Chromecast at [host]:[port]. [onEnded] runs if the TV ends the session. */
        fun open(host: InetAddress, port: Int, onEnded: () -> Unit): ChromecastSession {
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustChromecast), null) }
            val socket = ssl.socketFactory.createSocket() as SSLSocket
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.startHandshake()
            return ChromecastSession(socket, onEnded).also { it.start() }
        }
    }
}
