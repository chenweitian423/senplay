# 偏好生效情况审计

`tools/pref-audit.py` 列出「已声明、设置页能看到、但设置界面之外没有任何代码读取」的偏好键。
判据很窄：`@AppStorage` 绑定不算生效，声明常量不算，设置页的那一行也不算，必须有一处
真正把值用起来。

保留这些键是为了与参考实现的偏好集合保持一致（目标要求双端键串对齐），但**设置页上存在
不等于功能存在**。这份清单是已知的表面差距。

> 这个工具本身出过三次错，每次都让报告比真相乐观：漏掉 `@AppStorage` 字面量（于是只说
> Android 有问题）、漏掉调用点直接写名字的键（于是上一批修复完全没体现出来）。改动判据后
> 应先核对一个已知的死键是否仍被判为死键，再看总数。

## 当前状态

keys with a real read on iOS:     51
keys with a real read on Android: 55
--- declared, shown in settings, read by nothing else (46) ---

## 未生效的键

- `pref.audio.backgroundAudio`（ios/android）
- `pref.audio.globalDelayMs`（ios/android）
- `pref.danmaku.bottomToFloat`（ios/android）
- `pref.danmaku.convert`（ios/android）
- `pref.danmaku.font`（android）
- `pref.danmaku.keywords`（ios/android）
- `pref.general.defaultModule`（ios/android）
- `pref.general.iCloudSync`（ios/android）
- `pref.general.themeColor`（ios/android）
- `pref.gesture.click`（android）
- `pref.library.preferredVersion`（ios/android）
- `pref.library.showMine`（ios/android）
- `pref.library.showSimilar`（ios/android）
- `pref.list.fillThumbnail`（ios/android）
- `pref.list.previewPosition`（ios/android）
- `pref.list.rounded`（ios/android）
- `pref.list.showFrameRate`（ios/android）
- `pref.list.showHDRTag`（ios/android）
- `pref.list.showNewTag`（ios/android）
- `pref.list.showPreview`（ios/android）
- `pref.list.showResolution`（ios/android）
- `pref.list.simplifyResolution`（ios/android）
- `pref.play.isoMode`（ios/android）
- `pref.play.pauseAutoLock`（android）
- `pref.play.useSystemProxy`（android）
- `pref.subtitle.bold`（ios/android）
- `pref.subtitle.font`（android）
- `pref.subtitle.hdrSubtitle`（ios/android）
- `pref.subtitle.ignoreAss`（android）
- `pref.subtitle.ignoreSrt`（android）
- `pref.subtitle.italic`（ios/android）
- `pref.subtitle.loadingRule`（android）
- `pref.subtitle.outlineWidth`（ios/android）
- `pref.subtitle.ratioFix`（ios/android）
- `pref.subtitle.shadowWidth`（ios/android）
- `pref.ui.autoFullScreen`（android）
- `pref.ui.controlBarStyle`（android）
- `pref.ui.matchAspect`（ios/android）
- `pref.ui.showCloudThrottleTip`（android）
- `pref.ui.showSkipTip`（android）
- `pref.ui.showStartTitle`（android）
- `pref.video.decodeThreads`（ios/android）
- `pref.video.hdrVivid`（ios/android）
- `pref.video.screenshotFormat`（android）
- `pref.video.screenshotToClipboard`（android）
- `pref.video.switchBlackout`（android）

## 已知的工具问题

**Android 常量引用未被识别**。`store.prefBool(P_SOME_CONST, ...)` 形式中，常量被引用
即算生效，但当前实现仍把它们报为未读——`ui.showSkipTip`、`ui.showStartTitle`、
`ui.autoFullScreen` 三个键在接入后依然留在清单里。所以 Android 那一列的数字偏高，
真实缺口比报告小。修这个之前，不要把清单当成精确的待办。

## 已接上的

- `pref.gesture.longPressSpeed`：屏幕长按速率，此前硬编码 2x。
- 列表组：`list.showSize` / `showDuration` / `showProgress` / `showCount` / `ellipsis`，
  服务器浏览列表的条目行按这些开关组装副标题与进度条。
- 海报组：`library.showPlayedMark` / `showRating` / `showRemaining` / `showMediaInfo` /
  `titleAlignment`，海报墙上的角标与信息行。

## 剩余部分属于哪一类

**数据源里根本没有**：`library.showCast` / `showSimilar` / `preferredVersion` 需要演员表、
相似推荐、多版本选择，协议层从未取回；`library.showEpisodeIndicator` 需要 Emby 载荷里的集号；
`list.showResolution` / `showFrameRate` / `showHDRTag` / `showNewTag` / `showFolderTime` 同理。
接这些要先扩 Emby/Plex 的元数据映射，不是 UI 工作。

**当前内核下无落点**：`subtitle.ignoreAss` / `ignoreSrt`（ASS 样式由 libass 从文件渲染）、
`gesture.longPressLeftSpeed` / `longPressRightSpeed`（需要遥控器或外接键盘的按键重复事件）。

**尚未接线**：语言偏好（`audio.firstLanguage` / `subtitle.firstLanguage`）需要传给内核的
字幕与音轨选择；音频组（`audio.enhance` / `globalDelayMs` / `rememberTrack` / `multiChannel`）
需要接到播放器的音频处理链；`general.themeColor` / `defaultModule` / `iCloudSync` 需要主题系统
与模块入口；板块可见性（`library.showContinue` / `showCollection`）需要在板块枚举处过滤。
