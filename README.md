# 简记 · 纯本地记账 App

一台手机、一个 App、一个数据库。没有账号、没有服务器、没有联网权限 —— 数据只写在这台设备上。

- 技术栈：Kotlin + Jetpack Compose（Material 3）+ Room，单模块，无第三方 SDK
- 支持版本：Android 8.0（API 26）及以上，targetSdk 35
- 包名：`com.jianji.app`

## 功能

| 模块 | 内容 |
| --- | --- |
| 明细 | 按月翻页；本月结余 / 支出 / 收入汇总；按日分组的流水；点任意一笔进入编辑，可删除；顶栏胶囊可切换账本 |
| 账本 | **独立账本**：为一次旅行、一个项目单独开一本，塞进专项资金并设预算；支持改名、换图标与颜色、归档、删除；预算卡实时显示已花 / 剩余 / 超支 |
| 记一笔 | 自研数字键盘（不唤起系统输入法）；支出 / 收入 / 转账三种类型；分类宫格；日期、备注、账户；页头明写「记入哪一本」 |
| 统计 | 月/年维度切换；分类占比环形图；分类排行（金额 + 笔数 + 占比）；近六个月收支柱状图；当月每日支出图 |
| 账户 | 多账户与实时余额、总资产；新增 / 编辑 / 归档 / 恢复；期初余额 |
| 搜索 | 时间范围、类型、分类、关键词、金额区间五维筛选，结果可直接编辑 |
| 设置 | 分类自定义（改名、换图标、换颜色、新增、隐藏、删除）；**清空当前账本**；账本管理入口；数据规模概览 |

金额一律以「分」为单位的 `Long` 参与运算与存储，展示层才做千分位格式化，不存在浮点误差。

### 账本的作用域边界

引入独立账本之后，「谁跟着账本走、谁是全局的」必须有明确约定，否则「我有多少钱」会随当前账本跳变：

| 数据 | 作用域 | 原因 |
| --- | --- | --- |
| 流水（明细 / 统计 / 搜索 / 每日聚合 / 收支合计） | **按账本过滤** | 一次旅行的花销不该混进日常的「本月支出」 |
| 预算 | **每本各一份**，按该账本**全时间**累计支出计算 | 「专项资金花完了没」是累计口径，不是月度口径；转账不计入，收入不抵扣 |
| 分类 | 全局共享 | 旅行账本里的「餐饮」和日常账本里的是同一个，统计口径才可比 |
| 账户与余额 | 全局共享 | 余额回答的是「我有多少钱」，不该随当前选中的账本变化 |
| 「清空流水」 | **只清当前账本** | 有了独立账本之后，「清空全部」的语义必须收窄 |

## 液态玻璃外观

底栏、整页浮层与卡片使用真实折射的液态玻璃材质，基于开源库
**Kyant Backdrop "Liquid Glass" 2.0.0**（Apache-2.0，源码内联在
`app/src/main/java/com/kyant/backdrop/`，改动清单见该目录 `README.md`）。

三档材质，`ui/glass/GlassStyle.kt` 是唯一定义处：

- **Thick** —— 底栏、加载层。真模糊 + 折射 + 高光边缘。
- **Sheet** —— 整页浮层。更厚的模糊与色散。
- **Thin** —— 滚动区里的卡片。**只做半透底 + 上缘高光，不做逐行模糊**。

Thin 之所以不采样背板：滚动内容整体已被录进同一张 `LayerBackdrop`，
卡片若再采样就会采到自己刚画上去的那一帧 —— 结果是重影，不是折射。
同理，悬浮件（底栏、记一笔、整页浮层）必须留在背板**之外**。

运行期按系统能力自动降级（`ui/glass/GlassRuntime.kt`）：

| 档位 | 条件 | 表现 |
| --- | --- | --- |
| Liquid | API 33+ | 模糊 + AGSL 折射 + 色散 |
| Blur | API 31–32 | 模糊，无折射 |
| Solid | API 26–30 | 纯色底 + 高光描边 |

**动效一律非线性**，且遵循「从哪儿来，回哪儿去」：

- 浮层从**手指按下的那一点**长出来，关闭时缩回**同一点**。
  坐标只在打开的那一刻采样一次并冻结（`GlassOrigin.freeze()`）——
  关闭过程中绝不重新采样，否则点了浮层里的「关闭」按钮，浮层就会改成朝那个按钮缩回去。
- 图标动效的过冲来自 `dampingRatio < 1` 的弹簧本身，不是手写关键帧：
  底栏图标选中弹跳、分类图标选中「啵」一下、加号按 45° 自转（加号转成叉 = 正在记 / 关掉）、
  返回箭头先缩再弹回、月份箭头点按回弹。

## 隐私

`AndroidManifest.xml` 里**没有任何 `uses-permission`**。App 未申请 INTERNET、ACCESS_NETWORK_STATE、
READ_MEDIA_IMAGES、READ_EXTERNAL_STORAGE 等权限，因此它在系统层面就没有联网与读取照片的能力。

CI 每次构建后都会执行一次 `aapt2 dump permissions` 审计，一旦出现上述权限即构建失败。

数据落在应用私有目录的 Room 数据库 `jianji.db`。系统备份/换机迁移只导出该数据库与偏好设置
（见 `res/xml/backup_rules.xml`）。

## 工程结构

```
app/src/main/java/
├── com/kyant/backdrop/       内联的液态玻璃库（Apache-2.0，见其 README.md）
└── com/jianji/app/
    ├── data/       实体、DAO、数据库、仓储、迁移、默认分类与账户种子
    ├── domain/     金额与日期工具、类型常量（纯 Kotlin，无 Android 依赖）
    ├── ui/
    │   ├── component/  卡片、胶囊、环形图、柱状图、数字键盘、流水行
    │   ├── glass/      玻璃运行时、材质定义、玻璃表面、动效令牌、图标动效
    │   ├── screen/     明细 / 记一笔 / 统计 / 账户 / 搜索 / 设置 / 账本管理
    │   ├── theme/      暖白纸面配色与字体
    │   ├── IconRegistry.kt  「图标 key → ImageVector」注册表
    │   └── JianJiRoot.kt    底部导航 + 整屏覆盖层（含返回键处理与源点动效）
    └── vm/         ViewModel、UI 状态模型
```

设计上的一条硬规矩：数据库里只存图标的 key（字符串），真正的 `ImageVector` 由 `IconRegistry`
在运行期映射。这样以后替换或新增图标不会让历史数据失效。

`data/AppDatabase.kt` 里导出 Room schema 到 `app/schemas/`：迁移脚本必须与 Room 期望的表结构
逐字一致，导出的 JSON 就是这份「期望」的权威来源 —— 也是 `MigrationTest` 的对照物。

## 构建

仓库里的两个二进制文件（Gradle wrapper jar、签名库）以 `.b64` 形式托管，clone 后先还原一次：

```bash
bash scripts/bootstrap-binaries.sh
```

然后任选其一：

```bash
# 命令行
./gradlew :app:assembleDebug --no-daemon

# 或用 Android Studio 直接打开本目录
```

产物在 `app/build/outputs/apk/`。

## 验证

```bash
gradle :app:testDebugUnitTest   # 59 个单元测试
gradle :app:lintDebug           # 0 error
```

测试分五层（共 59 个）：

- `domain/AmountInputTest` + `DatesTest` + `MoneyTest`（20 个）—— 纯 Kotlin，钉死金额的格式化与键盘
  输入边界（千分位、小数位上限、退格、`fromCents`/`toCents` 往返、整数分模型下 0.1+0.2 不丢精度）
  以及日期区间与翻月。
- `data/LedgerDatabaseTest`（15 个）—— 用 Robolectric 起真实 SQLite 内存库，跑一遍全部手写 SQL：
  JOIN 出来的分类/账户名、账户余额的四段子查询（期初 + 收入 − 支出 − 转出 + 转入）、
  按天/按分类聚合、五维搜索、内置分类拒删、归档与恢复。
  引入账本后这里的断言**逐条保持原样**（统一用默认账本），因此它同时是
  「账本重构没有改变原有行为」的回归证据。
- `data/LedgerScopeTest`（13 个）—— 账本的作用域边界：流水互不串台、统计与搜索按账本过滤、
  清空只清自己、删除资格（内置 / 占用 / 可删）、归档与恢复、分类与账户跨账本共享、
  余额跨账本合计、预算只计支出且**转账不计入**。
- `data/MigrationTest`（3 个）—— v1 → v2 迁移。先在磁盘上手工造一个真正的 v1 库，
  再让 Room 带着 `MIGRATION_1_2` 打开；**Room 会在迁移跑完后自己校验表结构**，
  列名 / 类型 / NOT NULL / 默认值 / 索引只要有一处对不上就会抛
  `Migration didn't properly handle: ...`，所以这个用例不需要逐列比对即可证明迁移脚本是对的。
  另断言历史流水一条不少、全部落到内置账本，且迁移后的库还能正常新建账本与记账。
- `vm/BudgetMathTest`（8 个）—— 预算的四个派生量（比例 / 剩余 / 是否超支 / 是否设了预算）与
  月结余、总资产。纯 JVM，不需要 Robolectric：这些值全是 `LedgerUiState` 的派生属性，
  边界单独各来一条（未设预算、恰好花完、超一分、收入不抵扣）。

### 权限审计

```bash
aapt2 dump permissions app/build/outputs/apk/release/app-release.apk
```

期望输出里**只有** `com.jianji.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` ——
这是 AndroidX 自动加的签名级权限（用于 `registerReceiver` 的非导出语义），不是运行时权限，
不需要用户授权。CI 会用同样的命令做一次断言。

### 签名

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

debug 与 release 共用同一把密钥（证书 SHA-256 `7aa9237d…7eb5`），因此 release 可以覆盖安装到
已有的 debug 版本上。签名方案为 v2（API 26 是 minSdk，v2 从 API 24 起就支持，够用）。

## 云端构建

推送到 `main` 即触发 `.github/workflows/build.yml`：

1. 还原 `.b64` 二进制
2. 装 JDK 17、Gradle，并定位 runner 预装的 Android SDK + 补装所需组件
   （构建命令优先用仓库里的 `./gradlew`，把 Gradle 锁在 8.8；万一 wrapper jar 没还原成功，
   会自动退回 runner 上 setup-gradle 提供的 `gradle`，不让整条流水线因为一个文件挂掉）
3. 跑 59 个单元测试
4. 跑 lint，**报告里出现 error 级别问题即构建失败**
   （本项目 minSdk 26，而液态玻璃用到的 `RuntimeShader` 是 API 33 的类，
   `NewApi` 这类「运行期才炸」的问题必须由 lint 拦，不能靠肉眼看守卫）
5. 编译 debug 与 release 两个 APK
6. 计算 SHA-256，并审计 APK 是否混入网络/存储权限（有则**构建失败**）
7. 把构建报告打进日志，随 artifact 与 Release 一起上传

`runs-on` 钉在 `ubuntu-24.04`：`ubuntu-latest` 会随镜像大版本迁移，
迁移当天构建可能莫名其妙挂掉，不值得为省几个字符冒这个险。

**刻意不把构建报告提交回仓库**：那会让 `main` 每次构建都自己往前跑一格，
任何本地克隆都会莫名落后一个提交、下次 push 被拒。
报告放在 Actions 日志、artifact 和 Release 里，需要时随时可查。

### 关于 `scripts/push_to_github.py`

把源码推到 GitHub 走的是文件写入接口（纯文本通道），二进制文件无法承载，所以：

- `gradle/wrapper/gradle-wrapper.jar` → 仓库里存 `gradle-wrapper.jar.b64`
- `keystore/jianji.jks` → 仓库里存 `keystore/jianji.jks.b64`

`scripts/bootstrap-binaries.sh` 负责还原（本地 clone 后跑一次，CI 里也会先跑）。
`scripts/push_to_github.py` 做反向的事：只推送文本文件，批量提交。

## 签名说明

`keystore/jianji.jks`（别名 `jianji`，口令 `jianji2026`）是给这个个人应用用的自签名密钥，
本地与 CI 共用同一把，因此后续版本可以覆盖安装升级。

**它不是发布级密钥**：口令就写在本仓库里，任何人拿到它都能签出同包名的 APK。如果哪天要公开发布，
请自己重新生成一份密钥库、把口令换成 CI secret，并且不要再提交进仓库。

## 已知取舍

- 刻意不跟随系统深色模式：整站按浅色纸面设计，强行反色会让金额与分类色的对比度失控
- release 构建关闭了 R8 压缩：个人自用优先保证行为与调试版一致，包体约 10.2 MB
  （液态玻璃内联源码 + 图标库是主要体积来源）
- 目前每个账户只记录余额，没有对账（reconcile）与定期账单
- 预算按「本账本全时间累计支出」计算，没有月度预算与预算结转
- 账本之间**不共享预算**：预算是账本的一个字段，不做跨账本合并

## 第三方组件

`app/src/main/java/com/kyant/backdrop/` 是 **Kyant Backdrop "Liquid Glass" 2.0.0** 的源码内联拷贝：

- 上游：<https://github.com/Kyant0/AndroidLiquidGlass>
- 许可证：Apache License 2.0（全文见 `app/src/main/java/com/kyant/backdrop/LICENSE`）
- 版权：Copyright Kyant0 and contributors

选择源码内联而不是 Maven 依赖，是因为上游基于 Compose Multiplatform
（Kotlin 2.3.21 / Compose 1.11.0 / AGP 9.2.1），与本项目的 Kotlin 2.0.21 / Compose 1.7.6 / AGP 8.6.1
会产生 Gradle module metadata 版本冲突。内联时对上游源码做过的 8 处改动
（全部与 Compose 版本兼容性及 lint API 等级标注有关，**未改动任何运行期行为**）
逐条记在 `app/src/main/java/com/kyant/backdrop/README.md`，升级时按该清单对照上游重新套用。

## 许可

个人自用项目，未附许可协议。内联的第三方组件（见上一节）依其 Apache-2.0 条款随附许可证全文。
