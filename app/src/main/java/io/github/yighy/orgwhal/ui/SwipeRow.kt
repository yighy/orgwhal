package io.github.yighy.orgwhal.ui

import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** An action behind a row; [text], when given, shows in place of the [icon] (e.g. "+1d"). */
/** Fills the height given, with the content placed at [top] (given its height) from the top. */
private fun Modifier.inView(top: (Int) -> Int) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
    layout(placeable.width, height) { placeable.place(0, top(placeable.height)) }
}

data class SwipeAction(val icon: ImageVector, val label: String, val text: String? = null, val onClick: () -> Unit)

/**
 * Row that reveals [actions] when swiped left, and runs [onSwipeRight] when swiped right
 * past a threshold. Only the row whose [key] equals [openKey] stays open.
 */
@Composable
fun SwipeRow(
    key: String,
    openKey: String?,
    onOpenChange: (Boolean) -> Unit,
    swipeRightIcon: ImageVector,
    onSwipeRight: () -> Unit,
    actions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    val revealPx = with(density) { (56.dp * actions.size + 12.dp).toPx() }
    val triggerPx = with(density) { 96.dp.toPx() }
    val offset = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }

    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)
    val currentOnOpenChange by rememberUpdatedState(onOpenChange)

    // Close when another row opens, or when swiping is turned off
    LaunchedEffect(openKey, enabled) {
        if ((openKey != key || !enabled) && offset.value != 0f) offset.animateTo(0f, spatial)
    }

    // The part of the row on screen, from its top: on a tall row (a long content), what shows
    // behind it is centered there, not off screen
    var rowHeight by remember { mutableIntStateOf(0) }
    var visibleTop by remember { mutableFloatStateOf(0f) }
    var visibleBottom by remember { mutableFloatStateOf(0f) }

    /** Offset from the row's top that centers something [height] tall in its part on screen. */
    fun inView(height: Int): Int {
        val bottom = if (visibleBottom > visibleTop) visibleBottom else rowHeight.toFloat()
        val center = (visibleTop + bottom) / 2f
        return (center - height / 2f).roundToInt().coerceIn(0, (rowHeight - height).coerceAtLeast(0))
    }

    Box(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { c ->
                val top = c.positionInWindow().y
                val shown = c.boundsInWindow()
                rowHeight = c.size.height
                visibleTop = shown.top - top
                visibleBottom = shown.bottom - top
            },
    ) {
        val x = offset.value
        if (x > 0f) {
            // Swipe right: quick action, highlighted once past the threshold
            Box(
                Modifier
                    .matchParentSize()
                    .background(if (armed) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Icon(
                    swipeRightIcon,
                    null,
                    tint = if (armed) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.inView(::inView).padding(start = 24.dp),
                )
            }
        } else if (x < 0f) {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .inView(::inView)
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actions.forEach { action ->
                    // Plain icons, as for the swipe right
                    IconButton(onClick = {
                        scope.launch { offset.animateTo(0f, spatial) }
                        currentOnOpenChange(false)
                        action.onClick()
                    }) {
                        if (action.text != null) {
                            Text(
                                action.text,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { contentDescription = action.label },
                            )
                        } else {
                            Icon(action.icon, action.label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            }
        }

        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(revealPx, triggerPx, enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                when {
                                    offset.value > triggerPx -> {
                                        currentOnSwipeRight()
                                        offset.animateTo(0f, spatial)
                                    }
                                    offset.value < -revealPx / 2 -> {
                                        currentOnOpenChange(true)
                                        offset.animateTo(-revealPx, spatial)
                                    }
                                    else -> {
                                        if (offset.value < 0f) currentOnOpenChange(false)
                                        offset.animateTo(0f, spatial)
                                    }
                                }
                                armed = false
                            }
                        },
                        onDragCancel = {
                            scope.launch { offset.animateTo(0f, spatial) }
                            armed = false
                        },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            val min = if (actions.isEmpty()) 0f else -revealPx * 1.15f
                            val next = (offset.value + delta).coerceIn(min, triggerPx * 1.5f)
                            val nowArmed = next > triggerPx
                            if (nowArmed != armed) {
                                armed = nowArmed
                                if (nowArmed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            scope.launch { offset.snapTo(next) }
                        },
                    )
                },
        ) {
            content()
        }
    }
}

/** Icons used by the swipe actions (Material Symbols paths), to avoid the large extended icon set. */
object OrgwhalIcons {
    val Check: ImageVector get() = Icons.Default.Check

    val SubNote: ImageVector by lazy {
        icon("SubNote", "M19,15l-6,6l-1.42,-1.42L15.17,16H4V4h2v10h9.17l-3.59,-3.58L13,9L19,15z")
    }

    val Stop: ImageVector by lazy { icon("Stop", "M6,6h12v12H6z") }

    val DoubleArrowDown: ImageVector by lazy {
        icon(
            "DoubleArrowDown",
            "M18,6.41L16.59,5L12,9.58L7.41,5L6,6.41l6,6L18,6.41zM18,13l-1.41,-1.41L12,16.17l-4.59,-4.58L6,13l6,6L18,13z",
        )
    }

    val Archive: ImageVector by lazy {
        icon(
            "Archive",
            "M20.54,5.23l-1.39,-1.68C18.88,3.21 18.47,3 18,3H6c-0.47,0 -0.88,0.21 -1.16,0.55L3.46,5.23C3.17,5.57 3,6.02 3,6.5V19" +
                "c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V6.5c0,-0.48 -0.17,-0.93 -0.46,-1.27zM12,17.5L6.5,12H10v-2h4v2h3.5L12,17.5z" +
                "M5.12,5l0.81,-1h12l0.94,1H5.12z",
        )
    }

    val Tree: ImageVector by lazy { icon("Tree", "M22,11V3h-7v3H9V3H2v8h7V8h2v10h4v3h7v-8h-7v3h-2V8h2v3H22z") }

    val FitScreen: ImageVector by lazy {
        icon(
            "FitScreen",
            "M17,4h3c1.1,0 2,0.9 2,2v2h-2V6h-3V4zM4,8V6h3V4H4C2.9,4 2,4.9 2,6v2H4zM20,16v2h-3v2h3c1.1,0 2,-0.9 2,-2v-2H20z" +
                "M7,18H4v-2H2v2c0,1.1 0.9,2 2,2h3V18zM18,8H6v8h12V8z",
        )
    }

    val Undo: ImageVector by lazy {
        icon(
            "Undo",
            "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 " +
                "3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z",
        )
    }

    val UnfoldMore: ImageVector by lazy {
        icon(
            "UnfoldMore",
            "M12,5.83L15.17,9l1.41,-1.41L12,3 7.41,7.59 8.83,9 12,5.83zM12,18.17L8.83,15l-1.41,1.41L12,21l4.59,-4.59L15.17,15 12,18.17z",
        )
    }

    val UnfoldLess: ImageVector by lazy {
        icon(
            "UnfoldLess",
            "M7.41,18.59L8.83,20 12,16.41 15.17,20l1.41,-1.41L12,14l-4.59,4.59zM16.59,5.41L15.17,4 12,7.59 8.83,4 7.41,5.41 12,10l4.59,-4.59z",
        )
    }

    val NewAbove: ImageVector by lazy { icon("NewAbove", "M8,11h3v10h2V11h3l-4,-4 -4,4zM4,3v2h16V3H4z") }

    val NewBelow: ImageVector by lazy { icon("NewBelow", "M16,13h-3V3h-2v10H8l4,4 4,-4zM4,19v2h16v-2H4z") }

    val Cut: ImageVector by lazy {
        icon(
            "Cut",
            "M9.64,7.64c0.23,-0.5 0.36,-1.05 0.36,-1.64 0,-2.21 -1.79,-4 -4,-4S2,3.79 2,6s1.79,4 4,4c0.59,0 1.14,-0.13 " +
                "1.64,-0.36L10,12l-2.36,2.36C7.14,14.13 6.59,14 6,14c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4c0,-0.59 " +
                "-0.13,-1.14 -0.36,-1.64L12,14l7,7h3v-1L9.64,7.64zM6,8c-1.1,0 -2,-0.89 -2,-2s0.9,-2 2,-2 2,0.89 2,2 -0.9,2 " +
                "-2,2zM6,20c-1.1,0 -2,-0.89 -2,-2s0.9,-2 2,-2 2,0.89 2,2 -0.9,2 -2,2zM12,12.5c-0.28,0 -0.5,-0.22 -0.5,-0.5s0.22," +
                "-0.5 0.5,-0.5 0.5,0.22 0.5,0.5 -0.22,0.5 -0.5,0.5zM19,3l-6,6 2,2 7,-7L22,3z",
        )
    }

    val Copy: ImageVector by lazy {
        icon(
            "Copy",
            "M16,1L4,1c-1.1,0 -2,0.9 -2,2v14h2L4,3h12L16,1zM19,5L8,5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2," +
                "-0.9 2,-2L21,7c0,-1.1 -0.9,-2 -2,-2zM19,21L8,21L8,7h11v14z",
        )
    }

    val Paste: ImageVector by lazy {
        icon(
            "Paste",
            "M19,2h-4.18C14.4,0.84 13.3,0 12,0c-1.3,0 -2.4,0.84 -2.82,2L5,2c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h14c1.1," +
                "0 2,-0.9 2,-2L21,4c0,-1.1 -0.9,-2 -2,-2zM12,2c0.55,0 1,0.45 1,1s-0.45,1 -1,1 -1,-0.45 -1,-1 0.45,-1 1,-1zM19," +
                "20L5,20L5,4h2v3h10L17,4h2v16z",
        )
    }

    val ListBulleted: ImageVector by lazy {
        icon(
            "ListBulleted",
            "M4,10.5c-0.83,0 -1.5,0.67 -1.5,1.5s0.67,1.5 1.5,1.5 1.5,-0.67 1.5,-1.5 -0.67,-1.5 -1.5,-1.5zM4,4.5c-0.83,0 -1.5,0.67 -1.5,1.5S3.17,7.5 4,7.5 5.5,6.83 5.5,6 4.83,4.5 4,4.5zM4,16.67c-0.74,0 -1.33,0.6 -1.33,1.33s0.6,1.33 1.33,1.33 1.33,-0.6 1.33,-1.33 -0.59,-1.33 -1.33,-1.33zM7,19h14v-2H7v2zM7,13h14v-2H7v2zM7,5v2h14V5H7z",
        )
    }

    val Indent: ImageVector by lazy {
        icon("Indent", "M3,21h18v-2H3v2zM3,8v8l4,-4 -4,-4zM11,17h10v-2H11v2zM3,3v2h18V3H3zM11,9h10V7H11v2zM11,13h10v-2H11v2z")
    }

    val Outdent: ImageVector by lazy {
        icon("Outdent", "M11,17h10v-2H11v2zM3,12l4,4V8l-4,4zM3,21h18v-2H3v2zM3,3v2h18V3H3zM11,9h10V7H11v2zM11,13h10v-2H11v2z")
    }

    val Redo: ImageVector by lazy {
        icon(
            "Redo",
            "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.05,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z",
        )
    }

    val CheckBox: ImageVector by lazy {
        icon("CheckBox", "M19,3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.11,0 2,-0.9 2,-2V5c0,-1.1 -0.89,-2 -2,-2zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z")
    }

    val CheckBoxBlank: ImageVector by lazy {
        icon("CheckBoxBlank", "M19,5v14H5V5h14m0,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z")
    }

    val CheckBoxPartial: ImageVector by lazy {
        icon("CheckBoxPartial", "M19,3H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM17,13H7v-2h10v2z")
    }

    val BookmarkAdd: ImageVector by lazy {
        icon("BookmarkAdd", "M17,11v6.97l-5,-2.14l-5,2.14V5h6V3H7c-1.1,0 -2,0.9 -2,2v16l7,-3l7,3V11H17zM21,7h-2v2h-2V7h-2V5h2V3h2v2h2V7z")
    }

    val Link: ImageVector by lazy {
        icon("Link", "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z")
    }

    val Move: ImageVector by lazy { icon("Move", "M16,17.01V10h-2v7.01h-3L15,21l4,-3.99h-3zM9,3L5,6.99h3V14h2V6.99h3L9,3z") }

    val Scheduled: ImageVector by lazy {
        icon(
            "Scheduled",
            "M17,12h-5v5h5v-5zM16,1v2L8,3L8,1L6,1v2L5,3c-1.11,0 -1.99,0.9 -1.99,2L3,19c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 " +
                "2,-2L21,5c0,-1.1 -0.9,-2 -2,-2h-1L18,1h-2zM19,19L5,19L5,8h14v11z",
        )
    }

    val Deadline: ImageVector by lazy {
        icon(
            "Deadline",
            "M22,5.72l-4.6,-3.86 -1.29,1.53 4.6,3.86L22,5.72zM7.88,3.39L6.6,1.86 2,5.71l1.29,1.53 4.59,-3.85zM12.5,8L11," +
                "8v6l4.75,2.85 0.75,-1.23 -4,-2.37L12.5,8zM12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9c4.97,0 9,-4.03 9,-9s-4.03,-9 " +
                "-9,-9zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z",
        )
    }

    val State: ImageVector by lazy {
        icon(
            "State",
            "M17.63,5.84C17.27,5.33 16.67,5 16,5L5,5.01C3.9,5.01 3,5.9 3,7v10c0,1.1 0.9,1.99 2,1.99L16,19c0.67,0 1.27," +
                "-0.33 1.63,-0.84L22,12l-4.37,-6.16z",
        )
    }

    val ToNotebook: ImageVector by lazy {
        icon(
            "ToNotebook",
            "M20,6h-8l-2,-2H4C2.9,4 2.01,4.9 2.01,6L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2zM12," +
                "17v-3H8v-4h4V7l5,5 -5,5z",
        )
    }

    val Bold: ImageVector by lazy {
        icon(
            "Bold",
            "M15.6,10.79c0.97,-0.67 1.65,-1.77 1.65,-2.79 0,-2.26 -1.75,-4 -4,-4H7v14h7.04c2.09,0 3.71,-1.7 3.71," +
                "-3.79 0,-1.52 -0.86,-2.82 -2.15,-3.42zM10,6.5h3c0.83,0 1.5,0.67 1.5,1.5s-0.67,1.5 -1.5,1.5h-3v-3zM13.5," +
                "15.5H10v-3h3.5c0.83,0 1.5,0.67 1.5,1.5s-0.67,1.5 -1.5,1.5z",
        )
    }

    val Italic: ImageVector by lazy { icon("Italic", "M10,4v3h2.21l-3.42,8H6v3h8v-3h-2.21l3.42,-8H18V4z") }

    val Underline: ImageVector by lazy {
        icon("Underline", "M12,17c3.31,0 6,-2.69 6,-6V3h-2.5v8c0,1.93 -1.57,3.5 -3.5,3.5S8.5,12.93 8.5,11V3H6v8c0,3.31 2.69,6 6,6zM5,19v2h14v-2H5z")
    }

    val Strike: ImageVector by lazy { icon("Strike", "M10,19h4v-3h-4v3zM5,4v3h5v3h4V7h5V4H5zM3,14h18v-2H3v2z") }

    val Code: ImageVector by lazy {
        icon("Code", "M9.4,16.6L4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4zM14.6,16.6l4.6,-4.6 -4.6,-4.6L16,6l6,6 -6,6 -1.4,-1.4z")
    }

    private fun icon(name: String, path: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()
}
