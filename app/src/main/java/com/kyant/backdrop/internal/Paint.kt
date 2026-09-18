package com.kyant.backdrop.internal

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Paint
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asAndroidRuntimeShader

internal fun Paint.blur(radius: Float) {
    this.asFrameworkPaint().maskFilter =
        if (radius > 0f) BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        else null
}

/**
 * 把 AGSL 着色器挂到原生画笔上。
 *
 * `android.graphics.RuntimeShader` 是 API 33 才有的类，所以这里显式标注 ——
 * 上游没标，于是 lint 只能在赋值处报一个「隐式向上转型需要 API 33」。
 * 标上之后 lint 能顺着 [com.kyant.backdrop.isRuntimeShaderSupported] 的
 * `@ChecksSdkIntAtLeast` 认出调用方的 `if` 守卫，告警从根上消失，
 * 而不是被 suppression 捂住。运行期行为一字未改。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun Paint.setRuntimeShader(runtimeShader: RuntimeShader?) {
    this.asFrameworkPaint().shader = runtimeShader?.asAndroidRuntimeShader()
}
