package com.karadaglabs.cartv

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.PlayerView

@UnstableApi
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null

    private lateinit var playerView: PlayerView
    private lateinit var playerRoot: View
    private lateinit var topBar: LinearLayout
    private lateinit var controlBar: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private lateinit var backButton: ImageButton
    private lateinit var rewindButton: ImageButton
    private lateinit var playPauseButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var errorContainer: LinearLayout

    private var streamUrl = ""
    private var title = ""
    private var mediaType = ""

    private var isReleased = false
    private var isInitializing = false

    private val handler =
        Handler(Looper.getMainLooper())

    private val hideControlsRunnable =
        Runnable {
            hideControls()
        }

    private val isRadio: Boolean
        get() =
            mediaType.equals(
                "radio",
                ignoreCase = true
            )

    private val isVideo: Boolean
        get() =
            mediaType.equals(
                "video",
                ignoreCase = true
            )

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContentView(
            R.layout.activity_player
        )

        playerRoot =
            findViewById(
                R.id.playerRoot
            )

        playerView =
            findViewById(
                R.id.playerView
            )

        topBar =
            findViewById(
                R.id.topBar
            )

        controlBar =
            findViewById(
                R.id.controlBar
            )

        titleText =
            findViewById(
                R.id.titleText
            )

        errorText =
            findViewById(
                R.id.errorText
            )

        retryButton =
            findViewById(
                R.id.retryButton
            )

        backButton =
            findViewById(
                R.id.backButton
            )

        rewindButton =
            findViewById(
                R.id.rewindButton
            )

        playPauseButton =
            findViewById(
                R.id.playPauseButton
            )

        forwardButton =
            findViewById(
                R.id.forwardButton
            )

        errorContainer =
            findViewById(
                R.id.errorContainer
            )

        streamUrl =
            intent
                .getStringExtra(
                    EXTRA_STREAM_URL
                )
                .orEmpty()

        title =
            intent
                .getStringExtra(
                    EXTRA_TITLE
                )
                .orEmpty()

        mediaType =
            intent
                .getStringExtra(
                    EXTRA_MEDIA_TYPE
                )
                .orEmpty()

        configureInterface()

        setupControls()

        setupTouch()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )

        if (streamUrl.isBlank()) {

            showError(
                if (isRadio) {
                    "Radyo yayın adresi bulunamadı."
                } else {
                    "Yayın adresi bulunamadı."
                }
            )

        } else {
            initializePlayer()
        }
    }

    private fun configureInterface() {

        if (isRadio) {

            /*
             * Radyo audio-only çalışır.
             * Video surface kullanılmaz.
             */
            playerView.visibility =
                View.GONE

            titleText.text =
                if (title.isBlank()) {
                    "📻 CANLI RADYO"
                } else {
                    "📻 $title"
                }

            /*
             * Radyoda ileri / geri yok.
             */
            rewindButton.visibility =
                View.GONE

            forwardButton.visibility =
                View.GONE

        } else {

            playerView.visibility =
                View.VISIBLE

            titleText.text =
                title.ifBlank {
                    "Karadag Car TV"
                }

            /*
             * Canlı yayınlarda ileri / geri yok.
             */
            if (isLiveStream()) {

                rewindButton.visibility =
                    View.GONE

                forwardButton.visibility =
                    View.GONE

            } else {

                rewindButton.visibility =
                    View.VISIBLE

                forwardButton.visibility =
                    View.VISIBLE
            }
        }

        hideControls()
    }

    private fun setupControls() {

        backButton.setOnClickListener {
            finish()
        }

        playPauseButton.setOnClickListener {

            val currentPlayer =
                player
                    ?: return@setOnClickListener

            if (currentPlayer.isPlaying) {
                currentPlayer.pause()
            } else {
                currentPlayer.play()
            }

            updatePlayPauseIcon()
            showControls()
        }

        rewindButton.setOnClickListener {

            val currentPlayer =
                player
                    ?: return@setOnClickListener

            val newPosition =
                maxOf(
                    0L,
                    currentPlayer.currentPosition - 10_000L
                )

            currentPlayer.seekTo(
                newPosition
            )

            showControls()
        }

        forwardButton.setOnClickListener {

            val currentPlayer =
                player
                    ?: return@setOnClickListener

            val duration =
                currentPlayer.duration

            val targetPosition =
                currentPlayer.currentPosition +
                        10_000L

            if (
                duration != C.TIME_UNSET &&
                duration > 0
            ) {

                currentPlayer.seekTo(
                    minOf(
                        duration,
                        targetPosition
                    )
                )

            } else {

                currentPlayer.seekTo(
                    targetPosition
                )
            }

            showControls()
        }

        retryButton.setOnClickListener {

            if (!isInitializing) {
                initializePlayer()
            }
        }
    }

    private fun setupTouch() {

        playerRoot.setOnTouchListener {
                _: View,
                event: MotionEvent ->

            if (
                event.action ==
                MotionEvent.ACTION_UP
            ) {
                toggleControls()
            }

            true
        }
    }

    private fun toggleControls() {

        if (
            topBar.visibility ==
            View.VISIBLE
        ) {

            hideControls()

        } else {

            showControls()
        }
    }

    private fun showControls() {

        topBar.visibility =
            View.VISIBLE

        controlBar.visibility =
            View.VISIBLE

        updatePlayPauseIcon()

        handler.removeCallbacks(
            hideControlsRunnable
        )

        handler.postDelayed(
            hideControlsRunnable,
            4_000L
        )
    }

    private fun hideControls() {

        topBar.visibility =
            View.GONE

        controlBar.visibility =
            View.GONE

        handler.removeCallbacks(
            hideControlsRunnable
        )
    }

    private fun updatePlayPauseIcon() {

        if (
            player?.isPlaying == true
        ) {

            playPauseButton.setImageResource(
                android.R.drawable.ic_media_pause
            )

            playPauseButton.contentDescription =
                "Duraklat"

        } else {

            playPauseButton.setImageResource(
                android.R.drawable.ic_media_play
            )

            playPauseButton.contentDescription =
                "Oynat"
        }
    }

    private fun initializePlayer() {

        if (streamUrl.isBlank()) {
            return
        }

        if (isInitializing) {
            return
        }

        isInitializing =
            true

        releasePlayer()

        isReleased =
            false

        hideError()

        /*
         * Radyo için önceki stabil değerler.
         *
         * Testte 3 dakika boyunca kesintisiz
         * çalıştığı için bunları değiştirmiyoruz.
         */
        val loadControl =
            if (isRadio) {

                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        30_000,
                        90_000,
                        3_000,
                        10_000
                    )
                    .setPrioritizeTimeOverSizeThresholds(
                        true
                    )
                    .build()

            } else {

                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        15_000,
                        50_000,
                        2_500,
                        5_000
                    )
                    .setPrioritizeTimeOverSizeThresholds(
                        true
                    )
                    .build()
            }

        val httpDataSourceFactory =
            DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(
                    15_000
                )
                .setReadTimeoutMs(
                    20_000
                )
                .setAllowCrossProtocolRedirects(
                    true
                )
                .setUserAgent(
                    "KaradagCarTV/1.0"
                )

        val mediaItemBuilder =
            MediaItem.Builder()
                .setUri(
                    streamUrl
                )

        if (isLiveStream()) {

            mediaItemBuilder.setLiveConfiguration(

                MediaItem.LiveConfiguration
                    .Builder()
                    .setTargetOffsetMs(
                        if (isRadio) {
                            8_000L
                        } else {
                            5_000L
                        }
                    )
                    .setMinPlaybackSpeed(
                        0.98f
                    )
                    .setMaxPlaybackSpeed(
                        1.02f
                    )
                    .build()
            )
        }

        val mediaItem =
            mediaItemBuilder.build()

        val mediaSource =
            createMediaSource(
                httpDataSourceFactory,
                mediaItem
            )

        val audioAttributes =
            Media3AudioAttributes.Builder()
                .setUsage(
                    C.USAGE_MEDIA
                )
                .setContentType(
                    if (isRadio) {
                        C.AUDIO_CONTENT_TYPE_MUSIC
                    } else {
                        C.AUDIO_CONTENT_TYPE_MOVIE
                    }
                )
                .build()

        player =
            ExoPlayer.Builder(this)
                .setLoadControl(
                    loadControl
                )
                .build()
                .also { exoPlayer ->

                    /*
                     * Radyo audio-only.
                     */
                    if (!isRadio) {

                        playerView.player =
                            exoPlayer
                    }

                    exoPlayer.setAudioAttributes(
                        audioAttributes,
                        true
                    )

                    exoPlayer.addListener(

                        object : Player.Listener {

                            override fun
                                    onPlaybackStateChanged(
                                playbackState: Int
                            ) {

                                when (
                                    playbackState
                                ) {

                                    Player.STATE_IDLE -> {
                                        // Hazırlanıyor.
                                    }

                                    Player.STATE_BUFFERING -> {
                                        /*
                                         * Spinner davranışını
                                         * kullanıcıya göstermiyoruz.
                                         */
                                    }

                                    Player.STATE_READY -> {

                                        isInitializing =
                                            false

                                        hideError()

                                        exoPlayer.playWhenReady =
                                            true

                                        updatePlayPauseIcon()
                                    }

                                    Player.STATE_ENDED -> {

                                        isInitializing =
                                            false

                                        if (isVideo) {

                                            exoPlayer.seekTo(
                                                0
                                            )

                                            exoPlayer.play()
                                        }

                                        updatePlayPauseIcon()
                                    }
                                }
                            }

                            override fun
                                    onIsPlayingChanged(
                                isPlaying: Boolean
                            ) {

                                if (isPlaying) {

                                    isInitializing =
                                        false

                                    hideError()
                                }

                                updatePlayPauseIcon()
                            }

                            override fun
                                    onPlayerError(
                                error: PlaybackException
                            ) {

                                isInitializing =
                                    false

                                if (!isReleased) {

                                    showError(
                                        buildErrorMessage(
                                            error
                                        )
                                    )
                                }
                            }
                        }
                    )

                    exoPlayer.setMediaSource(
                        mediaSource
                    )

                    exoPlayer.prepare()

                    exoPlayer.playWhenReady =
                        true
                }
    }

    private fun createMediaSource(
        httpDataSourceFactory:
        DefaultHttpDataSource.Factory,
        mediaItem: MediaItem
    ): MediaSource {

        val lowerUrl =
            streamUrl.lowercase()

        return if (
            lowerUrl.contains(
                ".m3u8"
            )
        ) {

            HlsMediaSource.Factory(
                httpDataSourceFactory
            )
                .setAllowChunklessPreparation(
                    true
                )
                .createMediaSource(
                    mediaItem
                )

        } else {

            DefaultMediaSourceFactory(
                httpDataSourceFactory
            )
                .createMediaSource(
                    mediaItem
                )
        }
    }

    private fun isLiveStream(): Boolean {

        val lowerUrl =
            streamUrl.lowercase()

        return isRadio ||
                !isVideo ||
                lowerUrl.contains(
                    ".m3u8"
                )
    }

    private fun buildErrorMessage(
        error: PlaybackException
    ): String {

        val prefix =
            if (isRadio) {
                "Radyo yayını oynatılamadı."
            } else {
                "Yayın oynatılamadı."
            }

        return when (
            error.errorCode
        ) {

            PlaybackException
                .ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> {

                "$prefix\n\nİnternet bağlantısı kurulamadı."
            }

            PlaybackException
                .ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {

                "$prefix\n\nYayın sunucusuna bağlantı zaman aşımına uğradı."
            }

            PlaybackException
                .ERROR_CODE_IO_BAD_HTTP_STATUS -> {

                "$prefix\n\nYayın sunucusu yanıt vermiyor."
            }

            PlaybackException
                .ERROR_CODE_IO_FILE_NOT_FOUND -> {

                "$prefix\n\nYayın adresi bulunamadı."
            }

            PlaybackException
                .ERROR_CODE_DECODER_INIT_FAILED -> {

                "$prefix\n\nCihaz oynatıcıyı başlatamadı."
            }

            PlaybackException
                .ERROR_CODE_DECODING_FAILED -> {

                "$prefix\n\nSes/video çözümleme hatası."
            }

            else -> {

                "$prefix\n\n${error.errorCodeName}"
            }
        }
    }

    private fun showError(
        message: String
    ) {

        errorContainer.visibility =
            View.VISIBLE

        errorText.text =
            message

        retryButton.visibility =
            View.VISIBLE
    }

    private fun hideError() {

        errorContainer.visibility =
            View.GONE
    }

    override fun onStart() {

        super.onStart()

        if (
            player != null &&
            !isReleased
        ) {

            player?.playWhenReady =
                true
        }
    }

    override fun onStop() {

        super.onStop()

        releasePlayer()
    }

    override fun onDestroy() {

        handler.removeCallbacks(
            hideControlsRunnable
        )

        releasePlayer()

        super.onDestroy()
    }

    private fun releasePlayer() {

        isReleased =
            true

        isInitializing =
            false

        player?.playWhenReady =
            false

        player?.stop()

        player?.release()

        player = null

        if (
            ::playerView.isInitialized
        ) {

            playerView.player =
                null
        }
    }

    companion object {

        const val EXTRA_STREAM_URL =
            "stream_url"

        const val EXTRA_TITLE =
            "title"

        const val EXTRA_MEDIA_TYPE =
            "media_type"
    }
}