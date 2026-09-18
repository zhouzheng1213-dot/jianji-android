package com.jianji.app.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.jianji.app.ui.glass.utils.DampedDragAnimation
import com.jianji.app.ui.glass.utils.InteractiveHighlight
import com.jianji.app.ui.theme.LocalLedgerColors
import com.jianji.app.ui.theme.Palette
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** 底栏总槽数：4 个 Tab + 中间 1 个「记一笔」。 */
private const val SLOTS = 5
private const val TABS = 4

internal val LocalLiquidTabScale =
    staticCompositionLocalOf { { 1f } }

/**
 * 液态玻璃底栏 —— 完整移植自「琉音 Lyra」的 LiquidBottomTabs（同一套 backdrop 2.0.0），
 * 适配简记的五槽布局：4 个 Tab + 正中间红色「记一笔」。
 *
 * 三层结构（这就是音乐 App 底栏质感的全部秘密）：
 *
 *  1. **主玻璃层**（60dp 胶囊）：`vibrancy + blur(22) + lens(24,24)` 的真玻璃，
 *     Tab 图标画在玻璃面上；按下时整条胶囊微微鼓起。
 *  2. **隐藏着色层**（52dp，alpha 0）：同样的 Tab 内容用 `ColorFilter.tint(墨色)`
 *     画一遍并录进 [tabsBackdrop] —— 屏幕上看不见，但它是「液态墨滴」的原料。
 *  3. **滑动指示胶囊**（52dp，宽 = 1/5）：采样「页面背板 + 着色层」的合成像，
 *     `lens + 色散 + 内阴影`，速度越快被「甩」得越扁 ——
 *     切 Tab 时它像一滴墨水一样从旧图标滑到新图标，途经的内容都被它折射。
 *
 * 指示胶囊支持按住拖动换页（DampedDragAnimation），松手吸附最近档位。
 * 槽位映射：Tab 0/1 在槽 0/1，Tab 2/3 在槽 3/4 —— 滑动跨过中间「记一笔」时
 * 用 smoothstep 平滑插值，胶囊会连续地从槽 1 滑到槽 3，不跳变。
 */
@Composable
fun LiquidBottomBar(
    selectedTab: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    onAddEntry: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val accentColor = Palette.Ink
    val containerColor = Color.White.copy(0.30f)
    val addColor = LocalLedgerColors.current.expense

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (maxWidth - 8f.dp).toPx() / SLOTS
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTab) {
            mutableIntStateOf(selectedTab())
        }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTab().toFloat(),
                valueRange = 0f..(TABS - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.roundToInt().coerceIn(0, TABS - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (TABS - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        LaunchedEffect(selectedTab) {
            snapshotFlow { selectedTab() }
                .collectLatest { index ->
                    currentIndex = index
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelected(index)
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, offset ->
                    Offset(
                        if (isLtr) (slotFor(dampedDragAnimation.value) + 0.5f) * tabWidth + panelOffset
                        else size.width - (slotFor(dampedDragAnimation.value) + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // ---------- 第 1 层：主玻璃层 + Tab 内容 ----------
        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = {
                        vibrancy()
                        blur(22f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        // 上游用 16dp / 面板宽度；GraphicsLayerScope.size 在 Compose 1.7
                        // 还不存在，按 340dp 底栏宽折算 ≈ 0.047，取 1.05f。
                        val scale = lerp(1f, 1.05f, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(60f.dp)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = {
                repeat(SLOTS) { slot ->
                    if (slot == 2) {
                        AddEntrySlot(addColor = addColor, onClick = onAddEntry)
                    } else {
                        val tabIndex = if (slot < 2) slot else slot - 1
                        val spec = TAB_SPECS[tabIndex]
                        LiquidTabSlot(
                            icon = spec.icon,
                            label = spec.label,
                            selected = selectedTab() == tabIndex,
                            onClick = { onTabSelected(tabIndex) }
                        )
                    }
                }
            }
        )

        // ---------- 第 2 层：隐藏的着色层（原料） ----------
        CompositionLocalProvider(
            LocalLiquidTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(50) },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(22f.dp.toPx())
                            lens(
                                24f.dp.toPx() * progress,
                                24f.dp.toPx() * progress
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        // 玻璃面罩墨色：录进 tabsBackdrop 的是整颗墨色胶囊剪影，
                        // 指示胶囊折射它时就成了一滴「墨水」。
                        onDrawSurface = { drawRect(accentColor.copy(alpha = 0.55f)) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(52f.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = {
                    repeat(SLOTS) { slot ->
                        if (slot == 2) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val tabIndex = if (slot < 2) slot else slot - 1
                            val spec = TAB_SPECS[tabIndex]
                            // 隐藏层不可点：onClick 传 null，别让它挡住主层的点击。
                            // Compose 1.7 没有 GraphicsLayerScope.colorFilter（1.8 才有），
                            // 所以「整层染成墨色」直接用着色内容实现：
                            // 图标/文字用墨色画、玻璃面也罩墨色 ——
                            // 录进 tabsBackdrop 的就是一颗墨色胶囊剪影。
                            LiquidTabSlot(
                                icon = spec.icon,
                                label = spec.label,
                                selected = false,
                                accent = true,
                                onClick = null
                            )
                        }
                    }
                }
            )
        }

        // ---------- 第 3 层：滑动的液态指示胶囊 ----------
        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) slotFor(dampedDragAnimation.value) * tabWidth + panelOffset
                        else size.width - (slotFor(dampedDragAnimation.value) + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { RoundedCornerShape(50) },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            Color.Black.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(52f.dp)
                .fillMaxWidth(1f / SLOTS)
        )
    }
}

/**
 * Tab 序号（0..3）→ 槽位（0..4）的**连续**映射。
 * Tab 0/1 → 槽 0/1，Tab 2/3 → 槽 3/4；1→2 之间用 smoothstep 插值，
 * 让指示胶囊滑过中间「记一笔」槽时是连续加速再减速，而不是瞬移一格。
 */
private fun slotFor(value: Float): Float =
    if (value <= 1f) value
    else value + smoothStep((value - 1f).fastCoerceIn(0f, 1f))

private fun smoothStep(x: Float): Float = x * x * (3f - 2f * x)

private data class TabSpec(val icon: ImageVector, val label: String)

private val TAB_SPECS = listOf(
    TabSpec(Icons.AutoMirrored.Filled.ReceiptLong, "明细"),
    TabSpec(Icons.Filled.PieChart, "统计"),
    TabSpec(Icons.Filled.AccountBalanceWallet, "账户"),
    TabSpec(Icons.Filled.Settings, "设置")
)

/**
 * 单个 Tab：图标 + 文字，被选中时提色。
 * 按压时整行内容按 [LocalLiquidTabScale] 微缩放（液态「鼓起」的一部分）。
 *
 * [onClick] 为 null 时不挂点击（隐藏着色层用它，避免拦截主层的手势）。
 */
@Composable
private fun RowScope.LiquidTabSlot(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
    accent: Boolean = false
) {
    val colors = LocalLedgerColors.current
    val scale = LocalLiquidTabScale.current
    val normalTint = if (selected) Palette.Ink else colors.inkFaint
    val tint = if (accent) Palette.Ink else normalTint
    Column(
        Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .fillMaxSize()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(21.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

/**
 * 中间槽的「记一笔」：46dp 支出红圆钮，白加号。
 * 按下整颗缩小、加号转 45°——与整条底栏的液态手感同一套弹簧语言。
 */
@Composable
private fun RowScope.AddEntrySlot(
    addColor: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(pressed) {
        scale.animateTo(
            targetValue = if (pressed) 0.88f else 1f,
            animationSpec = spring(
                dampingRatio = if (pressed) 0.95f else 0.4f,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
            )
        )
    }
    val rotation = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(pressed) {
        rotation.animateTo(
            targetValue = if (pressed) 45f else 0f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
        )
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clip(CircleShape)
                .background(addColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "记一笔",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = rotation.value }
            )
        }
    }
}
