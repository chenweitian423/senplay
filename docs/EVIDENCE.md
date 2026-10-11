# 逆向证据（参考样本 SenPlayer 6.2.1）

样本：`SenPlayer_6.2.1_….ipa`，sha256 `29355c399e4d6fa3f49bea87d5bd8acb6d3a3e326c7e0037471767277fad00b5`
主二进制 sha256 `b2380394a6af7e7f42b08726e963b7930e366c3c09481af498bc95f20ffa8dce`（arm64, 61,914,192 B）

本文件是结论摘要，供实现对照。**未闭合项已在 Mac 侧结题**：见 `PATCH-VERDICT.md`（主二进制 patch 判定），
界面规格见 `UI-SPEC.md`。

## 包的构造

- 主二进制：arm64、**已砸壳**（`LC_ENCRYPTION_INFO_64.cryptid=0`，`cryptoff=16384` / `cryptsize=53772288`）
- 注入：83 条 `LC_LOAD_DYLIB`，最后两条为 `@executable_path/Frameworks/zxinject.dylib`、`…/KAlertSenPlayer.dylib`，均为 `weak`
- **签名：未被重签**。CMS 仍链到 `Apple iPhone OS Application Signing` → `Apple Root CA`，
  `designated_requirement` 完整；`SignedByEsign` 只是 61 字节的文本标记
- `embedded.mobileprovision` 缺失 → 安装侧需宿主签名工具补 profile
- 保留原 `SC_Info/`（App Store 购买信息），说明改自原包体
- `Info.plist` 在签名之后被改过（slot -1 哈希不符）；全部 57 个顶层键中**没有** StoreKit/Testing 相关字段

## 会员（官方实现）

- StoreKit 2；终身买断产品 `com.wuziqi.SenPlayer.LifeTimePro` / `.LifeTimeProIos` / `.LifeTimeProTV` / `.LifeTimeProMac` / `.LifeTimeUpgrade`
- 判定锚点：`StoreKit2Manager` / `checkPurchasePro` / `saveVerifyData(isPro:)`（源码路径 `SenPlayer/Util/Purchase/*.swift`，来自编译器内嵌字符串）
- 判定链完整：`Transaction.currentEntitlement(for:)` · `.all` · `.latest(for:)` · `.updates` · `.finish()` 全部存在且可达
- entitlements 为 Apple 发行版，**不含** `com.apple.developer.storekit.testing`

## 两个注入库

| 库 | 结论 |
|---|---|
| `zxinject.dylib` | App Group 容器重定向 + `NSUserDefaults` hook + fishhook `rebindSecFuncs` 重绑 `SecItem*`；与购买判定无关 |
| `KAlertSenPlayer.dylib` | 公告/推广弹窗 SDK（`KAlertRemoteConfig`、`KAlertTrackEvent`、JSON 签名校验） |

## 对早期结论的三处修正

| 早期结论 | 修正 | 依据 |
|---|---|---|
| 符号表 strip 是改造动作之一 | **Apple 发布流程常态**。官方 Mac 版（App Store 正版、签名完整）主二进制同样只剩 48 个已定义符号 | `nm -a` 对照 |
| 包内被塞入 `PurchaseTestConfiguration.storekit` | **官方自带**。官方 Mac 版 `Contents/Resources/` 下三份 `.storekit` 与样本 IPA 内**逐一字节相同**（sha256 `a68b2445…` / `426018cf…` / `e441a37d…`） | 逐文件 `cmp` |
| `SignedByEsign` 重签 | **未重签**，CMS 是 Apple 原始链 | `rea inspect-signature` |

## 复刻范围

URL Scheme 契约 + 本地/网络播放 + 外挂字幕 + 断点续播 + WebDAV/Emby 浏览与直链 + URL 列表与历史；
体验层覆盖见 `UI-SPEC.md` 第 8 节优先级表。

## 复现工具

`_re/tools/`（不在仓库内，随取证工作区提供）：

| 脚本 | 作用 |
|---|---|
| `codesig_diff.py` | 读取内嵌签名，逐页重算哈希，输出偏离签名的文件区间 |
| `stub_map.py` | 建立 `__stubs` → `__got` → 符号名的完整映射（3162 条） |
| `scan_storekit.py` | 扫描全部 `__text` 指令，按函数聚合外部符号调用点 |
| `probe.py` | 指定地址范围的带符号批注反汇编 |
| `find_refs.py` | 定位 ADRP+ADD/LDR 引用，并用 `LC_FUNCTION_STARTS` 反查所属函数 |
| `dump_ax.applescript` | 递归导出目标进程的可访问性树（控件类型 + 实时值） |
| `harvest_prefs.py` | 逐 tab 点击设置窗口并采集 AX + 截图 |
