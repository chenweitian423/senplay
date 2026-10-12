package com.senplay.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.senplay.core.LibraryStore

/**
 * Settings hub. Mirrors the reference app's grouping: 数据 / 媒体库配置 /
 * 媒体, each opening a sub-page. Every control writes a key that LibraryStore
 * namespaces with "pref." — the literal here must not carry the prefix itself,
 * or the stored name becomes pref.pref.*
 */
@Composable
fun SettingsScreen(store: LibraryStore) {
    var page by remember { mutableStateOf<String?>(null) }
    val open = page
    if (open == null) {
        SettingsHome(onOpen = { page = it })
    } else {
        SettingsDetail(store, open, onBack = { page = null })
    }
}

@Composable
private fun SettingsHome(onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        GroupHeader("数据")
        SettingsEntry("列表配置", "标题栏、缩略图、文件信息与文件类型筛选") { onOpen("list") }
        SettingsEntry("媒体库", "对 Emby / Jellyfin / Plex / fnOS 服务生效") { onOpen("library") }
        SettingsEntry("数据同步", "默认启动模块、主题、User-Agent 与播放历史") { onOpen("general") }
        GroupHeader("媒体库配置")
        SettingsEntry("播放设置", "断点续播、定位精度、快进快退与预缓存") { onOpen("playback") }
        SettingsEntry("界面偏好", "控制栏元素、自动隐藏与状态信息") { onOpen("interface") }
        SettingsEntry("播放手势", "单击、双击、滑动与长按的绑定") { onOpen("gesture") }
        GroupHeader("媒体")
        SettingsEntry("视频设置", "解码方式、截图格式与画质加工") { onOpen("video") }
        SettingsEntry("音频设置", "音轨记忆、音频增强与输出延迟") { onOpen("audio") }
        SettingsEntry("字幕设置", "颜色、描边、语言与加载规则") { onOpen("subtitle") }
        SettingsEntry("弹幕", "字体、区域、速度与屏蔽词") { onOpen("danmaku") }
        GroupHeader("其他")
        SettingsEntry("关于", "版本与实现来源说明") { onOpen("about") }
    }
}

@Composable
private fun SettingsDetail(store: LibraryStore, page: String, onBack: () -> Unit) {
    var ua by remember { mutableStateOf(store.defaultUserAgent) }
    var historyCount by remember { mutableStateOf(store.history().size) }
    // Read from the package rather than a literal: the row said 1.0.0 while the
    // build ships whatever versionName holds.
    val context = LocalContext.current
    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        SettingsBackBar(title = when (page) {
            "list" -> "列表配置"
            "library" -> "媒体库"
            "general" -> "数据同步"
            "playback" -> "播放设置"
            "interface" -> "界面偏好"
            "gesture" -> "播放手势"
            "video" -> "视频设置"
            "audio" -> "音频设置"
            "subtitle" -> "字幕设置"
            "danmaku" -> "弹幕"
            "about" -> "关于"
            else -> "设置"
        }, onBack = onBack)

        when (page) {
            "list" -> {
        // ---------------------------------------------------------------- 列表
        GroupHeader("列表")
        Text(
            "对 Nas/本地/网盘列表生效",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        PrefSwitch(store, "list.showCount", "标题栏显示文件数量", null, false)
        PrefSwitch(store, "list.showFolderTime", "文件夹项目显示时间", null, false)
        PrefSwitch(store, "list.rounded", "圆角风格", null, true)
        PrefSwitch(store, "list.fillThumbnail", "缩略图铺满", null, true)
        PrefSwitch(store, "list.showPreview", "显示媒体预览图", null, true)
        PrefSwitch(store, "list.showProgress", "显示播放进度", null, true)
        PrefChoice(
            store, "list.ellipsis", "名称过长省略",
            listOf("left" to "左边", "middle" to "中间", "right" to "右边"), "middle"
        )
        PrefSwitch(store, "list.showDuration", "媒体时长", null, true)
        PrefSwitch(store, "list.showSize", "文件大小", null, true)
        PrefSwitch(store, "list.showNewTag", "New标记", null, true)
        PrefSwitch(store, "list.showHDRTag", "HDR标记", null, true)
        PrefSwitch(store, "list.showFrameRate", "帧率", null, true)
        PrefSwitch(store, "list.showResolution", "分辨率", null, true)
        PrefSwitch(store, "list.simplifyResolution", "分辨率简化显示", null, true)
        PrefSwitch(store, "list.fullNameOnMenu", "菜单中显示完整名称", null, false)
        PrefChoice(
            store, "list.previewPosition", "预览图位置",
            listOf("left" to "左边", "right" to "右边"), "right"
        )

        // "文件类型" — the reference app's kFileTypeShowVideo and friends. Folders are
        // never filtered, so a deep tree stays reachable.
        GroupHeader("文件类型")
        PrefSwitch(store, "list.fileType.video", "视频", null, true)
        PrefSwitch(store, "list.fileType.music", "音乐", null, true)
        PrefSwitch(store, "list.fileType.picture", "图片", null, true)
        PrefSwitch(store, "list.fileType.subtitle", "字幕", null, true)
        PrefSwitch(store, "list.fileType.other", "其他", null, true)

            }
            "library" -> {
        // -------------------------------------------------------------- 媒体库
        GroupHeader("媒体库")
        Text(
            "对 Emby/Jellyfin/Plex/fnOS 服务生效",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        PrefSwitch(store, "library.showMine", "显示我的媒体", null, true)
        PrefSwitch(store, "library.showContinue", "显示继续观看", null, true)
        PrefSwitch(store, "library.showCount", "显示媒体数量", null, true)
        PrefSwitch(store, "library.showCollection", "列表中显示合集", null, false)
        PrefSwitch(store, "library.hidePlayedInLatest", "最新中隐藏已播放", null, false)
        PrefSwitch(store, "library.displayPlayButton", "封面上显示播放按钮", null, true)
        PrefSwitch(store, "library.showCarousel", "顶部轮播", null, true)
        PrefChoice(store, "library.preferredVersion", "视频首选版本", listOf("default" to "默认"), "default")
        PrefChoice(
            store, "library.titleAlignment", "名称对齐方式",
            listOf("left" to "左边", "center" to "居中", "right" to "右边"), "left"
        )
        PrefSwitch(store, "library.showRating", "显示社区评分", null, true)
        PrefSwitch(store, "library.showPlayedMark", "显示已播放标记", null, true)
        PrefSwitch(store, "library.showRemaining", "显示剩余播放数量", null, true)
        PrefSwitch(store, "library.showCast", "显示演职人员", null, true)
        PrefSwitch(store, "library.showEpisodeIndicator", "显示选集指示器", null, true)
        PrefSwitch(store, "library.showMediaInfo", "显示媒体信息", null, true)
        PrefSwitch(store, "library.showSimilar", "显示类似作品", null, true)

            }
            "playback" -> {
        // ---------------------------------------------------------------- 播放
        GroupHeader("播放设置")
        PrefSwitch(store, "play.resumeFromLast", "从上次进度播放", null, true)
        PrefSwitch(store, "play.pauseAutoLock", "暂停后自动锁屏", null, true)
        PrefSwitch(store, "play.accurateSeek", "精准定位进度", "可提高调节进度的时间准确度，但可能影响定位的速度", false)
        PrefChoice(
            store, "play.isoMode", "蓝光ISO播放",
            listOf("compatible" to "兼容模式", "fast" to "快速模式"), "compatible"
        )
        PrefChoice(
            store, "play.backwardSeconds", "快退时间",
            listOf("5" to "5 秒", "10" to "10 秒", "15" to "15 秒", "30" to "30 秒", "60" to "60 秒"), "15"
        )
        PrefChoice(
            store, "play.forwardSeconds", "快进时间",
            listOf("5" to "5 秒", "10" to "10 秒", "15" to "15 秒", "30" to "30 秒", "60" to "60 秒"), "15"
        )
        PrefChoice(
            store, "play.preloadMode", "预缓存方式",
            listOf("none" to "不缓存", "last" to "保留最后一次"), "none"
        )
        PrefSwitch(
            store, "play.useSystemProxy", "使用系统代理",
            "可解决代理工具未开启 Tun 模式（增强模式）时，无法代理播放内核的问题", true
        )

            }
            "interface" -> {
        // ---------------------------------------------------------------- 界面
        GroupHeader("界面偏好")
        PrefSwitch(store, "ui.progressDot", "进度条圆点", null, true)
        PrefChoice(store, "ui.timeMode", "进度条时间模式", listOf("total" to "总时长", "remaining" to "剩余时长"), "total")
        PrefChoice(store, "ui.controlBarStyle", "控制栏风格", listOf("bottom" to "底部", "floating" to "悬浮"), "bottom")
        PrefChoice(
            store, "ui.autoHideSeconds", "自动隐藏",
            listOf("0" to "不隐藏", "3" to "3 秒", "5" to "5 秒", "10" to "10 秒"), "3"
        )
        PrefSwitch(store, "ui.autoFullScreen", "播放视频自动进入全屏", null, false)
        PrefSwitch(store, "ui.matchAspect", "窗口比例和视频比例保持一致", null, true)
        PrefSwitch(store, "ui.fastSeekButtons", "快退快进按钮", null, false)
        PrefSwitch(store, "ui.switchMediaButton", "切换媒体按钮", null, true)
        PrefSwitch(store, "ui.screenshotButton", "截图按钮", null, true)
        PrefSwitch(store, "ui.playButton", "播放按钮", null, true)
        PrefSwitch(store, "ui.aspectRatioButton", "画面比例按钮", null, true)
        PrefSwitch(store, "ui.pipButton", "画中画按钮", null, true)
        PrefSwitch(store, "ui.speedButton", "倍速按钮", null, true)
        PrefSwitch(store, "ui.osd.playSpeed", "显示播放速度", null, true)
        PrefSwitch(store, "ui.osd.systemTime", "显示系统时间", null, true)
        PrefSwitch(store, "ui.osd.onlyLandscape", "仅横屏显示", null, false)
        PrefSwitch(store, "ui.osd.fps", "显示帧率", null, true)
        PrefSwitch(store, "ui.osd.networkSpeed", "显示网速", null, true)
        PrefSwitch(store, "ui.showOSD", "显示状态信息", "左上角显示实时码率、帧率与丢帧", false)
        PrefSwitch(store, "play.rememberSpeed", "记住播放倍速", "下次播放沿用上次选择的倍速", true)
        PrefSwitch(store, "ui.showStartTitle", "开始播放提示标题", null, true)
        PrefSwitch(store, "ui.showSkipTip", "即将跳过片头/片尾", null, true)
        PrefSwitch(store, "ui.showCloudThrottleTip", "网盘限速提示", null, true)

            }
            "gesture" -> {
        // ---------------------------------------------------------------- 手势
        GroupHeader("播放手势")
        PrefChoice(
            store, "gesture.click", "单击屏幕",
            GESTURE_OPTIONS, "playPause"
        )
        PrefChoice(store, "gesture.doubleLeft", "双击屏幕左边", GESTURE_OPTIONS, "backward")
        PrefChoice(store, "gesture.doubleCenter", "双击屏幕中间", GESTURE_OPTIONS, "playPause")
        PrefChoice(store, "gesture.doubleRight", "双击屏幕右边", GESTURE_OPTIONS, "forward")
        PrefChoice(store, "gesture.leftVertical", "左边垂直滑动", GESTURE_OPTIONS, "brightness")
        PrefChoice(store, "gesture.rightVertical", "右边垂直滑动", GESTURE_OPTIONS, "volume")
        PrefChoice(
            store, "gesture.longPressSpeed", "长按屏幕倍速",
            listOf("1.5" to "1.50X", "2.0" to "2.00X", "3.0" to "3.00X"), "2.0"
        )
        PrefChoice(
            store, "gesture.longPressLeftSpeed", "长按左方向键倍速",
            listOf(
                "0.25" to "0.25 X", "0.5" to "0.50 X", "0.75" to "0.75 X", "1.0" to "1.00 X",
                "1.5" to "1.50 X", "2.0" to "2.00 X", "3.0" to "3.00 X"
            ),
            "0.5"
        )
        PrefChoice(
            store, "gesture.longPressRightSpeed", "长按右方向键倍速",
            listOf(
                "0.25" to "0.25 X", "0.5" to "0.50 X", "0.75" to "0.75 X", "1.0" to "1.00 X",
                "1.5" to "1.50 X", "2.0" to "2.00 X", "3.0" to "3.00 X"
            ),
            "2.0"
        )
        PrefSwitch(store, "gesture.rememberZoomSize", "记住缩放大小", null, true)
        PrefSwitch(
            store, "gesture.useAppBrightness", "播放器独立亮度",
            "开启后只调整播放器画面亮度，不改变系统亮度", false
        )
        Text(
            "双指缩放画面 · 长按屏幕临时倍速 · 水平拖动调节进度",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp)
        )

            }
            "video" -> {
        // ---------------------------------------------------------------- 视频
        GroupHeader("视频设置")
        PrefSwitch(
            store, "video.hardwareDecode", "首选硬解码 (HW/GPU)",
            "硬解码：高效省电，基本不发热，能否硬解取决于你的设备及系统。软解码：兼容性强，解码稳定", true
        )
        PrefChoice(
            store, "video.decodeThreads", "软件解码线程",
            listOf("1" to "1", "2" to "2", "4" to "4", "8" to "8", "16" to "16"), "8"
        )
        PrefSwitch(store, "video.switchBlackout", "切换视频时黑屏", "开启后切换视频时画面不残留", true)
        PrefChoice(store, "video.screenshotFormat", "截图格式", listOf("jpg" to "jpg", "png" to "png"), "jpg")
        PrefSwitch(store, "video.screenshotToClipboard", "自动复制到剪贴板", null, true)
        PrefSwitch(store, "video.hdrMetadata", "HDR元数据", null, true)
        PrefSwitch(store, "video.hdrVivid", "HDR Vivid", null, true)
        PrefSwitch(store, "video.upscaling", "AI超分辨率", "适用于 1080P 及以下视频；需要播放内核支持", false)
        PrefSlider(store, "video.jpegQuality", "JPEG 质量", 0.5f, 1f, 0.05f, 0.8f, "%")

            }
            "audio" -> {
        // ---------------------------------------------------------------- 音频
        GroupHeader("音频设置")
        PrefSwitch(store, "audio.rememberTrack", "记住所选音轨", null, true)
        PrefChoice(
            store, "audio.firstLanguage", "音频首选语言",
            listOf("none" to "默认", "zh" to "中文", "en" to "英语", "ja" to "日语", "ko" to "韩语"), "none"
        )
        PrefSwitch(store, "audio.enhance", "音频增强", "音量到达 100% 后继续放大音量", true)
        PrefSlider(store, "audio.enhancePercent", "音频增强倍数", 100f, 400f, 10f, 200f, "%")
        PrefChoice(
            store, "audio.backgroundAudio", "后台播放",
            listOf("on" to "始终", "wifi" to "仅 Wi-Fi", "off" to "关闭"), "on"
        )
        PrefSwitch(
            store, "audio.multiChannel", "多声道模式",
            "部分双声道设备开启此模式后播放多声道影片可能出现只有背景声无人声的情况，关闭即可恢复正常", false
        )
        PrefSlider(store, "audio.globalDelayMs", "全局音频延迟", -2000f, 2000f, 50f, 0f, "ms")

            }
            "subtitle" -> {
        // ---------------------------------------------------------------- 字幕
        GroupHeader("字幕设置")
        PrefSwitch(store, "subtitle.rememberTrack", "记住所选字幕", null, true)
        PrefChoice(
            store, "subtitle.textColor", "字幕文字颜色",
            listOf(
                "#FFFFFF" to "白色", "#FFFF00" to "黄色", "#00FFFF" to "青色",
                "#00FF00" to "绿色", "#FFA500" to "橙色", "#FF69B4" to "粉色"
            ),
            "#FFFFFF"
        )
        PrefChoice(
            store, "subtitle.bgColor", "字幕背景颜色",
            listOf(
                "#00000000" to "无背景", "#8C000000" to "半透明黑",
                "#000000" to "纯黑", "#FFFFFFFF" to "白色"
            ),
            "#00000000"
        )
        PrefSlider(store, "subtitle.opacity", "字幕不透明度", 0.2f, 1f, 0.05f, 1f, "")
        PrefSlider(store, "subtitle.verticalOffset", "字幕垂直偏移", -60f, 200f, 2f, 0f, " pt")
        PrefChoice(
            store, "subtitle.firstLanguage", "字幕首选语言",
            listOf(
                "none" to "默认", "zh-Hans" to "简体中文", "zh-Hant" to "繁体中文",
                "en" to "英语", "ja" to "日语"
            ),
            "none"
        )
        PrefChoice(
            store, "subtitle.loadingRule", "外挂字幕加载规则",
            listOf("sameName" to "同文件夹同名字幕", "all" to "同文件夹所有字幕", "manual" to "仅手动选择"),
            "sameName"
        )
        PrefSwitch(store, "subtitle.mask", "字幕防遮挡", "仅对悬浮控制栏生效", false)
        PrefSwitch(store, "subtitle.ratioFix", "比例修正", null, true)
        PrefSwitch(store, "subtitle.hdrSubtitle", "HDR字幕", null, true)
        PrefSwitch(
            store, "subtitle.ignoreAss", "忽略ASS字幕样式",
            "忽略字幕样式后将不再使用 libass 进行渲染", false
        )
        PrefSwitch(store, "subtitle.ignoreSrt", "忽略SRT字幕样式", null, false)
        PrefText(store, "subtitle.font", "字体", "SF Pro")
        PrefSwitch(store, "subtitle.bold", "加粗", null, false)
        PrefSwitch(store, "subtitle.italic", "斜体", null, false)
        PrefSlider(store, "subtitle.scale", "大小缩放[主]", 0.5f, 2.5f, 0.05f, 1f, "x")
        PrefSlider(store, "subtitle.outlineWidth", "描边宽度", 0f, 5f, 0.5f, 1f, "px")
        PrefSlider(store, "subtitle.shadowWidth", "阴影宽度", 0f, 5f, 0.5f, 1f, "px")

            }
            "danmaku" -> {
        // ---------------------------------------------------------------- 弹幕
        GroupHeader("弹幕")
        PrefSwitch(store, "danmaku.enabled", "启用弹幕", "关闭后不显示弹幕按钮并且不加载弹幕", true)
        PrefText(store, "danmaku.font", "弹幕字体", "SF Pro")
        PrefChoice(
            store, "danmaku.convert", "中文简繁转换",
            listOf("none" to "不转换", "hans" to "转为简体", "hant" to "转为繁体"), "none"
        )
        PrefSwitch(store, "danmaku.bottomToFloat", "底部弹幕转浮动弹幕", null, false)
        PrefSwitch(store, "danmaku.skipShort", "短视频不自动加载弹幕", "视频时长低于 10 分钟", true)
        PrefText(store, "danmaku.keywords", "弹幕屏蔽词（以空格分隔）", "")
        PrefSlider(store, "danmaku.area", "显示区域", 0.05f, 0.5f, 0.01f, 0.15f, "x")
        PrefSlider(store, "danmaku.size", "字号", 12f, 48f, 1f, 25f, "")
        PrefSlider(store, "danmaku.speed", "速度", 0.1f, 1f, 0.05f, 0.5f, "x")

            }
            "general" -> {
        // ---------------------------------------------------------------- 通用
        GroupHeader("通用")

        PrefSwitch(store, "general.iCloudSync", "iCloud同步", "服务器、网盘、IPTV、URL及其播放信息支持同步", true)
        PrefChoice(
            store, "general.defaultModule", "默认启动模块",
            listOf("files" to "文件", "servers" to "服务器", "iptv" to "IPTV", "record" to "记录"),
            "files"
        )
        PrefSwitch(store, "ui.showRecordModule", "显示记录模块", null, true)
        PrefChoice(
            store, "general.themeColor", "主题颜色",
            listOf(
                "blue" to "蓝", "cyan" to "青", "green" to "绿", "purple" to "紫",
                "orange" to "橙", "red" to "红", "indigo" to "靛"
            ),
            "blue"
        )
        // ------------------------------------------------------------ 播放与历史
        GroupHeader("播放与历史")
        ListItem(
            headlineContent = { Text("记住播放进度") },
            supportingContent = { Text("退出播放器时记录位置，下次从断点继续") },
            trailingContent = {
                var remember by remember { mutableStateOf(store.rememberProgressEnabled) }
                Switch(checked = remember, onCheckedChange = {
                    remember = it
                    store.rememberProgressEnabled = it
                })
            }
        )

        HorizontalDivider()

        ListItem(
            headlineContent = { Text("默认 User-Agent") },
            supportingContent = { Text("供媒体服务器识别客户端；留空使用内置默认值") },
            modifier = Modifier.padding(top = 8.dp)
        )
        OutlinedTextField(
            value = ua,
            onValueChange = {
                ua = it
                store.defaultUserAgent = it.ifBlank { LibraryStore.DEFAULT_UA }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
            label = { Text("User-Agent") }
        )

        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))

        ListItem(
            headlineContent = { Text("播放历史") },
            supportingContent = { Text("$historyCount 条记录") },
            trailingContent = {
                TextButton(onClick = {
                    store.clearHistory()
                    historyCount = 0
                }) { Text("清空") }
            }
        )

        HorizontalDivider()

        ListItem(
            headlineContent = { Text("恢复默认设置") },
            supportingContent = { Text("只重置播放偏好，不影响服务器、URL 列表与播放历史") },
            trailingContent = {
                TextButton(onClick = { store.clearPreferences() }) { Text("恢复") }
            }
        )

            }
            "about" -> {
        // ------------------------------------------------------------------ 关于
        GroupHeader("关于")
        Text(
            "SenPlay 是按公开行为契约自行实现的播放器：URL Scheme、媒体服务器接入方式与交互" +
                "取自参考应用的可观察行为，不含其任何代码与素材。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp)
        )
        Text(
            "版本 $appVersion",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
            }
        }
    }
}

private val GESTURE_OPTIONS = listOf(
    "none" to "无",
    "playPause" to "播放/暂停",
    "fullscreen" to "全屏",
    "toggleControls" to "显示/隐藏控制栏",
    "forward" to "快进",
    "backward" to "快退",
    "brightness" to "调节亮度",
    "volume" to "调节音量",
    "speed" to "倍速"
)

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun PrefSwitch(
    store: LibraryStore,
    key: String,
    title: String,
    subtitle: String? = null,
    default: Boolean
) {
    var value by remember { mutableStateOf(store.prefBool(key, default)) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { text -> { Text(text) } },
        trailingContent = {
            Switch(
                checked = value,
                onCheckedChange = {
                    value = it
                    store.setPrefBool(key, it)
                }
            )
        }
    )
}

@Composable
private fun PrefChoice(
    store: LibraryStore,
    key: String,
    title: String,
    options: List<Pair<String, String>>,
    default: String
) {
    var value by remember { mutableStateOf(store.prefString(key, default)) }
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Box {
                TextButton(onClick = { open = true }) {
                    Text(options.firstOrNull { it.first == value }?.second ?: default)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    options.forEach { (optionKey, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                value = optionKey
                                store.setPrefString(key, optionKey)
                                open = false
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun PrefText(store: LibraryStore, key: String, title: String, default: String) {
    var value by remember { mutableStateOf(store.prefString(key, default)) }
    ListItem(headlineContent = { Text(title) })
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            store.setPrefString(key, it)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        singleLine = true,
        label = { Text(title) }
    )
}

@Composable
private fun PrefSlider(
    store: LibraryStore,
    key: String,
    title: String,
    min: Float,
    max: Float,
    step: Float,
    default: Float,
    unit: String
) {
    var value by remember { mutableStateOf(store.prefFloat(key, default)) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (unit.isEmpty()) value.toInt().toString() else "${value.toInt()}$unit",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        Slider(
            value = value,
            onValueChange = {
                value = it
                store.setPrefFloat(key, it)
            },
            valueRange = min..max,
            steps = if (step > 0f) ((max - min) / step).toInt() - 1 else 0
        )
    }
}

/** One row of the hub: what it is, what it covers, and where it leads. */
@Composable
private fun SettingsEntry(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/** The way back out of a sub-page, and which page you are on. */
@Composable
private fun SettingsBackBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) { Text("‹ 设置") }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}
