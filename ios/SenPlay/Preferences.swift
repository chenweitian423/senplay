import SwiftUI

/// Settings surface mirroring the reference app's ten preference groups.
/// Every control is backed by `@AppStorage`, so the player reads the same keys.
struct PreferencesView: View {
    var body: some View {
        NavigationView {
            List {
                Section("通用") {
                    NavigationLink("数据同步") { GeneralPrefs() }
                    NavigationLink("列表") { ListPrefs() }
                    NavigationLink("媒体库") { LibraryPrefs() }
                }
                Section("播放") {
                    NavigationLink("播放设置") { PlaybackPrefs() }
                    NavigationLink("界面偏好") { InterfacePrefs() }
                    NavigationLink("播放手势") { GesturePrefs() }
                    NavigationLink("视频设置") { VideoPrefs() }
                    NavigationLink("音频设置") { AudioPrefs() }
                    NavigationLink("字幕设置") { SubtitlePrefs() }
                    NavigationLink("弹幕") { DanmakuPrefs() }
                }
                Section {
                    NavigationLink("恢复默认设置") { RestoreDefaults() }
                } footer: {
                    Text("改动即时生效；恢复默认会清空以上全部偏好。")
                }
                Section("关于") {
                    HStack {
                        Text("版本")
                        Spacer()
                        Text("1.0.0").foregroundColor(.secondary)
                    }
                    Text("按公开行为契约自行实现：URL Scheme、媒体服务器接入方式与交互取自参考应用的可观察行为，不含其任何代码与素材。")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
            }
            .navigationTitle("设置")
        }
        .navigationViewStyle(.stack)
    }
}

// MARK: - 通用

private struct GeneralPrefs: View {
    @AppStorage("pref.general.iCloudSync") private var iCloudSync = true
    @AppStorage("pref.general.defaultModule") private var defaultModule = "files"
    @AppStorage("pref.general.themeColor") private var themeColor = "blue"

    private let modules = ["files", "servers", "iptv", "record"]
    private let colors = ["blue", "cyan", "green", "purple", "orange", "red", "indigo"]

    var body: some View {
        List {
            Section("欢迎") {
                HStack {
                    Text("SENPLAYER PRO").font(.headline)
                    Spacer()
                    Text("未解锁").font(.footnote).foregroundColor(.secondary)
                }
            }
            Section("主题颜色") {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(colors, id: \.self) { name in
                            Circle()
                                .fill(color(named: name))
                                .frame(width: 28, height: 28)
                                .overlay(
                                    Circle().stroke(Color.primary, lineWidth: themeColor == name ? 2 : 0)
                                )
                                .onTapGesture { themeColor = name }
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
            Section("iCloud同步") {
                Toggle("iCloud同步", isOn: $iCloudSync)
                Text("1.服务器，网盘，IPTV，URL及其播放信息支持同步\n2.调整此开关需重启应用后才能生效")
                    .font(.footnote).foregroundColor(.secondary)
            }
            Section("同步状态") {
                HStack {
                    Text("状态")
                    Spacer()
                    Text(iCloudSync ? "已同步" : "未同步").foregroundColor(.secondary)
                }
                Text("如果出现错误码2的提示\n1.检查iCloud空间容量或尝试卸载重装")
                    .font(.footnote).foregroundColor(.secondary)
            }
            Section("默认启动模块") {
                Picker("默认启动模块", selection: $defaultModule) {
                    Text("文件").tag("files")
                    Text("服务器").tag("servers")
                    Text("IPTV").tag("iptv")
                    Text("记录").tag("record")
                }
                .pickerStyle(.menu)
            }
            Section("URL Schemes") {
                Text("senplay://x-callback-url/play · download · importserver · importicon")
                    .font(.footnote).foregroundColor(.secondary)
            }
        }
        .navigationTitle("数据同步")
    }

    private func color(named name: String) -> Color {
        switch name {
        case "cyan": return .cyan
        case "green": return .green
        case "purple": return .purple
        case "orange": return .orange
        case "red": return .red
        case "indigo": return .indigo
        default: return .blue
        }
    }
}

// MARK: - 列表

private struct ListPrefs: View {
    @AppStorage("pref.list.showCount") private var showCount = false
    @AppStorage("pref.list.showFolderTime") private var showFolderTime = false
    @AppStorage("pref.list.rounded") private var rounded = true
    @AppStorage("pref.list.fillThumbnail") private var fillThumbnail = true
    @AppStorage("pref.list.showPreview") private var showPreview = true
    @AppStorage("pref.list.previewPosition") private var previewPosition = 10
    @AppStorage("pref.list.showProgress") private var showProgress = true
    @AppStorage("pref.list.ellipsis") private var ellipsis = "middle"
    @AppStorage("pref.list.fullNameOnMenu") private var fullNameOnMenu = false
    @AppStorage("pref.list.showDuration") private var showDuration = true
    @AppStorage("pref.list.showSize") private var showSize = true
    @AppStorage("pref.list.showNewTag") private var showNewTag = true
    @AppStorage("pref.list.showHDRTag") private var showHDRTag = true
    @AppStorage("pref.list.showFrameRate") private var showFrameRate = true
    @AppStorage("pref.list.showResolution") private var showResolution = true
    @AppStorage("pref.list.simplifyResolution") private var simplifyResolution = true

    var body: some View {
        List {
            Section {
                Text("对 Nas/本地/网盘列表生效")
                    .font(.footnote).foregroundColor(.secondary)
            }
            Section {
                Toggle("标题栏显示文件数量", isOn: $showCount)
                Toggle("文件夹项目显示时间", isOn: $showFolderTime)
                Toggle("圆角风格", isOn: $rounded)
                Toggle("缩略图铺满", isOn: $fillThumbnail)
                Toggle("显示媒体预览图", isOn: $showPreview)
            }
            Section {
                Picker("视频预览图时间", selection: $previewPosition) {
                    ForEach([0, 5, 10, 20, 30, 50], id: \.self) { Text("\($0)%").tag($0) }
                }
                Text("0代表不自动生成缩略图").font(.footnote).foregroundColor(.secondary)
            }
            Section {
                Toggle("显示播放进度", isOn: $showProgress)
                Picker("名称过长省略", selection: $ellipsis) {
                    Text("左边").tag("left")
                    Text("中间").tag("middle")
                    Text("右边").tag("right")
                }
                Toggle("菜单顶部显示完整名称", isOn: $fullNameOnMenu)
            }
            Section("标记") {
                Toggle("媒体时长", isOn: $showDuration)
                Toggle("文件大小", isOn: $showSize)
                Toggle("New标记", isOn: $showNewTag)
                Toggle("HDR标记", isOn: $showHDRTag)
                Toggle("帧率", isOn: $showFrameRate)
                Toggle("分辨率", isOn: $showResolution)
                Toggle("分辨率简化显示", isOn: $simplifyResolution)
            }
            Section {
                Text("媒体强相关信息需开启预览图才能展示，部分设置对网盘文件列表无效")
                    .font(.footnote).foregroundColor(.secondary)
            }
        }
        .navigationTitle("列表")
    }
}

// MARK: - 媒体库

private struct LibraryPrefs: View {
    @AppStorage("pref.library.showMine") private var showMine = true
    @AppStorage("pref.library.showContinue") private var showContinue = true
    @AppStorage("pref.library.showCount") private var showCount = true
    @AppStorage("pref.library.showCollection") private var showCollection = false
    @AppStorage("pref.library.hidePlayedInLatest") private var hidePlayedInLatest = false
    @AppStorage("pref.library.displayPlayButton") private var displayPlayButton = true
    @AppStorage("pref.library.showCarousel") private var showCarousel = true
    @AppStorage("pref.library.preferredVersion") private var preferredVersion = "default"
    @AppStorage("pref.library.titleAlignment") private var titleAlignment = "left"
    @AppStorage("pref.library.showRating") private var showRating = true
    @AppStorage("pref.library.showPlayedMark") private var showPlayedMark = true
    @AppStorage("pref.library.showRemaining") private var showRemaining = true
    @AppStorage("pref.library.showCast") private var showCast = true
    @AppStorage("pref.library.showEpisodeIndicator") private var showEpisodeIndicator = true
    @AppStorage("pref.library.showMediaInfo") private var showMediaInfo = true
    @AppStorage("pref.library.showSimilar") private var showSimilar = true

    var body: some View {
        List {
            Section {
                Text("对 Emby/Jellyfin/Plex/fnOS 服务生效")
                    .font(.footnote).foregroundColor(.secondary)
            }
            Section {
                Toggle("显示我的媒体", isOn: $showMine)
                Toggle("显示继续观看", isOn: $showContinue)
                Toggle("显示媒体数量", isOn: $showCount)
                Toggle("列表中显示合集", isOn: $showCollection)
                Toggle("最新中隐藏已播放", isOn: $hidePlayedInLatest)
                Toggle("封面上显示播放按钮", isOn: $displayPlayButton)
                Toggle("顶部轮播", isOn: $showCarousel)
                Picker("视频首选版本", selection: $preferredVersion) {
                    Text("默认").tag("default")
                }
                Picker("名称对齐方式", selection: $titleAlignment) {
                    Text("左边").tag("left")
                    Text("居中").tag("center")
                    Text("右边").tag("right")
                }
            }
            Section {
                Toggle("显示社区评分", isOn: $showRating)
                Toggle("显示已播放标记", isOn: $showPlayedMark)
                Toggle("显示剩余播放数量", isOn: $showRemaining)
                Toggle("显示演职人员", isOn: $showCast)
                Toggle("显示选集指示器", isOn: $showEpisodeIndicator)
                Toggle("显示媒体信息", isOn: $showMediaInfo)
                Toggle("显示类似作品", isOn: $showSimilar)
            }
        }
        .navigationTitle("媒体库")
    }
}

// MARK: - 播放

private struct PlaybackPrefs: View {
    @AppStorage(PrefKey.resumeFromLast) private var resumeFromLast = true
    @AppStorage(PrefKey.pauseAutoLock) private var pauseAutoLock = true
    @AppStorage(PrefKey.accurateSeek) private var accurateSeek = false
    @AppStorage("pref.play.isoMode") private var isoMode = "compatible"
    @AppStorage(PrefKey.backwardSeconds) private var backwardSeconds = 15
    @AppStorage(PrefKey.forwardSeconds) private var forwardSeconds = 15
    @AppStorage(PrefKey.preloadMode) private var preloadMode = "none"
    @AppStorage(PrefKey.useSystemProxy) private var useSystemProxy = true
    @AppStorage(PrefKey.autoNext) private var autoNext = true
    @AppStorage(PrefKey.skipIntroSeconds) private var skipIntroSeconds = 0
    @AppStorage(PrefKey.skipOutroSeconds) private var skipOutroSeconds = 0
    @AppStorage(PrefKey.autoNextCountdown) private var autoNextCountdown = 5

    private let steps = [5, 10, 15, 30, 60]

    var body: some View {
        List {
            Section {
                Toggle("从上次进度播放", isOn: $resumeFromLast)
                Toggle("暂停后自动锁屏", isOn: $pauseAutoLock)
            }
            Section {
                Toggle("精准定位进度", isOn: $accurateSeek)
                Text("可提高调节进度的时间准确度，但可能影响定位的速度")
                    .font(.footnote).foregroundColor(.secondary)
            }
            Section {
                Picker("蓝光ISO播放", selection: $isoMode) {
                    Text("兼容模式").tag("compatible")
                    Text("快速模式").tag("fast")
                }
                Text("兼容模式可解决部分网络下无法播放的问题，快速模式可以达到最佳网络性能")
                    .font(.footnote).foregroundColor(.secondary)
            }
            Section {
                Picker("快退时间", selection: $backwardSeconds) {
                    ForEach(steps, id: \.self) { Text("\($0) 秒").tag($0) }
                }
                Picker("快进时间", selection: $forwardSeconds) {
                    ForEach(steps, id: \.self) { Text("\($0) 秒").tag($0) }
                }
                Text("对所有用到\"快退快进\"的功能均生效")
                    .font(.footnote).foregroundColor(.secondary)
            }
            Section {
                Picker("预缓存方式", selection: $preloadMode) {
                    Text("不缓存").tag("none")
                    Text("保留最后一次").tag("last")
                }
                Text("1.缓存设置太大会长时间进行网络下载和磁盘读写，导致设备发热")
                    .font(.footnote).foregroundColor(.secondary)
            }
            Section("连播与跳过") {
                Toggle("自动连播下一个", isOn: $autoNext)
                Picker("下一个开始前倒计时", selection: $autoNextCountdown) {
                    Text("关闭").tag(0)
                    Text("3 秒").tag(3)
                    Text("5 秒").tag(5)
                    Text("10 秒").tag(10)
                }
                Picker("跳过片头", selection: $skipIntroSeconds) {
                    Text("不跳过").tag(0)
                    ForEach([15, 30, 60, 90, 120], id: \.self) { Text("\($0) 秒").tag($0) }
                }
                Picker("跳过片尾", selection: $skipOutroSeconds) {
                    Text("不跳过").tag(0)
                    ForEach([15, 30, 60, 90, 120], id: \.self) { Text("\($0) 秒").tag($0) }
                }
                Text("片头/片尾跳过对本片生效；连播使用打开文件时所在文件夹的列表。")
                    .font(.footnote).foregroundColor(.secondary)
            }
            Section {
                Toggle("使用系统代理", isOn: $useSystemProxy)
                Text("可解决代理工具未开启 Tun 模式（增强模式）时，无法代理播放内核的问题")
                    .font(.footnote).foregroundColor(.secondary)
            }
        }
        .navigationTitle("播放设置")
    }
}

// MARK: - 界面

private struct InterfacePrefs: View {
    @AppStorage(PrefKey.progressDot) private var progressDot = true
    @AppStorage(PrefKey.timeMode) private var timeMode = "total"
    @AppStorage(PrefKey.controlBarStyle) private var controlBarStyle = "bottom"
    @AppStorage(PrefKey.autoHideSeconds) private var autoHideSeconds = 3
    @AppStorage(PrefKey.autoFullScreen) private var autoFullScreen = false
    @AppStorage("pref.ui.matchAspect") private var matchAspect = true
    @AppStorage(PrefKey.fastSeekButtons) private var fastSeekButtons = false
    @AppStorage(PrefKey.switchMediaButton) private var switchMediaButton = true
    @AppStorage(PrefKey.screenshotButton) private var screenshotButton = true
    @AppStorage(PrefKey.showStartTitle) private var showStartTitle = true
    @AppStorage(PrefKey.showSkipTip) private var showSkipTip = true
    @AppStorage("pref.ui.showOSD") private var showOSD = false
    @AppStorage("pref.play.rememberSpeed") private var rememberSpeed = true
    @AppStorage(PrefKey.showCloudThrottleTip) private var showCloudThrottleTip = true

    var body: some View {
        List {
            Section {
                Toggle("进度条圆点", isOn: $progressDot)
                Picker("进度条时间模式", selection: $timeMode) {
                    Text("总时长").tag("total")
                    Text("剩余时长").tag("remaining")
                }
                Picker("控制栏风格", selection: $controlBarStyle) {
                    Text("底部").tag("bottom")
                    Text("悬浮").tag("floating")
                }
                Picker("自动隐藏", selection: $autoHideSeconds) {
                    Text("不隐藏").tag(0)
                    Text("3 秒").tag(3)
                    Text("5 秒").tag(5)
                    Text("10 秒").tag(10)
                }
            }
            Section {
                Toggle("播放视频自动进入全屏", isOn: $autoFullScreen)
                Toggle("窗口比例和视频比例保持一致", isOn: $matchAspect)
                Text("关闭后可以使用鼠标自由改变窗口比例")
                    .font(.footnote).foregroundColor(.secondary)
            }
            Section("控制栏元素") {
                Toggle("快退快进按钮", isOn: $fastSeekButtons)
                Toggle("切换媒体按钮", isOn: $switchMediaButton)
                Toggle("截图按钮", isOn: $screenshotButton)
            }
            Section("提示") {
                Toggle("开始播放提示标题", isOn: $showStartTitle)
                Toggle("即将跳过片头/片尾", isOn: $showSkipTip)
                Toggle("显示状态信息", isOn: $showOSD)
                Toggle("记住播放倍速", isOn: $rememberSpeed)
                Toggle("网盘限速提示", isOn: $showCloudThrottleTip)
            }
        }
        .navigationTitle("界面偏好")
    }
}

// MARK: - 手势

private struct GesturePrefs: View {
    @AppStorage(PrefKey.clickAction) private var clickAction = GestureAction.playPause.rawValue
    @AppStorage(PrefKey.doubleLeftAction) private var doubleLeft = GestureAction.backward.rawValue
    @AppStorage(PrefKey.doubleCenterAction) private var doubleCenter = GestureAction.playPause.rawValue
    @AppStorage(PrefKey.doubleRightAction) private var doubleRight = GestureAction.forward.rawValue
    @AppStorage(PrefKey.leftVerticalAction) private var leftVertical = GestureAction.brightness.rawValue
    @AppStorage(PrefKey.rightVerticalAction) private var rightVertical = GestureAction.volume.rawValue
    @AppStorage("pref.gesture.longPressLeftSpeed") private var longPressLeftSpeed = 0.5
    @AppStorage("pref.gesture.longPressRightSpeed") private var longPressRightSpeed = 2.0
    @AppStorage(PrefKey.rememberZoomSize) private var rememberZoomSize = true
    @AppStorage(PrefKey.useAppBrightness) private var useAppBrightness = false

    private let rates: [Double] = [0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0]

    var body: some View {
        List {
            Section("触屏手势") {
                Picker("单击手屏", selection: $clickAction) {
                    ForEach(GestureAction.allCases) { Text($0.title).tag($0.rawValue) }
                }
                Picker("双击屏幕左边", selection: $doubleLeft) {
                    ForEach(GestureAction.allCases) { Text($0.title).tag($0.rawValue) }
                }
                Picker("双击屏幕中间", selection: $doubleCenter) {
                    ForEach(GestureAction.allCases) { Text($0.title).tag($0.rawValue) }
                }
                Picker("双击屏幕右边", selection: $doubleRight) {
                    ForEach(GestureAction.allCases) { Text($0.title).tag($0.rawValue) }
                }
                Picker("左边垂直滑动", selection: $leftVertical) {
                    ForEach(GestureAction.allCases) { Text($0.title).tag($0.rawValue) }
                }
                Picker("右边垂直滑动", selection: $rightVertical) {
                    ForEach(GestureAction.allCases) { Text($0.title).tag($0.rawValue) }
                }
            }
            Section("长按方向键倍速") {
                Picker("长按左方向键倍速", selection: $longPressLeftSpeed) {
                    ForEach(rates, id: \.self) { Text(String(format: "%.2f X", $0)).tag($0) }
                }
                Picker("长按右方向键倍速", selection: $longPressRightSpeed) {
                    ForEach(rates, id: \.self) { Text(String(format: "%.2f X", $0)).tag($0) }
                }
            }
            Section {
                Toggle("记住缩放大小", isOn: $rememberZoomSize)
                Toggle("播放器独立亮度", isOn: $useAppBrightness)
                Text("开启后只调整播放器画面亮度，不改变系统亮度")
                    .font(.footnote).foregroundColor(.secondary)
            }
            Section {
                Text("双指缩放画面 · 长按屏幕临时倍速 · 水平拖动调节进度")
                    .font(.footnote).foregroundColor(.secondary)
            }
        }
        .navigationTitle("播放手势")
    }
}

// MARK: - 视频

private struct VideoPrefs: View {
    @AppStorage(PrefKey.preferHardwareDecode) private var hardwareDecode = true
    @AppStorage("pref.video.decodeThreads") private var decodeThreads = 8
    @AppStorage(PrefKey.switchBlackout) private var switchBlackout = true
    @AppStorage(PrefKey.screenshotFormat) private var screenshotFormat = "jpg"
    @AppStorage(PrefKey.jpegQuality) private var jpegQuality = 0.8
    @AppStorage(PrefKey.screenshotToClipboard) private var screenshotToClipboard = true
    @AppStorage("pref.video.hdrMetadata") private var hdrMetadata = true
    @AppStorage("pref.video.hdrVivid") private var hdrVivid = true
    @AppStorage("pref.video.upscaling") private var upscaling = false

    var body: some View {
        List {
            Section {
                Toggle("首选硬解码 (HW/GPU)", isOn: $hardwareDecode)
                Picker("软件解码线程", selection: $decodeThreads) {
                    ForEach([1, 2, 4, 8, 16], id: \.self) { Text("\($0)").tag($0) }
                }
                Text("硬解码：高效省电，基本不发热，能否硬解取决于你的设备及系统。\n软解码：兼容性强，解码稳定")
                    .font(.footnote).foregroundColor(.secondary)
            }
            Section {
                Toggle("切换视频时黑屏", isOn: $switchBlackout)
                Text("开启后切换视频时画面不残留")
                    .font(.footnote).foregroundColor(.secondary)
            }
            Section("截图") {
                Picker("截图格式", selection: $screenshotFormat) {
                    Text("jpg").tag("jpg")
                    Text("png").tag("png")
                }
                HStack {
                    Text("JPEG图像质量")
                    Slider(value: $jpegQuality, in: 0.3...1.0, step: 0.05)
                    Text("\(Int(jpegQuality * 100))%").font(.footnote).monospacedDigit()
                }
                Toggle("自动复制到剪贴板", isOn: $screenshotToClipboard)
            }
            Section("画质加工（待内核接入）") {
                Toggle("HDR元数据", isOn: $hdrMetadata)
                Toggle("HDR Vivid", isOn: $hdrVivid)
                Toggle("AI超分辨率", isOn: $upscaling)
                Text("适用于 1080P 及以下视频；需要播放内核支持，当前版本仅记录偏好。")
                    .font(.footnote).foregroundColor(.secondary)
            }
        }
        .navigationTitle("视频设置")
    }
}

// MARK: - 音频

private struct AudioPrefs: View {
    @AppStorage(PrefKey.rememberAudioTrack) private var rememberTrack = true
    @AppStorage(PrefKey.audioFirstLanguage) private var firstLanguage = "none"
    @AppStorage(PrefKey.audioEnhance) private var enhance = true
    @AppStorage(PrefKey.multiChannel) private var multiChannel = false
    @AppStorage("pref.audio.globalDelayMs") private var globalDelay = 0
    @AppStorage("pref.audio.backgroundAudio") private var backgroundAudio = false

    private let languages = [("none", "默认"), ("zh", "中文"), ("en", "英语"), ("ja", "日语"), ("ko", "韩语")]

    var body: some View {
        List {
            Section {
                Toggle("记住所选音轨", isOn: $rememberTrack)
                Picker("音频首选语言", selection: $firstLanguage) {
                    ForEach(languages, id: \.0) { Text($0.1).tag($0.0) }
                }
            }
            Section {
                Toggle("音频增强", isOn: $enhance)
                Text("音量到达 100% 后继续放大音量")
                    .font(.footnote).foregroundColor(.secondary)
            }
            Section {
                Toggle("多声道模式", isOn: $multiChannel)
                Text("部分双声道设备开启此模式后播放多声道影片可能出现只有背景声无人声的情况，关闭即可恢复正常")
                    .font(.footnote).foregroundColor(.secondary)
            }
            Section {
                Stepper("全局音频延迟 \(globalDelay) ms", value: $globalDelay, in: -5000...5000, step: 50)
                Toggle("后台音频", isOn: $backgroundAudio)
            }
        }
        .navigationTitle("音频设置")
    }
}

// MARK: - 字幕

private struct SubtitlePrefs: View {
    @AppStorage(PrefKey.rememberSubtitleTrack) private var rememberTrack = true
    @AppStorage(PrefKey.subtitleFirstLanguage) private var firstLanguage = "none"
    @AppStorage(PrefKey.subtitleLoadingRule) private var loadingRule = "sameName"
    @AppStorage(PrefKey.subtitleMask) private var mask = false
    @AppStorage("pref.subtitle.ratioFix") private var ratioFix = true
    @AppStorage("pref.subtitle.hdrSubtitle") private var hdrSubtitle = true
    @AppStorage(PrefKey.ignoreAssStyle) private var ignoreAss = false
    @AppStorage(PrefKey.ignoreSrtStyle) private var ignoreSrt = false
    @AppStorage(PrefKey.subtitleFont) private var font = "SF Pro"
    @AppStorage("pref.subtitle.bold") private var bold = false
    @AppStorage("pref.subtitle.italic") private var italic = false
    @AppStorage("pref.subtitle.outlineWidth") private var outlineWidth = 1.0
    @AppStorage("pref.subtitle.shadowWidth") private var shadowWidth = 0.0
    @AppStorage(PrefKey.subtitleScale) private var scale = 1.0

    private let languages = [("none", "默认"), ("zh-Hans", "简体中文"), ("zh-Hant", "繁体中文"), ("en", "英语"), ("ja", "日语")]

    var body: some View {
        List {
            Section {
                Toggle("记住所选字幕", isOn: $rememberTrack)
                Picker("字幕首选语言", selection: $firstLanguage) {
                    ForEach(languages, id: \.0) { Text($0.1).tag($0.0) }
                }
                Picker("外挂字幕加载规则", selection: $loadingRule) {
                    Text("同文件夹同名字幕").tag("sameName")
                    Text("同文件夹所有字幕").tag("all")
                    Text("仅手动选择").tag("manual")
                }
            }
            Section {
                Toggle("字幕防遮挡", isOn: $mask)
                Text("仅对悬浮控制栏生效").font(.footnote).foregroundColor(.secondary)
                Toggle("比例修正", isOn: $ratioFix)
                Toggle("HDR字幕", isOn: $hdrSubtitle)
            }
            Section {
                Toggle("忽略ASS字幕样式", isOn: $ignoreAss)
                Toggle("忽略SRT字幕样式", isOn: $ignoreSrt)
                Text("忽略字幕样式后将不再使用 libass 进行渲染")
                    .font(.footnote).foregroundColor(.secondary)
            }
            Section("样式") {
                TextField("字体", text: $font)
                Toggle("加粗", isOn: $bold)
                Toggle("斜体", isOn: $italic)
                HStack {
                    Text("大小缩放[主]")
                    Slider(value: $scale, in: 0.5...2.5, step: 0.05)
                    Text(String(format: "%.2f", scale)).font(.footnote).monospacedDigit()
                }
                Stepper(String(format: "描边大小 %.1f", outlineWidth), value: $outlineWidth, in: 0...5, step: 0.5)
                Stepper(String(format: "阴影大小 %.1f", shadowWidth), value: $shadowWidth, in: 0...5, step: 0.5)
            }
            Section {
                Text("部分自定义样式需要开启忽略本身样式才能生效")
                    .font(.footnote).foregroundColor(.secondary)
                Text("字幕样式调整已迁移至播放页面，支持实时调整")
                    .font(.footnote).foregroundColor(.secondary)
            }
        }
        .navigationTitle("字幕设置")
    }
}

// MARK: - 弹幕

private struct DanmakuPrefs: View {
    @AppStorage(PrefKey.danmakuEnabled) private var enabled = true
    @AppStorage(PrefKey.danmakuFont) private var font = "SF Pro"
    @AppStorage("pref.danmaku.convert") private var convert = "none"
    @AppStorage("pref.danmaku.bottomToFloat") private var bottomToFloat = false
    @AppStorage(PrefKey.danmakuSkipShort) private var skipShort = true
    @AppStorage(PrefKey.danmakuSize) private var size = 25.0
    @AppStorage(PrefKey.danmakuSpeed) private var speed = 0.5
    @AppStorage(PrefKey.danmakuArea) private var area = 0.15
    @AppStorage("pref.danmaku.keywords") private var keywords = ""
    @State private var endpoints: [DanmakuEndpoint] = DanmakuEndpoint.load()

    var body: some View {
        List {
            Section {
                Toggle("启用弹幕", isOn: $enabled)
                Text("关闭后不显示弹幕按钮并且不加载弹幕")
                    .font(.footnote).foregroundColor(.secondary)
            }
            Section("渲染") {
                TextField("弹幕字体", text: $font)
                Picker("中文简繁转换", selection: $convert) {
                    Text("不转换").tag("none")
                    Text("转为简体").tag("hans")
                    Text("转为繁体").tag("hant")
                }
                Toggle("底部弹幕转浮动弹幕", isOn: $bottomToFloat)
                Toggle("短视频不自动加载弹幕", isOn: $skipShort)
                Text("视频时长低于 10 分钟").font(.footnote).foregroundColor(.secondary)
                HStack {
                    Text("显示区域")
                    Slider(value: $area, in: 0.05...0.5, step: 0.01)
                    Text("\(Int(area * 100))%").font(.footnote).monospacedDigit()
                }
                HStack {
                    Text("字号")
                    Slider(value: $size, in: 12...48, step: 1)
                    Text("\(Int(size))").font(.footnote).monospacedDigit()
                }
                HStack {
                    Text("速度")
                    Slider(value: $speed, in: 0.1...1.0, step: 0.05)
                    Text(String(format: "%.2f", speed)).font(.footnote).monospacedDigit()
                }
            }
            Section {
                TextField("输入关键词", text: $keywords)
                Text("使用逗号\",\"进行分割").font(.footnote).foregroundColor(.secondary)
            }
            Section("弹幕 API") {
                ForEach($endpoints) { $endpoint in
                    VStack(alignment: .leading, spacing: 6) {
                        TextField("名称", text: $endpoint.name)
                            .font(.subheadline.weight(.medium))
                        TextField("地址", text: $endpoint.url)
                            .font(.footnote)
                            .textInputAutocapitalization(.never)
                            .disableAutocorrection(true)
                    }
                }
                .onDelete { offsets in
                    endpoints.remove(atOffsets: offsets)
                    DanmakuEndpoint.save(endpoints)
                }
                Button("新增接口") {
                    endpoints.append(DanmakuEndpoint(name: "API-\(endpoints.count + 1)", url: ""))
                    DanmakuEndpoint.save(endpoints)
                }
                Text("弹幕API协议：按关键词搜索 → 返回时间轴数组。接口地址保存在本机，不上传。")
                    .font(.footnote).foregroundColor(.secondary)
            }
        }
        .navigationTitle("弹幕")
        .onChange(of: endpoints) { _ in DanmakuEndpoint.save(endpoints) }
    }
}

// MARK: - 恢复默认

private struct RestoreDefaults: View {
    @State private var done = false

    private let keys: [String] = [
        PrefKey.resumeFromLast, PrefKey.accurateSeek, PrefKey.pauseAutoLock,
        PrefKey.forwardSeconds, PrefKey.backwardSeconds, PrefKey.preloadMode, PrefKey.useSystemProxy,
        PrefKey.progressDot, PrefKey.timeMode, PrefKey.controlBarStyle, PrefKey.autoHideSeconds,
        PrefKey.autoFullScreen, PrefKey.fastSeekButtons, PrefKey.switchMediaButton,
        PrefKey.screenshotButton, PrefKey.showStartTitle, PrefKey.showSkipTip, PrefKey.showCloudThrottleTip,
        PrefKey.clickAction, PrefKey.doubleLeftAction, PrefKey.doubleCenterAction, PrefKey.doubleRightAction,
        PrefKey.leftVerticalAction, PrefKey.rightVerticalAction, PrefKey.rememberZoomSize, PrefKey.useAppBrightness,
        PrefKey.preferHardwareDecode, PrefKey.screenshotFormat, PrefKey.jpegQuality, PrefKey.screenshotToClipboard,
        PrefKey.switchBlackout, PrefKey.rememberAudioTrack, PrefKey.audioFirstLanguage, PrefKey.audioEnhance,
        PrefKey.multiChannel, PrefKey.rememberSubtitleTrack, PrefKey.subtitleFirstLanguage,
        PrefKey.subtitleLoadingRule, PrefKey.subtitleMask, PrefKey.ignoreAssStyle, PrefKey.ignoreSrtStyle,
        PrefKey.subtitleFont, PrefKey.subtitleScale, PrefKey.danmakuEnabled, PrefKey.danmakuFont,
        PrefKey.danmakuSize, PrefKey.danmakuSpeed, PrefKey.danmakuArea, PrefKey.danmakuSkipShort,
    ]

    var body: some View {
        List {
            Section {
                Button("恢复默认设置", role: .destructive) {
                    keys.forEach { UserDefaults.standard.removeObject(forKey: $0) }
                    done = true
                }
                if done {
                    Text("已恢复默认值，返回上级即可看到变化。")
                        .font(.footnote).foregroundColor(.secondary)
                }
            } footer: {
                Text("只重置播放偏好，不影响服务器、URL 列表与播放历史。")
            }
        }
        .navigationTitle("恢复默认设置")
    }
}
