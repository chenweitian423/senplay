package com.senplay.player

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.senplay.core.Http
import com.senplay.core.LibraryStore
import com.senplay.deeplink.SchemeRouter
import com.senplay.ui.SenPlayTheme
import kotlinx.coroutines.delay

class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR

        setContent {
            SenPlayTheme {
                PlayerScreen(
                    url = url,
                    name = intent.getStringExtra(EXTRA_NAME) ?: url.substringAfterLast('/'),
                    subtitleUrl = intent.getStringExtra(EXTRA_SUBTITLE),
                    userAgent = intent.getStringExtra(EXTRA_USER_AGENT),
                    startPositionMs = intent.getLongExtra(EXTRA_POSITION_MS, 0L),
                    sourceUri = intent.data,
                    onFinish = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_USER_AGENT = "extra_user_agent"
        const val EXTRA_POSITION_MS = "extra_position_ms"
    }
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)

@Composable
fun PlayerScreen(
    url: String,
    name: String,
    subtitleUrl: String?,
    userAgent: String?,
    startPositionMs: Long,
    sourceUri: Uri?,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { LibraryStore.get(context) }
    var speed by remember { mutableStateOf(1f) }
    var speedMenuOpen by remember { mutableStateOf(false) }

    val player = remember {
        val dataSource = DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(Http.client).setUserAgent(userAgent ?: store.defaultUserAgent)
        )
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .build()
            .apply {
                val builder = MediaItem.Builder().setUri(Uri.parse(url))
                if (!subtitleUrl.isNullOrBlank()) {
                    builder.setSubtitleConfigurations(
                        listOf(
                            MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                                .setMimeType(subtitleMime(subtitleUrl))
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .setLanguage("zh")
                                .build()
                        )
                    )
                }
                setMediaItem(builder.build())
                prepare()
                if (startPositionMs > 0L) seekTo(startPositionMs)
                playWhenReady = true
            }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(5000)
            val duration = player.duration
            if (duration > 0 && store.rememberProgressEnabled) {
                store.rememberProgress(url, name, player.currentPosition, duration)
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            if (store.rememberProgressEnabled && player.duration > 0) {
                store.rememberProgress(url, name, player.currentPosition, player.duration)
            }
            SchemeRouter.dispatchCallback(context, sourceUri ?: Uri.EMPTY, url, player.currentPosition / 1000)
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setKeepContentOnPlayerReset(true)
                }
            }
        )

        IconButton(
            onClick = onFinish,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            IconButton(onClick = { speedMenuOpen = true }) {
                Icon(Icons.Filled.Speed, contentDescription = "Speed", tint = Color.White)
            }
            DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                SPEEDS.forEach { value ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (value == 1f) "1.0x" else value.toString() + "x",
                                color = if (value == speed) MaterialTheme.colorScheme.primary else Color.Unspecified
                            )
                        },
                        onClick = {
                            speed = value
                            player.setPlaybackSpeed(value)
                            speedMenuOpen = false
                        }
                    )
                }
            }
        }
    }
}

private fun subtitleMime(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "srt" -> MimeTypes.APPLICATION_SUBRIP
    "ass", "ssa" -> MimeTypes.TEXT_SSA
    "vtt" -> MimeTypes.TEXT_VTT
    "ttml" -> MimeTypes.APPLICATION_TTML
    else -> MimeTypes.APPLICATION_SUBRIP
}
