# 简记 · 纯本地记账 App

一台手机、一个 App、一个数据库。没有账号、没有服务器、没有联网权限 —— 数据只写在这台设备上。

- 技术栈：Kotlin + Jetpack Compose（Material 3）+ Room，单模块，无第三方 SDK
- 支持版本：Android 8.0（API 26）及以上，targetSdk 35
- 包名：`com.jianji.app`

## 功能

| 模块 | 内容 |
| --- | --- |
| 明细 | 按月翻页；本月结余 / 支出 / 收入汇总；按日分组的流水；点任意一笔进入编辑，可删除 |
| 记一笔 | 自研数字键盘（不唤起系统输入法）；支出 / 收入 / 转账三种类型；分类宫格；日期、备注、账户 |
| 统计 | 月/年维度切换；分类占比环形图；分类排行（金额 + 笔数 + 占比）；近六个月收支柱状图；当月每日支出图 |
| 账户 | 多账户与实时余额、总资产；新增 / 编辑 / 归档 / 恢复；期初余额 |
| 搜索 | 时间范围、类型、分类、关键词、金额区间五维筛选，结果可直接编辑 |
| 设置 | 分类自定义（改名、换图标、换颜色、新增、隐藏、删除）；清空流水；数据规模概览 |

金额一律以「分」为单位的 `Long` 参与运算与存储，展示层才做千分位格式化，不存在浮点误差。

## 隐私

`AndroidManifest.xml` 里**没有任何 `uses-permission`**。App 未申请 INTERNET、ACCESS_NETWORK_STATE、
READ_MEDIA_IMAGES、READ_EXTERNAL_STORAGE 等权限，因此它在系统层面就没有联网与读取照片的能力。

CI 每次构建后都会执行一次 `aapt2 dump permissions` 审计，一旦出现上述权限即构建失败。

数据落在应用私有目录的 Room 数据库 `jianji.db`。系统备份/换机迁移只导出该数据库与偏好设置
（见 `res/xml/backup_rules.xml`）。

## 工程结构

```
app/src/main/java/com/jianji/app/
├── data/       实体、DAO、数据库、仓储、默认分类与账户种子
├── domain/     金额与日期工具、类型常量（纯 Kotlin，无 Android 依赖）
├── ui/
│   ├── component/  卡片、胶囊、环形图、柱状图、数字键盘、流水行
│   ├── screen/     明细 / 记一笔 / 统计 / 账户 / 搜索 / 设置
│   ├── theme/      暖白纸面配色与字体
│   ├── IconRegistry.kt  「图标 key → ImageVector」注册表
│   └── JianJiRoot.kt    底部导航 + 整屏覆盖层（含返回键处理）
└── vm/         ViewModel、UI 状态模型
```

设计上的一条硬规矩：数据库里只存图标的 key（字符串），真正的 `ImageVector` 由 `IconRegistry`
在运行期映射。这样以后替换或新增图标不会让历史数据失效。

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
gradle :app:testDebugUnitTest   # 35 个单元测试
gradle :app:lintDebug           # 0 error
```

测试分两层：

- `DomainTest`（20 个）—— 纯 Kotlin，钉死金额的格式化与键盘输入边界（千分位、小数位上限、退格、
  `fromCents`/`toCents` 往返、整数分模型下 0.1+0.2 不丢精度）以及日期区间与翻月。
- `LedgerDatabaseTest`（15 个）—— 用 Robolectric 起真实 SQLite 内存库，跑一遍全部手写 SQL：
  JOIN 出来的分类/账户名、账户余额的四段子查询（期初 + 收入 − 支出 − 转出 + 转入）、
  按天/按分类聚合、五维搜索、内置分类拒删、归档与恢复。

### 权限审计

```bash
aapt2 dump permissions app/build/outputs/apk/release/app-release.apk
```

期望输出里**只有** `com.jianji.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` ——
这是 AndroidX 自动加的签名级权限（用于 `registerReceiver` 的非导出语义），不是运行时权限，
不需要用户授权。CI 会用同样的命令做一次断言。

## 云端构建

推送到 `main` 即触发 `.github/workflows/build.yml`：

1. 还原二进制 → 装 JDK 17 / Gradle / Android SDK 35
   （构建命令优先用仓库里的 `./gradlew`，把 Gradle 锁在 8.8；万一 wrapper jar 没还原成功，
   会自动退回 runner 上 setup-gradle 提供的 `gradle`，不让整条流水线因为一个文件挂掉）
2. 跑单元测试
3. 编译 debug 与 release 两个 APK
4. 计算 SHA-256，并审计 APK 是否混入网络/存储权限（有则失败）
5. 上传构建产物（artifact），同时发布一个 GitHub Release 附带两个 APK
6. 把本次构建的校验值写回仓库根目录的 `build-report.json`

工作流用 `paths-ignore: build-report.json` 避免第 6 步的提交再次触发构建。

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
- release 构建关闭了 R8 压缩：个人自用优先保证行为与调试版一致，包体约为 10 MB（主要来自图标库）
- 目前每个账户只记录余额，没有对账（reconcile）与定期账单

## 许可

个人自用项目，未附许可协议。
