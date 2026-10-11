# SenPlay 交接说明（Mac 侧接续）

## 目标

用 clean-room 方式做一个对标 SenPlayer 的 iOS + Android 播放器：参考它的**可观察行为**（URL Scheme 契约、界面与交互、协议接入方式、会员门控的设计），不使用它的任何代码、图片或文案资源。

## 样本与已得结论

样本：SenPlayer 6.2.1 的第三方改造包，sha256 `29355c399e4d6fa3f49bea87d5bd8acb6d3a3e326c7e0037471767277fad00b5`

包体构造（四层，均有铁证）：

1. 主二进制 arm64、**已砸壳**（`LC_ENCRYPTION_INFO_64.cryptid = 0`）、符号表被 strip（5448 条全为 undefined external）
2. 83 个 `LC_LOAD_DYLIB`，**最后两条是注入的** `@executable_path/Frameworks/zxinject.dylib`、`.../KAlertSenPlayer.dylib`
3. `embedded.mobileprovision` 缺失，只有 `SignedByEsign`（轻松签 fake-sign）
4. 保留原 `SC_Info/`（App Store 购买信息），说明是在原包体上改造

**会员**：StoreKit 2 终身买断。产品 ID `com.wuziqi.SenPlayer.LifeTimePro` / `.LifeTimeProIos` / `.LifeTimeProTV` / `.LifeTimeProMac` / `.LifeTimeUpgrade`。判定锚点 `StoreKit2Manager.checkPurchasePro`、`saveVerifyData(isPro:)`（源码路径来自编译器内嵌字符串：`SenPlayer/Util/Purchase/*.swift`）。

**这个包的"解锁"不是它自己实现的**：包里唯一解锁向素材是 `PurchaseTestConfiguration.storekit`（产品被改成 `Consumable`、价格 6.99/11.99）——StoreKit Testing 形态，而该包连 provisioning profile 都没有，这条路径不成立。两个注入库都不碰 StoreKit：`zxinject` 是 App Group 容器重定向 + `NSUserDefaults` hook + fishhook 重绑 `SecItem*`；`KAlert` 是公告/推广弹窗 SDK。

**唯一未闭合的问题**：主二进制有没有被就地 patch 过。符号已 strip，字符串层面看不出来——需要 Hopper/Ghidra 反汇编那两个函数。

## 当前实现状态

仓库：https://github.com/chenweitian423/senplay（public，Actions 不计费）

- Android：Kotlin + Compose + Media3，已出**签名 APK**（Release `android-v1.0.0`，证书 SHA-256 `52b84a9d…6d9efc`）
- iOS：SwiftUI + AVPlayer，XcodeGen 出工程，已出**无签名 IPA**（Release `ios-unsigned`）
- 已实现：URL Scheme 深链（`x-callback-url/play`、`download`、`importserver`、`importicon`）、本地与网络播放、外挂字幕、断点续播、URL 列表与历史、WebDAV 与 Emby/Jellyfin 浏览直链
- CI：`.github/workflows/android.yml`（Gradle 8.9 + JDK 17 + apksigner 证书指纹断言）、`ios.yml`（macos-15 + XcodeGen + 无签名 IPA）

**离"完全复刻"的差距**：

- **播放内核**：没有 FFmpeg / libass / libplacebo → iOS 不支持 MKV/TS/AVI、无 HDR/DOVI 色调映射、无 ASS 特效字幕、无 PGS 图形字幕；Android 靠系统解码覆盖较好但同样没有 ASS/HDR 加工。**证据显示 SenPlayer 自己也没写内核**——二进制内嵌路径指向 `/Users/kintan/Documents/Github/TracyPlayer/Modules/FFmpegKit/`（KSPlayer 作者的 FFmpeg 构建脚本），复刻应对齐这条路线，而不是从零写。
- **协议**：SMB / FTP / SFTP / UPnP-DLNA / Plex / 网盘（115、阿里、百度、123）/ 弹幕 API 未做
- **交互**：手势体系、画中画、多音轨与字幕轨选择面板、剧集自动连播、海报墙、投屏

## 在 Mac 上要做的三件事

**① 收口"是否 patch"**（REA 的 Apple 工具链在 Mac 上才存在；Windows 上 `inspect-macho` / `inspect-signature` 全部返回 `unsupported_provider`）

```bash
npx rea-agents@latest setup        # Node 22.19+ / 24.11+ / 26+
rea inspect-signature <app 内的主二进制> --json     # 权威证伪 ESign fake-sign
# 装 Hopper（rea setup 可引导）后：
rea analyze <主二进制> --provider hopper --json
rea function <主二进制> checkPurchasePro --provider hopper --json
rea decompile <主二进制> <saveVerifyData 的地址> --provider hopper --json
```

判据：反汇编结果里如果 ① 函数被替换成直接返回 true（如 `mov w0, #1; ret`）、② 或原函数代码被跳转覆盖，就是被 patch 过；如果逻辑完整地走 `Transaction.currentEntitlement` 校验，就说明这个包根本没改判定，"解锁"完全来自打包者塞进去的 storekit 配置。

**② 真机 UI 取证**（Mac 上有 App，这是 Windows 上拿不到的证据面）。计划见 `UI-EVIDENCE-PLAN.md`，产出一份 `UI-SPEC.md`：把 `zh-Hans.lproj/Localizable.strings` 的 1255 条文案逐条绑到实际界面、状态与默认值上。

**③ 按 UI-SPEC 补体验层**，再决定内核接不接（内核一旦要动，就需要 macOS 上的 FFmpeg 交叉编译流水线，可以先在 GitHub Actions 的 macos runner 上验证 KSPlayer 能否拉进来编译）。
