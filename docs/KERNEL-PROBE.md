# 内核路线验证（KSPlayer）

回答一个问题：**播放内核能不能接、接了之后自绘的体验层要不要丢。**

结论：**能接，且不必丢。**

验证对象是 `.github/kernel-probe/`，一个与 `ios/` 完全隔离的脚手架。它由独立 workflow
`ios-kernel.yml` 驱动，主发布链路 `ios.yml` 不引入任何第三方包，构建时间不受影响。

## 依赖事实（来自包解析，非文档）

| 项 | 值 |
|---|---|
| KSPlayer | `2.3.4`（`swift-tools-version:5.9`） |
| FFmpegKit | `6.1.4`（由 KSPlayer 2.3.4 以 `from: "6.1.3"` 解析得到） |
| main 分支 | `swift-tools-version:6.2`，需要 6.2 工具链；且新增 `KSPlayerUI` / `MPVPlayer` 两个 product |
| 2.3.4 暴露的 product | 仅 `KSPlayer` —— 但**模块内已含 UI 源码**（`Core/PlayerToolBar.swift`、`SwiftUI/KSVideoPlayerView.swift`） |

所以"只用内核"不是靠 product 划分实现的，而是靠**只调用 `KSPlayerLayer` 这一层**。

## 验证结果

本机（Xcode 26 / iPhoneOS 27.0 SDK）与 CI（`macos-15`）两端一致：

```
xcodebuild -resolvePackageDependencies  →  KSPlayer 2.3.4 + FFmpegKit 6.1.4
xcodebuild -sdk iphoneos (device arm64) →  BUILD SUCCEEDED
产物：39 MB .app，内置
  Libavcodec / Libavdevice / Libavfilter / Libavformat / Libavutil /
  Libswresample / Libswscale / gnutls / gmp / hogweed
链接：AVFoundation · Metal · CoreVideo · IOSurface · AudioToolbox · AVFAudio
```

两处环境特有的坑，都已写进 workflow：

- `KSPlayer` 的 `Metal/Shaders.metal` 需要 Metal Toolchain。Xcode 26 把它拆成独立组件，
  本地要 `xcodebuild -downloadComponent MetalToolchain`；`macos-15` 的 Xcode 16 自带。
- 包 checkouts 落在 DerivedData 下，不在工程目录；解析步骤必须传 `-derivedDataPath`
  才能稳定报告依赖版本。

## 接入形状（原型已编译通过）

`.github/kernel-probe/Sources/KernelPlayer.swift` 是接入骨架，CI 已验证其设备编译：

```swift
let options = KSOptions()
options.isAccurateSeek = true          // UI-SPEC「精准定位进度」
options.hardwareDecode = true          // UI-SPEC「首选解码模式」
options.userAgent = "SenPlay/1.1 (iOS)"

let layer = KSPlayerLayer(url: url, isAutoPlay: true, options: options, delegate: self)
let renderView = layer.player.view     // UIView，交给 UIViewRepresentable 承载
layer.play() / layer.pause()
layer.seek(time:autoPlay:completion:)
layer.player.playbackRate / playbackVolume / isMuted
layer.player.tracks(mediaType: .audio) / .subtitle
layer.player.select(track: some MediaPlayerTrack)   // 注意是 opaque parameter
```

代理协议只有四个回调，时间轴由它推送，不需要轮询：

```swift
public protocol KSPlayerLayerDelegate: AnyObject {
    func player(layer: KSPlayerLayer, state: KSPlayerState)
    func player(layer: KSPlayerLayer, currentTime: TimeInterval, totalTime: TimeInterval)
    func player(layer: KSPlayerLayer, finish error: Error?)
    func player(layer: KSPlayerLayer, bufferedCount: Int, consumeTime: TimeInterval)
}
```

## 需求映射

UI-SPEC 里 P3 那批能力，都能落在 `KSOptions` 上，不需要自己写：

| UI-SPEC 项 | KSOptions / API |
|---|---|
| 精准定位进度 `accurateSeek` | `isAccurateSeek` |
| 首选解码模式 | `hardwareDecode` · `syncDecodeVideo` · `videoFilters` |
| 预缓存方式 | `cache` · `maxBufferDuration` · `preferredForwardBufferDuration` |
| 使用系统代理 | `formatContextOptions`（透传 FFmpeg 参数） |
| 外挂字幕加载规则 | `autoSelectEmbedSubtitle` · `layer.player.select(track:)` |
| HDR 相关 | `destinationDynamicRange` · `display` |
| 画面比例 | `layer.player.contentMode` |
| 音轨首选语言 | `layer.player.tracks(mediaType: .audio)` + `select` |
| 全局音频延迟 | `videoDelay` · `audioFilters` |
| 投屏 / PiP | `layer.isPipActive` · `layer.player.allowsExternalPlayback` |
| 媒体信息面板 | `layer.player.dynamicInfo?.metadata` |
| 录制 | `layer.player.startRecord(url:)` |

## 接入主工程的步骤（下一轮）

1. 把 `KSPlayer` 加进 `ios/project.yml` 的 `packages:`，主 target 依赖 `product: KSPlayer`
2. `PlayerScreen.swift` 里把 `PlayerSurface(player:)` 换成承载 `layer.player.view` 的桥，
   把 `AVPlayer` 的调用面换成 `KernelCoordinator` 的；手势层与控制栏**不动**
3. `ios.yml` 的构建时间会从约 3 分钟升到 15 分钟量级，需要相应放宽 `timeout-minutes`
4. 字幕后处理：KSPlayer 自带 `subtitleView`，与现有 `SubtitleController` 叠层需要二选一

第 3 条是唯一需要权衡的代价：发布链路变重，换来的是 MKV/TS/ASS/HDR 这一整块能力。
