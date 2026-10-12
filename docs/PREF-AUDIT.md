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

keys with a real read on iOS:     98
keys with a real read on Android: 92
--- declared, shown in settings, read by nothing else (17) ---

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

**这一条我下错了结论，round 42 更正**。当时写的是「`grep -rn thumbnail` 只命中设置页
那一行，两端都没有任何缩略图实现」——搜索没错，结论错了：缩略图在源码里不叫 thumbnail。
iOS 的列表行一直有它（`ServerBrowser.swift` 里 `rowCell` 的 `AsyncImage`，46×66，图来自
服务器的 `/Items/{id}/Images/Primary`），`list.rounded` 那行说的"圆角风格"指的就是它的
圆角。又是同一个形状的错：**搜一个词没中，就当成了没有**——和把 `grep cast` 只匹配到
"演职人员"就判定 iOS 不能投屏，是同一件事。

所以 `list.fillThumbnail`（铺满还是留边）和 `list.showPreview`（显不显示）**确实是
接线**，已经接上：iOS 的列表行按这两个开关决定裁切方式与显示，Android 的列表行此前只
画一个文件类型图标，现在同样显示缩略图——这是 Android 侧顺手补上的一个能力缺口。

只有 `list.previewPosition` 还悬着，而且它悬的理由和上面两个不同：它管的是**客户端自己
抽帧**的位置（`previewTimePosition`，"0代表不自动生成缩略图"）。同族文案
（`automaticallyGeneratePreview`、`updatePreviewImage`、`numberOfConcurrentThreadsInPreviewImage`、
`prioritizeEmbeddedThumbnail` 的说明"开启后将优先使用文件自身内嵌的封面，预览图时间设定
则不起作用"）拼出来的是另一条链路：服务器不给图时自己解码取帧。本仓两端都没有这条链路，
键先留着。

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

## 单端对齐的第一批（round 42）

接上六个，死键 20 → 18，单端 20 → 16：

| 键 | 落点 |
|---|---|
| `list.fillThumbnail` | iOS 列表行 `AsyncImage` 的 contentMode（铺满 vs 留边）；Android 同步接上 |
| `list.showPreview` | 同上，决定显不显示 |
| `list.rounded`（Android） | 列表行缩略图的圆角，对齐 iOS 的 `RoundedRectangle(cornerRadius: display.rounded ? 5 : 0)` |
| `ui.showStartTitle`（iOS） | 开始播放的提示，文案与优先级都照 Android 的 `hud = when { … }` |
| `ui.showSkipTip`（iOS） | 片头/片尾跳过的提示，文案照 Android：「已跳过片头 N 秒」/「已到片尾」 |
| `play.accurateSeek`（Android） | `ExoPlayer.setSeekParameters(EXACT)` |

两个只有编译器才会说的事：`setSeekParameters` 返回 player 以便链式调用，因此 Kotlin 把
它当方法而不是属性，写成 `player.seekParameters = …` 会报 `'val' cannot be reassigned`；
Android 的列表行此前根本不画缩略图，只有文件类型图标。

**剩下 16 个单端键分三类**：

- **平台差异（不打算对齐）**：`video.decodeThreads`（Media3 没有软解线程数）；
  `video.hardwareDecode` / `hdrMetadata` / `upscaling`（Media3 不能禁用 MediaCodec，
  也没有 HDR 元数据与超分的接口，iOS 侧走 KSPlayer 的 `hardwareDecode` /
  `destinationDynamicRange` / `videoFilters`）；`gesture.longPressLeftSpeed` /
  `longPressRightSpeed`（需要按键重复事件）。
- **功能缺口**：`gesture.longPressSpeed`、`gesture.rememberZoomSize`（Android 既没有
  长按倍速也没有捏合缩放）；`ui.switchMediaButton`（Android 控制栏没有这个按钮）；
  `subtitle.scale` / `subtitle.mask`（Android 的嵌挂字幕由 Media3 的 SubtitleView
  渲染，还没接管它的样式）；`audio.rememberTrack` / `subtitle.rememberTrack`（两端
  「记住所选轨道」都还没把选择写回存储）。
- **待查**：`iptv.sources` / `iptv.channels`（Android 有这两个键，iOS 的 IPTV 入口
  是否另有存储，要单独看）。

## 手势在两端对齐，以及一个停不下来的倍速（round 43）

Android 此前既没有长按倍速也没有捏合缩放——设置页那两行在，手势不存在。补齐后单端键
从 16 降到 14：

| 项 | Android | iOS |
|---|---|---|
| 长按屏幕倍速 | `onLongPress` 进入倍速，`onPress` 里的 `tryAwaitRelease()` 抬手恢复 | 同一套语义 |
| 双指缩放画面 | 新增检测，只认两根手指，单指仍然走拖动 | 原有 `MagnificationGesture` |
| 记住缩放大小 | 新增 `pref.gesture.zoom` | 同键同默认值 |

**iOS 这边的长按原本停不下来**。`LongPressGesture` 只在长按被识别的那一刻回调，手指
抬起时没有任何通知，所以倍速会一直挂着，直到下一次拖动结束才被 `handleDragEnd` 复位。
改用 `onLongPressGesture(minimumDuration:maximumDistance:onPressingChanged:)`，由释放
分支负责回到原速。这类问题编译器不会说话：手势在，逻辑自洽，只是少了一条结束路径。

长按改的是内核速率，没有走 `setSpeed`：`setSpeed` 会写 `play.lastSpeed`，而
「记住播放倍速」记的是用户**选择**的倍速，不该被一次长按改写。Android 侧同一处理。

**`pref.gesture.zoom` 是这一轮新加的键**，不出现在任何设置页：它只是
`rememberZoomSize` 的存放处。参考实现里 `kIsRememberZoomSize` 是个开关，缩放值存哪里
没有取证，本实现自己定名，两端同名。

## `pref-audit.py` 的第七个盲点（round 43）

`rememberZoomSize` 在 iOS 一直被算作「已接」——因为
`@AppStorage(PrefKey.rememberZoomSize) private var rememberZoomSize = true` 这一行
**确实读了 UserDefaults**。问题是这之后整个文件再也没提过这个变量：值读出来了，没人用。

工具现在多输出一段「绑定存在、值从未被使用」，判据是绑定声明的变量名在该文件里除声明
行外一次都不出现（`$name` 的写法算使用）。写完先拿临时样本验过——一条绑定后从不使用、
一条绑定并在 `$used` 里出现——确认它抓得到前者、不误报后者。当前仓库这一项是 0，
因为 `rememberZoomSize` 刚好在这一轮被真的接上了。

**这四次修正（单端、过宽、插值噪声、绑定未用）是同一个形状**：工具的判据总是比现实窄
一格，而缺口正好落在窄掉的那一格外面。所以每次改完判据都要先喂一个已知的反例，
再看总数——总数不变不能证明判据有用。

## 第七个盲点的第一版是坏的，轨道记忆接上了（round 44）

「绑定存在、值从未被使用」这个检查第一版把变量名在文件里出现的**次数**当判据，于是
漏掉了 `rememberAudioTrack`：键常量的名字和变量名恰好一样
（`static let rememberAudioTrack = "pref.audio.rememberTrack"`），那一行被算成了一次
「出现」。

改成逐行判断：`static let` 的常量声明、`@AppStorage` 那一行本身、以及任何
`PrefKey.<同一个名字>` 的引用都不算使用。改完立刻抓出四个：

| 键 | 状态 |
|---|---|
| `audio.rememberTrack` | 本轮接上 |
| `subtitle.rememberTrack` | 本轮接上 |
| `gesture.longPressLeftSpeed` | 与「无落点」一类一致（需要按键重复事件） |
| `gesture.longPressRightSpeed` | 同上 |

**轨道记忆的实现要点**（两端同一套）：

- **存储不进 `pref.` 命名空间**。这是按文件的数据，不是一条设置：iOS 用
  `trackMemory.v1` 一个 JSON 字典，Android 用 `track_memory`。URL 做键太脆（长、
  含 `?` 和 `:`），所以整体存取、整体解析，两端都兜住空值和损坏。
- **恢复的时机是「轨道出现之后」，不是「播放器连上之后」**。Media3 的轨道要等 prepare，
  KSPlayer 的要等 `readyToPlay`。iOS 复用它原有的 `languagesApplied` 一次性钩子；
  Android 用一个 `LaunchedEffect` 等 `currentTracks`，最多等 4 秒（一个完全没有轨道
  的文件不该让协程挂到会话结束）。
- **记住的轨道优先于语言偏好**。语言偏好是「这部片子没碰过时的默认」，记住的是「上次
  在这部片子里选的是什么」。`applyRememberedTracks()` 返回它真正恢复的类别，语言偏好
  只补它没恢复的那几类——而不是「只要设过就一律跳过」，那样在记住的轨道已经不在文件
  里时会连默认选择也丢掉。
- **轨道的标识两端各自取**：iOS 用 `MediaPlayerTrack.name`，Android 用
  `format.id ?? label ?? language`。取不到就不存——存一个空值，下次会匹配到别的轨道。

编译期挡下来的两件事，都是同一条规则：**Kotlin 的局部函数必须先声明后使用**。
`trackId` 写在 `selectTrack` 后面（报 unresolved reference），`rememberZoom` 写在用它
的状态后面（同样报错）。另外 `private` 不能修饰局部函数。

## 一次做完剩下的（round 45）

**Android 的字幕样式和切换媒体按钮**：

- `subtitle.scale` → `SubtitleView.setFractionalTextSize(默认比例 × 缩放)`。SubtitleView
  按自身高度的比例排字，所以这个设置是乘数而不是像素值。
- `subtitle.mask` → `SubtitleView.setBottomPaddingFraction()`，只在悬浮控制栏时抬高——
  设置页那行小字写的就是「仅对悬浮控制栏生效」。
- `ui.switchMediaButton` → 控制栏那个「下一个」按钮**原本点了只弹一句 HUD，什么都不做**
  （`onClick = { showHud("切换媒体") }`）。现在它跳队列里的下一个，并受开关控制。
  这是第 9 个「点下去什么都不发生」的控件。

**预览图（`list.previewPosition`）两端都做了**。证据是完整的：`automaticallyGeneratePreview`、
`updatePreviewImage`、`numberOfConcurrentThreadsInPreviewImage`、`previewImageThreads`、
`prioritizeEmbeddedThumbnail`，加上 `0NotGeneratingPreviewImage`="0代表不自动生成缩略图"
和 `mediaInfoTips`="部分设置对网盘文件列表无效"。实现按这两句话来：

- 取帧位置 = 时长 × 百分比，**0 就是不生成**，行上留图标（iOS 用
  `AVAssetImageGenerator`，Android 用 `MediaMetadataRetriever`）。
- 只作用于**本地文件**列表。参考实现自己说这条设置在网盘列表上无效，所以没有为远程
  文件开一条「先下载再抽帧」的路。
- 帧按「路径 + 百分比」缓存，生成放在后台线程/IO 上：每滚回顶部就把文件夹里每个文件
  重开一遍是不能接受的。

**`list.showNewTag` 到此定案：不接**。「New」按什么算新，在 96 个 `k` 键名和 1255 条
文案里都没有依据（只有 `dayWatch` / `hourWatch` 这类观看时间的相对显示）。
`showRemaining` 那次教训已经证明按名字推断行为会错，所以它留在「等证据」一类。

**`general.iCloudSync` 也定案：不做实现**。iOS 侧要走 `NSUbiquitousKeyValueStore`，
需要 `ubiquity-kvstore-identifier` entitlement；本仓库的构建路径（CI 与本地都是
`CODE_SIGNING_ALLOWED=NO`）提供不了它，本机也没有第二个 iCloud 账号能验证同步真的
发生。写一段无法验证是否工作的代码，和画一个点了没反应的按钮是同一种问题。

**`pref-audit.py` 的第九个盲点**：报告里混进了不是设置的存储键。`iptv.sources` /
`iptv.channels` 是两端的**数据**（频道列表），但 Android 侧所有常量都会加上 `pref.`
前缀以便比较，于是它们伪装成偏好键，出现在「只有一端接了」的列表里。现在报告只包含
**设置页真的画出来的键**（iOS 侧扫 `Preferences.swift` 的 `@AppStorage` 与 `PrefKey`
引用，Android 侧扫 `PrefSwitch` / `PrefChoice` / `PrefSlider` / `PrefText` 的行）。
两条噪声消失，死键数不变——判据变窄没有误伤。

## 单端剩下的 7 条，全部是无落点或平台差异（round 45 定案）

| 键 | 为什么不动 |
|---|---|
| `gesture.longPressLeftSpeed` / `longPressRightSpeed` | 需要按键重复事件，触屏没有这个概念 |
| `video.decodeThreads` | Media3 没有软解线程数这个设置 |
| `video.hardwareDecode` | Media3 不能禁用 MediaCodec，没有「只用软解」的入口 |
| `video.hdrMetadata` | Media3 没有 HDR 元数据接口 |
| `video.upscaling` | Media3 没有超分接口 |
| `ui.autoFullScreen` | iOS 的播放界面本来就是全屏且隐藏状态栏，等价于常开 |

这一栏以后不再每轮重新评估。死键 17 条的分类见前几节：字幕样式 9 条（libass 与
Media3 的 SubtitleView 都从字幕文件取样式，没有「覆盖文件样式」的入口）、
`audio.globalDelayMs` 与 `video.hdrVivid`（内核没有对应入口）、`play.isoMode`
（两端都没有 ISO 播放路径）、`library.showMine` 与 `preferredVersion`（结构改动 / 选项集
不在取证材料里）、`list.showNewTag`（缺判据）、`general.iCloudSync`（需要 entitlement）。


## 参考键清单与本仓对照（round 46）

从砸壳 IPA 里挖出 195 条应用自有键（见 `docs/REFERENCE-KEYS.md`）。逐条对照后分三类。

### 已接（round 46）

| 组 | 键 | 落点 |
|---|---|---|
| 控制栏按钮 | `ui.playButton` / `aspectRatioButton` / `pipButton` / `speedButton` | 两端控制栏与「更多」面板 |
| 文件类型 | `list.fileType.video/music/picture/subtitle/other` | 两端服务器文件夹列表 |
| 状态信息 | `ui.osd.playSpeed` / `systemTime` / `onlyLandscape` | 两端 OSD |
| 字幕样式 | `subtitle.textColor` / `bgColor` / `opacity` / `verticalOffset` | iOS 自绘文本层；Android 的 `CaptionStyleCompat` |

### 判定无落点（本仓结构不同，不再重复评估）

- `kRecordShowBookmarkFile` / `BookmarkFolder` / `Common` / `Continue` / `Favirote` /
  `Histroy` / `Screen`：参考的记录模块有七个板块，本仓是一个列表。
- `kRecordPlayHistroy` / `kRecordAudioHistroy`：同上，本仓不区分视频与音频记录。
- `kSearchShowDrive` / `kSearchShowMedia` / `kShowMbGlobalSearchButton` / `kDisplaySearchBox`：
  参考有独立的搜索模块，本仓的搜索是列表内过滤。
- `kEmbySearchHistory` / `kSubtitleSearchHistoryShow` / `kDanmuSearchHistoryShow`：本仓没有搜索历史。
- `kShowPlayerFullScreenButton`：播放界面本即全屏，没有窗口态可切。
- `kShowPlayerListButton` / `kShowPlayerSkipButton`：没有对应的控件可开关。
- `kShowPlayerDlnaButton`：iOS 的投屏是系统 AirPlay 按钮，不在控制栏。
- `kShowOSDInternetSpeed`：需要字节或带宽计数，Android 侧本仓没有挂第二个 analytics listener。
- `kSubtitleStrokeSize` / `kSubtitleShadowColor` / `kSubtitleShadowSize` /
  `kSubtitleMaskCenterX` / `CenterY` / `Width` / `Height` / `kSubtitleHDRBrightnessValue`：
  Android 的 `CaptionStyleCompat` 只带文字色、背景色、描边色与字体，没有描边宽度、
  阴影和矩形遮罩的接口。

### 待做（第三档）

`kEnableDolbyP7FEL` / `kEnableDolbyP7ToP81` / `kEnableDolbyAtmos` /
`kVideoFrameInterpolationEnabled` / `kVideoUpscalingLevel` / `kSDRToHDREnabled`，
以及 `kAudioEnhancePercent`（Android 有 `LoudnessEnhancer.setTargetGain`）。
做之前先确认两端内核各自的可用接口，再决定是否落得下去。

### 第三档的判定（round 46 续）

| 键 | 证据 |
|---|---|
| `kAudioEnhancePercent` | **已接**。iOS 原来写死 `volume=2.0`，Android 的 `GainStage` 写死 `setTargetGain(600)`；两端现在都读设置，Android 按 `20·log10(比值)·100` 换算，200% 仍是原来那 +6 dB |
| `kEnableDolbyP7FEL` / `kEnableDolbyP7ToP81` | KSPlayer 里 `isDovi` 只出现一次，而且是硬编码的 `false`——没有 FEL 处理路径；iOS 上的 Profile 转换属于系统而不是应用 |
| `kEnableDolbyAtmos` | KSPlayer 通过 `setSupportsMultichannelContent` 跟随系统的空间音频开关，本身不提供应用级开关 |
| `kVideoFrameInterpolationEnabled` / `kVideoUpscalingLevel` / `kSDRToHDREnabled` | iOS 三条都能通过 FFmpeg 的 `videoFilters` 实现（现有的超分开关就是这么做的），但 Media3 没有对应接口——单端不做 |
| `kDisplaySearchBox` | iOS 用的是系统 `.searchable`，修饰符无法条件移除；Android 的搜索框可以——同样是单端不做 |

### 两次判断错误，记在这里

1. **`audio.enhance` 被误判成「读取但从未使用」**。我只在 `PlayerActivity.kt` 里 grep，
   而增益阶段在 `PlaybackService.kt` 里，一直都在。这与早先「grep 不到 cast 就断言
   iOS 不能投屏」是同一个错误。
2. **`LoudnessEnhancer` 一开始被放进了 Activity**，而 Activity 只有 `MediaController`，
   它没有 `audioSessionId`——那个属性属于 `ExoPlayer`，也就是服务里那个真正的播放器。

两条都指向同一件事：**在这两个平台上，「谁持有播放器」决定了哪一层能改音频**。
