# 在 Mac 上接手 SenPlay

## 0. 先解压这个包（AES-256 加密，文件名也加密）

```bash
brew install sevenzip              # 提供 7zz 命令
7zz x senplay-mac-kit-full.7z      # 会提示输入密码
```

图形工具也行：Keka（免费），或 7-Zip 官方 macOS 版（7-zip.org 有 arm64 下载）。

解压后结构：

```
senplay-mac-kit-full/
  HANDOVER.md           交接说明（含证据与待办）
  SETUP-MAC.md          本文件
  UI-EVIDENCE-PLAN.md   真机 UI 取证计划
  evidence/             逆向原始取证
  tools/                Mach-O / APK / IPA 校验脚本
  keys/                 Android 发布签名密钥（敏感）
  sample/               样本 IPA 本体（80 MB）
```

先核对样本没传坏：

```bash
cd senplay-mac-kit-full/sample && shasum -a 256 *.ipa
# 期望：
# 29355c399e4d6fa3f49bea87d5bd8acb6d3a3e326c7e0037471767277fad00b5
```

## 1. 拉代码

代码不需要打包 —— 仓库是 public，直接 clone。

```bash
git clone https://github.com/chenweitian423/senplay.git
cd senplay
git config user.name "zihen423"
git config user.email "zihen423@gmail.com"
```

这个 kit 里有四样东西，都是仓库里没有或不便公开的：
- `sample/` —— 样本 IPA 本体（80 MB，sha256 见上）
- `keys/` —— Android 发布签名密钥（换成别的密钥会导致老用户无法覆盖升级）
- `evidence/` —— 逆向取证的原始 JSON（3 MB）
- `tools/` —— 我自己写的 Mach-O / APK / IPA 校验脚本

---

## 1. iOS：本地构建（CI 之外多一条腿）

```bash
xcode-select --install            # 或从 App Store 装完整 Xcode
brew install xcodegen
cd ios
xcodegen generate
xcodebuild -list -project SenPlay.xcodeproj

# 出无签名包，命令与 CI 完全一致
xcodebuild -project SenPlay.xcodeproj -scheme SenPlay -configuration Release \
  -sdk iphoneos -destination 'generic/platform=iOS' -derivedDataPath build \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY="" CODE_SIGN_ENTITLEMENTS="" build
```

有 Xcode 之后就能真机调试了，这是 Windows 上完全做不到的部分。

## 2. Android：本地构建 + 真机调试

```bash
brew install --cask temurin@17
brew install --cask android-commandlinetools
sdkmanager "platforms;android-35" "build-tools;35.0.0"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

# 发布签名（用 kit 里那把钥匙，别名/口令见 keys/credentials.txt）
export APP_KEYSTORE="$PWD/../keys/senplay-release.p12"
export APP_KEYSTORE_PASSWORD='见 keys/credentials.txt'
export APP_KEY_ALIAS='见 keys/credentials.txt'
export APP_KEY_PASSWORD='见 keys/credentials.txt'

cd android
brew install gradle          # 或直接用 CI 同款 8.9
gradle :app:assembleRelease --no-daemon
```

## 3. REA：在 Mac 上它才是完整版

Windows 上 REA 的 Apple 工具链全部不可用（`unsupported_provider: Native macOS utilities require macOS`）。到了 Mac 这些才解锁：

```bash
# 需要 Node 22.19+ / 24.11+ / 26+
npx rea-agents@latest setup
```

解锁的能力：

| 命令 | 用途 |
|---|---|
| `rea inspect-macho` | Mach-O 结构、load commands、注入痕迹 |
| `rea inspect-signature` | 签名链校验（能直接证伪 ESign 的 fake-sign） |
| `rea inspect-plist` | Info.plist 结构化 |
| `rea demangle-swift` | Swift 符号还原（当前主二进制被 strip，需要配合 Hopper） |
| `rea inspect-native-dispatch-metadata` | ObjC / Swift 运行时派发元数据 |
| `rea project-apple-application-graph` | IPA 全量 inventory 投影 |

要反汇编 `checkPurchasePro` / `saveVerifyData(isPro:)`（**唯一没闭合的问题：主二进制到底有没有被 patch**），还需要 Hopper 或 Ghidra —— `rea setup` 可以在你批准后装 Hopper（首次运行可能让你选 demo 模式或激活 license）。

## 4. GitHub CLI（继续用 CI 出包）

```bash
brew install gh
gh auth login          # 账号 chenweitian423
gh run list --repo chenweitian423/senplay
gh workflow run android.yml --repo chenweitian423/senplay -f create_release=true
```

## 5. 把工作区放好

```bash
mkdir -p _re
cp -r ../evidence _re/evidence
cp -r ../tools    _re/tools

# 样本 IPA 需要单独拷过来（80 MB，kit 里没放）：
#   SenPlayer_6.2.1_解锁会员(6).ipa
# 解包目录（121 MB）不用带，Mac 上重新解一次即可：
#   mkdir -p _re/extract && tar -xf input.ipa -C _re/extract
```

## 6. 样本 IPA 的 sha256（用于确认拷过去的是同一份）

```
29355c399e4d6fa3f49bea87d5bd8acb6d3a3e326c7e0037471767277fad00b5
```

---
kit 里含签名私钥，别丢公开网盘或聊天群里。仓库里的 CI 已经能用这把钥匙出包，
所以本地拿它主要是为了「不经过 CI 直接出签名包」。
