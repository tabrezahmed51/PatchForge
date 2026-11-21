package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ForgeTheme
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun ForgePanel(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    accentBorder: Color? = null,
    contentPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val forgeColors = ForgeTheme.colors
    val containerColor = if (elevated) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = accentBorder ?: forgeColors.borderHairline

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun SectionHeaderLabel(
    title: String,
    trailingText: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!trailingText.isNullOrBlank()) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelSmall,
                color = ForgeTheme.colors.telemetryMuted
            )
        }
    }
}

@Composable
fun SeverityBadge(
    severity: String,
    modifier: Modifier = Modifier
) {
    val forge = ForgeTheme.colors
    val (bg, fg, border) = when (severity.uppercase()) {
        "CRITICAL" -> Triple(forge.criticalRedContainer, forge.criticalRed, forge.criticalRed)
        "HIGH" -> Triple(forge.amberContainer, forge.amber, forge.amber)
        "MEDIUM" -> Triple(forge.tacticalBlueContainer, forge.tacticalBlue, forge.tacticalBlue)
        else -> Triple(forge.verifiedGreenContainer, forge.verifiedGreen, forge.verifiedGreen)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(bg)
            .border(1.dp, border.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = severity.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg,
            maxLines = 1
        )
    }
}

@Composable
fun EngineBadge(
    engineType: String,
    modifier: Modifier = Modifier
) {
    val forge = ForgeTheme.colors
    val isSecurity = engineType.contains("SECURITY", ignoreCase = true)
    val bg = if (isSecurity) forge.amberContainer.copy(alpha = 0.65f) else forge.tacticalBlueContainer
    val fg = if (isSecurity) forge.amber else forge.tacticalBlue
    val label = if (isSecurity) "SEC-ENGINE" else "AST-AGENT"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = fg,
            maxLines = 1
        )
    }
}

@Composable
fun StatusLed(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, ForgeTheme.colors.borderHairline, RoundedCornerShape(3.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RiskGaugeMeter(
    riskScore: Int,
    modifier: Modifier = Modifier
) {
    val forge = ForgeTheme.colors
    val clamped = riskScore.coerceIn(0, 100)
    val activeColor = when {
        clamped >= 75 -> forge.criticalRed
        clamped >= 45 -> forge.amber
        clamped >= 20 -> forge.tacticalBlue
        else -> forge.verifiedGreen
    }
    val trackColor = forge.borderHairline

    Box(
        modifier = modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(64.dp)) {
            val strokeWidth = 6.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            drawArc(
                color = activeColor,
                startAngle = 140f,
                sweepAngle = 260f * (clamped / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$clamped",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = activeColor
            )
            Text(
                text = "RISK",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = forge.telemetryMuted
            )
        }
    }
}

@Composable
fun MonospaceDiffView(
    unifiedDiff: String,
    modifier: Modifier = Modifier
) {
    val forge = ForgeTheme.colors
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, forge.borderHairline, RoundedCornerShape(3.dp))
            .padding(vertical = 6.dp)
            .horizontalScroll(scrollState)
    ) {
        unifiedDiff.lines().forEach { line ->
            val (bg, fg) = when {
                line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff --git") ->
                    Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
                line.startsWith("@@") ->
                    forge.diffHunkBg to forge.diffHunkInk
                line.startsWith("+") ->
                    forge.diffAddedBg to forge.diffAddedInk
                line.startsWith("-") ->
                    forge.diffRemovedBg to forge.diffRemovedInk
                else ->
                    Color.Transparent to MaterialTheme.colorScheme.onSurface
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = line.ifEmpty { " " },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    ),
                    color = fg,
                    softWrap = false
                )
            }
        }
    }
}
