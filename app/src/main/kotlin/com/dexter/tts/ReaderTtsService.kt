package com.dexter.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.dexter.data.A11yPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** Broadcast page turns while narration runs, so the reader can follow along when auto-advance is on. */
object TtsPageEvents {
    /** The page index narration just reached. */
    val pages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
}

/**
 * Reads a chapter aloud and keeps going with the screen off. A foreground service owns a
 * [TextToSpeech] engine and a [MediaSession], so headset buttons, Bluetooth clickers, and the
 * notification's play/pause control narration. Start it with [start], toggle with [toggle].
 *
 * Manifest and permission snippets are in the batch report; the service itself needs
 * FOREGROUND_SERVICE_MEDIA_PLAYBACK and a foregroundServiceType of mediaPlayback.
 */
class ReaderTtsService : Service(), TextToSpeech.OnInitListener {
    companion object {
        private const val CHANNEL_ID = "tts_narration"
        private const val NOTIFICATION_ID = 41

        const val ACTION_START = "com.dexter.tts.START"
        const val ACTION_TOGGLE = "com.dexter.tts.TOGGLE"
        const val ACTION_STOP = "com.dexter.tts.STOP"
        const val ACTION_NEXT = "com.dexter.tts.NEXT"
        const val ACTION_PREV = "com.dexter.tts.PREV"
        private const val ACTION_MEDIA_BUTTON = "com.dexter.tts.MEDIA_BUTTON"

        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXTS = "texts"
        const val EXTRA_PAGES = "pages"
        const val EXTRA_AUTO_ADVANCE = "auto_advance"

        /**
         * Total characters of [EXTRA_TEXTS] kept in the start intent. Binder transactions fail
         * past ~1MB, and a whole chapter of OCR text can approach that.
         */
        private const val MAX_TEXT_CHARS = 400_000

        /**
         * Start narrating [texts], one utterance per entry. [pages] names the page each utterance
         * belongs to, so the reader can auto-advance; pass an empty array to skip page tracking.
         */
        fun start(context: Context, title: String, texts: List<String>, pages: IntArray = IntArray(0), autoAdvance: Boolean = true) {
            // The texts ride in the start intent: cap the payload, keeping [pages] aligned with
            // the texts that survive the cap.
            var remaining = MAX_TEXT_CHARS
            val capped = ArrayList<String>(texts.size)
            for (text in texts) {
                if (text.length > remaining) break
                capped += text
                remaining -= text.length
            }
            val intent = Intent(context, ReaderTtsService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_TITLE, title)
                .putStringArrayListExtra(EXTRA_TEXTS, capped)
                .putExtra(EXTRA_PAGES, if (pages.size == texts.size) pages.copyOf(capped.size) else pages)
                .putExtra(EXTRA_AUTO_ADVANCE, autoAdvance)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Pause or resume from anywhere, such as the reader's narration toggle. */
        fun toggle(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ReaderTtsService::class.java).setAction(ACTION_TOGGLE))
        }

        /** Stop narration and dismiss the notification. */
        fun stop(context: Context) {
            // startForegroundService: the reader calls this from a DisposableEffect's onDispose, which
            // may run while the app is in the background, where startService() throws on API 26+.
            ContextCompat.startForegroundService(context, Intent(context, ReaderTtsService::class.java).setAction(ACTION_STOP))
        }

        private val runningMutable = MutableStateFlow(false)

        /**
         * Emits while narration runs. The reader collects this for its narration toggle, so the icon
         * follows the service even when narration stops on its own (the last utterance ending).
         */
        val runningFlow: StateFlow<Boolean> = runningMutable.asStateFlow()

        /** True while narration is running; the reader shows its narration toggle from this. */
        @Volatile var running = false
            private set(value) {
                field = value
                runningMutable.value = value
            }
    }

    private var tts: TextToSpeech? = null
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var title = ""
    private var texts: List<String> = emptyList()
    private var pages: IntArray = IntArray(0)
    private var autoAdvance = true
    private var index = 0
    private var playing = false
    private var ttsReady = false
    private var speed = 1f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            val prefs = A11yPrefs(this@ReaderTtsService).current()
            speed = prefs.ttsSpeed
            tts?.setSpeechRate(speed)
        }
        tts = TextToSpeech(this, this).apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit
                override fun onDone(utteranceId: String) = onUtteranceDone(utteranceId.toIntOrNull())
                override fun onError(utteranceId: String?, errorCode: Int) = onUtteranceDone(utteranceId?.toIntOrNull())

                // The older callback is still abstract, so it must be overridden even though the one above replaces it.
                @Deprecated("Replaced by onError(String, Int)")
                override fun onError(utteranceId: String?) = onUtteranceDone(utteranceId?.toIntOrNull())
            })
        }
        session = MediaSession(this, "DexterTts").apply {
            setCallback(SessionCallback())
            setMediaButtonReceiver(
                PendingIntent.getService(
                    this@ReaderTtsService, 0,
                    Intent(this@ReaderTtsService, ReaderTtsService::class.java).setAction(ACTION_MEDIA_BUTTON),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            isActive = true
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Narration", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            stopSelf()
            return
        }
        ttsReady = true
        tts?.language = Locale.getDefault()
        tts?.setSpeechRate(speed)
        if (playing) speakCurrent()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val rawTexts = intent.getStringArrayListExtra(EXTRA_TEXTS).orEmpty()
                val rawPages = intent.getIntArrayExtra(EXTRA_PAGES) ?: IntArray(0)
                // Blank utterances stall some engines, so drop them and keep the page mapping aligned.
                val kept = rawTexts.indices.filter { rawTexts[it].isNotBlank() }
                texts = kept.map { rawTexts[it] }
                pages = if (rawPages.size == rawTexts.size) kept.map { rawPages[it] }.toIntArray() else IntArray(0)
                autoAdvance = intent.getBooleanExtra(EXTRA_AUTO_ADVANCE, true)
                index = 0
                playing = true
                running = true
                promoteToForeground()
                if (ttsReady) speakCurrent() else updatePlaybackState()
            }
            ACTION_TOGGLE -> togglePlayback()
            ACTION_STOP -> shutDown()
            ACTION_NEXT -> skip(1)
            ACTION_PREV -> skip(-1)
            ACTION_MEDIA_BUTTON -> {
                val event = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent
                }
                if (event?.action == KeyEvent.ACTION_DOWN) handleMediaKey(event.keyCode)
            }
        }
        return START_STICKY
    }

    private fun togglePlayback() {
        if (texts.isEmpty()) return
        playing = !playing
        running = true
        if (playing) {
            promoteToForeground()
            if (ttsReady) speakCurrent() else updatePlaybackState()
        } else {
            tts?.stop()
            updateNotification()
            updatePlaybackState()
        }
    }

    private fun skip(direction: Int) {
        if (texts.isEmpty()) return
        index = (index + direction).coerceIn(0, texts.size - 1)
        if (playing && ttsReady) speakCurrent()
        updatePlaybackState()
    }

    /** Headset hooks, Bluetooth clickers, and media keys land here. */
    private fun handleMediaKey(keyCode: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> togglePlayback()
            KeyEvent.KEYCODE_MEDIA_PLAY -> if (!playing) togglePlayback()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> if (playing) togglePlayback()
            KeyEvent.KEYCODE_MEDIA_NEXT -> skip(1)
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> skip(-1)
        }
    }

    private fun speakCurrent() {
        val text = texts.getOrNull(index) ?: return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, index.toString())
        announcePage(index)
        updateNotification()
        updatePlaybackState()
    }

    private fun onUtteranceDone(doneIndex: Int?) {
        if (doneIndex == null || doneIndex != index) return
        scope.launch {
            if (!playing) return@launch
            if (index + 1 < texts.size) {
                index++
                if (ttsReady) speakCurrent()
            } else {
                shutDown()
            }
        }
    }

    private fun announcePage(utteranceIndex: Int) {
        if (!autoAdvance || pages.size != texts.size) return
        val page = pages.getOrNull(utteranceIndex) ?: return
        val previous = pages.getOrNull((utteranceIndex - 1).coerceAtLeast(0))
        if (utteranceIndex == 0 || page != previous) TtsPageEvents.pages.tryEmit(page)
    }

    private fun promoteToForeground() {
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        fun pending(action: String): PendingIntent = PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, ReaderTtsService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(if (title.isBlank()) "Reading aloud" else title)
            .setContentText(if (playing) "Page ${index + 1} of ${texts.size}" else "Paused")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(playing)
            .addAction(Notification.Action.Builder(null, if (playing) "Pause" else "Play", pending(ACTION_TOGGLE)).build())
            .addAction(Notification.Action.Builder(null, "Stop", pending(ACTION_STOP)).build())
            .setStyle(Notification.MediaStyle().setMediaSession(session?.sessionToken))
            .build()
    }

    private fun updatePlaybackState() {
        val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS,
                )
                .setState(state, index.toLong(), speed)
                .build(),
        )
    }

    private fun shutDown() {
        playing = false
        running = false
        tts?.stop()
        session?.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        tts?.shutdown()
        session?.release()
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback() {
        override fun onPlay() { if (!playing) togglePlayback() }
        override fun onPause() { if (playing) togglePlayback() }
        override fun onSkipToNext() = skip(1)
        override fun onSkipToPrevious() = skip(-1)
        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
            val event = if (Build.VERSION.SDK_INT >= 33) {
                mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent
            }
            if (event?.action == KeyEvent.ACTION_DOWN) handleMediaKey(event.keyCode)
            return true
        }
    }
}

/**
 * The narration for a chapter with no extractable text: one announcement per page, so TTS still
 * works for image-only webtoons. [chapterLabel] is something like "Chapter 12".
 */
fun ttsNarration(chapterLabel: String, pageCount: Int, announceEvery: Int = 1): Pair<List<String>, IntArray> {
    val texts = ArrayList<String>(pageCount)
    val pages = IntArray(pageCount)
    for (page in 0 until pageCount) {
        texts += if (page % announceEvery == 0 || page == 0) "$chapterLabel, page ${page + 1} of $pageCount" else ""
        pages[page] = page
    }
    return texts to pages
}
