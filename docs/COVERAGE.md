# 能力覆盖

对照参考应用的可观察行为，逐项记录落地状态。**未做的项都写明原因**，
不做"看起来支持、实际不通"的表面适配。

## 播放层

| 能力 | 状态 | 实现位置 |
|---|---|---|
| 解码内核 | 完成 | iOS 走 KSPlayer 2.3.4 + FFmpegKit 6.1.4；Android 走 Media3/ExoPlayer |
| MKV / TS / ASS / HDR / 杜比视界 | 完成 | 内核自带；iOS 的 `destinationDynamicRange` 由「HDR元数据」偏好驱动 |
| 手势全表 | 完成 | 双击三分区、左右垂直亮度/音量、水平拖动 seek、双指缩放、长按倍速 |
| 控制栏全元素 | 完成 | 关闭 / 切换媒体 / 时间 / 进度条（圆点可选）/ 倍速 / 字幕 / 音轨 / 画面比例 / 音量 / 章节 / 更多 |
| 连播 | 完成 | 队列随 `PlayRequest` 传递，双端键串一致 |
| 片头片尾跳过 | 完成 | 阈值由偏好给出，片尾只倒计时不强制跳，可取消 |
| 断点续播 | 完成 | 双端各自持久化，Emby/Plex 的服务器端进度也读入 |
| 倍速记忆 | 完成 | `pref.play.rememberSpeed` / `pref.play.lastSpeed`；恢复上次选择的倍速 |
| 后台音频 | 完成 | iOS `UIBackgroundModes: audio`；Android 是 `MediaSessionService`，播放器归服务所有，界面通过 MediaController 驱动 |
| 锁屏 / 控制中心 | 完成 | iOS `MPRemoteCommandCenter`：播放、暂停、切换、拖动、前后跳 |
| 画中画 | 完成 | iOS 用 KSPlayerLayer 的 PiP 生命周期；Android `PictureInPictureParams` |
| 投屏 | 完成 | iOS 走 AirPlay（`allowsExternalPlayback`）；Android 走 DLNA 设备发现 |
| 章节跳转 | 完成 | iOS 读内核 `chapters`；**Android 未做**，Media3 不暴露章节轨 |

## 协议层

| 能力 | 状态 | 备注 |
|---|---|---|
| WebDAV | 完成 | PROPFIND 多状态解析，双端 |
| Emby / Jellyfin | 完成 | 认证、视图、条目、搜索、排序、继续观看、直链 |
| 飞牛 fnOS | 完成 | 与 Emby 同族，`isEmbyLike` 已覆盖 |
| Plex | 完成 | 独立 XML 客户端，token 走密码字段，直连 `/library/parts` |
| FTP | 完成 | iOS 自实现 RFC 959（被动模式 + REST 续传）；Android 用 commons-net |
| SMB | **仅 Android** | 见下节 |
| SFTP | **仅 Android** | 见下节 |
| DLNA / UPnP | **仅 Android** | 见下节 |
| S3 对象存储 | 完成 | 签名用 presigned URL；这也是网盘类需求的落点。另有 `S3DataSource`（每次请求现签，不受有效期限制）已实现但**未接线**，原因见 `PREF-AUDIT.md` 的断链一节 |
| 在线字幕搜索 | **未做** | 见下节 |
| IPTV M3U / M3U8 | 完成 | EXTINF 属性、分组、搜索、播放列队 |
| 局域网发现 | 完成 | mDNS（`_http` / `_https` / `_webdav` / `_smb`），双端 |

## 媒体库层

| 能力 | 状态 | 备注 |
|---|---|---|
| 海报墙 | 完成 | 网格 / 列表切换，进度条叠在海报底部 |
| 搜索 | 完成 | 客户端过滤 + 服务端 `SearchTerm` |
| 排序 | 完成 | 名称 / 加入时间 / 年份 / 评分 / 时长，升降序 |
| 继续观看 | 完成 | Emby `/Items/Resume`、Plex `/library/onDeck` |
| 最近添加 | 完成 | Emby 按 `DateCreated` 倒序、Plex `/library/recentlyAdded` |
| 播放记录 | 完成 | 独立模块，逐条进度与删除 |
| 合集 | 完成 | Emby 的 BoxSet 与 Plex 的 collection，都作为第四个板块列出并可进入 |
| 详情页 | 完成 | 按 id 取单条记录，展示简介、演职人员、分辨率 / 帧率 / HDR |
| 顶部轮播 | 完成 | 复用「最近添加」的数据源，横铺在浏览器顶部 |
| 类似作品 | 完成 | 用 Emby 的 `/Items/{id}/Similar`，排序交给服务端 |
| 封面播放按钮 | 完成 | `pref.library.displayPlayButton` |
| 最新中隐藏已播放 | 完成 | `pref.library.hidePlayedInLatest`，在板块内过滤 |

## 字幕与弹幕

| 能力 | 状态 | 备注 |
|---|---|---|
| 内嵌字幕与 ASS 渲染 | 完成 | 由内核负责 |
| 外挂字幕自动匹配 | 完成 | 简中 → 繁中 → 其他 → 英文 排序 |
| 外挂字幕加载规则 | 完成 | `pref.subtitle.loadingRule`：同名字幕 / 全部字幕 / 仅手动。**「全部字幕」当前是放宽匹配而非多轨**，播放器只挂一条轨 |
| 弹幕关键词屏蔽 | 完成 | 大小写不敏感的子串匹配，分隔符按用户可能输入的都切开 |
| 底部弹幕转浮动 | 完成 | `pref.danmaku.bottomToFloat` |
| 字幕样式偏好 | **部分** | 字号缩放与防遮挡已接；`bold` / `italic` / `outlineWidth` / `shadowWidth` / `ratioFix` / `hdrSubtitle` / `ignoreAss` / `ignoreSrt` **未接**——iOS 走 libass 从字幕文件渲染样式，Android 走 Media3 的 `SubtitleView` 默认样式，两端都没有「覆盖文件样式」的接口。见 PREF-AUDIT 的「内核无落点」一节 |
| 弹幕协议 | 完成 | dandanplay 兼容的两个端点，端点由用户自填 |
| 弹幕渲染 | 完成 | 车道分配 + 着色 + 顶部/底部/反向，成本与在屏数量成正比 |

## 三处未做，以及原因

### SMB 与 SFTP 在 iOS 上缺失

两个协议各试过一遍，卡在不同的地方，但结论相同。

**SMB**：AMSMB2 在 3.4.0 与 4.0.x 上都几乎没有公开面——`SMB2Manager` 的公开成员
只有初始化器与镜像支持，文件句柄是 internal 的 final class，ObjC 兼容层只暴露
六个 `__` 前缀方法。没有能打开文件并读取的入口。

**SFTP**：NMSSH 的通道只有 `readFile(atPath:)`，没有偏移读取，等于每次 seek 都要
从头下载。Citadel 0.12.1 有 `SFTPFile.read(from:length:)`，正是需要的能力，但它
要求 iOS 17——而参考实现的 `MinimumOSVersion` 是 10.0，本工程已在内核约束下抬到
15。为一个协议再抬两个大版本，代价远大于收益，因此撤回依赖。剩下的路是基于
`swift-nio-ssh`（它支持 iOS 13）自己实现 SFTP 子系统，但本机 `sshd` 需要 root
才能切换用户、起不来服务，没有可端到端验证的环境——写出一份"看起来对但连不上"
的协议栈比不做更糟。

iOS 侧同类需求由 WebDAV 覆盖：Alist 之类的桥接层可以把 SMB、SFTP 统一挂成
WebDAV，S3 那条路径也适用于对象存储。

### DLNA / UPnP 仅 Android

Android 侧已实现完整链路：SSDP 多播发现 MediaRenderer、拉取设备描述拿
AVTransport 控制地址、SOAP 下发 SetAVTransportURI / Play / Pause / Stop / Seek。
投屏前会把当前地址递交给接收端，因此只有网络地址可投——本地文件需要额外跑一个
文件服务器，没有做。

iOS 侧缺同一块：SSDP 要发多播，需要 `com.apple.developer.networking.multicast`
entitlement，得向 Apple 申请。局域网发现因此改用不依赖该授权的 mDNS，能覆盖媒体
服务器的发现；DLNA 遥控留空。iOS 的投屏由 AirPlay 承担。

### 网盘：走 S3 对象存储，而非四套 OAuth

115 / 阿里云盘 / 百度网盘 / 123 云盘各有自己的开放平台，授权链路互不相同，都要
用户以自己的账号登录并授予应用权限，而且控制台随时会变。本仓库选择实现 S3 兼容
接入：用户填 endpoint、bucket、AccessKey、SecretKey 即可，阿里云 OSS、腾讯 COS、
七牛、Cloudflare R2、Backblaze 以及局域网里的 MinIO 都覆盖在内。不需要注册应用，
也不会有哪个环节随时失效。

签名（Signature V4）双端各自手写，没有引入 SDK——为三个请求拉一套 SDK 并不划算，
而且手写让 Swift 与 Kotlin 两份实现可以逐行比对。签名是这一段里唯一无法靠点按
验证的部分，因此用 AWS 官方 `aws4_testsuite` 的向量做单元测试，其中 get-vanilla
用例固定了规范请求的布局与派生链，而不是只做自洽检查。

若仍要接入某家专有网盘，Alist 之类的桥接层可以把它挂成 WebDAV，那条路径同样已
支持。

### 在线字幕搜索未做

内核带 OpenSubtitles 之类的源接口，但那需要把各家的 API key 内置进客户端。
更干净的做法是让用户自备：外挂字幕已经支持从服务器目录自动匹配，把字幕文件
放在媒体旁边即可，不依赖任何第三方服务。

## 与参考实现的差异清单

- 参考实现的播放菜单快捷键（`Space` / `←` / `→` / `↑` / `↓` / `+` / `-` / `0` / `F` / `I`）
  面向键鼠场景，SenPlay 的 iOS 端未绑定；Android 由遥控器事件处理。
- 参考实现的 AI 超分与 HDR Vivid 依赖其内核私有实现，SenPlay 用 FFmpeg 的
  `scale` 与 `destinationDynamicRange` 近似，效果不等价。
