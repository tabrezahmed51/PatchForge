package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Automatically scrolls the enclosing scrollable container upward so this composable
 * (plus [extraBottomPadding] breathing room below it) remains clearly visible above the
 * soft keyboard whenever it (or any descendant TextField) gains focus and while IME insets grow.
 *
 * Uses [conflate] on IME height snapshots so scroll animations complete smoothly without
 * frame-by-frame coroutine cancellation stutter during keyboard slide-up transitions.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bringIntoViewOnImeFocus(
    extraBottomPadding: Dp = 24.dp
): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    var hasFocus by remember { mutableStateOf(false) }
    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    val imeBottomPx by rememberUpdatedState(WindowInsets.ime.getBottom(density))
    val extraBottomPx = with(density) { extraBottomPadding.toPx() }

    suspend fun requestBringIntoView() {
        val targetRect = if (componentSize.width > 0 && componentSize.height > 0) {
            Rect(
                left = 0f,
                top = 0f,
                right = componentSize.width.toFloat(),
                bottom = componentSize.height.toFloat() + extraBottomPx
            )
        } else {
            null
        }
        requester.bringIntoView(targetRect)
    }

    LaunchedEffect(hasFocus) {
        if (!hasFocus) return@LaunchedEffect
        requestBringIntoView()
        snapshotFlow { imeBottomPx }
            .distinctUntilChanged()
            .conflate()
            .collect { currentImePx ->
                if (currentImePx > 0) {
                    requestBringIntoView()
                    delay(80L)
                    requestBringIntoView()
                }
            }
    }

    this
        .onSizeChanged { componentSize = it }
        .bringIntoViewRequester(requester)
        .onFocusChanged { focusState ->
            hasFocus = focusState.hasFocus
        }
}
