# 偏好生效情况审计

`tools/pref-audit.py` 会列出「已声明、设置页能看到、但没有任何其他代码读取」的偏好键。
判据很窄：仅在设置界面提到不算生效，声明常量也算不上生效，必须有一处真正把值用起来。

保留这些键是为了与参考实现的偏好集合保持一致（目标要求双端键串对齐），但**设置页上存在
不等于功能存在**。这份清单是已知的表面差距，不是待办列表——每一项要么接上，要么在设置页
说明它在当前内核下无对应能力。

## 当前状态

keys with a real read on iOS:     42
keys with a real read on Android: 31
--- declared, shown in settings, read by nothing else (71) ---

## 未生效的键

- `pref.audio.backgroundAudio`（android）
- `pref.audio.firstLanguage`（android）
- `pref.audio.globalDelayMs`（android）
- `pref.danmaku.bottomToFloat`（android）
- `pref.danmaku.convert`（android）
- `pref.danmaku.font`（android）
- `pref.danmaku.keywords`（android）
- `pref.general.defaultModule`（android）
- `pref.general.iCloudSync`（android）
- `pref.general.themeColor`（android）
- `pref.gesture.click`（android）
- `pref.gesture.longPressLeftSpeed`（android）
- `pref.gesture.longPressRightSpeed`（android）
- `pref.library.preferredVersion`（android）
- `pref.library.showCast`（android）
- `pref.library.showCollection`（android）
- `pref.library.showContinue`（android）
- `pref.library.showCount`（android）
- `pref.library.showEpisodeIndicator`（android）
- `pref.library.showMediaInfo`（android）
- `pref.library.showMine`（android）
- `pref.library.showPlayedMark`（android）
- `pref.library.showRating`（android）
- `pref.library.showRemaining`（android）
- `pref.library.showSimilar`（android）
- `pref.library.titleAlignment`（android）
- `pref.list.ellipsis`（android）
- `pref.list.fillThumbnail`（android）
- `pref.list.fullNameOnMenu`（android）
- `pref.list.previewPosition`（android）
- `pref.list.rounded`（android）
- `pref.list.showCount`（android）
- `pref.list.showDuration`（android）
- `pref.list.showFolderTime`（android）
- `pref.list.showFrameRate`（android）
- `pref.list.showHDRTag`（android）
- `pref.list.showNewTag`（android）
- `pref.list.showPreview`（android）
- `pref.list.showProgress`（android）
- `pref.list.showResolution`（android）
- `pref.list.showSize`（android）
- `pref.list.simplifyResolution`（android）
- `pref.play.isoMode`（android）
- `pref.play.pauseAutoLock`（android）
- `pref.play.rememberSpeed`（android）
- `pref.play.useSystemProxy`（android）
- `pref.subtitle.bold`（android）
- `pref.subtitle.firstLanguage`（android）
- `pref.subtitle.font`（android）
- `pref.subtitle.hdrSubtitle`（android）
- `pref.subtitle.ignoreAss`（android）
- `pref.subtitle.ignoreSrt`（android）
- `pref.subtitle.italic`（android）
- `pref.subtitle.loadingRule`（android）
- `pref.subtitle.outlineWidth`（android）
- `pref.subtitle.ratioFix`（android）
- `pref.subtitle.shadowWidth`（android）
- `pref.ui.autoFullScreen`（android）
- `pref.ui.controlBarStyle`（android）
- `pref.ui.matchAspect`（android）
- `pref.ui.showCloudThrottleTip`（android）
- `pref.ui.showOSD`（android）
- `pref.ui.showSkipTip`（android）
- `pref.ui.showStartTitle`（android）
- `pref.video.decodeThreads`（android）
- `pref.video.hdrMetadata`（android）
- `pref.video.hdrVivid`（android）
- `pref.video.screenshotFormat`（android）
- `pref.video.screenshotToClipboard`（android）
- `pref.video.switchBlackout`（android）
- `pref.video.upscaling`（android）

## 说明

- `pref.subtitle.ignoreAss` / `pref.subtitle.ignoreSrt`：ASS 样式由内核的 libass 渲染，
  样式来自文件本身，这一层没有可干预的开关。
- `pref.gesture.longPressLeftSpeed` / `longPressRightSpeed`：区分左右方向键长按需要遥控器
  或外接键盘的重复事件，两端当前都没有处理。屏幕长按已接上 `pref.gesture.longPressSpeed`。
- 列表与媒体库的显示开关（`pref.list.*` / `pref.library.*`）需要列表视图读取，尚未接。
- 语言偏好（`pref.audio.firstLanguage` / `pref.subtitle.firstLanguage`）需要传给内核的字幕
  与音轨选择逻辑，尚未接。
