package io.github.yighy.orgwhal.ui

import kotlin.math.roundToInt
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import io.github.yighy.orgwhal.data.AppSettings
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import io.github.yighy.orgwhal.core.NoteTree
import androidx.compose.ui.unit.round
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Animatable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.yighy.orgwhal.R
import io.github.yighy.orgwhal.core.OrgNote
import io.github.yighy.orgwhal.core.TreeKeys
import io.github.yighy.orgwhal.core.TreeLayout
import io.github.yighy.orgwhal.data.NoteRepository

private val BoxWidth = 176.dp
private val BoxHeight = 64.dp
private val ColumnGap = 40.dp
private val RowGap = 12.dp
private val Margin = 24.dp
private const val MIN_SCALE = 0.2f
private const val READABLE_SCALE = 0.6f
private const val MAX_SCALE = 2.5f

/**
 * A notebook drawn as a tree from left to right, to be moved and zoomed with the fingers: a tap
 * on a note opens it, a long press selects it (with the notebook's selection bars, to move notes
 * among others), the round button at its right folds its sub-notes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreeScreen(viewModel: AppViewModel, library: NoteRepository.Library, file: NoteRepository.LoadedFile, snackbar: SnackbarHostState) {
    val notes = file.notes
    // Notes are known by keys that follow them when they move or are changed (folds, animations)
    val noteKeys = remember { TreeKeys() }
    val keys = remember(notes) { noteKeys.update(outlineKeys(notes)) }
    // Where the tree was left (also in an earlier session), its folds kept by the notes' paths
    val paths = remember(notes) { NoteTree.titlePaths(notes) }
    val saved = remember { viewModel.treeState(file.uri) }
    var folded by remember { mutableStateOf(keys.filterIndexedTo(HashSet()) { i, _ -> paths[i] in saved?.folded.orEmpty() }.toSet()) }
    var scale by remember { mutableFloatStateOf(saved?.scale ?: 1f) }
    var offset by remember { mutableStateOf(saved?.let { Offset(it.x, it.y) }) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    val currentKeys by rememberUpdatedState(keys)
    val currentPaths by rememberUpdatedState(paths)
    fun save() {
        val o = offset ?: return
        val pathOf = currentKeys.zip(currentPaths).toMap()
        viewModel.setTreeState(file.uri, AppSettings.TreeState(scale, o.x, o.y, folded.mapNotNullTo(HashSet()) { pathOf[it] }))
    }
    // Saved once the view rests, and when leaving
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(scale, offset, folded) }.collectLatest {
            delay(SAVE_DELAY_MS)
            save()
        }
    }
    DisposableEffect(Unit) { onDispose { save() } }
    val selection = viewModel.selection?.takeIf { it.fileUri == file.uri }
    val selecting = selection != null
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    val levels = remember(notes) { notes.map { it.level } }
    val nodes = remember(levels, keys, folded) {
        TreeLayout.layout(levels, keys.indices.filterTo(HashSet()) { keys[it] in folded })
    }
    fun keyOf(node: TreeLayout.Node) = keys.getOrNull(node.index) ?: ROOT
    val density = LocalDensity.current
    val step = with(density) { Offset((BoxWidth + ColumnGap).toPx(), (BoxHeight + RowGap).toPx()) }
    val box = with(density) { Size(BoxWidth.toPx(), BoxHeight.toPx()) }
    val margin = with(density) { Margin.toPx() }
    fun position(node: TreeLayout.Node) = Offset(margin + node.depth * step.x, margin + node.row * step.y)
    val size = Size(
        2 * margin + (nodes.maxOf { it.depth }) * step.x + box.width,
        2 * margin + (nodes.maxOf { it.row }) * step.y + box.height,
    )
    val target = remember(nodes, keys, step, margin) { nodes.associate { keyOf(it) to position(it) } }

    // Boxes slide to their new places (from their parent when they appear)
    val motion = remember { TreeMotion() }
    val slide = remember(target) { motion.start(target) }
    LaunchedEffect(slide) { slide.animateTo(1f, tween(MOVE_MS)) }
    fun at(key: String, parent: String?) = motion.at(key, parent, slide.value)

    /** [o] kept so that the tree is not moved past its edges (as an image), or off screen when smaller. */
    fun inView(o: Offset, s: Float): Offset {
        val room = Offset(viewport.width - size.width * s, viewport.height - size.height * s)
        return Offset(o.x.coerceIn(minOf(0f, room.x), maxOf(0f, room.x)), o.y.coerceIn(minOf(0f, room.y), maxOf(0f, room.y)))
    }
    /**
     * Shows the whole tree, as large as it fits up to its own size, centered; a tree too large to
     * be read that small shows from its top left, at the smallest readable size.
     */
    fun fitted(smallest: Float = MIN_SCALE): Pair<Float, Offset> {
        val s = minOf(1f, viewport.width / size.width, viewport.height / size.height).coerceAtLeast(smallest)
        return s to Offset(maxOf(0f, (viewport.width - size.width * s) / 2), maxOf(0f, (viewport.height - size.height * s) / 2))
    }
    // The first time, the whole tree when it can be read so; else where it was left, within the tree
    LaunchedEffect(viewport) {
        if (viewport == Size.Zero) return@LaunchedEffect
        val o = offset
        if (o == null) {
            fitted(smallest = READABLE_SCALE).let { (s, at) -> scale = s; offset = at }
        } else {
            offset = inView(o, scale)
        }
    }

    val scope = rememberCoroutineScope()
    val edge = with(density) { 16.dp.toPx() }
    val toolbarRoom = with(density) { 96.dp.toPx() }
    /** Moves the view to [goal], zoomed to [goalScale], smoothly (the fingers stop it). */
    fun glide(goal: Offset, goalScale: Float = scale) {
        val o = offset ?: return
        val s = scale
        if (goal == o && goalScale == s) return
        motion.scroll?.cancel()
        motion.scroll = scope.launch {
            animate(0f, 1f, animationSpec = tween(MOVE_MS)) { t, _ ->
                scale = s + (goalScale - s) * t
                offset = lerp(o, goal, t)
            }
        }
    }
    /** Shows the whole tree (see fitted). */
    fun fit() = fitted().let { (s, at) -> glide(at, s) }
    /**
     * Double tap: zooms in to full size around [point], or back to the whole tree; a tree that
     * is already whole at full size zooms in twice as large.
     */
    fun zoomAt(point: Offset) {
        val o = offset ?: return
        fun around(s: Float) = glide(inView(point - (point - o) * (s / scale), s), s)
        val (whole, at) = fitted()
        when {
            scale < 1f - ZOOM_SLACK -> around(1f)
            whole < scale - ZOOM_SLACK -> glide(at, whole)
            else -> around(DOUBLE_TAP_SCALE)
        }
    }
    /** Moves the view just enough to show the boxes of [shown] (their top left first when they don't fit). */
    fun reveal(shown: Collection<String>) {
        val o = offset ?: return
        val rects = shown.mapNotNull { k -> target[k]?.let { Rect(it, box) } }
        if (rects.isEmpty()) return
        val area = rects.reduce { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
        val maxRight = viewport.width - edge
        val maxBottom = viewport.height - edge - if (selecting) toolbarRoom else 0f
        fun shift(start: Float, end: Float, max: Float) = when {
            start < edge -> edge - start
            end > max -> maxOf(max - end, edge - start)
            else -> 0f
        }
        val dx = shift(o.x + area.left * scale, o.x + area.right * scale, maxRight)
        val dy = shift(o.y + area.top * scale, o.y + area.bottom * scale, maxBottom)
        glide(inView(o + Offset(dx, dy), scale))
    }
    // After everything is folded or unfolded, the tree centered (from its left side when too wide)
    var recenter by remember { mutableStateOf(false) }
    LaunchedEffect(target, recenter) {
        if (!recenter) return@LaunchedEffect
        recenter = false
        val width = size.width * scale
        glide(inView(Offset(if (width <= viewport.width) (viewport.width - width) / 2 else 0f, (viewport.height - size.height * scale) / 2), scale))
    }
    // An unfolded note's sub-notes come into view
    var unfolded by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(target, unfolded) {
        val key = unfolded ?: return@LaunchedEffect
        unfolded = null
        val start = nodes.indexOfFirst { keyOf(it) == key }.takeIf { it >= 0 } ?: return@LaunchedEffect
        val depth = nodes[start].depth
        val end = (start + 1..nodes.size).first { it == nodes.size || nodes[it].depth <= depth }
        reveal(nodes.subList(start, end).map(::keyOf))
    }
    // Selected notes stay shown: their parents unfold, and the view follows them when they move
    val selectedKeys = selection?.indices.orEmpty().mapNotNull { keys.getOrNull(it) }.toSet()
    LaunchedEffect(selection?.indices, keys) {
        val parents = selection?.indices.orEmpty().filter { it < notes.size }
            .flatMap { i -> NoteTree.ancestors(notes, i).map { keys[it.index] } }
        if (parents.any { it in folded }) folded = folded - parents.toSet()
    }
    var lastKeys by remember { mutableStateOf(keys) }
    LaunchedEffect(target) {
        if (keys != lastKeys) {
            lastKeys = keys
            if (selectedKeys.isNotEmpty()) reveal(selectedKeys)
        }
    }

    val foldable = remember(nodes) { nodes.filter { it.index >= 0 && it.hasChildren }.map(::keyOf) }
    val anyOpen = foldable.any { it !in folded }

    Scaffold(
        topBar = {
            TopBarWithSelection(selecting, selectionBar = { SelectionTopBar(viewModel, library, file) }) {
                TopAppBar(
                    title = { Text(file.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = blendedTopBarColors(),
                    navigationIcon = {
                        IconButton(onClick = { viewModel.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    },
                    actions = {
                        if (foldable.isNotEmpty()) {
                            IconButton(onClick = {
                                folded = if (anyOpen) foldable.toSet() else emptySet()
                                recenter = true
                            }) {
                                Icon(
                                    if (anyOpen) OrgwhalIcons.UnfoldLess else OrgwhalIcons.UnfoldMore,
                                    stringResource(if (anyOpen) R.string.collapse_all else R.string.expand_all),
                                )
                            }
                        }
                        IconButton(onClick = { fit() }) { Icon(OrgwhalIcons.FitScreen, stringResource(R.string.tree_fit)) }
                    },
                )
            }
        },
        snackbarHost = { SwipeableSnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .clipToBounds()
                .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
                // Again when the tree changes size (a note folded or unfolded), for the limits of inView
                .pointerInput(size, viewport) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val from = offset ?: return@detectTransformGestures
                        motion.scroll?.cancel()
                        val to = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        // Zooms around the fingers, which also move the tree
                        offset = inView(centroid - (centroid - from) * (to / scale) + pan, to)
                        scale = to
                    }
                }
                // Between the boxes (a box takes taps for itself)
                .pointerInput(size, viewport) { detectTapGestures(onDoubleTap = { zoomAt(it) }) },
        ) {
            val edgeColor = MaterialTheme.colorScheme.outlineVariant
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = offset?.x ?: 0f
                        translationY = offset?.y ?: 0f
                        alpha = if (offset == null) 0f else 1f
                    },
            ) {
                // Square links, as in an org chart: across from the parent's right side, along its
                // children halfway to them, then across to each child's left side (rounded corners)
                Canvas(Modifier.fillMaxSize()) {
                    val path = Path()
                    val corner = 8.dp.toPx()
                    for (node in nodes) {
                        if (node.parent < 0) continue
                        val parent = keyOf(nodes[node.parent])
                        val from = at(parent, null) + Offset(box.width, box.height / 2)
                        val to = at(keyOf(node), parent) + Offset(0f, box.height / 2)
                        val middle = (from.x + to.x) / 2
                        val r = minOf(corner, abs(to.y - from.y) / 2, abs(to.x - from.x) / 2)
                        val down = if (to.y >= from.y) 1f else -1f
                        path.moveTo(from.x, from.y)
                        if (r < 0.5f) {
                            path.lineTo(to.x, to.y)
                            continue
                        }
                        path.lineTo(middle - r, from.y)
                        path.quadraticTo(middle, from.y, middle, from.y + down * r)
                        path.lineTo(middle, to.y - down * r)
                        path.quadraticTo(middle, to.y, middle + r, to.y)
                        path.lineTo(to.x, to.y)
                    }
                    drawPath(path, edgeColor, style = Stroke(1.5.dp.toPx()))
                }
            }
            // The boxes at their size on screen, their content zoomed inside (not in the zoomed
            // layer): a box opening a note grows from where and how big it is shown
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (offset == null) 0f else 1f }) {
                fun onScreen(at: Offset) = (offset ?: Offset.Zero) + at * scale
                for (node in nodes) {
                    val key = keyOf(node)
                    key(key) {
                        val parent = nodes.getOrNull(node.parent)?.let(::keyOf)
                        val note = notes.getOrNull(node.index)
                        TreeBox(
                            title = note?.let { displayTitle(it.title) } ?: file.title,
                            note = note,
                            selected = key in selectedKeys,
                            onClick = {
                                if (note != null) {
                                    if (selecting) viewModel.toggleSelected(note) else viewModel.openNote(note)
                                }
                            },
                            onLongClick = { if (note != null) viewModel.toggleSelected(note) },
                            modifier = Modifier
                                .offset { onScreen(at(key, parent)).round() }
                                .then(if (note != null) Modifier.sharedContainer("note-${note.fileUri}#${note.index}", RoundedCornerShape(12.dp * scale)) else Modifier)
                                .zoomed { scale },
                        )
                        if (note != null && node.hasChildren) {
                            FoldButton(
                                hidden = node.hidden,
                                onClick = {
                                    if (key in folded) {
                                        folded = folded - key
                                        unfolded = key
                                    } else {
                                        folded = folded + key
                                    }
                                },
                                modifier = Modifier
                                    .offset {
                                        val half = 12.dp.toPx()
                                        onScreen(at(key, parent) + Offset(box.width - half, box.height / 2 - half)).round()
                                    }
                                    .zoomed { scale },
                            )
                        }
                    }
                }
            }
            if (selecting) {
                SelectionToolbar(viewModel, file, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
            }
        }
    }
}

/**
 * Lays the content out at its own size and shows it zoomed by [scale], taking the zoomed size: its
 * bounds are the ones on screen (a shared transition starts from them, a tap lands in them).
 */
private fun Modifier.zoomed(scale: () -> Float) = layout { measurable, _ ->
    val s = scale()
    val placeable = measurable.measure(Constraints())
    layout((placeable.width * s).roundToInt(), (placeable.height * s).roundToInt()) {
        placeable.placeWithLayer(0, 0) {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = s
            scaleY = s
        }
    }
}

/** Key of the notebook's own box, apart from the notes' keys. */
private const val ROOT = "\u0000root"
private const val MOVE_MS = 300
private const val SAVE_DELAY_MS = 500L
private const val DOUBLE_TAP_SCALE = 2f
private const val ZOOM_SLACK = 0.05f

/**
 * Where the boxes are drawn while they slide: from where they were ([from]) to their places in
 * the tree ([to]); boxes that appear come out of their parent.
 */
private class TreeMotion {
    private var from: Map<String, Offset> = emptyMap()
    private var to: Map<String, Offset> = emptyMap()
    private var slide: Animatable<Float, *>? = null
    /** The view's own move, stopped by the fingers. */
    var scroll: Job? = null

    /** Starts a slide to [target], from where the boxes are now (even halfway through a slide). */
    fun start(target: Map<String, Offset>): Animatable<Float, *> {
        val now = slide?.value ?: 1f
        from = to.keys.associateWith { at(it, null, now) }
        to = target
        // Nothing to slide from the first time
        return Animatable(if (from.isEmpty()) 1f else 0f).also { slide = it }
    }

    fun at(key: String, parent: String?, progress: Float): Offset {
        val end = to[key] ?: return Offset.Zero
        val start = from[key] ?: parent?.let { from[it] } ?: end
        return lerp(start, end, progress)
    }
}

/** A note's box (or the notebook's, without [note]): its state, priority and title. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeBox(
    title: String,
    note: OrgNote?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val done = note?.isDone == true
    val text = buildAnnotatedString {
        note?.state?.let { state ->
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (done) colors.outline else colors.tertiary)) { append(state) }
            append(' ')
        }
        note?.priority?.let { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.primary)) { append("[#$it] ") } }
        append(title)
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .size(BoxWidth, BoxHeight)
            .clip(shape)
            .background(
                when {
                    note == null -> colors.primaryContainer
                    selected -> colors.secondaryContainer
                    else -> colors.surfaceContainerHigh
                },
            )
            .then(if (selected) Modifier.border(2.dp, colors.primary, shape) else Modifier)
            .then(if (note != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick) else Modifier)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            style = if (note == null) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = when {
                note == null -> colors.onPrimaryContainer
                selected -> colors.onSecondaryContainer
                done -> colors.outline
                else -> colors.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** On a note's right side: folds its sub-notes, or shows how many are folded and unfolds them. */
@Composable
private fun FoldButton(hidden: Int, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (hidden > 0) colors.secondaryContainer else colors.surface,
        contentColor = if (hidden > 0) colors.onSecondaryContainer else colors.outline,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier.size(24.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(if (hidden > 0) "$hidden" else "−", style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}
