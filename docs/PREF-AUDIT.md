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

keys with a real read on iOS:     89
keys with a real read on Android: 79
--- declared, shown in settings, read by nothing else (20) ---

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

## 能力对不齐的地方（round 36 核对）

目标是双端全量复刻，所以两端的实现差异本身就是要记录的状态。

| 能力 | iOS | Android | 说明 |
|---|---|---|---|
| 投屏 | 有（AirPlay） | 有（DLNA） | **两端都有，走的是各自平台的机制**。iOS 用 `allowsExternalPlayback`，Android 用 SSDP 发现 DLNA 设备。iOS 额外做 DLNA 需要 `com.apple.developer.networking.multicast` entitlement，当前没申请——但那是"要不要加第二个投屏方式"，不是"iOS 不能投屏"。 |
| SMB | **无** | 有 | AMSMB2 没有可用的公开 API；其余候选都要求更高系统版本。 |
| SFTP | **无** | 有 | Citadel 要求 iOS 17，而参考实现的 `MinimumOSVersion` 是 10.0。 |
| 画中画 | 有（`layer.isPipActive`） | 有（`enterPip`） | 两端都成立，iOS 由 KSPlayer 持有生命周期。 |
| 后台音频 | 有（`setAudioSession`） | 有（`MediaSessionService`） | 两端都成立。 |
| 片头片尾跳过、连播、倍速记忆 | 有 | 有 | — |

**更正（round 38）**：这张表在 round 36 把投屏记成了"iOS 无"。那是错的——当时我
`grep cast` 只匹配到"演职人员"，就把搜不到当成了没有。iOS 的投屏走
`allowsExternalPlayback`，用的词不一样，功能是存在的。**grep 不到不等于不存在**，
这条错误在文档里留了两轮，因为它看起来像一条结论而不是一次搜索的结果。

**iOS 缺 SMB 和 SFTP 不是"忘了做"**，每一条都有具体的外部原因。它们应当留在表里，
而不是被"双端已对齐"这类总结盖过去——下一轮或下一个人需要知道的是**为什么缺**，
以及**重新评估的前提是什么**（申请到 entitlement、或内核换库、或最低版本上调）。

## 内核无落点的一类

这些键读得到，但当前的播放内核没有接收它们的入口：

- 字幕样式一组：`bold` / `italic` / `outlineWidth` / `shadowWidth` / `ratioFix` /
  `hdrSubtitle` / `ignoreAss` / `ignoreSrt`。iOS 走 libass 从字幕文件渲染样式，
  Android 走 Media3 的 `SubtitleView` 默认样式，两端都没有暴露"覆盖文件样式"的接口。
- 键长按速率：`gesture.longPressLeftSpeed` / `longPressRightSpeed`。这需要遥控器或
  外接键盘的按键重复事件；触摸屏的长按是连续手势，没有"重复速率"这个概念。

**把这一类与"没接线"分开写**，是因为它们的处理方式不同：没接线的是待办，内核无落点的
要么等内核暴露接口，要么接受这一项在触摸设备上不适用。

## 断链：实现存在但接不上（round 37）

上一轮找到的投屏属于一类更难看见的问题——**代码是对的，但没有任何路径能让用户到达它**。
偏好键审计查不到（标志位确实被读了），按实现搜索也查不到（实现确实存在）。
`tools/check-orphans.py` 是为此写的粗筛：列出「只在定义文件里出现过」的类型。

**这个工具只能给候选，不能给结论**。它分不清「只在自身文件内使用的辅助类型」（正常）
和「没有任何调用者的客户端」（问题）。49 个 iOS 候选里绝大多数属于前者。

核对下来，真正的断链只有两类：

- **投屏**（round 36 已修）：`castOpen` 被设置，对话框从未渲染。
- **`S3DataSource`**（本轮）：实现正确，用来解决 presigned URL 在长片播放中途过期的问题。
  当前 S3 走 presigned URL，能用。**接入它需要把凭据送到播放服务**，而服务当前拿不到
  `ServerConfig`——要么像 SMB/SFTP 那样塞进 URI，要么给服务传当前队列所属的服务器。
  已在类注释里写明「未接线」和原因，避免它被当成死代码删掉。

**核对了没问题的**：`SchemeRoutingDataSourceFactory` 确实路由了 smb / ftp / sftp 三种
scheme，`PlaybackService` 用的是它。

## 最后一次分类核对（round 40）

收尾时把 30 个未读键逐个过了一遍，**修正了一处过宽的分类**：此前把「后台音频」
归进了「内核无落点」，实际不是——后台播放是实现了的（iOS 的
`UIBackgroundModes: audio`、Android 的 `MediaSessionService`），缺的只是**开关没接线**。
已接上：iOS 监听 `didEnterBackgroundNotification`，Android 监听 `ON_STOP`，两处在
开关关闭时暂停。

核对同时确认了这些**确实是「无落点」而不是「没接线」**：

- `subtitle.font`：两端都没有自定义字幕字体的接口。检查过 `FontFamily.Monospace`
  那几处，是 OSD 的等宽字体，与字幕无关。
- 字幕样式一组（`bold` / `italic` / `outlineWidth` / `shadowWidth` / `ratioFix` /
  `hdrSubtitle` / `ignoreAss` / `ignoreSrt`）：libass 与 Media3 的 `SubtitleView` 都从
  字幕文件取样式。
- `gesture.longPressLeftSpeed` / `longPressRightSpeed`：需要按键重复事件。
- `ui.matchAspect`：桌面窗口概念，移动端没有可对齐的窗口比例。

**仍然是「没接线」的**（不是无落点，是待办）：`list.fillThumbnail` / `showPreview` /
`previewPosition` / `showNewTag`、`general.defaultModule` / `iCloudSync`、
`video.switchBlackout` / `screenshotFormat` / `decodeThreads` / `hdrVivid`、
`danmaku.convert` / `font`、`gesture.click`、`ui.showCloudThrottleTip`、
`play.isoMode` / `pauseAutoLock` / `useSystemProxy`、`audio.globalDelayMs`、
`library.preferredVersion` / `showMine`。

**这三类混在一起的后果**：把它们都叫「未完成」会让剩下的工作看起来比实际大，也会
让真正无落点的那些被反复重新评估。每次核对都要重新判断一遍某个键属于哪一类，这就是
这份文档存在的理由。

## 接上的十个（round 41）

十个键从「只出现在设置页」变成「有一处真的读它」，双端都改、各自编译验证：

| 键 | 落点 | 依据 |
|---|---|---|
| `gesture.click` | iOS `perform(clickAction)`；Android `performGesture()` | 设置页下拉的默认值是「播放/暂停」，而两端此前都把它写死成「显示/隐藏控制栏」。接上之后**默认行为会变**——这正是设置页那行字的意思 |
| `play.pauseAutoLock` | iOS `isIdleTimerDisabled`；Android `FLAG_KEEP_SCREEN_ON` | 文案「暂停后自动锁屏」：播放中必须常亮，暂停之后是否交还给系统锁屏由开关决定 |
| `video.screenshotFormat` + `jpegQuality` | iOS 编码后写 `PHPhotoLibrary`；Android `Bitmap.compress` | 两端此前都硬编码 PNG。「jpg」是设置页的默认值，此前是空头支票 |
| `play.useSystemProxy` | iOS `KSOptions.useSystemHTTPProxy`；Android OkHttp `Proxy.NO_PROXY` | KSPlayer 的 `MediaPlayerProtocol.setHttpProxy()` 把这个开关写进 `http_proxy` 环境变量交给 FFmpeg；OkHttp 默认跟随系统代理，关闭必须显式写 NO_PROXY |
| `video.switchBlackout` | iOS 切换瞬间盖黑；Android `setKeepContentOnPlayerReset(!switchBlackout)` | Android 此前硬编码保留上一帧，与设置页默认的「开」（不残留）**正好相反** |
| `danmaku.font` | iOS `UIFont(name:)`；Android `FontFamily(Typeface)` | 「SF Pro」是参考实现给系统字体起的名字，两端都按「认不出这个名字就用系统字体」处理，输入错字不会导致弹幕消失 |
| `danmaku.convert` | iOS `CFStringTransform`；Android ICU `Transliterator` | 两端都用系统的 ICU 数据，不自己维护字表；设备缺规则时保留原文 |
| `general.defaultModule` | iOS `TabView(selection:)` 初值；Android 初始 tab | 只在启动时读一次，不跟随设置变化把用户正在看的那一页换掉 |
| `ui.showCloudThrottleTip` | 播放来自 S3 的请求时提示一次 | `PlayRequest` 新增 `isCloudDrive`：预签名地址里没有来源信息，标记必须随请求走。与「开始播放提示标题」同属播放时的提示，这是它的位置依据 |
| `video.decodeThreads` | iOS `KSOptions.decoderOptions["threads"]` | 只对 FFmpeg 路径有意义的「软件解码线程」。Android 没有对应 API，未接 |

## 剩下的十个重新分了类（round 41）

做这一批时发现上一轮的四类分得还不够细，有两类被混进了「没接线」：

**不是「没接线」，是功能本身不存在**——`list.fillThumbnail` / `showPreview` /
`previewPosition`。这三个键描述的是同一件事：列表里每个视频项的缩略图、它取哪一帧
（`previewTimePosition`，0 表示不生成）、以及它是铺满还是留边（`thumbnailCropDisplay`）。
可是 `grep -rn thumbnail` 在两个平台的源码里**只命中设置页那一行**，两端都没有任何
缩略图实现。接这三个键的前提是先做出一条预览图链路，而参考实现那条链路的规模从同族
文案就能看出来：`automaticallyGeneratePreview`、`updatePreviewImage`、
`numberOfConcurrentThreadsInPreviewImage`、`prioritizeEmbeddedThumbnail`
（"开启后将优先使用文件自身内嵌的封面，预览图时间设定则不起作用"）。

**不是「没接线」，是无落点**：

- `video.hdrVivid`。KSPlayer 确实读 `AV_FRAME_DATA_DYNAMIC_HDR_VIVID`，但那个分支
  只是把 side data 绑定下来，之后没有任何使用；`DynamicRange` 枚举只有
  sdr / hdr10 / hlg / dolbyVision。Media3 侧同样没有这个开关。参考实现的说明里写着
  「低性能设备不建议开启，否则可能导致发热或掉帧」——那是软解 HDR Vivid，本仓两端
  都没有这条解码路径。
- `play.isoMode`。两端都没有 ISO 播放路径（`grep -i iso` 只命中 `episode` 和时间码）。
  「兼容模式/快速模式」描述的是网络读取方式，内核没有暴露入口。
- `audio.globalDelayMs`。KSOptions 里只有 `videoDelay`，没有音频延迟；Media3 也没有
  对应 API。

**仍然缺证据**：

- `list.showNewTag`。「New」按什么算新，取证字符串里只有 `newTag = New标记` 一条，
  96 个 `k` 开头的键名里没有日期阈值之类的东西。`showRemaining` 已经证明按名字推断
  行为会错，这条不猜。
- `library.preferredVersion` / `showMine`：与上一轮相同。

**平台差异，不是待办**：

- `general.iCloudSync`：iOS 侧可以用 `NSUbiquitousKeyValueStore` 接上，但需要
  `ubiquity-kvstore-identifier` entitlement，而且本机没有第二个 iCloud 账号可以验证；
  Android 没有 iCloud 这个平台概念。
- `video.decodeThreads`：iOS 已接，Android 的 Media3 没有软解线程数这个设置。

## 这一轮查出来的一个真问题（round 41）

`PlayerActivity` 在 manifest 里是 `launchMode="singleTop"`，而它**没有实现
`onNewIntent`**。连播和「切换媒体」都是 `startActivity` 一个新的 intent 给同一个
Activity；singleTop 让这个 intent 复用已有实例，于是走 `onNewIntent`——而那里没有
任何代码，新 intent 只是被存下来。

结果：Android 的「自动连播下一个」和播放器的「切换媒体」按钮**点了什么都不发生**。
它们能编译、自洽、测试也不报错，和之前那七个控件是同一类。

修法是补上 `onNewIntent`，并把播放参数提升为 Compose 状态、用 `key(url)` 重建播放
界面。这样切集变成同一 Activity 内换媒体，`video.switchBlackout` 也才有了真正的落点
（同一块 PlayerView 重置时保不保留上一帧）。

## `pref-audit.py` 的第五个盲点，和修好之后露出来的东西（round 42）

上一轮顺手核对时发现：iOS 的 `ui.showStartTitle` 和 `ui.showSkipTip` 只在设置页出现，
播放器里一次都没读——但它们在报告里**不是死键**。

原因是判据最后一步：

```python
dead = [k for k in all_keys if not ios.get(k) and not android.get(k)]
```

「两端都没读」才算死。单端缺一个，报告一声不响。这两个键 Android 读了，于是整条记录
消失，看起来双端都好。这个工具的每一次出错都是同一个形状：**判据比现实窄，缺口就
隐身**。

现在它多输出一段「只有一端接了」，并顺手收掉了判据里两处过宽（第六次修正）：

- `IOS_DECL` 原本匹配所有 `static let X = "…"`，把 user-agent 和 `servers.v1` 这类
  存储键也当了偏好键——现在只认 `pref.` 开头的值。
- `pref.danmaku.endpoint.\(index)` 这种按槽位拼出来的键不是设置项，按插值符排除。

改完先验了两个已知项：`pref.subtitle.bold`（已知死键）仍在死键表里，
`pref.list.rounded`（已知活键）不再出现在死键表、而是出现在新的单端表里。死键总数
20，与收窄前一致——判据变严没有误伤。

**露出来的 20 个单端键**（工具只给候选，不给结论：有些是刻意的平台差异，其余是真的漏）：

| 只有 iOS 读 | 只有 Android 读 |
|---|---|
| `audio.rememberTrack` | `iptv.channels` |
| `gesture.longPressSpeed` | `iptv.sources` |
| `gesture.longPressLeftSpeed` | `ui.autoFullScreen` |
| `gesture.longPressRightSpeed` | `ui.showSkipTip` |
| `gesture.rememberZoomSize` | `ui.showStartTitle` |
| `list.rounded` | |
| `play.accurateSeek` | |
| `subtitle.mask` | |
| `subtitle.rememberTrack` | |
| `subtitle.scale` | |
| `ui.switchMediaButton` | |
| `video.hardwareDecode` | |
| `video.hdrMetadata` | |
| `video.upscaling` | |
| `video.decodeThreads` | |

**`video.decodeThreads` 是已知的平台差异**（Media3 没有软解线程数这个设置），
`gesture.longPressLeftSpeed` / `longPressRightSpeed` 属于上面写的「无落点」一类
（需要按键重复事件）。剩下的十四条要在下一轮里一条条查：`list.rounded` 和
`play.accurateSeek` 这类，Android 侧多半只是没把值接上。

**这张表改变了一件事**：此前「双端键串对齐、默认值零处不一致」这句话是对的，但它
只保证了**设置页长得一样**。有没有真的生效，是另一条轴——而这条轴上两端并不齐。

