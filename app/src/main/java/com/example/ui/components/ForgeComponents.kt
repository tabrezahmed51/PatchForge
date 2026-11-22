package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
                color = ForgeTheme.colors.telemetryMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Compact Dynamic Header System for mobile vertical screens.
 * Reclaims vertical viewport space by pairing a single-line truncated title & status LED
 * with an expandable info/metadata drawer for secondary context and telemetry.
 */
@Composable
fun ForgeDynamicHeader(
    title: String,
    compactSubtitle: String,
    statusLabel: String,
    statusColor: Color,
    infoDetail: String,
    metadataChips: List<Pair<String, String>> = emptyList(),
    trailingAction: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val forge = ForgeTheme.colors
    var infoExpanded by rememberSaveable(title) { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, forge.borderStrong)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(26.dp)
                        .background(forge.amber)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = compactSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = forge.telemetryMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                StatusLed(label = statusLabel, color = statusColor)

                IconButton(
                    onClick = { infoExpanded = !infoExpanded },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (infoExpanded) Icons.Default.ExpandLess else Icons.Outlined.Info,
                        contentDescription = if (infoExpanded) "Hide header details" else "Show header details",
                        tint = if (infoExpanded) forge.amber else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (trailingAction != null) {
                    trailingAction()
                }
            }

            AnimatedVisibility(
                visible = infoExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = forge.borderHairline)
                    Text(
                        text = infoDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (metadataChips.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            metadataChips.forEach { (k, v) ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, forge.borderHairline, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "$k:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = forge.telemetryMuted
                                    )
                                    Text(
                                        text = v,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = forge.tacticalBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
