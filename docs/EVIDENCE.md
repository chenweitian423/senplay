# 逆向证据（SenPlayer 6.2.1 参考样本）

完整报告见 `F:\codex项目\DeepSeek\work\evidence\EVIDENCE.md`（分析工作区）。
本文件保留结论摘要，供实现对照。

## 包的构造

- 主二进制：arm64、**未加密**（`cryptid=0`）、符号表已 strip（5448 条全为 undefined external）
- 注入：83 个 `LC_LOAD_DYLIB` 的最后两条是 `@executable_path/Frameworks/zxinject.dylib`、`.../KAlertSenPlayer.dylib`
- 重签：`SignedByEsign`（轻松签），**无 `embedded.mobileprovision`** → fake-sign
- 保留原 `SC_Info/`（App Store 购买信息），说明是在原包体上改造

## 会员（官方实现）

- StoreKit 2；终身买断产品 `com.wuziqi.SenPlayer.LifeTimePro` / `LifeTimeProIos` / `LifeTimeProTV` / `LifeTimeProMac` / `LifeTimeUpgrade`
- 判定锚点：`StoreKit2Manager` / `checkPurchasePro` / `saveVerifyData(isPro:)`（源码路径 `SenPlayer/Util/Purchase/*.swift`，来自编译器内嵌字符串）
- 包内被塞入 `PurchaseTestConfiguration.storekit`（产品改成 `Consumable`、价格 6.99/11.99）→ StoreKit Testing 形态

## 两个注入库

| 库 | 结论 |
|---|---|
| `zxinject.dylib` | App Group 容器重定向 + `NSUserDefaults` hook + fishhook `rebindSecFuncs` 重绑 `SecItem*`；与购买判定无关 |
| `KAlertSenPlayer.dylib` | 公告/推广弹窗 SDK（`KAlertRemoteConfig`、`KAlertTrackEvent`、JSON 签名校验） |

## 未验证

主二进制是否被就地 patch 过 —— 符号已 strip，需要 Ghidra 反汇编
`checkPurchasePro` / `saveVerifyData(isPro:)` 才能给出是/否。

## 复刻范围

URL Scheme 契约 + 本地/网络播放 + 外挂字幕 + 断点续播 + WebDAV/Emby 浏览与直链 + URL 列表与历史。
