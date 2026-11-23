package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.AgentPermissionMode
import com.example.ui.theme.ForgeTheme
import com.example.ui.theme.JetBrainsMonoFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Atmospheric forged-steel HUD blueprint grid with subtle ambient amber & blue telemetry glow.
 * Caches grid geometry and gradient brushes via [drawWithCache] so zero objects are allocated per draw pass.
 */
@Composable
fun TacticalGridBackdrop(
    modifier: Modifier = Modifier,
    gridSpacing: Dp = 32.dp,
    accentGlowAlpha: Float = 0.13f,
    content: @Composable BoxScope.() -> Unit
) {
    val forge = ForgeTheme.colors
    val bg = MaterialTheme.colorScheme.background
    val gridColor = forge.borderHairline.copy(alpha = 0.25f)
    val amberGlow = forge.amber.copy(alpha = accentGlowAlpha)
    val blueGlow = forge.tacticalBlue.copy(alpha = accentGlowAlpha * 0.9f)

    Box(
        modifier = modifier
            .background(bg)
            .drawWithCache {
                val stepPx = gridSpacing.toPx().coerceAtLeast(16f)
                val gridPath = Path().apply {
                    var x = 0f
                    while (x <= size.width) {
                        moveTo(x, 0f)
                        lineTo(x, size.height)
                        x += stepPx
                    }
                    var y = 0f
                    while (y <= size.height) {
                        moveTo(0f, y)
                        lineTo(size.width, y)
                        y += stepPx
                    }
                }
                val gridStroke = Stroke(width = 0.75f)
                val topBlueCenter = Offset(size.width * 0.86f, size.height * 0.12f)
                val topBlueRadius = (size.maxDimension * 0.48f).coerceAtLeast(1f)
                val topBlueBrush = Brush.radialGradient(
                    colors = listOf(blueGlow, Color.Transparent),
                    center = topBlueCenter,
                    radius = topBlueRadius
                )
                val lowAmberCenter = Offset(size.width * 0.14f, size.height * 0.80f)
                val lowAmberRadius = (size.maxDimension * 0.44f).coerceAtLeast(1f)
                val lowAmberBrush = Brush.radialGradient(
                    colors = listOf(amberGlow, Color.Transparent),
                    center = lowAmberCenter,
                    radius = lowAmberRadius
                )

                onDrawBehind {
                    drawCircle(brush = topBlueBrush, radius = topBlueRadius, center = topBlueCenter)
                    drawCircle(brush = lowAmberBrush, radius = lowAmberRadius, center = lowAmberCenter)
                    drawPath(path = gridPath, color = gridColor, style = gridStroke)
                }
            },
        content = content
    )
}

/**
 * Machined tactical HUD card featuring cached dark-steel metallic gradient, hairline perimeter,
 * optional illuminated top signal rail, and precision L-shaped corner targeting brackets.
 */
@Composable
fun HudCornerFrame(
    modifier: Modifier = Modifier,
    cornerAccent: Color = ForgeTheme.colors.amber,
    borderColor: Color = ForgeTheme.colors.borderHairline,
    elevated: Boolean = false,
    showTopRail: Boolean = true,
    contentPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val topColor = if (elevated) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val bottomColor = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(4.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .drawWithCache {
                val fillBrush = Brush.verticalGradient(colors = listOf(topColor, bottomColor))
                val bracketLen = 11.dp.toPx()
                val strokeW = 1.6.dp.toPx()
                val inset = 1.dp.toPx()
                val w = size.width
                val h = size.height
                val bracketPath = Path().apply {
                    // Top-left
                    moveTo(inset, inset + bracketLen)
                    lineTo(inset, inset)
                    lineTo(inset + bracketLen, inset)
                    // Top-right
                    moveTo(w - inset - bracketLen, inset)
                    lineTo(w - inset, inset)
                    lineTo(w - inset, inset + bracketLen)
                    // Bottom-left
                    moveTo(inset, h - inset - bracketLen)
                    lineTo(inset, h - inset)
                    lineTo(inset + bracketLen, h - inset)
                    // Bottom-right
                    moveTo(w - inset - bracketLen, h - inset)
                    lineTo(w - inset, h - inset)
                    lineTo(w - inset, h - inset - bracketLen)
                }
                val bracketStroke = Stroke(width = strokeW)
                val railBrush = if (showTopRail) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            cornerAccent.copy(alpha = 0.65f),
                            Color.Transparent
                        ),
                        startX = w * 0.16f,
                        endX = w * 0.84f
                    )
                } else null
                val railStrokeW = 1.5.dp.toPx()

                onDrawWithContent {
                    drawRect(brush = fillBrush)
                    drawContent()
                    drawPath(path = bracketPath, color = cornerAccent, style = bracketStroke)
                    if (railBrush != null) {
                        drawLine(
                            brush = railBrush,
                            start = Offset(w * 0.16f, inset),
                            end = Offset(w * 0.84f, inset),
                            strokeWidth = railStrokeW
                        )
                    }
                }
            }
            .padding(contentPadding),
        content = content
    )
}

/**
 * Animated pulsing telemetry LED dot reading animated states exclusively during the draw phase
 * (zero recompositions per animation frame).
 */
@Composable
fun PulsingStatusDot(
    color: Color,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "led_pulse")
    val haloScaleState = transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "halo_scale"
    )
    val haloAlphaState = transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "halo_alpha"
    )

    Box(
        modifier = modifier
            .size(size * 2)
            .drawBehind {
                val baseRadius = size.toPx() / 2f
                drawCircle(
                    color = color.copy(alpha = haloAlphaState.value),
                    radius = baseRadius * haloScaleState.value
                )
                drawCircle(
                    color = color,
                    radius = baseRadius
                )
            }
    )
}

/**
 * Animated concentric security scanner core for the AuthScreen hero, framing the center emblem.
 * Uses [drawWithCache] and deferred draw-phase state reads for zero-recomposition 60fps rendering.
 */
@Composable
fun AnimatedSecurityRadarCore(
    modifier: Modifier = Modifier,
    diameter: Dp = 152.dp,
    centerContent: @Composable BoxScope.() -> Unit
) {
    val forge = ForgeTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "auth_radar")
    val outerAngleState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_sweep"
    )
    val innerAngleState = infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_sweep"
    )

    Box(
        modifier = modifier
            .size(diameter)
            .drawWithCache {
                val c = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension / 2f
                val coreGlow = Brush.radialGradient(
                    colors = listOf(
                        forge.amber.copy(alpha = 0.22f),
                        forge.tacticalBlue.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = c,
                    radius = maxR.coerceAtLeast(1f)
                )
                val dashedStroke = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
                )
                val outerArcR = maxR - 11.dp.toPx()
                val outerTopLeft = Offset(c.x - outerArcR, c.y - outerArcR)
                val outerSize = Size(outerArcR * 2, outerArcR * 2)
                val outerStroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Butt)

                val innerArcR = maxR - 20.dp.toPx()
                val innerTopLeft = Offset(c.x - innerArcR, c.y - innerArcR)
                val innerSize = Size(innerArcR * 2, innerArcR * 2)
                val innerStroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Butt)

                val majorTickPath = Path()
                val minorTickPath = Path()
                for (i in 0 until 12) {
                    val rad = (i * 30.0) * PI / 180.0
                    val isMajor = i % 3 == 0
                    val rOuter = maxR - 2.dp.toPx()
                    val rInner = rOuter - if (isMajor) 8.dp.toPx() else 4.dp.toPx()
                    val target = if (isMajor) majorTickPath else minorTickPath
                    target.moveTo(c.x + (cos(rad) * rInner).toFloat(), c.y + (sin(rad) * rInner).toFloat())
                    target.lineTo(c.x + (cos(rad) * rOuter).toFloat(), c.y + (sin(rad) * rOuter).toFloat())
                }
                val majorTickStroke = Stroke(width = 1.8.dp.toPx())
                val minorTickStroke = Stroke(width = 1.dp.toPx())

                onDrawBehind {
                    drawCircle(brush = coreGlow, radius = maxR, center = c)
                    drawCircle(
                        color = forge.borderStrong.copy(alpha = 0.65f),
                        radius = maxR - 4.dp.toPx(),
                        center = c,
                        style = dashedStroke
                    )
                    drawPath(majorTickPath, forge.amber.copy(alpha = 0.85f), style = majorTickStroke)
                    drawPath(minorTickPath, forge.borderStrong, style = minorTickStroke)

                    rotate(degrees = outerAngleState.value, pivot = c) {
                        drawArc(
                            color = forge.amber,
                            startAngle = 0f,
                            sweepAngle = 78f,
                            useCenter = false,
                            topLeft = outerTopLeft,
                            size = outerSize,
                            style = outerStroke
                        )
                        drawArc(
                            color = forge.amber.copy(alpha = 0.32f),
                            startAngle = 180f,
                            sweepAngle = 54f,
                            useCenter = false,
                            topLeft = outerTopLeft,
                            size = outerSize,
                            style = outerStroke
                        )
                    }

                    rotate(degrees = innerAngleState.value, pivot = c) {
                        drawArc(
                            color = forge.tacticalBlue.copy(alpha = 0.78f),
                            startAngle = 20f,
                            sweepAngle = 92f,
                            useCenter = false,
                            topLeft = innerTopLeft,
                            size = innerSize,
                            style = innerStroke
                        )
                        drawArc(
                            color = forge.tacticalBlue.copy(alpha = 0.35f),
                            startAngle = 200f,
                            sweepAngle = 60f,
                            useCenter = false,
                            topLeft = innerTopLeft,
                            size = innerSize,
                            style = innerStroke
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
        content = centerContent
    )
}

/**
 * Futuristic circular Security Posture Integrity Reactor for the Dashboard hero panel.
 * Defers animated arc and orbit rotation reads into [drawWithCache] so frames never trigger recomposition.
 */
@Composable
fun PostureReactorGauge(
    integrityScore: Int,
    statusLabel: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 112.dp
) {
    val forge = ForgeTheme.colors
    val clamped = integrityScore.coerceIn(0, 100)
    val animatedFractionState = animateFloatAsState(
        targetValue = clamped / 100f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "integrity_arc"
    )
    val sweepTransition = rememberInfiniteTransition(label = "reactor_sweep")
    val radarRotationState = sweepTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "reactor_rotation"
    )

    Box(
        modifier = modifier
            .size(diameter)
            .drawWithCache {
                val c = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension / 2f
                val strokeW = 7.dp.toPx()
                val wellRadius = (maxR - strokeW - 6.dp.toPx()).coerceAtLeast(1f)
                val orbitR = maxR - 1.5.dp.toPx()
                val orbitTopLeft = Offset(c.x - orbitR, c.y - orbitR)
                val orbitSize = Size(orbitR * 2, orbitR * 2)
                val orbitStroke = Stroke(width = 1.4.dp.toPx())

                val arcR = maxR - strokeW / 2f - 5.dp.toPx()
                val arcTopLeft = Offset(c.x - arcR, c.y - arcR)
                val arcSize = Size(arcR * 2, arcR * 2)
                val arcStroke = Stroke(width = strokeW, cap = StrokeCap.Butt)

                val tickR = (arcR - 7.dp.toPx()).coerceAtLeast(1f)
                val tickStroke = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 6f), 0f)
                )

                onDrawBehind {
                    drawCircle(color = forge.recessedWell, radius = wellRadius, center = c)

                    rotate(degrees = radarRotationState.value, pivot = c) {
                        drawArc(
                            color = accentColor.copy(alpha = 0.45f),
                            startAngle = 0f,
                            sweepAngle = 55f,
                            useCenter = false,
                            topLeft = orbitTopLeft,
                            size = orbitSize,
                            style = orbitStroke
                        )
                    }

                    drawArc(
                        color = forge.borderHairline,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = arcStroke
                    )

                    drawArc(
                        color = accentColor,
                        startAngle = 135f,
                        sweepAngle = 270f * animatedFractionState.value,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = arcStroke
                    )

                    drawCircle(
                        color = forge.borderStrong.copy(alpha = 0.55f),
                        radius = tickR,
                        center = c,
                        style = tickStroke
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$clamped%",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp
                ),
                color = accentColor
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * Real-time risk telemetry waveform plotting recent audit run risk scores,
 * caching path geometry & fill shaders via [drawWithCache] and animating only the scanline in draw phase.
 */
@Composable
fun TelemetrySparkWave(
    riskScores: List<Int>,
    modifier: Modifier = Modifier
) {
    val forge = ForgeTheme.colors
    val hasData = riskScores.isNotEmpty()
    val latestRisk = riskScores.lastOrNull() ?: 0
    val lineColor = when {
        !hasData -> forge.tacticalBlue
        latestRisk >= 75 -> forge.criticalRed
        latestRisk >= 45 -> forge.amber
        else -> forge.verifiedGreen
    }

    val pulseTransition = rememberInfiniteTransition(label = "wave_pulse")
    val scanXFractionState = pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_line_x"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(forge.recessedWell)
            .border(1.dp, forge.borderHairline, RoundedCornerShape(3.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingStatusDot(color = lineColor, size = 6.dp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (hasData) "RISK TELEMETRY WAVEFORM" else "THREAT MONITOR // STANDBY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (hasData) "PEAK ${riskScores.max()} · LAST $latestRisk" else "AWAITING SCAN",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = lineColor
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val padY = 5.dp.toPx()
                    val usableH = (h - padY * 2f).coerceAtLeast(4f)

                    val points: List<Offset> = if (hasData) {
                        val series = if (riskScores.size == 1) {
                            listOf(riskScores[0], riskScores[0])
                        } else {
                            riskScores.takeLast(12)
                        }
                        series.mapIndexed { idx, score ->
                            val x = w * (idx.toFloat() / (series.lastIndex).coerceAtLeast(1))
                            val norm = (score.coerceIn(0, 100)) / 100f
                            val y = padY + usableH * (1f - norm)
                            Offset(x, y)
                        }
                    } else {
                        val sample = listOf(14, 16, 15, 22, 18, 15, 19, 16, 15, 17)
                        sample.mapIndexed { idx, v ->
                            val x = w * (idx.toFloat() / sample.lastIndex)
                            val y = padY + usableH * (1f - v / 100f)
                            Offset(x, y)
                        }
                    }

                    val linePath = Path().apply {
                        if (points.size >= 2) {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }
                    }
                    val fillPath = Path().apply {
                        if (points.size >= 2) {
                            addPath(linePath)
                            lineTo(points.last().x, h)
                            lineTo(points.first().x, h)
                            close()
                        }
                    }
                    val fillBrush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
                    )
                    val lineStroke = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                    val scanColor = lineColor.copy(alpha = 0.35f)
                    val scanStrokeW = 1.8.dp.toPx()
                    val gridY = listOf(0.25f, 0.5f, 0.75f).map { padY + usableH * it }
                    val gridLineColor = forge.borderHairline.copy(alpha = 0.5f)
                    val lastRadius = 3.5.dp.toPx()
                    val dotRadius = 2.dp.toPx()

                    onDrawBehind {
                        gridY.forEach { y ->
                            drawLine(
                                color = gridLineColor,
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 0.8f
                            )
                        }
                        val scanX = w * scanXFractionState.value
                        drawLine(
                            color = scanColor,
                            start = Offset(scanX, 0f),
                            end = Offset(scanX, h),
                            strokeWidth = scanStrokeW
                        )
                        if (points.size >= 2) {
                            drawPath(path = fillPath, brush = fillBrush)
                            drawPath(path = linePath, color = lineColor, style = lineStroke)
                            if (hasData) {
                                points.forEachIndexed { idx, pt ->
                                    drawCircle(
                                        color = lineColor,
                                        radius = if (idx == points.lastIndex) lastRadius else dotRadius,
                                        center = pt
                                    )
                                }
                            }
                        }
                    }
                }
        )
    }
}

/**
 * Ergonomic touch-friendly futuristic launchpad tile for primary dashboard navigation.
 */
@Composable
fun TacticalCommandTile(
    title: String,
    telemetryLabel: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(4.dp)

    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = modifier.height(82.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val wash = Brush.verticalGradient(
                        colors = listOf(accentColor.copy(alpha = 0.12f), Color.Transparent)
                    )
                    onDrawBehind { drawRect(brush = wash) }
                }
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(accentColor.copy(alpha = 0.18f))
                            .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = telemetryLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.5.sp
                        ),
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Compact horizontal live subsystem readiness strip (Cloud, Vault, Gate, Shell).
 */
@Composable
fun SubsystemReadinessStrip(
    items: List<Triple<String, String, Color>>,
    modifier: Modifier = Modifier
) {
    val forge = ForgeTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (label, status, color) ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, forge.borderHairline, RoundedCornerShape(3.dp))
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(
                    text = "$label:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp
                    ),
                    color = forge.telemetryMuted
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = color
                )
            }
        }
    }
}

/**
 * Avionics-style 3-column hardware toggle deck for Global Agent Permission Modes.
 */
@Composable
fun TacticalPermissionDeck(
    currentMode: AgentPermissionMode,
    onSelectMode: (AgentPermissionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val forge = ForgeTheme.colors
    val activeAccent = when (currentMode) {
        AgentPermissionMode.AUTONOMOUS -> forge.verifiedGreen
        AgentPermissionMode.ASK_WHEN_NEEDED -> forge.amber
        AgentPermissionMode.ALWAYS_ASK -> forge.tacticalBlue
    }

    HudCornerFrame(
        cornerAccent = activeAccent,
        borderColor = forge.borderStrong,
        modifier = modifier
    ) {
        SectionHeaderLabel(
            title = "Agent Governance Deck",
            trailingText = currentMode.shortLabel
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AgentPermissionMode.entries.forEach { mode ->
                val selected = currentMode == mode
                val modeAccent = when (mode) {
                    AgentPermissionMode.AUTONOMOUS -> forge.verifiedGreen
                    AgentPermissionMode.ASK_WHEN_NEEDED -> forge.amber
                    AgentPermissionMode.ALWAYS_ASK -> forge.tacticalBlue
                }
                val compactTitle = when (mode) {
                    AgentPermissionMode.AUTONOMOUS -> "AUTO"
                    AgentPermissionMode.ASK_WHEN_NEEDED -> "SMART"
                    AgentPermissionMode.ALWAYS_ASK -> "STRICT"
                }
                val subTag = when (mode) {
                    AgentPermissionMode.AUTONOMOUS -> "FULL EXEC"
                    AgentPermissionMode.ASK_WHEN_NEEDED -> "RISK GATE"
                    AgentPermissionMode.ALWAYS_ASK -> "MANUAL"
                }
                val shape = RoundedCornerShape(3.dp)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .clip(shape)
                        .background(
                            if (selected) modeAccent.copy(alpha = 0.16f)
                            else forge.recessedWell
                        )
                        .border(
                            width = if (selected) 1.2.dp else 1.dp,
                            color = if (selected) modeAccent else forge.borderHairline,
                            shape = shape
                        )
                        .clickable { onSelectMode(mode) }
                        .testTag("perm_mode_${mode.id.lowercase()}")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(if (selected) modeAccent else Color.Transparent)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = compactTitle,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = if (selected) modeAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subTag,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 9.sp
                            ),
                            color = if (selected) MaterialTheme.colorScheme.onSurface else forge.telemetryMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(forge.recessedWell)
                .border(1.dp, forge.borderHairline, RoundedCornerShape(3.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(22.dp)
                    .background(activeAccent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = currentMode.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Compact horizontal segmented severity distribution bar for visual scanning.
 */
@Composable
fun SegmentedSeverityBar(
    critical: Int,
    high: Int,
    medium: Int,
    low: Int,
    modifier: Modifier = Modifier
) {
    val forge = ForgeTheme.colors
    val total = (critical + high + medium + low).coerceAtLeast(0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(forge.recessedWell)
            .border(0.5.dp, forge.borderHairline, RoundedCornerShape(2.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (total == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(forge.verifiedGreen.copy(alpha = 0.55f))
            )
        } else {
            if (critical > 0) {
                Box(
                    modifier = Modifier
                        .weight(critical.toFloat())
                        .fillMaxHeight()
                        .background(forge.criticalRed)
                )
            }
            if (high > 0) {
                Box(
                    modifier = Modifier
                        .weight(high.toFloat())
                        .fillMaxHeight()
                        .background(forge.amber)
                )
            }
            if (medium > 0) {
                Box(
                    modifier = Modifier
                        .weight(medium.toFloat())
                        .fillMaxHeight()
                        .background(forge.tacticalBlue)
                )
            }
            if (low > 0) {
                Box(
                    modifier = Modifier
                        .weight(low.toFloat())
                        .fillMaxHeight()
                        .background(forge.verifiedGreen)
                )
            }
        }
    }
}

/**
 * Tactile 3D highly embossed forged-metal H1 wordmark for the PatchForge app title.
 * Builds authentic raised-metal bevel relief using theme tokens only:
 * deep ambient shadow -> stepped 3D extrusion walls -> top-left specular highlight -> metallic face gradient.
 */
@Composable
fun EmbossedForgeWordmark(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 34.sp,
    letterSpacing: TextUnit = 4.sp,
    depthScale: Float = 1f
) {
    val forge = ForgeTheme.colors
    val scheme = MaterialTheme.colorScheme
    val displayText = text.uppercase()
    val scale = depthScale.coerceIn(0.45f, 1.5f)

    val baseStyle = MaterialTheme.typography.displaySmall.copy(
        fontSize = fontSize,
        lineHeight = fontSize * 1.12f,
        fontWeight = FontWeight.Black,
        letterSpacing = letterSpacing
    )

    val metallicFaceBrush = remember(scheme.onBackground, scheme.onSurface, forge.amber) {
        Brush.verticalGradient(
            0.0f to scheme.onBackground,
            0.46f to scheme.onSurface.copy(alpha = 0.94f),
            0.76f to scheme.onSurfaceVariant.copy(alpha = 0.92f),
            1.0f to forge.amber.copy(alpha = 0.96f)
        )
    }

    val castShadow = remember(forge.recessedWell, scale) {
        Shadow(
            color = forge.recessedWell,
            offset = Offset(3.5f * scale, 6.5f * scale),
            blurRadius = 11f * scale
        )
    }

    val faceGlow = remember(forge.amber, scale) {
        Shadow(
            color = forge.amber.copy(alpha = 0.34f),
            offset = Offset(0f, 1.4f * scale),
            blurRadius = 4f * scale
        )
    }

    Box(
        modifier = modifier
            .padding(
                start = (1.5f * scale).dp,
                top = (1.5f * scale).dp,
                end = (3.5f * scale).dp,
                bottom = (4f * scale).dp
            )
            .semantics(mergeDescendants = true) {
                contentDescription = text
            }
    ) {
        // 1. Deep cast ambient drop shadow
        Text(
            text = displayText,
            style = baseStyle.copy(shadow = castShadow),
            color = forge.recessedWell.copy(alpha = 0.96f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset(x = (2.6f * scale).dp, y = (3.6f * scale).dp)
                .clearAndSetSemantics {}
        )

        // 2. Deepest 3D metallic extrusion floor
        Text(
            text = displayText,
            style = baseStyle,
            color = forge.amberContainer,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset(x = (2.0f * scale).dp, y = (2.6f * scale).dp)
                .clearAndSetSemantics {}
        )

        // 3. Mid-extrusion machined titanium wall
        Text(
            text = displayText,
            style = baseStyle,
            color = forge.borderStrong,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset(x = (1.3f * scale).dp, y = (1.7f * scale).dp)
                .clearAndSetSemantics {}
        )

        // 4. Lower-right warm amber rim catch
        Text(
            text = displayText,
            style = baseStyle,
            color = forge.amber.copy(alpha = 0.65f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset(x = (0.65f * scale).dp, y = (0.85f * scale).dp)
                .clearAndSetSemantics {}
        )

        // 5. Raised top-left specular bevel highlight (signature embossed ridge)
        Text(
            text = displayText,
            style = baseStyle,
            color = scheme.onBackground.copy(alpha = 0.85f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset(x = (-0.95f * scale).dp, y = (-1.15f * scale).dp)
                .clearAndSetSemantics {}
        )

        // 6. Front brushed-titanium to forged-amber face
        Text(
            text = displayText,
            style = baseStyle.copy(
                brush = metallicFaceBrush,
                shadow = faceGlow
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

