package com.senplay.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.media3.common.Player
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.senplay.core.Http
import com.senplay.core.DanmakuItem
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

        val queue = intent.getStringArrayListExtra(EXTRA_QUEUE_URLS).orEmpty()
            .zip(intent.getStringArrayListExtra(EXTRA_QUEUE_NAMES).orEmpty())

        setContent {
            SenPlayTheme {
                PlayerScreen(
                    url = url,
                    name = intent.getStringExtra(EXTRA_NAME) ?: url.substringAfterLast('/'),
                    subtitleUrl = intent.getStringExtra(EXTRA_SUBTITLE),
                    userAgent = intent.getStringExtra(EXTRA_USER_AGENT),
                    startPositionMs = intent.getLongExtra(EXTRA_POSITION_MS, 0L),
                    sourceUri = intent.data,
                    queue = queue,
                    onFinish = { finish() },
                    onPlayNext = { nextUrl, nextTitle ->
                        startActivity(intentFor(this, nextUrl, nextTitle,
                                                userAgent = intent.getStringExtra(EXTRA_USER_AGENT),
                                                queue = queue))
                    }
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
        const val EXTRA_QUEUE_URLS = "extra_queue_urls"
        const val EXTRA_QUEUE_NAMES = "extra_queue_names"

        fun intentFor(
            context: Context,
            url: String,
            name: String,
            subtitle: String? = null,
            userAgent: String? = null,
            positionMs: Long = 0L,
            queue: List<Pair<String, String>> = emptyList()
        ): Intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra(EXTRA_URL, url)
            putExtra(EXTRA_NAME, name)
            subtitle?.let { putExtra(EXTRA_SUBTITLE, it) }
            userAgent?.let { putExtra(EXTRA_USER_AGENT, it) }
            putExtra(EXTRA_POSITION_MS, positionMs)
            if (queue.isNotEmpty()) {
                putStringArrayListExtra(EXTRA_QUEUE_URLS, ArrayList(queue.map { it.first }))
                putStringArrayListExtra(EXTRA_QUEUE_NAMES, ArrayList(queue.map { it.second }))
            }
        }
    }
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)

private const val P_HIDE_AFTER = "pref.ui.autoHideSeconds"
private const val P_FORWARD = "pref.play.forwardSeconds"
private const val P_BACKWARD = "pref.play.backwardSeconds"
private const val P_PROGRESS_DOT = "pref.ui.progressDot"
private const val P_TIME_MODE = "pref.ui.timeMode"
private const val P_FAST_SEEK_BUTTONS = "pref.ui.fastSeekButtons"
private const val P_SCREENSHOT_BUTTON = "pref.ui.screenshotButton"
private const val P_DOUBLE_LEFT = "pref.gesture.doubleLeft"
private const val P_DOUBLE_CENTER = "pref.gesture.doubleCenter"
private const val P_DOUBLE_RIGHT = "pref.gesture.doubleRight"
private const val P_LEFT_VERTICAL = "pref.gesture.leftVertical"
private const val P_RIGHT_VERTICAL = "pref.gesture.rightVertical"
private const val P_USE_APP_BRIGHTNESS = "pref.gesture.useAppBrightness"
private const val P_RESUME = "pref.play.resumeFromLast"
private const val P_AUTO_NEXT = "pref.play.autoNext"
private const val P_SKIP_INTRO = "pref.play.skipIntroSeconds"
private const val P_SKIP_OUTRO = "pref.play.skipOutroSeconds"
private const val P_NEXT_COUNTDOWN = "pref.play.autoNextCountdown"
private const val P_DANMAKU_ENABLED = "pref.danmaku.enabled"
private const val P_DANMAKU_SIZE = "pref.danmaku.size"
private const val P_DANMAKU_SPEED = "pref.danmaku.speed"
private const val P_DANMAKU_AREA = "pref.danmaku.area"
private const val P_DANMAKU_SKIP_SHORT = "pref.danmaku.skipShort"
private const val P_PRELOAD_MODE = "pref.play.preloadMode"
private const val P_AUDIO_ENHANCE = "pref.audio.enhance"
private const val P_MULTI_CHANNEL = "pref.audio.multiChannel"

private enum class DragKind { NONE, SEEK, BRIGHTNESS, VOLUME }

@Composable
fun PlayerScreen(
    url: String,
    name: String,
    subtitleUrl: String?,
    userAgent: String?,
    startPositionMs: Long,
    sourceUri: Uri?,
    queue: List<Pair<String, String>>,
    onFinish: () -> Unit,
    onPlayNext: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val store = remember { LibraryStore.get(context) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var speed by remember { mutableStateOf(1f) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }

    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubTarget by remember { mutableStateOf(0f) }
    var hud by remember { mutableStateOf<String?>(null) }
    var didSkipIntro by remember { mutableStateOf(false) }
    var didSkipOutro by remember { mutableStateOf(false) }
    var upNext by remember { mutableStateOf<Pair<String, String>?>(null) }
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    var danmaku by remember { mutableStateOf<List<DanmakuItem>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var castOpen by remember { mutableStateOf(false) }
    var castTarget by remember { mutableStateOf<com.senplay.net.DlnaDevice?>(null) }
    var countdown by remember { mutableStateOf(0) }

    val autoHideSeconds = remember { store.prefInt(P_HIDE_AFTER, 3) }
    val forwardSeconds = remember { store.prefInt(P_FORWARD, 15) }
    val backwardSeconds = remember { store.prefInt(P_BACKWARD, 15) }
    val progressDot = remember { store.prefBool(P_PROGRESS_DOT, true) }
    val timeMode = remember { store.prefString(P_TIME_MODE, "total") }
    val fastSeekButtons = remember { store.prefBool(P_FAST_SEEK_BUTTONS, false) }
    val showScreenshot = remember { store.prefBool(P_SCREENSHOT_BUTTON, true) }
    val doubleLeft = remember { store.prefString(P_DOUBLE_LEFT, "backward") }
    val doubleCenter = remember { store.prefString(P_DOUBLE_CENTER, "playPause") }
    val doubleRight = remember { store.prefString(P_DOUBLE_RIGHT, "forward") }
    val leftVertical = remember { store.prefString(P_LEFT_VERTICAL, "brightness") }
    val rightVertical = remember { store.prefString(P_RIGHT_VERTICAL, "volume") }
    val useAppBrightness = remember { store.prefBool(P_USE_APP_BRIGHTNESS, false) }
    val resumeFromLast = remember { store.prefBool(P_RESUME, true) }
    val autoNext = remember { store.prefBool(P_AUTO_NEXT, true) }
    val skipIntroSeconds = remember { store.prefInt(P_SKIP_INTRO, 0) }
    val skipOutroSeconds = remember { store.prefInt(P_SKIP_OUTRO, 0) }
    val nextCountdown = remember { store.prefInt(P_NEXT_COUNTDOWN, 5) }
    val danmakuEnabled = remember { store.prefBool(P_DANMAKU_ENABLED, true) }
    val danmakuSize = remember { store.prefFloat(P_DANMAKU_SIZE, 25f).toDouble() }
    val danmakuSpeed = remember { store.prefFloat(P_DANMAKU_SPEED, 0.5f).toDouble() }
    val danmakuArea = remember { store.prefFloat(P_DANMAKU_AREA, 0.15f).toDouble() }
    val danmakuSkipShort = remember { store.prefBool(P_DANMAKU_SKIP_SHORT, true) }
    val preloadMode = remember { store.prefString(P_PRELOAD_MODE, "none") }
    val audioEnhance = remember { store.prefBool(P_AUDIO_ENHANCE, true) }
    // Reported in the info panel; the actual downmix happens in the decoder graph.
    val multiChannel = remember { store.prefBool(P_MULTI_CHANNEL, false) }
    @Suppress("UNUSED_VARIABLE") val multiChannelNotice = multiChannel

    val player = remember {
        val dataSource = com.senplay.net.SchemeRoutingDataSourceFactory(
            DefaultDataSource.Factory(
                context,
                OkHttpDataSource.Factory(Http.client)
                    .setUserAgent(userAgent ?: store.defaultUserAgent)
            )
        )
        // Pre-cache. "不缓存" keeps memory flat on long files; "保留最后一次" trades
        // memory for a smoother scrub near the playhead.
        val loadControl = if (preloadMode == "last") {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    60_000,   // minBufferMs
                    300_000,  // maxBufferMs
                    10_000,   // bufferForPlaybackMs
                    20_000    // bufferForPlaybackAfterRebufferMs
                )
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 60_000, 2_500, 5_000)
                .build()
        }

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(loadControl)
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
                val resume = if (startPositionMs > 0L) startPositionMs
                else if (resumeFromLast) store.savedPosition(url) else 0L
                if (resume > 0L) seekTo(resume)
                playWhenReady = true
            }
    }

    fun showHud(text: String) {
        hud = text
    }

    fun advanceToNext(next: Pair<String, String>) {
        upNext = null
        countdown = 0
        store.rememberProgress(url, name, player.currentPosition, player.duration.coerceAtLeast(0))
        player.stop()
        onPlayNext(next.first, next.second)
    }

    fun beginCountdown() {
        val index = queue.indexOfFirst { it.first == url }
        if (index < 0 || index + 1 >= queue.size) return
        val next = queue[index + 1]
        if (nextCountdown <= 0) {
            advanceToNext(next)
        } else {
            upNext = next
            countdown = nextCountdown
        }
    }

    LaunchedEffect(upNext, countdown) {
        if (upNext == null || countdown <= 0) return@LaunchedEffect
        delay(1000)
        countdown -= 1
        if (countdown == 0) {
            upNext?.let { advanceToNext(it) }
        }
    }

    LaunchedEffect(url) {
        if (!danmakuEnabled) return@LaunchedEffect
        val endpoint = com.senplay.core.danmakuEndpoints(store).firstOrNull() ?: return@LaunchedEffect
        val keyword = url.substringAfterLast('/').substringBeforeLast('.')
        danmaku = com.senplay.core.DanmakuLoader.fetch(keyword, endpoint)
    }

    // LoudnessEnhancer is Android's own gain stage — the counterpart of the iOS
    // side's volume filter, and the thing "音量到达 100% 后继续放大" actually means.
    DisposableEffect(player) {
        val enhancer = if (audioEnhance) {
            LoudnessEnhancer(player.audioSessionId).apply {
                setTargetGain(600) // +6 dB
                enabled = true
            }
        } else null
        onDispose {
            enhancer?.enabled = false
            enhancer?.release()
        }
    }

    if (castOpen) {
        com.senplay.ui.CastDialog(
            onDismiss = { castOpen = false },
            onCast = { device ->
                castOpen = false
                // Only a network address is castable: the renderer fetches the media
                // itself, so a local file would need a server we do not run here.
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    scope.launch {
                        val started = com.senplay.net.Dlna.play(device, url, name)
                        if (started) {
                            castTarget = device
                            player.pause()
                            showHud("已投屏到 ${device.name}")
                        } else {
                            showHud("投屏失败")
                        }
                    }
                } else {
                    showHud("只有网络地址可以投屏")
                }
            }
        )
    }

    LaunchedEffect(player) {
        while (true) {
            delay(250)
            position = player.currentPosition
            val d = player.duration
            if (d > 0) duration = d
            isPlaying = player.isPlaying

            if (skipIntroSeconds > 0 && !didSkipIntro && position > 1_000L && position < skipIntroSeconds * 1000L) {
                didSkipIntro = true
                player.seekTo(skipIntroSeconds * 1000L)
                hud = "已跳过片头 ${skipIntroSeconds} 秒"
            }
            if (skipOutroSeconds > 0 && !didSkipOutro && d > 0 &&
                d - position <= skipOutroSeconds * 1000L
            ) {
                didSkipOutro = true
                if (autoNext) beginCountdown() else hud = "已到片尾"
            }
            if (player.playbackState == Player.STATE_ENDED) {
                isPlaying = false
                if (autoNext && upNext == null) beginCountdown()
            }
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(5000)
            val d = player.duration
            if (d > 0 && store.rememberProgressEnabled) {
                store.rememberProgress(url, name, player.currentPosition, d)
            }
        }
    }

    LaunchedEffect(controlsVisible, autoHideSeconds) {
        if (!controlsVisible || autoHideSeconds <= 0) return@LaunchedEffect
        delay(autoHideSeconds * 1000L)
        controlsVisible = false
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

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
            inPip = true
        } else {
            showHud("此系统不支持画中画")
        }
    }

    fun seekBy(deltaSeconds: Int) {
        val target = (player.currentPosition + deltaSeconds * 1000L)
            .coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        player.seekTo(target)
        position = target
        hud = if (deltaSeconds >= 0) "+${deltaSeconds}s" else "${deltaSeconds}s"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                    },
                    onDoubleTap = { offset: Offset ->
                        val zone = when {
                            offset.x < size.width / 3f -> doubleLeft
                            offset.x > size.width * 2f / 3f -> doubleRight
                            else -> doubleCenter
                        }
                        when (zone) {
                            "playPause" -> {
                                if (player.isPlaying) player.pause() else player.play()
                                isPlaying = !isPlaying
                            }
                            "forward" -> seekBy(forwardSeconds)
                            "backward" -> seekBy(-backwardSeconds)
                            else -> controlsVisible = !controlsVisible
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var kind = DragKind.NONE
                var startPosition = 0L
                var startBrightness = 0f
                detectDragGestures(
                    onDragStart = {
                        startPosition = player.currentPosition
                        startBrightness = activity?.window?.attributes?.screenBrightness ?: 0.5f
                        if (startBrightness < 0f) startBrightness = 0.5f
                        kind = DragKind.NONE
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (kind == DragKind.NONE) {
                            kind = if (kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y)) {
                                DragKind.SEEK
                            } else if (change.position.x < size.width / 2f && leftVertical == "brightness") {
                                DragKind.BRIGHTNESS
                            } else if (change.position.x >= size.width / 2f && rightVertical == "volume") {
                                DragKind.VOLUME
                            } else {
                                DragKind.NONE
                            }
                        }
                        when (kind) {
                            DragKind.SEEK -> {
                                val span = if (duration > 0) duration else 120_000L
                                val delta = (dragAmount.x / size.width) * span
                                val target = (player.currentPosition + delta.toLong()).coerceIn(0L, span)
                                player.seekTo(target)
                                position = target
                                showHud("${formatTime(target)} / ${formatTime(duration)}")
                            }
                            DragKind.BRIGHTNESS -> {
                                val next = (startBrightness - dragAmount.y / 600f).coerceIn(0.02f, 1f)
                                if (!useAppBrightness) {
                                    activity?.window?.let { w ->
                                        val lp = w.attributes
                                        lp.screenBrightness = next
                                        w.attributes = lp
                                    }
                                }
                                startBrightness = next
                                showHud("亮度 ${(next * 100).toInt()}%")
                            }
                            DragKind.VOLUME -> {
                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val step = dragAmount.y / -600f * max
                                val next = (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + step)
                                    .toInt().coerceIn(0, max)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                showHud("音量 ${(next * 100 / max)}%")
                            }
                            DragKind.NONE -> Unit
                        }
                    },
                    onDragEnd = { kind = DragKind.NONE; showHud("") }
                )
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setKeepContentOnPlayerReset(true)
                }
            }
        )

        if (danmakuEnabled && danmaku.isNotEmpty() &&
            !(danmakuSkipShort && duration in 1..599_999L)
        ) {
            com.senplay.ui.DanmakuOverlay(
                items = danmaku,
                currentTime = position / 1000.0,
                fontSize = danmakuSize,
                speed = danmakuSpeed,
                area = danmakuArea,
                modifier = Modifier.fillMaxSize()
            )
        }

        hud?.takeIf { it.isNotEmpty() }?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(text, color = Color.White, fontSize = 15.sp)
            }
        }

        if (controlsVisible) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onFinish) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                    }
                    Text(
                        text = name,
                        color = Color.White,
                        fontSize = 15.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (showScreenshot) {
                        IconButton(onClick = { showHud("已保存截图") }) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = "截图", tint = Color.White)
                        }
                    }
                    Box {
                        IconButton(onClick = { moreMenuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = Color.White)
                        }
                        DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                            DropdownMenuItem(text = { Text("截图") }, onClick = { moreMenuOpen = false })
                            DropdownMenuItem(text = { Text("媒体信息") }, onClick = { moreMenuOpen = false })
                            DropdownMenuItem(text = { Text("画面比例") }, onClick = { moreMenuOpen = false })
                            DropdownMenuItem(
                                text = {
                                    Text(if (castTarget == null) "投屏" else "投屏到 ${castTarget?.name}")
                                },
                                onClick = {
                                    moreMenuOpen = false
                                    castOpen = true
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.42f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            formatTime(if (scrubbing) scrubTarget.toLong() else position),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Slider(
                            value = if (duration > 0) {
                                ((if (scrubbing) scrubTarget.toLong() else position).toFloat() / duration).coerceIn(0f, 1f)
                            } else 0f,
                            onValueChange = {
                                scrubbing = true
                                scrubTarget = it * duration
                            },
                            onValueChangeFinished = {
                                player.seekTo(scrubTarget.toLong())
                                position = scrubTarget.toLong()
                                scrubbing = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        )
                        Text(
                            if (timeMode == "remaining") "-" + formatTime((duration - position).coerceAtLeast(0))
                            else formatTime(duration),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onFinish) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                        }
                        if (fastSeekButtons) {
                            IconButton(onClick = { seekBy(-backwardSeconds) }) {
                                Icon(Icons.Filled.FastRewind, contentDescription = "快退", tint = Color.White)
                            }
                        }
                        IconButton(onClick = {
                            if (player.isPlaying) player.pause() else player.play()
                            isPlaying = !isPlaying
                        }) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "播放/暂停",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        if (fastSeekButtons) {
                            IconButton(onClick = { seekBy(forwardSeconds) }) {
                                Icon(Icons.Filled.FastForward, contentDescription = "快进", tint = Color.White)
                            }
                        }
                        IconButton(onClick = { showHud("切换媒体") }) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "下一个", tint = Color.White)
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Box {
                            IconButton(onClick = { speedMenuOpen = true }) {
                                Icon(Icons.Filled.Speed, contentDescription = "倍速", tint = Color.White)
                            }
                            DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                                SPEEDS.forEach { value ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (value == 1f) "1.0x" else value.toString() + "x",
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
                        IconButton(onClick = { showHud("字幕：外挂/内嵌") }) {
                            Icon(Icons.Filled.Subtitles, contentDescription = "字幕", tint = Color.White)
                        }
                        IconButton(onClick = { showHud("音轨选择") }) {
                            Icon(Icons.Filled.Audiotrack, contentDescription = "音轨", tint = Color.White)
                        }
                        IconButton(onClick = { showHud("音量 ${(player.volume * 100).toInt()}%") }) {
                            Icon(Icons.Filled.VolumeUp, contentDescription = "音量", tint = Color.White)
                        }
                        IconButton(onClick = { enterPip() }) {
                            Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "画中画", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

private fun subtitleMime(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "srt" -> MimeTypes.APPLICATION_SUBRIP
    "ass", "ssa" -> MimeTypes.TEXT_SSA
    "vtt" -> MimeTypes.TEXT_VTT
    "ttml" -> MimeTypes.APPLICATION_TTML
    else -> MimeTypes.APPLICATION_SUBRIP
}
