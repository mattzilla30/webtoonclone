package com.dexter.cast

import android.content.Context
import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Longest queue sent to the receiver in one load; longer chapters queue a window from the page. */
private const val MAX_QUEUE_PAGES = 200

/**
 * Casts reader pages to a Chromecast as photos on the Default Media Receiver. Every public call
 * no-ops gracefully when Play Services is missing or no Cast session is connected.
 */
class CastManager private constructor(appContext: Context) {
    private val castContext: CastContext? =
        runCatching { CastContext.getSharedInstance(appContext) }.getOrNull()

    private val _isAvailable = MutableStateFlow(castContext != null)

    /** True when the Cast framework initialized (Play Services present). */
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isCasting = MutableStateFlow(false)

    /** True while a Cast session is connected. */
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private var queuePages: List<String> = emptyList()
    private var queueIndex: Int = -1

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            _isCasting.value = true
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            _isCasting.value = false
            resetQueue()
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            _isCasting.value = true
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _isCasting.value = false
        }

        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
    }

    init {
        runCatching {
            castContext?.sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
        }
    }

    private fun remoteClient(): RemoteMediaClient? = runCatching {
        castContext?.sessionManager?.currentCastSession?.remoteMediaClient
    }.getOrNull()

    /** Casts one page image as a photo. Title shows on the receiver as "Series - Ep. N". */
    fun castPage(url: String, title: String, subtitle: String) {
        val client = remoteClient() ?: return
        runCatching {
            client.load(photoInfo(url, title, subtitle), MediaLoadOptions.Builder().setAutoplay(true).build())
        }
    }

    /**
     * Casts the chapter as a queue of photos starting at [startIndex], so turning pages advances
     * the receiver instead of reloading. Chapters longer than [MAX_QUEUE_PAGES] queue a window
     * starting at [startIndex].
     */
    fun startChapter(pages: List<String>, startIndex: Int, title: String, subtitle: String) {
        val client = remoteClient() ?: return
        if (pages.isEmpty()) return
        val start = startIndex.coerceIn(pages.indices)
        val windowed = pages.size > MAX_QUEUE_PAGES
        val window = if (windowed) pages.drop(start).take(MAX_QUEUE_PAGES) else pages
        val items = window.map {
            MediaQueueItem.Builder(photoInfo(it, title, subtitle))
                .setAutoplay(true)
                .setPreloadTime(20.0)
                .build()
        }
        runCatching {
            client.queueLoad(
                items.toTypedArray(),
                if (windowed) 0 else start,
                MediaStatus.REPEAT_MODE_REPEAT_OFF,
                null,
            )
            queuePages = window
            queueIndex = if (windowed) 0 else start
        }
    }

    /** Moves the receiver to [index] of the queued chapter without reloading. */
    fun seekToPage(index: Int) {
        val client = remoteClient() ?: return
        if (queuePages.isEmpty() || index !in queuePages.indices || index == queueIndex) return
        runCatching {
            when (index) {
                queueIndex + 1 -> client.queueNext(null)
                queueIndex - 1 -> client.queuePrev(null)
                else -> client.queueJumpToItem(itemIdAt(index), null)
            }
            queueIndex = index
        }
    }

    /** Disconnects the current Cast session. */
    fun endSession() {
        runCatching { castContext?.sessionManager?.endCurrentSession(true) }
    }

    private fun itemIdAt(index: Int): Int = runCatching {
        remoteClient()?.mediaQueue?.itemIdAtIndex(index) ?: MediaQueueItem.INVALID_ITEM_ID
    }.getOrDefault(MediaQueueItem.INVALID_ITEM_ID)

    private fun resetQueue() {
        queuePages = emptyList()
        queueIndex = -1
    }

    private fun photoInfo(url: String, title: String, subtitle: String): MediaInfo {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_PHOTO).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            putString(MediaMetadata.KEY_SUBTITLE, subtitle)
            runCatching { addImage(WebImage(Uri.parse(url))) }
        }
        return MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_NONE)
            .setContentType(contentTypeFor(url))
            .setMetadata(metadata)
            .build()
    }

    private fun contentTypeFor(url: String): String {
        val path = runCatching { Uri.parse(url).path }.getOrNull().orEmpty()
        return when {
            path.endsWith(".png", ignoreCase = true) -> "image/png"
            path.endsWith(".gif", ignoreCase = true) -> "image/gif"
            path.endsWith(".webp", ignoreCase = true) -> "image/webp"
            path.endsWith(".bmp", ignoreCase = true) -> "image/bmp"
            else -> "image/jpeg"
        }
    }

    companion object {
        /** Creates the manager; safe to call even without Play Services (all calls then no-op). */
        fun create(context: Context): CastManager = CastManager(context.applicationContext)
    }
}
