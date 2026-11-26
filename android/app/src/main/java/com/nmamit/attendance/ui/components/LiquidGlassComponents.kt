package com.nmamit.attendance.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmamit.attendance.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Shared HazeState used ONLY for floating overlays/modals (`enableBlur = true`)
 * so scrolling surfaces stay locked at 60-120fps across all devices without
 * running 10+ simultaneous Gaussian RenderEffect blurs per frame.
 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

private val DefaultGlassBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.26f),
        Color.White.copy(alpha = 0.13f),
        Color.White.copy(alpha = 0.08f)
    )
)

private val DefaultSubCardBorderBrush = Brush.verticalGradient(
    colors = listOf(Color.White.copy(alpha = 0.14f), GlassBorderSubtle)
)

private val FocusedInputBorderBrush = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.45f))
)

private val UnfocusedInputBorderBrush = Brush.verticalGradient(
    listOf(GlassInputTopRim, Color.White.copy(alpha = 0.15f))
)

private val DropdownFocusedBorderBrush = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.35f))
)

private val DropdownUnfocusedBorderBrush = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.12f))
)

/**
 * Zero-recomposition fluid entrance animation running entirely on the RenderNode graphicsLayer
 * (smooth upward glide + spring scale + alpha fade).
 */
fun Modifier.fluidEnter(
    delayMs: Int = 0,
    offsetYDp: Float = 16f,
    initialScale: Float = 0.965f
): Modifier = this then FluidEnterElement(delayMs, offsetYDp, initialScale)

private data class FluidEnterElement(
    val delayMs: Int,
    val offsetYDp: Float,
    val initialScale: Float
) : ModifierNodeElement<FluidEnterNode>() {
    override fun create() = FluidEnterNode(delayMs, offsetYDp, initialScale)
    override fun update(node: FluidEnterNode) {
        node.delayMs = delayMs
        node.offsetYDp = offsetYDp
        node.initialScale = initialScale
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "fluidEnter"
        properties["delayMs"] = delayMs
    }
}

private class FluidEnterNode(
    var delayMs: Int,
    var offsetYDp: Float,
    var initialScale: Float
) : Modifier.Node(), LayoutModifierNode {

    override val shouldAutoInvalidate: Boolean get() = false

    private val progress = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            if (delayMs > 0) delay(delayMs.toLong())
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        val offsetPx = offsetYDp.dp.toPx()
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val p = progress.value.coerceIn(0f, 1.15f)
                val clampedAlpha = p.coerceIn(0f, 1f)
                val s = initialScale + (1f - initialScale) * p
                scaleX = s
                scaleY = s
                alpha = clampedAlpha
                translationY = (1f - p) * offsetPx
            }
        }
    }
}

/**
 * Tactile iOS spring compression on press (matching CSS `transform: scale(0.96)`),
 * implemented via Modifier.Node for zero subcomposition and zero recomposition overhead.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.95f,
    onClick: () -> Unit
): Modifier = this then BouncyClickableElement(
    enabled = enabled,
    pressedScale = pressedScale,
    onClick = onClick
)

private data class BouncyClickableElement(
    val enabled: Boolean,
    val pressedScale: Float,
    val onClick: () -> Unit
) : ModifierNodeElement<BouncyClickableNode>() {
    override fun create() = BouncyClickableNode(enabled, pressedScale, onClick)

    override fun update(node: BouncyClickableNode) {
        val enabledChanged = node.enabled != enabled
        node.enabled = enabled
        node.pressedScale = pressedScale
        node.onClick = onClick
        if (enabledChanged) {
            node.invalidateSemantics()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "bouncyClickable"
        properties["enabled"] = enabled
        properties["pressedScale"] = pressedScale
    }
}

private class BouncyClickableNode(
    var enabled: Boolean,
    var pressedScale: Float,
    var onClick: () -> Unit
) : DelegatingNode(), LayoutModifierNode, SemanticsModifierNode {

    override val shouldAutoInvalidate: Boolean get() = false

    private val scaleAnim = Animatable(1f)
    private val pressSpring = spring<Float>(
        dampingRatio = 0.58f,
        stiffness = Spring.StiffnessMediumLow
    )

    @Suppress("unused")
    private val pointerInputNode = delegate(
        SuspendingPointerInputModifierNode {
            detectTapGestures(
                onPress = {
                    if (!enabled) return@detectTapGestures
                    val downJob = coroutineScope.launch {
                        scaleAnim.animateTo(pressedScale, pressSpring)
                    }
                    tryAwaitRelease()
                    downJob.cancel()
                    coroutineScope.launch {
                        scaleAnim.animateTo(1f, pressSpring)
                    }
                },
                onTap = {
                    if (enabled) onClick()
                }
            )
        }
    )

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val s = scaleAnim.value
                scaleX = s
                scaleY = s
            }
        }
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        role = Role.Button
        if (!enabled) disabled()
        onClick {
            if (enabled) onClick()
            true
        }
    }
}

/**
 * Replicates `.grid-pattern-fixed` + `.grid-ambient-glow` + `body::before` top beam
 * over a pure #000000 pitch-black canvas with all gradients & coordinates cached
 * inside `drawWithCache` (zero per-frame allocations).
 */
@Composable
fun TechGridBackground(
    modifier: Modifier = Modifier,
    hazeState: HazeState = remember { HazeState() },
    enableHazeSource: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(BgCanvas)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (enableHazeSource) Modifier.hazeSource(state = hazeState) else Modifier)
                    .drawWithCache {
                        val widthPx = size.width.roundToInt().coerceAtLeast(1)
                        val heightPx = size.height.roundToInt().coerceAtLeast(1)
                        val cachedBitmap = ImageBitmap(
                            width = widthPx,
                            height = heightPx,
                            config = ImageBitmapConfig.Argb8888
                        )
                        val step = 28.dp.toPx()
                        val strokeW = 1.dp.toPx()
                        val centerX = size.width * 0.5f
                        val centerY = size.height * 0.34f
                        val maxRadius = hypot(size.width * 0.65f, size.height * 0.62f)
                        val glowCenter = Offset(centerX, size.height * 0.06f)
                        val glowRadius = size.width * 0.9f

                        val ambientGlowBrush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.045f),
                                Color.White.copy(alpha = 0.014f),
                                Color.Transparent
                            ),
                            center = glowCenter,
                            radius = glowRadius
                        )

                        val topBeamBrush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.24f),
                                Color.Transparent
                            )
                        )

                        CanvasDrawScope().draw(
                            density = this,
                            layoutDirection = layoutDirection,
                            canvas = ComposeCanvas(cachedBitmap),
                            size = size
                        ) {
                            drawRect(color = BgCanvas)
                            drawCircle(
                                brush = ambientGlowBrush,
                                radius = glowRadius,
                                center = glowCenter
                            )
                            var x = 0f
                            while (x <= size.width) {
                                val distX = abs(x - centerX) / (size.width * 0.55f)
                                val alpha = (0.088f * (1f - distX * 0.55f)).coerceIn(0.018f, 0.088f)
                                val vBrush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = alpha * 0.75f),
                                        Color.White.copy(alpha = alpha),
                                        Color.White.copy(alpha = alpha * 0.85f),
                                        Color.White.copy(alpha = alpha * 0.3f),
                                        Color.Transparent
                                    ),
                                    startY = 0f,
                                    endY = size.height
                                )
                                drawLine(
                                    brush = vBrush,
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = strokeW
                                )
                                x += step
                            }

                            var y = 0f
                            while (y <= size.height) {
                                val distY = abs(y - centerY) / maxRadius
                                val alpha = (0.088f * (1f - distY * 0.85f)).coerceIn(0.012f, 0.088f)
                                val hBrush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = alpha * 0.6f),
                                        Color.White.copy(alpha = alpha),
                                        Color.White.copy(alpha = alpha * 0.6f),
                                        Color.Transparent
                                    )
                                )
                                drawLine(
                                    brush = hBrush,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = strokeW
                                )
                                y += step
                            }

                            drawLine(
                                brush = topBeamBrush,
                                start = Offset(size.width * 0.1f, strokeW),
                                end = Offset(size.width * 0.9f, strokeW),
                                strokeWidth = strokeW
                            )
                        }

                        onDrawBehind {
                            drawImage(cachedBitmap)
                        }
                    }
            )

            content()
        }
    }
}

/**
 * Replicates `.liquid-glass` / `.auth-card`:
 * - Translucent dark acrylic surface with cached 135-degree diagonal glass sheen
 * - Top 32% subtle glass highlight gradient (`.auth-card::before`)
 * - Asymmetric Apple specular rim (brighter top rim + diagonal specular border)
 * - Optional real-time Haze backdrop blur (`enableBlur = true`) reserved for modals/overlays
 *   so scrolling feeds sustain 60-120fps on all Android devices.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    tintColor: Color = GlassCardTint,
    glassAlpha: Float = 0.62f,
    blurRadius: Dp = 24.dp,
    enableBlur: Boolean = false,
    accentOverlayBrush: Brush? = null,
    topAccentBarBrush: Brush? = null,
    borderBrush: Brush = DefaultGlassBorderBrush,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape: Shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val hazeState = if (enableBlur) LocalHazeState.current else null

    val hazeStyle = if (enableBlur && hazeState != null) {
        remember(tintColor, glassAlpha, blurRadius) {
            HazeStyle(
                backgroundColor = tintColor,
                tint = HazeTint(tintColor.copy(alpha = glassAlpha)),
                blurRadius = blurRadius,
                noiseFactor = 0.035f
            )
        }
    } else null

    // Slightly richer opacity when GPU RenderEffect blur is skipped so cards stay crisp over the grid
    val effectiveAlpha = if (hazeStyle != null) glassAlpha else (glassAlpha + 0.16f).coerceIn(0.68f, 0.88f)

    Column(
        modifier = modifier
            .then(
                if (enableBlur) {
                    Modifier.shadow(
                        elevation = 16.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.9f),
                        spotColor = Color.Black.copy(alpha = 0.9f)
                    )
                } else Modifier
            )
            .clip(shape)
            .then(
                if (hazeState != null && hazeStyle != null) {
                    Modifier.hazeEffect(state = hazeState, style = hazeStyle)
                } else {
                    Modifier.background(tintColor.copy(alpha = effectiveAlpha), shape)
                }
            )
            .drawWithCache {
                val cr = cornerRadius.toPx()
                val stroke1 = 1.dp.toPx()
                val stroke2 = 2.dp.toPx()
                val diagonalSheen = Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.055f),
                    0.5f to Color.White.copy(alpha = 0.012f),
                    1f to Color.White.copy(alpha = 0.035f),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
                val topSheen = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.045f),
                    0.32f to Color.Transparent
                )
                val topSpecularRim = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.24f),
                        Color.White.copy(alpha = 0.24f),
                        Color.Transparent
                    )
                )
                val cornerRadiusObj = CornerRadius(cr, cr)
                val outerStroke = Stroke(width = stroke1)

                onDrawWithContent {
                    drawRect(brush = diagonalSheen)
                    if (accentOverlayBrush != null) {
                        drawRect(brush = accentOverlayBrush)
                    }
                    drawRect(brush = topSheen)
                    drawContent()
                    if (topAccentBarBrush != null) {
                        drawLine(
                            brush = topAccentBarBrush,
                            start = Offset(cr * 0.45f, stroke1),
                            end = Offset(size.width - cr * 0.45f, stroke1),
                            strokeWidth = stroke2
                        )
                    }
                    drawLine(
                        brush = topSpecularRim,
                        start = Offset(cr * 0.35f, stroke1),
                        end = Offset(size.width - cr * 0.35f, stroke1),
                        strokeWidth = stroke1
                    )
                    drawRoundRect(
                        brush = borderBrush,
                        cornerRadius = cornerRadiusObj,
                        style = outerStroke
                    )
                }
            }
            .padding(contentPadding),
        content = content
    )
}

/**
 * Nested translucent glass sub-container matching CSS `--glass-subcard`.
 */
@Composable
fun GlassSubCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    backgroundColor: Color = GlassSubCardBg,
    borderColor: Brush = DefaultSubCardBorderBrush,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor, shape)
            .border(1.dp, borderColor, shape),
        content = content
    )
}

@Composable
fun GlassPillBadge(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Foreground,
    bgColor: Color = GlassSubCardBg,
    borderColor: Color = GlassBorder
) {
    val pillShape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(pillShape)
            .background(bgColor)
            .border(1.dp, borderColor, pillShape)
            .padding(horizontal = 9.dp, vertical = 3.5.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.5.sp,
            fontFamily = AppMonoFont,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
fun SectionTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ForegroundMuted
) {
    Text(
        text = text,
        fontSize = 10.5.sp,
        fontFamily = AppMonoFont,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = color,
        modifier = modifier
    )
}

/**
 * Custom 44dp/38dp sleek glassmorphic text field matching `.auth-card .form-input`.
 */
@Composable
fun GlassInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    height: Dp = 44.dp,
    cornerRadius: Dp = 14.dp,
    readOnly: Boolean = false,
    textStyle: TextStyle = TextStyle(
        color = Foreground,
        fontFamily = AppBodyFont,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal
    ),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val bgColor by animateColorAsState(
        targetValue = if (isFocused) Color.White.copy(alpha = 0.10f) else GlassInputBg,
        animationSpec = tween(180),
        label = "inputBg"
    )
    val borderBrush = if (isFocused) FocusedInputBorderBrush else UnfocusedInputBorderBrush

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        singleLine = true,
        textStyle = textStyle.copy(
            fontFamily = textStyle.fontFamily ?: AppBodyFont,
            color = Foreground.copy(alpha = if (readOnly) 0.92f else 1f)
        ),
        cursorBrush = SolidColor(Foreground),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onFocusChanged { isFocused = it.isFocused },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(bgColor, shape)
                    .border(
                        width = if (isFocused) 1.5.dp else 1.dp,
                        brush = borderBrush,
                        shape = shape
                    )
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            color = ForegroundSubtle,
                            fontFamily = textStyle.fontFamily ?: AppBodyFont,
                            fontSize = textStyle.fontSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    trailingIcon()
                }
            }
        }
    )
}

/**
 * Matches `.custom-select-wrapper` + `.custom-select-trigger` + `.custom-select-dropdown`.
 */
@Composable
fun GlassSelectDropdown(
    selectedLabel: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation = animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
        label = "dropdownArrow"
    )
    val triggerBg by animateColorAsState(
        targetValue = if (expanded) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.05f),
        animationSpec = tween(180),
        label = "dropdownBg"
    )
    val shape = remember { RoundedCornerShape(14.dp) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .bouncyClickable(pressedScale = 0.985f) { expanded = !expanded }
                .clip(shape)
                .background(triggerBg, shape)
                .border(
                    width = 1.dp,
                    brush = if (expanded) DropdownFocusedBorderBrush else DropdownUnfocusedBorderBrush,
                    shape = shape
                )
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Expand",
                tint = if (expanded) Foreground else ForegroundMuted,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = arrowRotation.value }
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 260.dp, max = 360.dp)
                .background(GlassCardSolid)
                .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
        ) {
            options.forEach { (key, label) ->
                val isSelected = key == selectedKey
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Foreground else Foreground.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Emerald,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    },
                    modifier = Modifier.background(
                        if (isSelected) Color.White.copy(alpha = 0.10f) else Color.Transparent
                    )
                )
            }
        }
    }
}
