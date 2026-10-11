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
| 倍速记忆 | 完成 | `pref.play.forwardSeconds` / `backwardSeconds` 驱动的按钮与手势 |
| 后台音频 | 完成 | iOS `UIBackgroundModes: audio`；Android 前台服务由 Media3 管理 |
| 锁屏 / 控制中心 | 完成 | iOS `MPRemoteCommandCenter`：播放、暂停、切换、拖动、前后跳 |
| 画中画 | 完成 | iOS 用 KSPlayerLayer 的 PiP 生命周期；Android `PictureInPictureParams` |
| 投屏 | 完成 | iOS 走 AirPlay / 外部播放；Android 另有 DLNA（见下） |
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
| 网盘（115 / 阿里 / 百度 / 123） | **未做** | 见下节 |
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

## 字幕与弹幕

| 能力 | 状态 | 备注 |
|---|---|---|
| 内嵌字幕与 ASS 渲染 | 完成 | 由内核负责 |
| 外挂字幕自动匹配 | 完成 | 同目录同名字幕，简中 → 繁中 → 其他 → 英文 排序 |
| 字幕样式偏好 | 完成 | 字号缩放、防遮挡、忽略 ASS/SRT 样式（透传内核） |
| 弹幕协议 | 完成 | dandanplay 兼容的两个端点，端点由用户自填 |
| 弹幕渲染 | 完成 | 车道分配 + 着色 + 顶部/底部/反向，成本与在屏数量成正比 |

## 三处未做，以及原因

### SMB 与 SFTP 在 iOS 上缺失

两者在 Swift 生态里都缺少可用的实现，原因一致：库要么没有公开 API，要么没有
按偏移读取的能力，而播放器必须要后者。

SMB：AMSMB2 在 3.4.0 与 4.0.x 上都几乎没有公开面——`SMB2Manager` 的公开成员
只有初始化器与镜像支持，文件句柄是 internal 的 final class，ObjC 兼容层只暴露
六个 `__` 前缀方法。

SFTP：NMSSH 的通道只有 `readFile(atPath:)`，没有偏移读取，等于每次 seek 都要
从头下载。swift-nio-ssh 提供的是 SSH 传输层，SFTP 子系统仍要自己实现（约数百行
协议代码），性价比不成立。

与其引入一个打不开文件的依赖，不如让 iOS 的服务器类型列表里不出现这两个协议。
iOS 侧同类需求由 WebDAV 覆盖——Alist 之类的桥接层可以把 SMB、SFTP、网盘统一挂
成 WebDAV。

### DLNA / UPnP 仅 Android

Android 侧已实现完整链路：SSDP 多播发现 MediaRenderer、拉取设备描述拿
AVTransport 控制地址、SOAP 下发 SetAVTransportURI / Play / Pause / Stop / Seek。
投屏前会把当前地址递交给接收端，因此只有网络地址可投——本地文件需要额外跑一个
文件服务器，没有做。

iOS 侧缺同一块：SSDP 要发多播，需要 `com.apple.developer.networking.multicast`
entitlement，得向 Apple 申请。局域网发现因此改用不依赖该授权的 mDNS，能覆盖媒体
服务器的发现；DLNA 遥控留空。iOS 的投屏由 AirPlay 承担。

### 网盘未做

各家开放平台的授权链路与接口差异很大，且都要求用户以自己的账号登录并授予
应用权限。实际使用中，Alist 之类的桥接层可以把网盘挂成 WebDAV，而 WebDAV 已经
支持——所以更合理的路径是让用户自行桥接，而不是在客户端里塞四套 OAuth。

### 在线字幕搜索未做

内核带 OpenSubtitles 之类的源接口，但那需要把各家的 API key 内置进客户端。
更干净的做法是让用户自备：外挂字幕已经支持从服务器目录自动匹配，把字幕文件
放在媒体旁边即可，不依赖任何第三方服务。

## 与参考实现的差异清单

- 参考实现的播放菜单快捷键（`Space` / `←` / `→` / `↑` / `↓` / `+` / `-` / `0` / `F` / `I`）
  面向键鼠场景，SenPlay 的 iOS 端未绑定；Android 由遥控器事件处理。
- 参考实现的 AI 超分与 HDR Vivid 依赖其内核私有实现，SenPlay 用 FFmpeg 的
  `scale` 与 `destinationDynamicRange` 近似，效果不等价。
