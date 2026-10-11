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

**语义已从取证材料确定，但当前结构下无落点**：

- `library.showMine`（"显示我的媒体"）。取证字符串里有 `kEmbyShowMyMedia`，指向
  该开关控制 Emby 的用户视图入口（`/Users/{id}/Views`）。本实现的服务器浏览页
  **顶层就是这一层视图**，没有单独的"我的媒体"入口可隐藏，所以开关无处安放。
  要接它得先让导航多一层——那是结构改动，不是接线改动。
- `library.preferredVersion`（"视频首选版本"，下拉默认"默认"）。取证字符串里有
  `/SenPlayer/Model/MediaVersion.swift`，说明它在一部影片的多个版本之间选择。
  Emby 的 `MediaSources` 确实是数组，每个源有 `Name` 和码率，所以数据在；但
  **下拉的选项集没有出现在字符串里**（可能是代码内枚举或按服务器动态生成）。
  选项不明就不猜——`showRemaining` 已经证明按名字推断行为会错。

**尚未接线，但可做**：`video.decodeThreads` / `upscaling` / `hdrMetadata` / `hdrVivid`、
`play.isoMode` / `useSystemProxy` / `pauseAutoLock`、`ui.controlBarStyle` /
`showCloudThrottleTip`、`general.themeColor` / `defaultModule` / `iCloudSync`、
音频组剩余的 `globalDelayMs`、弹幕组的 `font` / `keywords` / `convert` / `bottomToFloat`。

## 取证材料里的一组键名（round 30）

`main-strings.json` 里有一族以 `k` 开头的设置键名，把若干「含义不明」的条目一次定了性：

| 取证键名 | 对应的设置项 | 结论 |
|---|---|---|
| `kEmbyShowMyMedia` | 显示我的媒体 | Emby 用户视图入口；本实现顶层就是它，无落点 |
| `kEmbyShowRecommended` | 显示类似作品 | 确实是 Emby 的推荐功能，需要详情页 + `/Similar` 接口 |
| `kEmbyShowCarousel` | （未实现的轮播） | 参考实现有而本实现没有的板块 |
| `kEmbyHidePlayedInLatest` | （未实现的过滤） | 同上 |
| `kEmbyDisplayPlayButton` | （未实现的按钮） | 同上 |
| `kEmbyResumeShowEpisodeImage` | （未实现的封面策略） | 同上 |
| `kEmbyTextAlignment` | 名称对齐方式 | 已接 |
| `kSubtitleLoadRule` | 外挂字幕加载规则 | 已接（round 30） |
| `kIsMemorySubtitleStream` | 记住所选字幕/音轨 | 需要把用户选择的轨写回存储 |
| `kIgnoringSubtitleEffects` | 忽略 ASS/SRT 特效 | libass 从文件取样式，当前内核无落点 |
| `kShowPlayerDlnaButton` | 播放器 DLNA 按钮 | 投屏入口 |

没有出现 `preferredVersion` 的对应键名，选项集也不在字符串里——它要么是编译进二进制的
枚举（字符串不以明文出现），要么由服务端驱动。**这是它仍然未实现的唯一原因**。

**这张表的用途**：区分「参考实现有而我们没有的板块」与「同一功能的不同实现」。前者是
能力缺口（`kEmbyShowCarousel` 那一组），后者是接线问题。
