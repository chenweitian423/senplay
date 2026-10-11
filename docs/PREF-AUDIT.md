# 偏好生效情况审计

`tools/pref-audit.py` 列出「已声明、设置页能看到、但设置界面之外没有任何代码读取」的偏好键。
判据很窄：`@AppStorage` 绑定不算生效，声明常量不算，设置页的那一行也不算，必须有一处
真正把值用起来。

保留这些键是为了与参考实现的偏好集合保持一致（目标要求双端键串对齐），但**设置页上存在
不等于功能存在**。这份清单是已知的表面差距。

## 当前状态

keys with a real read on iOS:     42
keys with a real read on Android: 31
--- declared, shown in settings, read by nothing else (72) ---

## 未生效的键

- `pref.audio.backgroundAudio`（ios/android）
- `pref.audio.firstLanguage`（android）
- `pref.audio.globalDelayMs`（ios/android）
- `pref.danmaku.bottomToFloat`（ios/android）
- `pref.danmaku.convert`（ios/android）
- `pref.danmaku.font`（android）
- `pref.danmaku.keywords`（ios/android）
- `pref.general.defaultModule`（ios/android）
- `pref.general.iCloudSync`（ios/android）
- `pref.general.themeColor`（ios/android）
- `pref.gesture.click`（android）
- `pref.gesture.longPressLeftSpeed`（ios/android）
- `pref.gesture.longPressRightSpeed`（ios/android）
- `pref.library.preferredVersion`（ios/android）
- `pref.library.showCast`（ios/android）
- `pref.library.showCollection`（ios/android）
- `pref.library.showContinue`（ios/android）
- `pref.library.showCount`（ios/android）
- `pref.library.showEpisodeIndicator`（ios/android）
- `pref.library.showMediaInfo`（ios/android）
- `pref.library.showMine`（ios/android）
- `pref.library.showPlayedMark`（ios/android）
- `pref.library.showRating`（ios/android）
- `pref.library.showRemaining`（ios/android）
- `pref.library.showSimilar`（ios/android）
- `pref.library.titleAlignment`（ios/android）
- `pref.list.ellipsis`（ios/android）
- `pref.list.fillThumbnail`（ios/android）
- `pref.list.fullNameOnMenu`（ios/android）
- `pref.list.previewPosition`（ios/android）
- `pref.list.rounded`（ios/android）
- `pref.list.showCount`（ios/android）
- `pref.list.showDuration`（ios/android）
- `pref.list.showFolderTime`（ios/android）
- `pref.list.showFrameRate`（ios/android）
- `pref.list.showHDRTag`（ios/android）
- `pref.list.showNewTag`（ios/android）
- `pref.list.showPreview`（ios/android）
- `pref.list.showProgress`（ios/android）
- `pref.list.showResolution`（ios/android）
- `pref.list.showSize`（ios/android）
- `pref.list.simplifyResolution`（ios/android）
- `pref.play.isoMode`（ios/android）
- `pref.play.lastSpeed`（ios）
- `pref.play.pauseAutoLock`（android）
- `pref.play.rememberSpeed`（ios/android）
- `pref.play.useSystemProxy`（android）
- `pref.subtitle.bold`（ios/android）
- `pref.subtitle.firstLanguage`（android）
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
- `pref.ui.showOSD`（ios/android）
- `pref.ui.showSkipTip`（android）
- `pref.ui.showStartTitle`（android）
- `pref.video.decodeThreads`（ios/android）
- `pref.video.hdrMetadata`（ios/android）
- `pref.video.hdrVivid`（ios/android）
- `pref.video.screenshotFormat`（android）
- `pref.video.screenshotToClipboard`（android）
- `pref.video.switchBlackout`（android）
- `pref.video.upscaling`（ios/android）

## 已接上的

- `pref.gesture.longPressSpeed`：屏幕长按速率，此前硬编码 2x。
- `pref.list.showSize` / `showDuration` / `showProgress` / `showCount` / `ellipsis`：
  服务器浏览列表的条目行现在按这些开关组装副标题与进度条，双端一致。

## 说明

- **一部分键不是「没接 UI」，而是数据源里根本没有**。`MediaEntry` 携带 size、时长、
  播放位置与看完状态，但没有分辨率、帧率、HDR 标记、添加时间——协议层从未取回它们。
  要接 `pref.list.showResolution` 这类开关，得先扩展 Emby/Plex 的元数据映射。
- `pref.subtitle.ignoreAss` / `ignoreSrt`：ASS 样式由内核的 libass 渲染，样式来自文件本身，
  这一层没有可干预的开关。
- `pref.gesture.longPressLeftSpeed` / `longPressRightSpeed`：区分左右方向键长按需要遥控器或
  外接键盘的重复事件，两端当前都没有处理。
- 语言偏好（`pref.audio.firstLanguage` / `pref.subtitle.firstLanguage`）需要传给内核的
  字幕与音轨选择逻辑，尚未接。
