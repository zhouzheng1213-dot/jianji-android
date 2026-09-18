# com.kyant.backdrop（内联移植）

本目录是第三方开源库 **Kyant Backdrop "Liquid Glass" 2.0.0** 的源码内联拷贝（vendored source），
随「简记」App 一同编译，**不是**通过 Maven 依赖引入。

- 上游项目：https://github.com/Kyant0/AndroidLiquidGlass
- 版本：2.0.0
- 许可证：Apache License 2.0（见同目录 `LICENSE`）
- 版权：Copyright Kyant0 and contributors

## 为什么内联而不是加依赖

上游 2.0.0 基于 Compose Multiplatform 构建（Kotlin 2.3.21 / Compose 1.11.0 / AGP 9.2.1），
而「简记」使用 Kotlin 2.0.21 + Compose BOM 2024.12.01（Compose 1.7.6）+ AGP 8.6.1。
直接以 Maven 坐标引入会产生 Gradle module metadata 版本冲突。因此采用源码内联。

## 内联时做过的改动（仅此 8 处）

1. 去掉 Compose Multiplatform 的 `expect` / `actual` 关键字，只保留 Android 实现
   （`Platform.kt`、`RuntimeShader.kt`、`internal/Paint.kt`、`internal/RenderEffect.kt`）。
2. 移除 `org.intellij.lang.annotations.Language` 依赖及其 `@Language("AGSL")` 注解
   （纯 IDE 提示注解，运行期无作用）。
3. `effects/Lens.kt` 移除 `com.kyant.shapes.RoundedRectangularShape` 分支
   （该类型来自另一个库 `com.kyant.shapes`；其能力已被 Compose 自带的
   `CornerBasedShape` / `AbsoluteRoundedCornerShape` 分支完全覆盖）。
4. `internal/LayerRecorder.kt` 的 `recordLayer` 由 Kotlin **context receivers**
   （`context(node: DelegatableNode)`）改为显式参数 `node: DelegatableNode`。
   该特性在 Kotlin 2.0 仍需 `-Xcontext-receivers` 实验开关，改为普通参数即可避免；
   三处调用点同步补上 `this@DrawBackdropNode` / `this@LayerBackdropNode`。
5. **合并 4 组同名文件**。上游 `commonMain` 与 `androidMain` 存在 4 个相对路径完全相同的文件
   （`RuntimeShader.kt`、`Platform.kt`、`internal/Paint.kt`、`internal/RenderEffect.kt`），
   直接拷贝会互相覆盖并丢掉 `commonMain` 里的 `interface RuntimeShader` 声明。
   现已把声明与 Android 实现合并进同一文件。
6. `internal/InverseLayerScope.kt` 的 `blendMode` / `colorFilter` 由 `override` 降级为普通属性
   —— 二者在 Compose **1.8** 才加入 `GraphicsLayerScope`，本项目 Compose 为 1.7.6。
7. **补 API 等级标注**（`internal/Paint.kt`、`RuntimeShaderCache.kt`）。
   `android.graphics.RuntimeShader` 是 API 33 的类，上游没给几处接触它的内部函数加
   `@RequiresApi`，于是 `lintDebug` 报了三条 `NewApi`。本次只补标注、不改一行行为：
   - `Paint.setRuntimeShader` 加 `@RequiresApi(TIRAMISU)`；
   - `RuntimeShaderCacheImpl.obtainRuntimeShader` 加 `@RequiresApi(TIRAMISU)`
     （**只加在方法上、不加在类上**：该类在 Compose 组合期间即被实例化，标在类上会让实例化处反报 NewApi）。
   标注加完之后，lint 能顺着 `isRuntimeShaderSupported()` 上已有的
   `@ChecksSdkIntAtLeast(TIRAMISU)` 认出所有调用点都在守卫之内，于是告警从根上消失，
   而不是被 suppression 捂住。
8. `RuntimeShaderCache` 接口补上 `clear()`（`RuntimeShaderCache.kt`、`BackdropEffectScope.kt`）。
   第 7 点把约束加在实现方法上之后，`BackdropEffectScopeImpl` 里
   `private val runtimeShaderCache = RuntimeShaderCacheImpl()` 会因为「依赖了具体类型」
   被 lint 顺延判定为也需要 API 33。把字段声明成接口类型即可切断这条误传导；
   而 `reset()` 仍要清缓存，所以把 `clear()` 提到接口层。
   `BackdropEffectScopeImpl` 相应新增一个转发的 `override fun clear()`。

除此之外与上游一致；如需升级，请以 `diff` 对照上游源码重新套用上述 8 处改动。

## 兼容性基线

已验证可在以下环境编译通过（`:app:compileDebugKotlin`）：

| 项目 | 版本 |
| --- | --- |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.12.01（Compose UI 1.7.6） |
| AGP | 8.6.1 |
| minSdk / compileSdk | 26 / 35 |

运行期能力分级：`isRenderEffectSupported()` = API 31+，`isRuntimeShaderSupported()` = API 33+。
低于这两个版本时折射 / 色散等 AGSL 效果会自动跳过，需在 UI 层准备降级外观（见 `ui/glass/`）。
