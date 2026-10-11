# Mac 真机 UI 取证计划

样本机上装着的 SenPlayer 是 Windows 上完全取不到的证据面。静态分析已经到天花板（符号 strip、REA 在 Windows 上不可用），真机是唯一能把"字符串"变成"界面规格"的途径。

## 为什么要做

我从包里提取出 `zh-Hans.lproj/Localizable.strings` 的 **1255 条文案**，但那只是 key/value 列表，不知道：

- 哪条文案出现在哪个页面、哪个控件、什么状态下
- 设置项的默认值与枚举值（`defaultSetting`、`advancedSetting`、`accurateSeek`、`audioRenderTips`…）
- 手势的方向与幅度映射
- 控件层级与隐藏逻辑

这些东西无法从二进制反推，但**照着屏幕抄一遍就有**。

## 怎么取证

**工具**：Mac 自带 QuickTime（录屏）、`Cmd+Shift+4` 截图、`Cmd+Shift+5` 录窗口。iPhone 投屏 + 录屏也可以。

**方法**：录屏 + 关键帧截图，每屏产出「截图 + 元素清单」；再与 `Localizable.strings` 交叉核对，把每个文案 key 标到具体位置。

## 逐屏清单

1. **首页 / 媒体库**：分类结构、排序方式、列表/网格切换、空状态文案、右上角入口
2. **服务器添加流程**：每种类型（WebDAV / SMB / FTP / Emby / Jellyfin / 飞牛）的表单字段、必填项、错误提示、备用线路（`addressN`）的填写方式
3. **服务器浏览页**：列表样式、排序/筛选、文件夹图标、播放按钮出现条件、返回层级、多选操作
4. **播放器**（最关键）：控件自动隐藏/呼出逻辑、顶部栏元素、底部栏元素、进度条样式与章节标记
   - 手势：**双击左/右/中间**分别做什么；水平拖动调什么；**垂直左右两侧**分别调什么（亮度/音量/进度）；单指/双指差异
   - 手势参数的**幅度**：拖满屏宽对应多少秒、垂直滑动的灵敏度
   - 菜单项全表：字幕（外挂/内嵌/在线）、音轨、倍速（可选值列表）、画面比例、解码方式（硬解/软解切换的入口与文案）、画面调节、截图、投屏、弹幕
   - 画中画、后台播放、锁屏控制的行为
   - `accurateSeek`（精准定位）打开/关闭时的实际差异
5. **设置页全表**：每一项的标题、说明文案、默认值、可选值。已知线索：`advancedSetting`（高级设置）、`p_Advanced`（购买高级会员）、`defaultSetting`（恢复默认设置）、`accurateSeek`、`JPEGQuality`、`0NotGeneratingPreviewImage`、`HDRTag`、`OSDDisplay`、`OPSeconds`/`EDSeconds`（片头/片尾时长）、`rememberZoomSize`、`rememberSubtitleTrack`、`rememberAudioTrack`
6. **哪些功能是 Pro 门控**：把 `advancedSetting` 区域里被锁的项记下来，与 `purchaseProVersion` / `moreBenefits` 对照——这直接告诉我们会员到底卖什么
7. **投屏 / 网盘 / 弹幕入口**：DLNA 与 AirPlay 的入口位置与设备列表页；115 / 阿里 / 百度 / 123 的登录方式与限速提示（`d123PanOverVolume` 提到"123 网盘每日 1GB 限制，开通会员不限速"）；弹幕的 API 配置界面
8. **URL Scheme 边界行为**：用 `senplayer://x-callback-url/play?url=...&position=915` 实测
   - 不带 `sub` 时会怎样、带不存在的 `sub` 时会怎样
   - `position` 越界/非法值的处理
   - `x-success` 回调的确切字段与时机
   - `saveURL` 是否真的覆盖同地址条目

## 产出格式

`UI-SPEC.md`，每节：**截图文件名 → 元素清单（层级/位置/文案 key/默认值）→ 交互规则 → 复刻优先级**。截图放 `ui-shots/`，命名用 `播放器-手势-垂直左.png` 这种能自解释的名字。

## 纪律

- 截图**脱敏**：遮掉 Apple ID、服务器地址、私人文件名、网盘账号
- 只记录**行为与布局**，不要复制它的图标、插画、字体文件或大段文案原文；文案只作为"这条 key 对应这个位置"的索引
