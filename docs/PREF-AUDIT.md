# 偏好生效情况审计

`tools/pref-audit.py` 列出「已声明、设置页能看到、但设置界面之外没有任何代码读取」的偏好键。
判据很窄：`@AppStorage` 绑定不算生效，声明常量不算，设置页的那一行也不算，必须有一处
真正把值用起来。

保留这些键是为了与参考实现的偏好集合保持一致（目标要求双端键串对齐），但**设置页上存在
不等于功能存在**。

> **这个工具出过四次错，每次都让报告偏离现实**：漏掉 `@AppStorage` 字面量（于是只说
> Android 有问题）；漏掉调用点直接写键名的键（于是修复不体现）；`scan()` 返回的键不带
> `pref.` 前缀而 iOS 侧带（于是整个列表必然全标 `android`，一整列缺失的键就此隐身数轮）；
> 漏掉 `UserDefaults` 的直接读取（于是 `list.rounded` 这类明显接好的键被报成未读）。
> 改判据后应先拿一个已知的死键和一个已知的活键各验一次，再看总数。

## 当前状态

keys with a real read on iOS:     70
keys with a real read on Android: 54
--- declared, shown in settings, read by nothing else (48) ---

## 未生效的键

- `pref.audio.backgroundAudio`（ios/android）
- `pref.audio.globalDelayMs`（ios/android）
- `pref.danmaku.bottomToFloat`（ios/android）
- `pref.danmaku.convert`（ios/android）
- `pref.danmaku.font`（ios/android）
- `pref.danmaku.keywords`（ios/android）
- `pref.general.defaultModule`（ios/android）
- `pref.general.iCloudSync`（ios/android）
- `pref.general.themeColor`（ios/android）
- `pref.gesture.click`（ios/android）
- `pref.library.preferredVersion`（ios/android）
- `pref.library.showMine`（ios/android）
- `pref.library.showSimilar`（ios/android）
- `pref.list.fillThumbnail`（ios/android）
- `pref.list.previewPosition`（ios/android）
- `pref.list.showFrameRate`（ios/android）
- `pref.list.showHDRTag`（ios/android）
- `pref.list.showNewTag`（ios/android）
- `pref.list.showPreview`（ios/android）
- `pref.list.showResolution`（ios/android）
- `pref.list.simplifyResolution`（ios/android）
- `pref.play.isoMode`（ios/android）
- `pref.play.pauseAutoLock`（ios/android）
- `pref.play.useSystemProxy`（ios/android）
- `pref.subtitle.bold`（ios/android）
- `pref.subtitle.font`（ios/android）
- `pref.subtitle.hdrSubtitle`（ios/android）
- `pref.subtitle.ignoreAss`（ios/android）
- `pref.subtitle.ignoreSrt`（ios/android）
- `pref.subtitle.italic`（ios/android）
- `pref.subtitle.loadingRule`（ios/android）
- `pref.subtitle.outlineWidth`（ios/android）
- `pref.subtitle.ratioFix`（ios/android）
- `pref.subtitle.shadowWidth`（ios/android）
- `pref.ui.controlBarStyle`（ios/android）
- `pref.ui.matchAspect`（ios/android）
- `pref.ui.showCloudThrottleTip`（ios/android）
- `pref.video.decodeThreads`（ios/android）
- `pref.video.hdrVivid`（ios/android）
- `pref.video.screenshotFormat`（ios/android）
- `pref.video.screenshotToClipboard`（ios/android）
- `pref.video.switchBlackout`（ios/android）

## 已接上的

- `gesture.longPressSpeed`：屏幕长按速率，此前硬编码 2x。
- 列表组：`list.showSize` / `showDuration` / `showProgress` / `showCount`（标题栏）/ `ellipsis`，
  以及 `list.showFolderTime`（文件夹时间）。
- 海报组：`library.showPlayedMark` / `showRating` / `showMediaInfo` / `titleAlignment` /
  `showEpisodeIndicator` / `showCast`。
- 板块与计数：`library.showContinue` / `showCollection` / `showCount` / `showRemaining`
  （剩余条目数，取自 Emby 的 `UnplayedItemCount`）。
- 播放界面（Android）：`ui.showSkipTip` / `showStartTitle` / `autoFullScreen`。
- 语言：`audio.firstLanguage` / `subtitle.firstLanguage` 已传给内核的轨道选择。
- 倍速：`play.rememberSpeed` / `play.lastSpeed`。

## 剩余部分属于哪一类

**协议层没有的数据**：`library.showSimilar`（相似推荐接口）、`library.preferredVersion`
（多版本枚举）、`list.showResolution` / `showFrameRate` / `showHDRTag` / `showNewTag`
（Emby 的 `MediaSources` 里可能有，尚未映射）。

**当前内核没有落点**：`subtitle.ignoreAss` / `ignoreSrt`（ASS 样式由 libass 从文件渲染）、
`gesture.longPressLeftSpeed` / `longPressRightSpeed`（需要遥控器或外接键盘的按键重复事件）、
`subtitle.bold` / `italic` / `outlineWidth` / `shadowWidth` / `ratioFix` / `hdrSubtitle`
（同属字幕渲染，libass 从文件取样式）。

**含义未从取证材料确定，不予猜测**：`library.showMine`（"显示我的媒体"）、
`preferredVersion`（"视频首选版本"，下拉默认"默认"）。这两条的文案推不出行为，
而 `showRemaining` 已经证明听起来合理的名字不是证据。

**尚未接线，但可做**：`video.decodeThreads` / `upscaling` / `hdrMetadata` / `hdrVivid`、
`play.isoMode` / `useSystemProxy` / `pauseAutoLock`、`ui.controlBarStyle` /
`showCloudThrottleTip`、`general.themeColor` / `defaultModule` / `iCloudSync`、
音频组剩余的 `globalDelayMs`、弹幕组的 `font` / `keywords` / `convert` / `bottomToFloat`。
