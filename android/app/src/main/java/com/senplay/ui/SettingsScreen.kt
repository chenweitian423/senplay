package com.senplay.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.senplay.core.LibraryStore

/**
 * Settings surface mirroring the reference app's ten preference groups.
 * Every control writes a key that LibraryStore namespaces with "pref." — the literal
 * here must not carry the prefix itself, or the stored name becomes pref.pref.*
 */
@Composable
fun SettingsScreen(store: LibraryStore) {
    var ua by remember { mutableStateOf(store.defaultUserAgent) }
    var historyCount by remember { mutableStateOf(store.history().size) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // ---------------------------------------------------------------- 通用
        GroupHeader("通用")

        PrefSwitch(store, "general.iCloudSync", "iCloud同步", "服务器、网盘、IPTV、URL及其播放信息支持同步", true)
        PrefChoice(
            store, "general.defaultModule", "默认启动模块",
            listOf("files" to "文件", "servers" to "服务器", "iptv" to "IPTV", "record" to "记录"),
            "files"
        )
        PrefChoice(
            store, "general.themeColor", "主题颜色",
            listOf(
                "blue" to "蓝", "cyan" to "青", "green" to "绿", "purple" to "紫",
                "orange" to "橙", "red" to "红", "indigo" to "靛"
            ),
            "blue"
        )

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
        PrefSwitch(store, "list.fullNameOnMenu", "菜单中显示完整名称", null, true)
        PrefChoice(
            store, "list.previewPosition", "预览图位置",
            listOf("left" to "左边", "right" to "右边"), "right"
        )

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
        PrefSwitch(store, "ui.showOSD", "显示状态信息", "左上角显示实时码率、帧率与丢帧", false)
        PrefSwitch(store, "play.rememberSpeed", "记住播放倍速", "下次播放沿用上次选择的倍速", true)
        PrefSwitch(store, "ui.showStartTitle", "开始播放提示标题", null, true)
        PrefSwitch(store, "ui.showSkipTip", "即将跳过片头/片尾", null, true)
        PrefSwitch(store, "ui.showCloudThrottleTip", "网盘限速提示", null, true)

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

        // ---------------------------------------------------------------- 音频
        GroupHeader("音频设置")
        PrefSwitch(store, "audio.rememberTrack", "记住所选音轨", null, true)
        PrefChoice(
            store, "audio.firstLanguage", "音频首选语言",
            listOf("none" to "默认", "zh" to "中文", "en" to "英语", "ja" to "日语", "ko" to "韩语"), "none"
        )
        PrefSwitch(store, "audio.enhance", "音频增强", "音量到达 100% 后继续放大音量", true)
        PrefChoice(
            store, "audio.backgroundAudio", "后台播放",
            listOf("on" to "始终", "wifi" to "仅 Wi-Fi", "off" to "关闭"), "on"
        )
        PrefSwitch(
            store, "audio.multiChannel", "多声道模式",
            "部分双声道设备开启此模式后播放多声道影片可能出现只有背景声无人声的情况，关闭即可恢复正常", false
        )
        PrefSlider(store, "audio.globalDelayMs", "全局音频延迟", -2000f, 2000f, 50f, 0f, "ms")

        // ---------------------------------------------------------------- 字幕
        GroupHeader("字幕设置")
        PrefSwitch(store, "subtitle.rememberTrack", "记住所选字幕", null, true)
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

        // ------------------------------------------------------------------ 关于
        GroupHeader("关于")
        Text(
            "SenPlay 是按公开行为契约自行实现的播放器：URL Scheme、媒体服务器接入方式与交互" +
                "取自参考应用的可观察行为，不含其任何代码与素材。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp)
        )
        Text(
            "版本 1.0.0",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
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
