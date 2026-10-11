# 主二进制 patch 收口报告（P0 未闭合项结题）

样本：`SenPlayer_6.2.1_解锁会员(6).ipa`
主二进制 sha256：`b2380394a6af7e7f42b08726e963b7930e366c3c09481af498bc95f20ffa8dce`（62 B → 61,914,192 B, arm64 thin）
取证主机：macOS 26.6.2 / Apple Silicon；REA 6.4.0（provider `native-macos`, `hopper`）

---

## 结论

**主二进制未被就地 patch。**

"解锁"不来自二进制改动。可归因的改造只有三处，全部在 `__TEXT` 头部与注入层，不触及任何判定逻辑。

---

## 证据 1：逐页哈希比对（决定性，可复算）

签名 blob 仍携带 **Apple 原始 CMS 签名**（见证据 2），因此 CodeDirectory 里的 14737 个 4 KiB 页哈希代表**砸壳前、未经改造**的原始文件状态。逐页重算并与存储值比对（`tools/codesig_diff.py`）：

```
directory v=0x20500 hashSize=32 pageSize=4096 codeLimit=60361232 slots=14737
mismatching pages: 13131 / 14737
  pages     0-2      file[0x0000000:0x0003000]     12288 B
  pages     4-13131  file[0x0004000:0x334c000]  53772288 B
total mismatching bytes: 53784576 of 60361232 (89.10%)
```

两段不匹配区间与改造动作**逐字节对应**：

| 区间 | 字节数 | 对应改造 |
|---|---|---|
| `file[0x0 : 0x3000]` | 12,288 | Mach-O header / load commands：追加 2 条 `LC_LOAD_DYLIB`、`LC_ENCRYPTION_INFO_64.cryptid` 置 0、`ncmds`/`sizeofcmds` 重算 |
| `file[0x4000 : 0x334c000]` | 53,772,288 | **精确等于 `cryptsize=53772288`、起点精确等于 `cryptoff=16384`** → 砸壳解密区（磁盘密文 → 明文） |

**区间之外全部匹配**，包括：

- `__TEXT` 尾部 4 页（`file[0x334c000 : 0x3350000]`）
- `__DATA_CONST`：`__got`、`__const`、`__objc_classlist`、`__cfstring`
- `__DATA`：`__objc_const`（**类的方法列表与实现指针**）、`__objc_data`、`__data`
- `__LINKEDIT`：符号表、动态符号表、字符串表、bind/export 表、`LC_FUNCTION_STARTS`

⇒ **方法表替换、vtable patch、GOT 劫持、符号重定向、`__swift5_*` 元数据改写这几类 patch 形式已被逐一排除**——它们必然落在上述已匹配区间内。

同时 `Info.plist` 与 slot -1 哈希不符：

```
sha256(Info.plist) = 38222684d525ee535c775afa7737c0092b0667910e367e5cfe207d039687fb9e
slot -1            = 2ba97cc7e99801d64ae2d75b66aaa730773ff834ba73c599d8f70943fd5f2382
```

`Info.plist` 在签名之后被改过（`codesign --verify` 亦报 `invalid Info.plist`），但主二进制代码段不受影响。全部 57 个顶层键中**没有**任何 `StoreKit` / `Testing` 相关字段。

## 证据 2：签名链仍是 Apple 原始签名（REA 复证）

```
rea inspect-signature extract/Payload/SenPlayer.app
  signed            = true
  identifier        = com.wuziqi.SenPlayer
  team_identifier   = N43L2KH7JD
  cdhashes          = [a5005f8142e6bf8b8d54374bf78e552873185a99]
  hash_algorithms   = [sha1, sha256]
  authorities       = ["Apple iPhone OS Application Signing",
                       "Apple iPhone Certification Authority",
                       "Apple Root CA"]
  designated_requirement = anchor apple generic and certificate leaf[field.1.2.840.113635.100.6.1.3]
                           and identifier "com.wuziqi.SenPlayer"
  hardened_runtime  = false
```

**打包者没有重签。** 签名 blob（含 CodeDirectory + CMS）是 App Store 原件，只被改了 `Info.plist`。

- bundle 里那个 61 字节的 `SignedByEsign` 只是**文本标记文件**，不是签名事实；
- `embedded.mobileprovision` 缺失 ⇒ 安装侧依赖宿主签名工具补 profile；
- entitlements 仍是 Apple 发行版：`aps-environment=production`、`iCloud.com.wuziqi.SenPlayer`、`group.com.wuziqi.SenPlayer`，**没有 `com.apple.developer.storekit.testing`**。

⇒ 支撑 HANDOVER 中"StoreKit Testing 路径不成立"的判断：该 entitlement 不存在，`PurchaseTestConfiguration.storekit` 在真机上不会被激活。

## 证据 3：会员判定逻辑完整（函数级）

对 `__stubs`（3162 条，步长 12 B）→ `__got`（4645 条 bind）→ 符号名建立完整映射后，扫描全部 1,991,559 条 `__text` 指令，定位每一个 StoreKit 调用点：

```
_$s8StoreKit11TransactionV18currentEntitlement3forAA18VerificationResultOyACGSgSS_tYaFZ   calls=1  site=0x10016de4c  func=0x10016de1c
_$s8StoreKit11TransactionV3allAC12TransactionsVvgZ                                        calls=1  site=0x10016b318  func=0x10016a968
_$s8StoreKit11TransactionV6latest3forAA18VerificationResultOyACGSgSS_tYaFZ                calls=1  site=0x10016ddec  func=0x10016dd2c
_$s8StoreKit11TransactionV7updatesAC12TransactionsVvgZ                                    calls=1  site=0x10016f4d0  func=0x10016f3c4
_$s8StoreKit11TransactionV6finishyyYaF                                                      calls=1  site=0x10016a590  func=0x10016a4dc
_$s8StoreKit7ProductV8purchase7optionsAC14PurchaseResultOShyAC0F6OptionVG_tYaKF             calls=1  site=0x10016a250  func=0x100169e70
```

`checkPurchasePro` 的 async 状态机（帧恢复点 `0x10016dcf0` 起）：

```asm
0x10016dc74  strb w1,  [x22, #0x90]      ; 入口：把 Bool 入参写进 async 帧
0x10016dcf4  sub  sp, sp, #0xa0
0x10016dd14  ldrb w19, [x22, #0x90]      ; 恢复：读回该入参（非常量）
0x10016dd84  cmp  w19, #1
0x10016dd88  b.eq #0x10016ddf0           ; ==1 → currentEntitlement 路径
0x10016dd8c  cbnz w19, #0x10016de50      ; !=0 → 处理既有交易对象
0x10016ddec  b    #0x10295dbbc           ; === StoreKit.Transaction.latest(for:)
0x10016de4c  b    #0x10295db14           ; === StoreKit.Transaction.currentEntitlement(for:)
```

判定分支的输入是**函数入参**（`strb w1,[x22,#0x90]` → `ldrb w19,[x22,#0x90]`），不是编译期常量；两条分支各自尾调用真实的 StoreKit 2 async API，并带完整 `_swift_task_alloc` / continuation 帧协议（`stp x22, x9, [x0]`）。`saveVerifyData` 路径同样在使用 `SwiftUI.AppStorage.wrappedValue` 做本地持久化。

**反向验证**：从 `LC_FUNCTION_STARTS` 恢复的 **164,163 个函数入口**逐一检查首 16 字节，`mov w0,#1; ret` / `mov w0,#0; ret` 类单指令返回桩数量为 **0**。

---

## 残留不确定度（明示）

`__text` 整段位于砸壳区，其磁盘字节与签名哈希不可比，因此"没有 patch"的证明来自**结构性与语义性证据**，不是逐字节同源比对：

1. 注入的两个 dylib 均为 `weak` 链接，作用面是 App Group 容器重定向 / Keychain 重绑定（`zxinject`）与公告弹窗 SDK（`KAlert`），不触及 StoreKit；
2. 判定链上每一个 StoreKit API 调用点都在，且状态机帧协议自洽；
3. 若在此状态下 patch 判定逻辑，唯一可行形式是替换 `__text` 内的指令，但保留调用点会得到自相矛盾的代码；而删除调用点会立刻被证据 3 的调用点清单暴露。

**要把残留不确定度压到零，只剩一条路**：取得同一版本未改造的 App Store 明文二进制做逐字节 diff。加密原包的 `__TEXT` 不可读，此路在现有材料下不可达。

---

## 对既有结论的修正

| 原结论（HANDOVER / EVIDENCE.md） | 修正 |
|---|---|
| "符号表被 strip" 列为四层改造之二 | **是 Apple 发布流程常态**。官方 Mac 版（App Store 正版安装，签名完整）主二进制同样只剩 48 个已定义符号，其余全为 undefined external |
| "塞入测试用 StoreKit 配置" `PurchaseTestConfiguration.storekit` | **官方自带**。官方 Mac 版 `Contents/Resources/` 下同名文件与之**字节相同**（sha256 `e441a37d…`），且 `PurchaseConfiguration.storekit`、`PurchaseConfiguration(China).storekit` 三份全部一致 |
| "SignedByEsign 重签" | **未重签**。CMS 仍链到 Apple Root CA（证据 2），仅 `Info.plist` 被改 |

三份 `.storekit` 的字节同一性直接证明：它们是被开发者误打包进 App 资源的 Xcode 工程文件，不是为解锁而注入的东西。

---

## 复算方式

```bash
cd _re
python3 tools/codesig_diff.py extract/Payload/SenPlayer.app/SenPlayer   # 证据 1
python3 tools/stub_map.py extract/Payload/SenPlayer.app/SenPlayer bind.txt stubs.json
python3 tools/scan_storekit.py extract/Payload/SenPlayer.app/SenPlayer stubs.json storekit  # 证据 3
python3 tools/probe.py extract/Payload/SenPlayer.app/SenPlayer bind.txt 0x10016dc60:0x10016df00
rea inspect-signature extract/Payload/SenPlayer.app                    # 证据 2
```
