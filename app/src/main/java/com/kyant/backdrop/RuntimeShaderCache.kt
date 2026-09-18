package com.kyant.backdrop

import android.os.Build
import androidx.annotation.RequiresApi


sealed interface RuntimeShaderCache {

    fun obtainRuntimeShader(key: String, string: String): RuntimeShader

    /** 清空已缓存的着色器。放进接口里，调用方才不必依赖具体实现。 */
    fun clear()
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    private val runtimeShaders = mutableMapOf<String, RuntimeShader>()

    /**
     * 「取一个 RuntimeShader」这件事本身就要 API 33 —— 返回的
     * [AndroidRuntimeShader] 内部持有 `android.graphics.RuntimeShader`。
     *
     * 标注只加在实现方法上，不加在类上：这个类在 Compose 组合期间就会被实例化
     * （`BackdropEffectScopeImpl` / `HighlightModifier` 的字段初始化），
     * 如果标在类上，实例化处反而会各报一条 NewApi。真正需要 API 33 的只有这个方法，
     * 而它的调用方都已经在 `isRuntimeShaderSupported()` 守卫里面。
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return runtimeShaders.getOrPut(key) { RuntimeShader(string) }
    }

    override fun clear() {
        runtimeShaders.clear()
    }
}
