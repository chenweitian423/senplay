package com.senplay.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.senplay.core.Http
import com.senplay.core.LibraryStore
import com.senplay.net.SchemeRoutingDataSourceFactory

/**
 * Owns the player so playback survives leaving the app.
 *
 * Video apps on Android cannot keep playing from an Activity: background work needs
 * a foreground service, and the media notification is what the platform shows while
 * it runs. MediaSessionService is the supported shape for this — it also gives the
 * headset buttons and the lock screen their transport controls for free.
 *
 * The activity connects with a MediaController rather than owning the player, which
 * is why the same session keeps running when the activity is gone.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var gain: GainStage? = null

    override fun onCreate() {
        super.onCreate()

        val store = LibraryStore.get(this)
        // "使用系统代理": OkHttp follows the system proxy through the default selector,
        // so switching the preference off has to say so explicitly. Without this the
        // switch would read as connected while every request still went through the
        // proxy.
        val http = if (store.prefBool("play.useSystemProxy", true)) {
            Http.client
        } else {
            Http.client.newBuilder().proxy(java.net.Proxy.NO_PROXY).build()
        }
        val dataSource = SchemeRoutingDataSourceFactory(
            DefaultDataSource.Factory(
                this,
                OkHttpDataSource.Factory(http).setUserAgent(store.defaultUserAgent)
            )
        )

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 60_000, 2_500, 5_000)
                    .build()
            )
            // Audio focus and the headphone-unplug pause are what make background
            // playback behave rather than play over everything.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PlayerActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Preferred languages. Media3 matches these against the track metadata the
        // container carries, the same way the iOS side matches on languageCode — the
        // preference is a hint, and a file without a matching track simply keeps
        // whatever it would have chosen anyway.
        val audioLanguage = store.prefString("audio.firstLanguage", "")
        val textLanguage = store.prefString("subtitle.firstLanguage", "")
        if (audioLanguage.isNotBlank() || textLanguage.isNotBlank()) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .apply {
                    if (audioLanguage.isNotBlank()) setPreferredAudioLanguage(audioLanguage)
                    if (textLanguage.isNotBlank()) setPreferredTextLanguage(textLanguage)
                }
                .build()
        }

        player.addAnalyticsListener(analyticsListener())
        PlaybackStats.reset()

        // The gain stage needs the player's audio session, so it is attached here
        // rather than in the activity.
        if (store.prefBool("audio.enhance", true)) {
            gain = GainStage(player.audioSessionId)
        }

        session = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Swiping the task away should not silently keep a media notification alive, so
     * playback stops with it. This is the behaviour a video player is expected to
     * have; audio players usually choose the opposite.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        session?.player?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        gain?.release()
        gain = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
