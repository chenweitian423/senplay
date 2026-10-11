# UI-SPEC — SenPlayer 可观察界面规格（复刻用）

来源：**官方 App Store 构建**，非改造样本。

| 取证对象 | 版本 | 性质 |
|---|---|---|
| `/Applications/SenPlayer.app` | 5.9.2 (155) | App Store 正版安装，`codesign --verify` 通过，TeamIdentifier `N43L2KH7JD` |
| `SenPlayer_6.2.1_….ipa` | 6.2.1 (1) | 改造样本，仅用于二进制取证（见 `PATCH-VERDICT.md`） |

**跨平台有效性判据**：Mac 版与 iOS 版共用同一份 `Localizable.strings`（1255 键，两份包内**文件名与键集完全一致**），且 Mac 版设置页存在 `macTab_list` / `macTab_gesture_short` / `macTab_mediaLibrary` / `macTab_audio` / `macTab_subtitle` 这组 **mac 专属标题键**，与 iOS 侧的 `list` / `gesture_short` / … 成对出现——即设置页是**同一份 SwiftUI 代码、按平台切换标题文案**。

结论：**Mac 版的控件层级、设置项集合、枚举值、默认值可直接作为 iOS 复刻依据**；差异仅在标题文案与"键盘/鼠标"措辞（Mac 版写"左键单击"，iOS 版写"单击手势"）。

取证方式：`tools/dump_ax.applescript`（AX 递归 dump，含控件类型与实时值）+ `tools/harvest_prefs.py`（逐 tab 点击并采集） + `screencapture`。全流程只读，未改动样本机任何配置。

---

## 1. 应用骨架

三窗口模型（`SenPlayer.SenPlayerApp`，`NSWindow Frame` 键证实）：

```
WindowMain      → SidebarNavigationSplitView     主窗口，标题随选中项变化（如"本地"）
SettingsScene   → 独立设置窗口，标题"数据同步"       ← Cmd+, / 应用菜单"设置…"
PlayerWindow    → 播放器窗口（900×506 @ 默认）
```

**应用菜单（menu bar）**：`SenPlayer` / `编辑` / `显示` / `播放` / `音量` / `窗口` / `帮助`

`SenPlayer` 菜单项：`关于SenPlayer` · `下载` · `设置…` · `服务` · `隐藏/隐藏其他/全部显示` · `退出SenPlayer`

`播放` 菜单项与快捷键：

| 菜单项 | 快捷键 |
|---|---|
| 播放/暂停 | `Space` |
| 后退 | `←` |
| 前进 | `→` |
| 上一个 | `↑` |
| 下一个 | `↓` |
| 加速 | `+` |
| 减速 | `-` |
| 重置 | `0` |
| 全屏 | `F` |
| 媒体信息 | `I` |

`音量` 菜单项：`音量加` · `音量减` · `静音`

**复刻优先级**：P1（iOS 用 `AVPlayerViewController` 或自绘控制层时按此补齐命令集；`Space/←/→/+/-/0/F/I` 是硬约束）

---

## 2. 主窗口（截图 `ui-shots/01-home.png`、`06-fullscreen-live.png`）

### 2.1 侧边栏（`AXOutline`，`SidebarNavigationSplitView`）

固定五项 + 服务器分组：

| 顺序 | 文案 key | 文案 | 图标 |
|---|---|---|---|
| 1 | `files_local_short` | 文件 | 文件夹 |
| 2 | `server` | 服务器 | 服务器机架 |
| 3 | `iptv` | IPTV | 电视+加号 |
| 4 | `record` | 记录 | 播放三角 |
| 5 | `setting` | 设置 | 齿轮 |
| — | `server` | 服务器（分组标题） | — |
| 6+ | 用户添加的服务器 | | 绿/蓝菱形（协议图标） |

`NSWindow Frame … SidebarNavigationSplitView` = `208×646`（侧边栏默认宽 208pt），主区 `1087×646`。

### 2.2 主区顶栏

```
[ 本地 ]                       [ 本地文件 | 网络链接 ]        [ ＋▾ ] [ 历史 ] [ 刷新 ] [ ⋯▾ ]
  标题（20pt 粗体）              分段控件（SegmentedControl）        右侧动作组
```

- 分段控件即 `网络链接` 模式：输入 URL 直接播放（对应 deep link `play?url=`）
- `＋▾`：添加服务器（长按/展开列出协议类型）
- 历史：播放记录
- `⋯▾`：更多（视图/排序）

### 2.3 空状态

居中图标（显示器 + 音箱，描边蓝）+ 两个胶囊按钮：

| 文案 key | 文案 |
|---|---|
| `openLocalFile` | 打开文件 |
| `mountFolder` | 挂载文件夹 |

**复刻优先级**：P0（骨架）；空状态 P1

---

## 3. 设置窗口（10 个 tab，逐项实测）

截图：`ui-shots/prefs/01-general.png` … `10-danmaku.png`
AX 原文：`ui-shots/prefs/*.txt`
Tab 标题取自 `AXToolbar` 的 `AXButton.title`。

> **默认值**列来自本机 AX 实时值 + 偏好 plist（`~/Library/Containers/com.wuziqi.SenPlayer/Data/Library/Preferences/com.wuziqi.SenPlayer.plist`），两者一致处才标注；仅 plist 有记录的标 `plist`。

### 3.1 通用（`general`，窗口标题"数据同步"）

| 项 | 控件 | 值 / 默认 | key |
|---|---|---|---|
| SENPLAYER PRO | 标题 + 按钮 | 未购买时按钮=`purchaseProVersion`"解锁PRO"；已购=`purchasedProVersion`"已解锁" | `proVersion` / `purchasedProVersion` |
| 主题颜色 | 7 色块单选 | 蓝（默认）/ 青 / 绿 / 紫 / 橙 / 红 / 靛 | — |
| iCloud同步 | 开关 | **开** | `iCloudSyncTips` |
| ↑说明 | 文本 | `1.服务器，网盘，IPTV，URL及其播放信息支持同步` / `2.调整此开关需重启应用后才能生效` | — |
| 同步状态 | 文本 | `已同步` | — |
| 默认启动模块 | 下拉 | **文件** | `defaultRecordPage` |
| 下载文件夹 | 按钮 | — | — |
| URL Schemes | 按钮 | 打开 deep link 契约文档 | — |
| 好评引导 | 文本+按钮 | `favorableCommentTips` | — |
| 社区引导 | 文本+按钮 | `teltgramTips`"和1万多个Senplayer爱好者一起交流" | — |

### 3.2 列表（`macTab_list`）

分组说明：`对Nas/本地/网盘列表生效`

| 项 | 控件 | 默认 | 备注 |
|---|---|---|---|
| 标题栏显示文件数量 | 开关 | **关** | |
| 文件夹项目显示时间 | 开关 | **关** | `showFolderTime` |
| 圆角风格 | 开关 | **开** | |
| 缩略图铺满 | 开关 | **开** | |
| 显示媒体预览图 | 开关 | **开** | `displayVideoPreview` |
| 视频预览图时间 | 文本 | `10%` | `previewTimePosition`；`0NotGeneratingPreviewImage`="0代表不自动生成缩略图" |
| 显示播放进度 | 开关 | **开** | |
| 名称过长省略 | 下拉 | **中间** | 枚举：左边/中间/右边 |
| 菜单顶部显示完整名称 | 开关 | **关** | |
| 媒体时长 | 开关 | **开** | |
| 文件大小 | 开关 | **开** | |
| New标记 | 开关 | **开** | |
| HDR标记 | 开关 | **开** | |
| 帧率 | 开关 | **开** | |
| 分辨率 | 开关 | **开** | |
| 分辨率简化显示 | 开关 | **开** | |

脚注：`媒体强相关信息需开启预览图才能展示，部分设置对网盘文件列表无效`

### 3.3 媒体库（`macTab_mediaLibrary`）

分组说明：`对Emby/Jellyfin/Plex/fnOS服务生效`

| 项 | 控件 | 默认 |
|---|---|---|
| 显示我的媒体 | 开关 | **开** |
| 显示继续观看 | 开关 | **开** |
| 显示媒体数量 | 开关 | **开** |
| 列表中显示合集 | 开关 | **关** | `showCollection` |
| 视频首选版本 | 下拉 | **默认** |
| 名称对齐方式 | 下拉 | **左边** | `titleAlignment` |
| 显示社区评分 | 开关 | **开** |
| 显示已播放标记 | 开关 | **开** |
| 显示剩余播放数量 | 开关 | **开** |
| 显示演职人员 | 开关 | **开** | `showCast` |
| 显示选集指示器 | 开关 | **开** |
| 显示媒体信息 | 开关 | **开** |
| 显示类似作品 | 开关 | **开** |

### 3.4 播放（标题"播放设置"）

| 项 | 控件 | 默认 | key |
|---|---|---|---|
| 从上次进度播放 | 开关 | **开** | 断点续播 |
| 暂停后自动锁屏 | 开关 | **开** | `pauseAutoLock` |
| 精准定位进度 | 开关 | **关** | `accurateSeek` |
| ↑说明 | 文本 | `可提高调节进度的时间准确度，但可能影响定位的速度` | |
| 蓝光ISO播放 | 下拉 | **兼容模式** | 枚举：兼容模式/快速模式 |
| ↑说明 | 文本 | `兼容模式可解决部分网络下无法播放的问题，快速模式可以达到最佳网络性能` | |
| 快退时间 | 下拉 | **15 秒** | `OPSeconds` 同族 |
| 快进时间 | 下拉 | **15 秒** | |
| ↑说明 | 文本 | `对所有用到"快退快进"的功能均生效` | |
| 预缓存方式 | 下拉 | **不缓存** | `kCacheType` |
| ↑说明 | 文本 | `1.缓存设置太大会长时间进行网络下载和磁盘读写，导致设备发热` | |
| 使用系统代理 | 开关 | **开** | |
| ↑说明 | 文本 | `可解决代理工具未开启Tun模式(增强模式)时，无法代理播放内核的问题` | |

### 3.5 界面（标题"界面偏好"）

| 项 | 控件 | 默认 | key |
|---|---|---|---|
| 进度条圆点 | 开关 | **开** | |
| 进度条时间模式 | 下拉 | **总时长** | 枚举：总时长/剩余时长 |
| 控制栏风格 | 下拉 | **底部** | |
| 自动隐藏 | 下拉 | **3 秒** | |
| 播放视频自动进入全屏 | 开关 | **关** | `autoFullScreen` |
| 窗口比例和视频比例保持一致 | 开关 | **开** | `rememberZoomSize` 同族 |
| ↑说明 | 文本 | `关闭后可以使用鼠标自由改变窗口比例` | |
| 快退快进按钮 | 开关 | **关** | |
| 切换媒体按钮 | 开关 | **开** | |
| 截图按钮 | 开关 | **开** | |
| 开始播放提示标题 | 开关 | **开** | |
| 即将跳过片头/片尾 | 开关 | **开** | |
| 网盘限速提示 | 开关 | **开** | |

### 3.6 手势（标题"手势"）

| 项 | 控件 | 默认 | key / 说明 |
|---|---|---|---|
| 长按左方向键倍速 | 下拉 | **0.50 X** | `longPressLeftArrowToSpeedUp` |
| 长按右方向键倍速 | 下拉 | **2.00 X** | `longPressRightArrowToSpeedUp` |
| 左键单击 | 下拉 | **播放/暂停** | `clickGesture` |
| 左键双击 | 下拉 | **全屏** | `doubleClickLeft/Right` |
| 右键单击 | 下拉 | **显示/隐藏控制栏** | |
| 右键双击 | 下拉 | **全屏** | |

iOS 侧同族键（`Localizable.strings`，未在 Mac 版出现但同属该体系）给出**触屏手势全表**：

| key | 文案 | 推断语义 |
|---|---|---|
| `doubleClickCenter` | 双击屏幕中间 | 播放/暂停 |
| `doubleClickLeft/Right` | 双击屏幕两边 | 快退/快进（iOS 惯例 ±10s） |
| `leftVerticalSlidingBrightness` | 左边垂直滑动调节亮度 | 左半屏上下 = 亮度 |
| `rightVerticalSlidingVolume` | 右边垂直滑动调节音量 | 右半屏上下 = 音量 |
| `doubleFingerScreenZoom` | 双指缩放画面 | 画面缩放，配合 `rememberZoomSize` |
| `longPressScreen` | 长按屏幕 | 倍速（长按不放） |
| `doubleClickMultipleSpeed` | 双击倍速 | 自定义双击动作枚举值之一 |
| `longPressPlayPauseGesture` | 长按播放暂停键退出播放器 | 控制栏长按退出 |
| `volumeAmplification` | 音量放大 | 超过 100% 的增强（配合 `audioEnhanceTips`） |

**枚举值集合**（下拉可选项，从同族文案推断）：播放/暂停 · 全屏 · 显示/隐藏控制栏 · 快退 · 快进 · 调节亮度 · 调节音量 · 无

**复刻优先级**：P0（手势是全屏播放器的核心体验）

### 3.7 视频（标题"视频设置"）

| 项 | 控件 | 默认 | key |
|---|---|---|---|
| 首选解码模式 | 下拉 | **硬解码(HW/GPU)** | `hardDecode_short`/`softDecode_short` |
| 软件解码线程 | 下拉 | **8** | |
| 切换视频时黑屏 | 开关 | **开** | `switchHardDecode` 同族 |
| ↑说明 | 文本 | `开启后切换视频时画面不残留` | |
| 截图格式 | 下拉 | **jpg** | `JPEGQuality` |
| 保存文件夹 | 按钮 | — | |
| JPEG图像质量 | 文本 | **80%** | |
| 自动复制到剪贴板 | 开关 | **开** | |

同族未在本 tab 露出但已存在文案：`hdrMetadata`(HDR元数据) · `enableHDRVivid`(HDR Vivid) · `videoUpscaling`(AI超分辨率) · `videoFrameInterpolationScore`(评分) · `enableDolbyP7FEL`(杜比P7启用FEL) · `enableDolbyP7ToP81`(杜比P7转杜比P8.1) · `aspectRatio`(画面比例) · `scaleAspectFit`(适应屏幕) · `scaleAspectFill`(裁剪全屏) · `decodeModeTips`

`decodeModeTips` 原文：`硬解码：高效省电，基本不发热，能否硬解取决于你的设备及系统。⏎软解码：兼容性强，解码稳定`

### 3.8 音频（标题"音频"）

| 项 | 控件 | 默认 | key |
|---|---|---|---|
| 记住所选音轨 | 开关 | **开** | `rememberAudioTrack` |
| 音频首选语言 | 下拉 | **默认** | `audioFirstLanguage`（plist 值 `none`） |
| 音频增强 | 开关 | **开** | `audioEnhance` |
| ↑说明 | 文本 | `音量到达100%后，按键盘的↑持续放大音量` | `audioEnhanceTips` |
| 多声道模式 | 开关 | **关** | |
| ↑说明 | 文本 | `部分双声道设备开启此模式后播放多声道影片可能出现只有背景声无人声的情况，关闭即可恢复正常` | |

同族：`globalAudioDelay`(全局音频延迟) · `backgroundAudio`(后台音频) · `enablePCMRelayiOS`(原生音频) · `audioRenderTips` · `numberOfAudioChannels` · `channelLayout`

### 3.9 字幕（`macTab_subtitle`）

| 项 | 控件 | 默认 | key |
|---|---|---|---|
| 记住所选字幕 | 开关 | **开** | `rememberSubtitleTrack` |
| 字幕首选语言 | 下拉 | **默认** | `kSubtitleFirstLanguage`=`none` |
| 外挂字幕加载规则 | 下拉 | **同文件夹同名字幕** | `subtitleLoadingRule`（另有 `subtitleLoadingAll`="同文件夹所有字幕"） |
| 字幕防遮挡 | 开关 | **关** | `subtitleMask` |
| ↑说明 | 文本 | `仅对悬浮控制栏生效` | |
| 比例修正 | 开关 | **开** | |
| HDR字幕 | 开关 | **开** | |
| 忽略ASS字幕样式 | 开关 | **关** | `ignoreEffectsTips` |
| 忽略SRT字幕样式 | 开关 | **关** | |
| ↑说明 | 文本 | `忽略字幕样式后将不再使用libass进行渲染` | |
| 字体 | 下拉 | **SF Pro** | `font` |
| 加粗 | 开关 | **关** | |
| 斜体 | 开关 | **关** | |
| 字体颜色 / 背景颜色 / 描边颜色 | 颜色面板 | — | |
| 描边大小 | 文本 | **1.0** | |
| 阴影颜色 / 阴影大小 | 面板 / 文本 | **0.0** | |
| 脚注 | 文本 | `部分自定义样式需要开启忽略本身样式才能生效` | `customStyleTips` |

同族：`subtitleStyleMovedTips`="字幕样式调整已迁移至播放页面，支持实时调整" · `subtitleChineseConversion` · `subtitleToTraditional` · `primarySubtitleScale`(大小缩放[主]) · `secondarySubtitleShort`(副字幕) · `dualSubtitleSpacing`(双字幕间距) · `subtitleVerticalOffset`(垂直偏移) · `subtitleCache` · `showSubtitleSearchHistory`

### 3.10 弹幕（`danmaku`）

| 项 | 控件 | 默认 | key |
|---|---|---|---|
| 启用弹幕 | 开关 | **开** | `enableDanmaku` |
| ↑说明 | 文本 | `关闭后不显示弹幕按钮并且不加载弹幕` | `disableDanmakuTips` |
| 弹幕字体 | 下拉 | **SF Pro** | `danmakuFontSize` 同族 |
| 中文简繁转换 | 下拉 | **不转换** | |
| 底部弹幕转浮动弹幕 | 开关 | **关** | |
| 短视频不自动加载弹幕 | 开关 | **开** | |
| ↑说明 | 文本 | `视频时长低于10分钟` | |
| 关键词 | 文本框 | — | `输入关键词` + `使用逗号","进行分割` |
| API 列表 | 名称/地址 ×N 行 | 用户自填（本机有 3 条已配置） | 名称占位 `API-1…` |

**弹幕 API 协议入口**：脚注 `弹幕API协议:` + 按钮（打开协议文档）。

弹幕参数（偏好 plist 实测，非 UI 项）：`kDanmuArea=0.15`（显示区域高度比）· `kDanmuSize=25`（字号）· `kDanmuSpeed=0.5` · `permCloseDanmu=true`

**复刻优先级**：P2（协议层可后置，UI 入口先留）

---

## 4. 播放器窗口（截图 `ui-shots/06-fullscreen-live.png`）

**顶部栏**（`播放/暂停` 后自动隐藏）：

```
左侧：窗口控制                       中央：文件名 senplayer-test.mp4          右侧：[ 截图 ] [ 列表/☰ ]
```

**底部栏**（从左到右）：

| 位置 | 元素 | 对应 key |
|---|---|---|
| 1 | ✕ 关闭 | `close` |
| 2 | ⏸/▶ 播放暂停 | `play/pause` |
| 3 | ⏭ 下一个 | `next` |
| 4 | `01:16` 当前时间 | — |
| 5 | ━━━━●━━━ 进度条（带圆点，见"界面→进度条圆点"） | — |
| 6 | `02:00` 总时长（可切剩余时长） | — |
| 7 | 🔊 音量图标 + 音量条 | `volume` |
| 8 | **CC** 字幕 | `subtitleTrack` |
| 9 | 🎵 音轨 | `audioStream` |
| 10 | 🎧 音频/增强 | `e_Audio` |
| 11 | ⋯ 更多 | — |

**OSD 左上角**（默认显示，可关）：实时码率（如 `833`）、当前帧率（如 `2275`），对应 `kShowOSDInternetSpeed` / `kOSDPlayTime` / `realTimeFrameRate`

**菜单/full-screen 内功能全表**（来自 `Localizable.strings` + `播放` menu bar）：

字幕（外挂/内嵌/在线） · 音轨 · 倍速 · 画面比例 · 解码方式切换（`switchHardDecode`/`switchHardDecodeFailed`/`switchSoftDecodeUnsupportedForDolby`） · 画面调节 · 截图 · 投屏 · 弹幕 · 媒体信息（`I`） · 全屏（`F`）

---

## 5. 默认值总表（偏好 plist 实测）

用户 `~/Library/Containers/com.wuziqi.SenPlayer/Data/Library/Preferences/com.wuziqi.SenPlayer.plist`：

```
kIsAutoPip            = false    kIsGestureMove      = false   kIsGestureZoom = false
kIsProgressAlwaysOn   = false    kIsShowBasicMediaInfo = false kUseAppBrightness = false
kOSDPlayTime          = false    kShowOSDInternetSpeed = false kShowOSDPower = false
kSkipOP               = false    kSkipED             = false   kTraditionalToSimplified = false
kScaleMode            = "scaleAspectFit"        kContentMode = 1
kAudioFirstLanguage   = "none"   kSubtitleFirstLanguage = "none"
kFirstResolution      = "none"   kCacheType          = "none"
kDanmuArea            = 0.15     kDanmuSize          = 25      kDanmuSpeed = 0.5
kEmbyMoviesSortBy     = "productionYear"  kEmbyMoviesSortOrder = "dscending"   ← 原样，平台会照抄拼写
kShowSidebar          = true     kFont = true        kVolume = 1.03
kMainWindowSizeHeight = 646      kMainWindowSizeWidth = 1087
```

**投影/统计键**（非设置）：`launchCountKey` · `kPlayTotalTime`（累计秒） · `mkvCount` · `k4KCount` · `firstLaunchDateKey` · `formalProDate` · `ProId`

---

## 6. 文案索引

1255 键的完整 zh-Hans 表已导出为 JSON（`_re/strings/zh-Hans.json`，同名 en 版可对照）。

聚类规模：

| 面 | 键数 |
|---|---|
| 列表/媒体库 | 93 |
| 播放器控件 | 84 |
| 视频/画质 | 76 |
| 设置项与提示 | 73 |
| 字幕 | 62 |
| 协议/服务器 | 59 |
| 提示/错误 | 41 |
| 播放手势 | 29 |
| 音频 | 29 |
| 会员/购买 | 27 |

**纪律**：复刻时**只把这些当作"哪个 key 出现在哪个位置"的索引**，界面文案自行撰写；下方脱敏表里出现的用户私有数据（服务器名、弹幕 API 地址）不得进入代码库。

---

## 7. 脱敏说明

本目录下截图均来自取证机，含以下**个人数据，复刻代码中绝不引用**：

- 侧边栏服务器列表（12 个用户自建媒体服务器名称）
- 弹幕 tab 的 3 条已配置 API 地址与 token
- 本机 iCloud 状态、购买状态、`ProId`

---

## 8. 复刻优先级

| 优先级 | 范围 | 依赖 |
|---|---|---|
| **P0** | 三窗口骨架、侧边栏五项、播放器控制栏全元素、手势全表（双击三分区/垂直左右/双指缩放）、`Space ←→↑↓ +/- 0 F I` 命令集、断点续播、`accurateSeek` | 无（纯 UI/交互层，当前实现可直接补） |
| **P1** | 设置窗口 10 tab、列表/媒体库两个 tab 的 13+16 个开关、解码模式切换、外挂字幕加载规则枚举、iCloud 同步开关（iOS 侧即 CloudKit） | 需先定持久化模型 |
| **P2** | 弹幕（协议 + 渲染）、投屏 DLNA/AirPlay、网盘（115/阿里/百度/123）、SMB/FTP/SFTP、Plex | 协议层，逐个独立 |
| **P3** | HDR Vivid / AI 超分辨率 / AI 视频插帧 / 杜比 P7 FEL | **依赖内核**（KSPlayer + FFmpegKit 路线）；见 `HANDOVER.md` 第 35 行证据：参考实现自身的 FFmpeg 来自 `…/TracyPlayer/Modules/FFmpegKit/` |

---

## 9. 实现进度

### 1.1.0 已落地（本规格 P0 + P1）

| 项 | 落地位置 |
|---|---|
| 十个偏好分组，含列表 16 项 / 媒体库 13 项开关 | `ios/SenPlay/Preferences.swift`、`android/…/ui/SettingsScreen.kt` |
| 播放器手势：双击三分区、左右垂直亮度/音量、水平 seek、捏合缩放、长按倍速 | `ios/SenPlay/PlayerScreen.swift`、`android/…/player/PlayerActivity.kt` |
| 控制栏全元素：关闭 / 切换媒体 / 时间 / 进度条（圆点可选）/ 倍速 / 字幕 / 音轨 / 音量 / 更多 | 同上（两端的控制栏都是自绘，不接管平台的默认控制器） |
| `accurateSeek`、`timeMode`、`autoHideSeconds`、`fastSeekButtons`、`screenshotButton` | 播放器与偏好面板读同一组键 |
| 双端键串对齐 | iOS `@AppStorage` 与 Android `LibraryStore.prefXxx` 使用 **相同 key**，可直接互通 |

iOS 侧为此把渲染面从 `VideoPlayer` 换成裸 `AVPlayerLayer` 宿主（`PlayerSurface`），因为前者会强制安装系统控制栏。

### 仍待推进

1. 键盘/遥控命令集（`Space ← → ↑ ↓ + - 0 F I`）——iPadOS 与遥控器场景
2. OSD 叠层（实时码率 / 帧率 / 播放时间）——依赖内核上报的统计量
3. 媒体库 tab 的 Plex / fnOS 协议枚举入口（当前 Emby / Jellyfin / 飞牛已通）
4. 弹幕协议层（UI 与偏好已就位，搜索接口与渲染未接）
5. 投屏（DLNA / AirPlay）、SMB / FTP / SFTP、网盘四家
6. **内核**：`.github/kernel-probe` 正在验证 KSPlayer + FFmpegKit 的设备编译链路；
   一旦通过，播放器只换解码路径，控制栏与手势层不动（KSPlayer 2.3.4 只发布内核、不含 UI，
   正好契合这一分工）
