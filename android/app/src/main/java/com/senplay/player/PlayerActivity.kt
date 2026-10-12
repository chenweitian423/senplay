package com.senplay.player

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName
import com.google.common.util.concurrent.MoreExecutors
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.senplay.core.Http
import com.senplay.core.DanmakuItem
import com.senplay.ui.OsdStats
import com.senplay.core.LibraryStore
import com.senplay.deeplink.SchemeRouter
import com.senplay.ui.SenPlayTheme
import kotlinx.coroutines.delay

class PlayerActivity : ComponentActivity() {

    private data class Media(
        val url: String,
        val name: String,
        val subtitle: String?,
        val userAgent: String?,
        val positionMs: Long,
        val queue: List<Pair<String, String>>,
        val cloudDrive: Boolean
    )

    private var media by mutableStateOf<Media?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val first = read(intent)
        if (first == null) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        media = first

        setContent {
            SenPlayTheme {
                val playing = media
                if (playing != null) {
                    // Keyed on the URL: the queue hands this same instance the next
                    // item, and the player screen has to be rebuilt around it.
                    key(playing.url) {
                        PlayerScreen(
                            url = playing.url,
                            name = playing.name,
                            subtitleUrl = playing.subtitle,
                            userAgent = playing.userAgent,
                            startPositionMs = playing.positionMs,
                            sourceUri = intent.data,
                            queue = playing.queue,
                            cloudDrive = playing.cloudDrive,
                            onFinish = { finish() },
                            onPlayNext = { nextUrl, nextTitle ->
                                startActivity(
                                    intentFor(
                                        this, nextUrl, nextTitle,
                                        userAgent = playing.userAgent,
                                        queue = playing.queue
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    /**
     * `singleTop` makes the next item in the queue reuse this instance instead of
     * creating one. Without this override the new intent would only be stored and
     * nothing else would happen — "自动连播" and the switch-media button would both
     * look like they do nothing at all.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        read(intent)?.let { media = it }
    }

    private fun read(intent: Intent?): Media? {
        val url = intent?.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) return null
        return Media(
            url = url,
            name = intent.getStringExtra(EXTRA_NAME) ?: url.substringAfterLast('/'),
            subtitle = intent.getStringExtra(EXTRA_SUBTITLE),
            userAgent = intent.getStringExtra(EXTRA_USER_AGENT),
            positionMs = intent.getLongExtra(EXTRA_POSITION_MS, 0L),
            queue = intent.getStringArrayListExtra(EXTRA_QUEUE_URLS).orEmpty()
                .zip(intent.getStringArrayListExtra(EXTRA_QUEUE_NAMES).orEmpty()),
            cloudDrive = intent.getBooleanExtra(EXTRA_CLOUD_DRIVE, false)
        )
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_USER_AGENT = "extra_user_agent"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_QUEUE_URLS = "extra_queue_urls"
        const val EXTRA_QUEUE_NAMES = "extra_queue_names"
        const val EXTRA_CLOUD_DRIVE = "extra_cloud_drive"

        fun intentFor(
            context: Context,
            url: String,
            name: String,
            subtitle: String? = null,
            userAgent: String? = null,
            positionMs: Long = 0L,
            queue: List<Pair<String, String>> = emptyList(),
            cloudDrive: Boolean = false
        ): Intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra(EXTRA_URL, url)
            putExtra(EXTRA_NAME, name)
            subtitle?.let { putExtra(EXTRA_SUBTITLE, it) }
            userAgent?.let { putExtra(EXTRA_USER_AGENT, it) }
            putExtra(EXTRA_POSITION_MS, positionMs)
            putExtra(EXTRA_CLOUD_DRIVE, cloudDrive)
            if (queue.isNotEmpty()) {
                putStringArrayListExtra(EXTRA_QUEUE_URLS, ArrayList(queue.map { it.first }))
                putStringArrayListExtra(EXTRA_QUEUE_NAMES, ArrayList(queue.map { it.second }))
            }
        }
    }
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)

private const val P_HIDE_AFTER = "ui.autoHideSeconds"
private const val P_FORWARD = "play.forwardSeconds"
private const val P_BACKWARD = "play.backwardSeconds"
private const val P_PROGRESS_DOT = "ui.progressDot"
private const val P_TIME_MODE = "ui.timeMode"
private const val P_FAST_SEEK_BUTTONS = "ui.fastSeekButtons"
private const val P_SCREENSHOT_BUTTON = "ui.screenshotButton"
// The reference app switches each control-bar button separately
// (kShowPlayerPlayButton, kShowPlayerPipButton, …) rather than offering one
// "show the bar" setting.
private const val P_PLAY_BUTTON = "ui.playButton"
private const val P_ASPECT_RATIO_BUTTON = "ui.aspectRatioButton"
private const val P_PIP_BUTTON = "ui.pipButton"
private const val P_SPEED_BUTTON = "ui.speedButton"
private const val P_DOUBLE_LEFT = "gesture.doubleLeft"
private const val P_DOUBLE_CENTER = "gesture.doubleCenter"
private const val P_DOUBLE_RIGHT = "gesture.doubleRight"
private const val P_LEFT_VERTICAL = "gesture.leftVertical"
private const val P_RIGHT_VERTICAL = "gesture.rightVertical"
private const val P_USE_APP_BRIGHTNESS = "gesture.useAppBrightness"
private const val P_RESUME = "play.resumeFromLast"
private const val P_AUTO_NEXT = "play.autoNext"
private const val P_SKIP_INTRO = "play.skipIntroSeconds"
private const val P_SKIP_OUTRO = "play.skipOutroSeconds"
private const val P_NEXT_COUNTDOWN = "play.autoNextCountdown"
private const val P_DANMAKU_ENABLED = "danmaku.enabled"
private const val P_DANMAKU_SIZE = "danmaku.size"
private const val P_DANMAKU_SPEED = "danmaku.speed"
private const val P_DANMAKU_AREA = "danmaku.area"
private const val P_DANMAKU_SKIP_SHORT = "danmaku.skipShort"
private const val P_DANMAKU_FONT = "danmaku.font"
private const val P_DANMAKU_CONVERT = "danmaku.convert"
private const val P_PRELOAD_MODE = "play.preloadMode"
private const val P_AUDIO_ENHANCE = "audio.enhance"
private const val P_MULTI_CHANNEL = "audio.multiChannel"
private const val P_SHOW_OSD = "ui.showOSD"
private const val P_SHOW_SKIP_TIP = "ui.showSkipTip"
private const val P_SHOW_START_TITLE = "ui.showStartTitle"
private const val P_SHOW_CLOUD_THROTTLE_TIP = "ui.showCloudThrottleTip"
private const val P_AUTO_FULLSCREEN = "ui.autoFullScreen"
private const val P_DANMAKU_KEYWORDS = "danmaku.keywords"
private const val P_DANMAKU_BOTTOM_FLOAT = "danmaku.bottomToFloat"
private const val P_SCREENSHOT_TO_CLIPBOARD = "video.screenshotToClipboard"
private const val P_BACKGROUND_AUDIO = "audio.backgroundAudio"
private const val P_CLICK = "gesture.click"
private const val P_PAUSE_AUTO_LOCK = "play.pauseAutoLock"
private const val P_SCREENSHOT_FORMAT = "video.screenshotFormat"
private const val P_JPEG_QUALITY = "video.jpegQuality"
private const val P_SWITCH_BLACKOUT = "video.switchBlackout"
private const val P_LONG_PRESS_SPEED = "gesture.longPressSpeed"
private const val P_REMEMBER_ZOOM = "gesture.rememberZoomSize"
private const val P_ZOOM = "gesture.zoom"
private const val P_REMEMBER_AUDIO_TRACK = "audio.rememberTrack"
private const val P_REMEMBER_SUBTITLE_TRACK = "subtitle.rememberTrack"
private const val P_SWITCH_MEDIA_BUTTON = "ui.switchMediaButton"
private const val P_SUBTITLE_SCALE = "subtitle.scale"
private const val P_SUBTITLE_MASK = "subtitle.mask"

/**
 * The same three modes the iOS side offers, mapped onto the view's own resize modes
 * rather than a scale transform of our own.
 */
private enum class TrackPanel { SUBTITLE, AUDIO }

private enum class AspectMode(val title: String, val resizeMode: Int) {
    FIT("适应屏幕", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("裁剪全屏", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("拉伸铺满", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL)
}
private const val P_REMEMBER_SPEED = "play.rememberSpeed"
private const val P_LAST_SPEED = "play.lastSpeed"

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
    cloudDrive: Boolean = false,
    onFinish: () -> Unit,
    onPlayNext: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val store = remember { LibraryStore.get(context) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var speedMenuOpen by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    var aspectMenuOpen by remember { mutableStateOf(false) }
    var trackPanel by remember { mutableStateOf<TrackPanel?>(null) }
    var aspectMode by remember { mutableStateOf(AspectMode.FIT) }

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
    var showOSD by remember { mutableStateOf(store.prefBool(P_SHOW_OSD, false)) }
    val rememberSpeed = remember { store.prefBool(P_REMEMBER_SPEED, true) }
    var speed by remember {
        mutableStateOf(
            if (rememberSpeed) store.prefFloat(P_LAST_SPEED, 1f) else 1f
        )
    }
    var osd by remember { mutableStateOf<OsdStats?>(null) }
    var castOpen by remember { mutableStateOf(false) }
    var castTarget by remember { mutableStateOf<com.senplay.net.DlnaDevice?>(null) }
    var countdown by remember { mutableStateOf(0) }
    // Declared here rather than with the other preference reads below: the zoom state
    // is initialised from it, and a local val has to exist before it is used.
    val rememberZoom = remember { store.prefBool(P_REMEMBER_ZOOM, true) }
    val rememberAudioTrack = remember { store.prefBool(P_REMEMBER_AUDIO_TRACK, true) }
    val rememberSubtitleTrack = remember { store.prefBool(P_REMEMBER_SUBTITLE_TRACK, true) }
    val switchMediaButton = remember { store.prefBool(P_SWITCH_MEDIA_BUTTON, true) }
    val subtitleScale = remember { store.prefFloat(P_SUBTITLE_SCALE, 1f) }
    val subtitleMask = remember { store.prefBool(P_SUBTITLE_MASK, false) }
    /** "记住缩放大小" — the pinch scale comes back with the next file. */
    var zoom by remember {
        mutableStateOf(if (rememberZoom) store.prefFloat(P_ZOOM, 1f).coerceIn(0.6f, 3f) else 1f)
    }
    var holdSpeedActive by remember { mutableStateOf(false) }
    var speedBeforeHold by remember { mutableStateOf(1f) }

    val autoHideSeconds = remember { store.prefInt(P_HIDE_AFTER, 3) }
    val forwardSeconds = remember { store.prefInt(P_FORWARD, 15) }
    val backwardSeconds = remember { store.prefInt(P_BACKWARD, 15) }
    val progressDot = remember { store.prefBool(P_PROGRESS_DOT, true) }
    val timeMode = remember { store.prefString(P_TIME_MODE, "total") }
    val fastSeekButtons = remember { store.prefBool(P_FAST_SEEK_BUTTONS, false) }
    val showScreenshot = remember { store.prefBool(P_SCREENSHOT_BUTTON, true) }
    val showPlayButton = remember { store.prefBool(P_PLAY_BUTTON, true) }
    val showAspectRatioButton = remember { store.prefBool(P_ASPECT_RATIO_BUTTON, true) }
    val showPipButton = remember { store.prefBool(P_PIP_BUTTON, true) }
    val showSpeedButton = remember { store.prefBool(P_SPEED_BUTTON, true) }
    val doubleLeft = remember { store.prefString(P_DOUBLE_LEFT, "backward") }
    val doubleCenter = remember { store.prefString(P_DOUBLE_CENTER, "playPause") }
    val doubleRight = remember { store.prefString(P_DOUBLE_RIGHT, "forward") }
    val leftVertical = remember { store.prefString(P_LEFT_VERTICAL, "brightness") }
    val rightVertical = remember { store.prefString(P_RIGHT_VERTICAL, "volume") }
    val useAppBrightness = remember { store.prefBool(P_USE_APP_BRIGHTNESS, false) }
    val resumeFromLast = remember { store.prefBool(P_RESUME, true) }
    val autoNext = remember { store.prefBool(P_AUTO_NEXT, true) }
    val skipIntroSeconds = remember { store.prefInt(P_SKIP_INTRO, 0) }
    val showSkipTip = remember { store.prefBool(P_SHOW_SKIP_TIP, true) }
    val showStartTitle = remember { store.prefBool(P_SHOW_START_TITLE, true) }
    val showCloudThrottleTip = remember { store.prefBool(P_SHOW_CLOUD_THROTTLE_TIP, true) }
    val autoFullScreen = remember { store.prefBool(P_AUTO_FULLSCREEN, false) }
    val bottomToFloat = remember { store.prefBool(P_DANMAKU_BOTTOM_FLOAT, false) }
    val screenshotToClipboard = remember {
        store.prefBool(P_SCREENSHOT_TO_CLIPBOARD, true)
    }
    val backgroundAudio = remember { store.prefBool(P_BACKGROUND_AUDIO, false) }
    val clickAction = remember { store.prefString(P_CLICK, "playPause") }
    val pauseAutoLock = remember { store.prefBool(P_PAUSE_AUTO_LOCK, true) }
    val screenshotFormat = remember { store.prefString(P_SCREENSHOT_FORMAT, "jpg") }
    val jpegQuality = remember { store.prefFloat(P_JPEG_QUALITY, 0.8f) }
    val switchBlackout = remember { store.prefBool(P_SWITCH_BLACKOUT, true) }
    /** "长按屏幕倍速" — the hold rate, as a number the settings screen stores as text. */
    val longPressRate = remember { store.prefString(P_LONG_PRESS_SPEED, "2.0").toFloatOrNull() ?: 2f }
    val floatingControls = remember {
        store.prefString("ui.controlBarStyle", "bottom") == "floating"
    }
    /** Separators are whatever the viewer typed, not a single fixed character. */
    val blockedKeywords = remember {
        store.prefString(P_DANMAKU_KEYWORDS, "")
            .split(',', '，', ';', '；', '\n', '|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
    val skipOutroSeconds = remember { store.prefInt(P_SKIP_OUTRO, 0) }
    val nextCountdown = remember { store.prefInt(P_NEXT_COUNTDOWN, 5) }
    val danmakuEnabled = remember { store.prefBool(P_DANMAKU_ENABLED, true) }
    val danmakuSize = remember { store.prefFloat(P_DANMAKU_SIZE, 25f).toDouble() }
    val danmakuSpeed = remember { store.prefFloat(P_DANMAKU_SPEED, 0.5f).toDouble() }
    val danmakuArea = remember { store.prefFloat(P_DANMAKU_AREA, 0.15f).toDouble() }
    val danmakuSkipShort = remember { store.prefBool(P_DANMAKU_SKIP_SHORT, true) }
    val danmakuFontName = remember { store.prefString(P_DANMAKU_FONT, "SF Pro") }
    val danmakuConvert = remember { store.prefString(P_DANMAKU_CONVERT, "none") }
    val preloadMode = remember { store.prefString(P_PRELOAD_MODE, "none") }
    val audioEnhance = remember { store.prefBool(P_AUDIO_ENHANCE, true) }
    // Reported in the info panel; the actual downmix happens in the decoder graph.
    val multiChannel = remember { store.prefBool(P_MULTI_CHANNEL, false) }
    @Suppress("UNUSED_VARIABLE") val multiChannelNotice = multiChannel

    // The player lives in PlaybackService so playback survives leaving the app; the
    // activity drives it through a controller. The connection is asynchronous, so the
    // UI waits for it and everything below this point sees a non-optional player.
    val connection = remember { mutableStateOf<MediaController?>(null) }
    val ready = connection.value

    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val controller = future.get()
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
                controller.setMediaItem(builder.build())
                controller.prepare()
                val resume = if (startPositionMs > 0L) startPositionMs
                else if (resumeFromLast) store.savedPosition(url) else 0L
                if (resume > 0L) controller.seekTo(resume)
                // "开始播放提示标题" and "网盘限速提示" share the one HUD line, so what
                // the file is served from wins over the title.
                hud = when {
                    cloudDrive && showCloudThrottleTip -> "网盘限速，播放可能不流畅"
                    showStartTitle && name.isNotBlank() -> name
                    else -> null
                }
                // "播放视频自动进入全屏" — on a phone that means hiding the system
                // bars, which is the only full screen this platform has.
                if (autoFullScreen) {
                    activity?.let { host ->
                        WindowCompat.setDecorFitsSystemWindows(host.window, false)
                        WindowInsetsControllerCompat(host.window, host.window.decorView)
                            .hide(WindowInsetsCompat.Type.systemBars())
                    }
                }

                // Restore the rate the viewer last chose, if they asked for that.
                if (rememberSpeed && speed != 1f) {
                    controller.setPlaybackSpeed(speed)
                }
                controller.playWhenReady = true
                connection.value = controller
            },
            MoreExecutors.directExecutor()
        )
        onDispose {
            connection.value = null
            MediaController.releaseFuture(future)
        }
    }

    if (ready == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val player: MediaController = ready

    LaunchedEffect(player, showOSD) {
        while (showOSD) {
            osd = OsdStats(
                videoBitrate = PlaybackStats.videoBitrate,
                audioBitrate = PlaybackStats.audioBitrate,
                dropped = PlaybackStats.droppedFrames,
                width = PlaybackStats.width,
                height = PlaybackStats.height
            )
            delay(1000)
        }
    }

    /** Every rate change goes through here so the stored value matches what plays. */
    fun setSpeed(value: Float) {
        speed = value
        player.setPlaybackSpeed(value)
        if (rememberSpeed) store.setPrefFloat(P_LAST_SPEED, value)
    }

    fun showHud(text: String) {
        hud = text
    }

    fun saveScreenshot(bitmap: android.graphics.Bitmap) {
        // The format switch decides the bytes, so the name, the MIME type and the
        // encoder all follow it rather than being fixed at PNG.
        val png = screenshotFormat == "png"
        val extension = if (png) "png" else "jpg"
        val mime = if (png) "image/png" else "image/jpeg"
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                "senplay-" + System.currentTimeMillis() + "." + extension)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, mime)
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                android.os.Environment.DIRECTORY_PICTURES + "/SenPlay")
        }
        val uri = resolver.insert(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        )
        if (uri == null) {
            showHud("截图失败")
            return
        }
        val format = if (png) android.graphics.Bitmap.CompressFormat.PNG
        else android.graphics.Bitmap.CompressFormat.JPEG
        val quality = if (png) 100 else (jpegQuality * 100f).toInt().coerceIn(1, 100)
        runCatching {
            resolver.openOutputStream(uri)?.use { bitmap.compress(format, quality, it) }
        }.onFailure {
            showHud("截图失败")
            return
        }
        if (screenshotToClipboard) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newUri(resolver, "SenPlay", uri))
        }
        showHud(if (screenshotToClipboard) "已保存截图并复制" else "已保存截图")
    }

    /**
     * The selectable tracks of one kind, with the currently selected index.
     *
     * Media3 reports tracks as groups; a group with `isAdaptiveSupported` is a set of
     * renditions of one track rather than a choice between languages, so only the
     * first track of each group is offered — which is what a viewer picking
     * "Japanese audio" means.
     */
    fun tracksOf(type: Int): List<Pair<String, androidx.media3.common.Tracks.Group>> {
        val out = mutableListOf<Pair<String, androidx.media3.common.Tracks.Group>>()
        for (group in player.currentTracks.groups) {
            if (group.type != type) continue
            val format = group.getTrackFormat(0)
            val language = format.language
            val label = buildString {
                when (language) {
                    null, "", "und" -> append(if (type == C.TRACK_TYPE_TEXT) "字幕" else "音轨")
                    else -> append(language)
                }
                if (format.label != null) append(" · ").append(format.label)
                if (group.isSelected) append("（当前）")
            }
            out += label to group
        }
        return out
    }

    /** Turns subtitles off without touching the audio selection. */
    fun disableSubtitles() {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    /**
     * A name for a track group that survives until the next visit. Media3 gives each
     * format an id when the container carries one; the label and the language are the
     * fallbacks. An empty result means the group cannot be identified, and nothing is
     * stored rather than storing a value that would match some other track.
     *
     * Declared before the two functions that call it: a local function has to exist
     * before it is used.
     */
    fun trackId(group: androidx.media3.common.Tracks.Group): String {
        val format = group.getTrackFormat(0)
        return format.id ?: format.label ?: format.language ?: ""
    }

    fun selectTrack(group: androidx.media3.common.Tracks.Group) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(group.type, false)
            .setOverrideForType(
                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, 0)
            )
            .build()
        // "记住所选字幕/音轨" — the two switches are separate, so a viewer can keep the
        // subtitle choice without pinning the audio.
        val subtitle = group.type == C.TRACK_TYPE_TEXT
        val remember = if (subtitle) rememberSubtitleTrack else rememberAudioTrack
        if (remember) {
            trackId(group).takeIf { it.isNotBlank() }?.let {
                store.rememberTrack(url, if (subtitle) "subtitle" else "audio", it)
            }
        }
    }

    /** Re-selects the tracks this file was left on, when the switches say to. */
    fun restoreTracks(target: androidx.media3.common.Player) {
        if (rememberAudioTrack) {
            val saved = store.rememberedTrack(url, "audio")
            tracksOf(C.TRACK_TYPE_AUDIO)
                .firstOrNull { !saved.isNullOrBlank() && trackId(it.second) == saved }
                ?.let { selectTrack(it.second) }
        }
        if (rememberSubtitleTrack) {
            val saved = store.rememberedTrack(url, "subtitle")
            tracksOf(C.TRACK_TYPE_TEXT)
                .firstOrNull { !saved.isNullOrBlank() && trackId(it.second) == saved }
                ?.let { selectTrack(it.second) }
        }
    }

    // "记住所选字幕/音轨": tracks arrive with the media rather than with the connection,
    // so this waits for them instead of assuming they are ready when the controller
    // is. The delay is bounded — a file with no tracks at all must not leave a
    // coroutine running for the rest of the session.
    LaunchedEffect(player) {
        var waited = 0
        while (waited < 40 && player.currentTracks.groups.isEmpty()) {
            delay(100)
            waited++
        }
        restoreTracks(player)
    }

    /**
     * Copies the current video frame out of the surface.
     *
     * The button used to show "已保存截图" and save nothing, which is worse than an
     * absent button: the viewer walks away believing the frame is in their gallery.
     */
    fun captureFrame(view: PlayerView?) {
        val surface = view?.videoSurfaceView as? SurfaceView
        if (surface == null || surface.width == 0 || surface.height == 0) {
            showHud("截图失败")
            return
        }
        val bitmap = android.graphics.Bitmap.createBitmap(
            surface.width, surface.height, android.graphics.Bitmap.Config.ARGB_8888
        )
        android.view.PixelCopy.request(
            surface, bitmap,
            { result ->
                if (result == android.view.PixelCopy.SUCCESS) {
                    saveScreenshot(bitmap)
                } else {
                    showHud("截图失败")
                }
            },
            android.os.Handler(android.os.Looper.getMainLooper())
        )
    }



    fun advanceToNext(next: Pair<String, String>) {
        upNext = null
        countdown = 0
        store.rememberProgress(url, name, player.currentPosition ?: 0L,
            (player.duration ?: 0L).coerceAtLeast(0L))
        player.stop()
        onPlayNext(next.first, next.second)
    }

    /** "切换媒体按钮" — moves to the next item in the queue, on demand rather than by
     * countdown. Reports when there is nothing to move to instead of doing nothing. */
    fun switchToNextMedia() {
        val index = queue.indexOfFirst { it.first == url }
        val next = queue.getOrNull(index + 1)
        if (index < 0 || next == null) {
            showHud("队列中没有下一个")
            return
        }
        advanceToNext(next)
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
                if (showSkipTip) hud = "已跳过片头 ${skipIntroSeconds} 秒"
            }
            if (skipOutroSeconds > 0 && !didSkipOutro && d > 0 &&
                d - position <= skipOutroSeconds * 1000L
            ) {
                didSkipOutro = true
                if (autoNext) {
                    beginCountdown()
                } else if (showSkipTip) {
                    hud = "已到片尾"
                }
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

    // "暂停后自动锁屏": the screen has to stay awake while playing, and the switch
    // decides what happens once paused — off keeps it awake through the pause too.
    LaunchedEffect(isPlaying, pauseAutoLock) {
        activity?.window?.let { window ->
            if (isPlaying || !pauseAutoLock) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.clearFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
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

    /**
     * The one place a configurable gesture is carried out, so the single tap, the
     * three double-tap zones and the keyboard all agree on what an action means.
     * "fullscreen" has nothing to toggle on a phone, and brightness, volume and speed
     * are drag or hold gestures; both fall back to the control bar, which is where
     * those controls actually live.
     */
    fun performGesture(action: String) {
        when (action) {
            "playPause" -> {
                if (player.isPlaying) player.pause() else player.play()
                isPlaying = !isPlaying
            }
            "forward" -> seekBy(forwardSeconds)
            "backward" -> seekBy(-backwardSeconds)
            "fullscreen" -> showHud("播放器已全屏")
            "toggleControls", "brightness", "volume", "speed" -> {
                controlsVisible = !controlsVisible
            }
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                // "双指缩放画面": only two fingers count, so a one-finger drag still
                // reaches the seek and brightness detector below.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            val change = event.calculateZoom()
                            if (change != 1f) {
                                zoom = (zoom * change).coerceIn(0.6f, 3f)
                                if (rememberZoom) store.setPrefFloat(P_ZOOM, zoom)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { performGesture(clickAction) },
                    onDoubleTap = { offset: Offset ->
                        performGesture(
                            when {
                                offset.x < size.width / 3f -> doubleLeft
                                offset.x > size.width * 2f / 3f -> doubleRight
                                else -> doubleCenter
                            }
                        )
                    },
                    // "长按屏幕倍速": hold to speed up, release to go back. The kernel
                    // rate is set directly rather than through setSpeed, so a hold does
                    // not overwrite the speed "记住播放倍速" remembers.
                    onLongPress = {
                        holdSpeedActive = true
                        speedBeforeHold = player.playbackParameters.speed
                        player.setPlaybackSpeed(longPressRate)
                        hud = "%.2fx".format(longPressRate)
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (holdSpeedActive) {
                            holdSpeedActive = false
                            player.setPlaybackSpeed(speedBeforeHold)
                            hud = null
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
        var playerView by remember { mutableStateOf<PlayerView?>(null) }
        trackPanel?.let { panel ->
            val isSubtitle = panel == TrackPanel.SUBTITLE
            val options = tracksOf(if (isSubtitle) C.TRACK_TYPE_TEXT else C.TRACK_TYPE_AUDIO)
            AlertDialog(
                onDismissRequest = { trackPanel = null },
                title = { Text(if (isSubtitle) "字幕" else "音轨") },
                text = {
                    Column {
                        if (options.isEmpty()) {
                            Text(if (isSubtitle) "此文件没有字幕轨" else "此文件没有音轨")
                        }
                        options.forEach { (label, group) ->
                            TextButton(onClick = {
                                selectTrack(group)
                                trackPanel = null
                            }) { Text(label) }
                        }
                        if (isSubtitle && options.isNotEmpty()) {
                            TextButton(onClick = {
                                disableSubtitles()
                                trackPanel = null
                            }) { Text("关闭字幕") }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // "后台音频" off means the sound stops when the app leaves the foreground.
        // The service keeps playing otherwise, which is what the switch is about.
        DisposableEffect(backgroundAudio) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && !backgroundAudio) {
                    player.pause()
                }
            }
            val owner = (context as? androidx.lifecycle.LifecycleOwner)
            owner?.lifecycle?.addObserver(observer)
            onDispose { owner?.lifecycle?.removeObserver(observer) }
        }

        if (castOpen) {
            com.senplay.ui.CastDialog(
                onDismiss = { castOpen = false },
                onCast = { device ->
                    castOpen = false
                    castTarget = device
                    // The device fetches the media itself, so the local player stops
                    // rather than continuing to decode into an invisible surface.
                    scope.launch {
                        val ok = runCatching {
                            com.senplay.net.Dlna.play(device, url, name)
                        }.getOrDefault(false)
                        if (ok) {
                            player.pause()
                            showHud("已投屏到 ${device.name}")
                        } else {
                            castTarget = null
                            showHud("投屏失败")
                        }
                    }
                }
            )
        }

        if (aspectMenuOpen) {
            AlertDialog(
                onDismissRequest = { aspectMenuOpen = false },
                title = { Text("画面比例") },
                text = {
                    Column {
                        AspectMode.values().forEach { mode ->
                            TextButton(onClick = {
                                aspectMode = mode
                                aspectMenuOpen = false
                            }) {
                                Text((if (aspectMode == mode) "✓ " else "") + mode.title)
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        AndroidView(
            // The pinch scale is applied to the video surface, so the controls and the
            // gesture layer keep their own coordinates.
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = zoom, scaleY = zoom),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    playerView = this
                    resizeMode = aspectMode.resizeMode
                    this.player = player
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    // "切换视频时黑屏" — 开启时不保留上一帧。Media3 的默认值就是
                    // false，这里让它跟着偏好走，关掉开关才看得出区别。
                    setKeepContentOnPlayerReset(!switchBlackout)
                    subtitleView?.let { captions ->
                        // "大小缩放[主]": SubtitleView sizes its text as a fraction of its
                        // own height, so the setting multiplies the platform default.
                        captions.setFractionalTextSize(
                            SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleScale
                        )
                        // "字幕防遮挡" — only the floating bar sits over the picture, so
                        // that is the case the switch describes.
                        captions.setBottomPaddingFraction(
                            if (subtitleMask && floatingControls) 0.20f
                            else SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION
                        )
                    }
                }
            }
        )

        if (showOSD) {
            osd?.let { current ->
                com.senplay.ui.OsdOverlay(
                    stats = current,
                    positionMs = position,
                    bufferedMs = player.bufferedPosition ?: 0L
                )
            }
        }

        if (danmakuEnabled && danmaku.isNotEmpty() &&
            !(danmakuSkipShort && duration in 1..599_999L)
        ) {
            com.senplay.ui.DanmakuOverlay(
                items = danmaku,
                currentTime = position / 1000.0,
                fontSize = danmakuSize,
                speed = danmakuSpeed,
                area = danmakuArea,
                blockedKeywords = blockedKeywords,
                bottomFloats = bottomToFloat,
                fontName = danmakuFontName,
                convert = danmakuConvert,
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
            // "控制栏风格": bottom pins the chrome to the edges, floating insets it
            // with a scrim so it reads as a layer over the video instead.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (floatingControls) {
                            Modifier
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .background(Color.Black.copy(alpha = 0.25f))
                        } else {
                            Modifier
                        }
                    )
            ) {
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
                        IconButton(onClick = { captureFrame(playerView) }) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = "截图", tint = Color.White)
                        }
                    }
                    Box {
                        IconButton(onClick = { moreMenuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = Color.White)
                        }
                        DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("截图") },
                                onClick = { moreMenuOpen = false; captureFrame(playerView) }
                            )
                            // Both of these used to just close the menu. The info panel
                            // reuses the counters the service already collects; the
                            // aspect picker maps onto the view's own resize modes.
                            DropdownMenuItem(
                                text = { Text(if (showOSD) "隐藏媒体信息" else "媒体信息") },
                                onClick = { moreMenuOpen = false; showOSD = !showOSD }
                            )
                            if (showAspectRatioButton) {
                                DropdownMenuItem(
                                    text = { Text("画面比例") },
                                    onClick = { moreMenuOpen = false; aspectMenuOpen = true }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (showOSD) "隐藏状态信息" else "显示状态信息") },
                                onClick = {
                                    moreMenuOpen = false
                                    showOSD = !showOSD
                                    store.setPrefBool(P_SHOW_OSD, showOSD)
                                }
                            )
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
                        if (showPlayButton) {
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
                        }
                        if (fastSeekButtons) {
                            IconButton(onClick = { seekBy(forwardSeconds) }) {
                                Icon(Icons.Filled.FastForward, contentDescription = "快进", tint = Color.White)
                            }
                        }
                        // "切换媒体按钮". The button used to report "切换媒体" and do
                        // nothing at all, which is worse than not drawing it.
                        if (switchMediaButton) {
                            IconButton(onClick = { switchToNextMedia() }) {
                                Icon(
                                    Icons.Filled.SkipNext,
                                    contentDescription = "下一个",
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Box {
                            if (showSpeedButton) {
                                IconButton(onClick = { speedMenuOpen = true }) {
                                    Icon(Icons.Filled.Speed, contentDescription = "倍速", tint = Color.White)
                                }
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
                        IconButton(onClick = { trackPanel = TrackPanel.SUBTITLE }) {
                            Icon(Icons.Filled.Subtitles, contentDescription = "字幕", tint = Color.White)
                        }
                        IconButton(onClick = { trackPanel = TrackPanel.AUDIO }) {
                            Icon(Icons.Filled.Audiotrack, contentDescription = "音轨", tint = Color.White)
                        }
                        IconButton(onClick = { showHud("音量 ${(player.volume * 100).toInt()}%") }) {
                            Icon(Icons.Filled.VolumeUp, contentDescription = "音量", tint = Color.White)
                        }
                        if (showPipButton) {
                            IconButton(onClick = { enterPip() }) {
                                Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "画中画", tint = Color.White)
                            }
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
