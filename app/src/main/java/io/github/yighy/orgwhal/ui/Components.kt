package io.github.yighy.orgwhal.ui

import androidx.compose.runtime.collectAsState
import io.github.yighy.orgwhal.core.Templates
import io.github.yighy.orgwhal.data.TemplatesHidden
import androidx.compose.ui.composed
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.TopAppBarState
import androidx.compose.animation.core.animate
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.isSpecified
import io.github.yighy.orgwhal.core.Cookies
import io.github.yighy.orgwhal.core.Clock
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.content.res.Resources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.Surface
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.requiredSize
import io.github.yighy.orgwhal.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import io.github.yighy.orgwhal.core.Markup
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.Placeholder
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.InlineTextContent
import io.github.yighy.orgwhal.core.Checkboxes
import io.github.yighy.orgwhal.core.Links
import java.time.LocalDate
import io.github.yighy.orgwhal.core.Agenda
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import io.github.yighy.orgwhal.core.IdLinks
import io.github.yighy.orgwhal.core.InternalLinks
import io.github.yighy.orgwhal.core.NoteTree
import io.github.yighy.orgwhal.core.SearchQuery
import java.time.LocalDateTime
import io.github.yighy.orgwhal.core.OrgNote
import io.github.yighy.orgwhal.core.OrgTime
import io.github.yighy.orgwhal.data.NoteRepository

/** Title with id: links shown as their description. */
fun displayTitle(title: String): String = IdLinks.plainText(title)

/** App bar colors matching the background, so the bar blends with the screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun blendedTopBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surface,
    scrolledContainerColor = MaterialTheme.colorScheme.surface,
)

/**
 * A large bar's scroll behavior (collapsing as the list scrolls), still while [selecting]: the
 * bar is then folded away under the selection bar, and scrolling up must not open it again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun largeBarScrollBehavior(selecting: Boolean): TopAppBarScrollBehavior {
    val current by rememberUpdatedState(selecting)
    return TopAppBarDefaults.exitUntilCollapsedScrollBehavior(canScroll = remember { { !current } })
}

/**
 * The screen's own top [bar], with [selectionBar] laid over it while [selecting]: the bar keeps
 * its height, so the list under it does not jump, except a large bar, which folds away smoothly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarWithSelection(
    selecting: Boolean,
    selectionBar: @Composable () -> Unit,
    /** A large bar's state: selecting folds it away, then opens it again if it was open. */
    largeBar: TopAppBarState? = null,
    bar: @Composable () -> Unit,
) {
    if (largeBar != null) {
        // The selection bar is a small one: what shows of the large bar under it would be empty
        val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        var restore by remember { mutableStateOf<Float?>(null) }
        LaunchedEffect(selecting) {
            val target = if (selecting) {
                restore = largeBar.heightOffset
                largeBar.heightOffsetLimit
            } else {
                restore?.also { restore = null } ?: return@LaunchedEffect
            }
            animate(largeBar.heightOffset, target, animationSpec = spec) { value, _ -> largeBar.heightOffset = value }
        }
    }
    Box {
        bar()
        if (selecting) {
            // A surface also keeps taps from reaching the bar under it
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.matchParentSize()) {
                Box(contentAlignment = Alignment.TopStart) { selectionBar() }
            }
        }
    }
}

/**
 * Large app bar that collapses on scroll; the search icon turns it into a search field.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchableTopBar(
    title: String,
    subtitle: (@Composable () -> Unit)?,
    navigationIcon: ImageVector,
    navigationDescription: String,
    onNavigation: () -> Unit,
    searchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    searchPlaceholder: String,
    scrollBehavior: TopAppBarScrollBehavior,
    extraActions: @Composable RowScope.() -> Unit = {},
    /** Key of the element the title moves from when the screen opens (see [sharedText]). */
    sharedTitleKey: String? = null,
    /** Saves the search being made; the button shows only when there is something to save. */
    onSaveSearch: (() -> Unit)? = null,
    menu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }

    if (searchActive) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        TopAppBar(
            colors = blendedTopBarColors(),
            navigationIcon = {
                IconButton(onClick = { onSearchActiveChange(false) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.close_search))
                }
            },
            title = {
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            searchPlaceholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            },
            actions = {
                if (onSaveSearch != null) {
                    IconButton(onClick = onSaveSearch) { Icon(OrgwhalIcons.BookmarkAdd, stringResource(R.string.save_search)) }
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Clear, stringResource(R.string.clear_search)) }
                }
            },
        )
    } else {
        // A large flexible app bar whose expanded title (not the collapsed one) moves
        // from the card or row that opened the screen
        TwoRowsTopAppBar(
            title = { expanded ->
                if (expanded) {
                    Text(
                        title,
                        style = MaterialTheme.typography.displaySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = (sharedTitleKey?.let { Modifier.sharedText(it) } ?: Modifier)
                            .graphicsLayer { alpha = expandedAlpha(scrollBehavior) }
                            .then(if (subtitle == null) Modifier.padding(bottom = LargeTitleBottomPadding) else Modifier),
                    )
                } else {
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer { alpha = collapsedAlpha(scrollBehavior) },
                    )
                }
            },
            subtitle = subtitle?.let { content ->
                { expanded ->
                    if (expanded) {
                        Box(Modifier.padding(bottom = LargeTitleBottomPadding).graphicsLayer { alpha = expandedAlpha(scrollBehavior) }) {
                            // The subtitle moves with the title, so they never overlap
                            Box(sharedTitleKey?.let { Modifier.sharedText(subtitleKey(it)) } ?: Modifier) {
                                ProvideTextStyle(MaterialTheme.typography.titleMedium) { content() }
                            }
                        }
                    } else {
                        Box(Modifier.graphicsLayer { alpha = collapsedAlpha(scrollBehavior) }) { content() }
                    }
                }
            },
            collapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
            expandedHeight = if (subtitle != null) {
                TopAppBarDefaults.LargeFlexibleAppBarWithSubtitleExpandedHeight
            } else {
                TopAppBarDefaults.LargeFlexibleAppBarWithoutSubtitleExpandedHeight
            },
            navigationIcon = {
                IconButton(onClick = onNavigation) { Icon(navigationIcon, navigationDescription) }
            },
            actions = {
                extraActions()
                IconButton(onClick = { onSearchActiveChange(true) }) { Icon(Icons.Default.Search, stringResource(R.string.search)) }
                if (menu != null) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            menu { menuOpen = false }
                        }
                    }
                }
            },
            colors = blendedTopBarColors(),
            scrollBehavior = scrollBehavior,
        )
    }
}

/** The expanded title and subtitle fade out over the first half of the collapse... */
@OptIn(ExperimentalMaterial3Api::class)
private fun expandedAlpha(scrollBehavior: TopAppBarScrollBehavior) =
    (1f - scrollBehavior.state.collapsedFraction * 2f).coerceIn(0f, 1f)

/** ...and the collapsed ones fade in over the second half. */
@OptIn(ExperimentalMaterial3Api::class)
private fun collapsedAlpha(scrollBehavior: TopAppBarScrollBehavior) =
    ((scrollBehavior.state.collapsedFraction - 0.5f) * 2f).coerceIn(0f, 1f)

/** Key of the subtitle that moves with the title shared as [titleKey]. */
fun subtitleKey(titleKey: String) = "$titleKey/subtitle"

/** Space under the expanded title, as in LargeFlexibleTopAppBar (measured there from the baseline). */
private val LargeTitleBottomPadding = 22.dp

/**
 * Set when a swipe cuts the screen transition short: shared elements then stop moving and
 * show where they are (see [cutTransitionOnScroll]).
 */
val LocalSharedCut = staticCompositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }

/** Shared transition scopes, provided by the screen host so screens can share elements. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Container transform: an element with the same [key] on the next screen grows out of this one
 * (notebook card into notebook screen, note row into note screen, tree box into note screen).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedContainer(key: String, shape: Shape): Modifier {
    if (LocalSharedCut.current.value) return this
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalAnimatedVisibilityScope.current ?: return this
    return with(shared) {
        this@sharedContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
            // Not too long: a card scrolled while coming back follows its place until it settles
            boundsTransform = { _, _ -> SharedBoundsSpec },
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

/**
 * Shared elements move along a fixed-length curve slowing down at the end (Material's
 * emphasized decelerate), rather than a spring: they land at a known time, without a long tail,
 * so scrolling held during the transition comes back soon.
 */
private val SharedBoundsSpec = tween<Rect>(200, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))

/**
 * Text that moves and scales from the element with the same [key] on the previous screen
 * (notebook name into the app bar, note title and content into the note screen).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedText(key: String): Modifier {
    if (LocalSharedCut.current.value) return this
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalAnimatedVisibilityScope.current ?: return this
    return with(shared) {
        this@sharedText.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> SharedBoundsSpec },
            // Laid out once at its own size, then scaled: text keeps its lines while it grows
            resizeMode = scaleToBounds(ContentScale.FillWidth, Alignment.TopStart),
        )
    }
}

/**
 * True while the screen is coming in or going away, until its shared elements (cards, titles)
 * have landed: scrolling then would move the list under them while they slide to their place.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun screenTransitionRunning(): Boolean {
    // The screen is still coming in or going away until it has reached the state it is going to
    val screen = LocalAnimatedVisibilityScope.current?.transition
    return (screen != null && screen.currentState != screen.targetState) ||
        LocalSharedTransitionScope.current?.isTransitionActive == true
}

/** Search results over [notes]. */
@Composable
fun SearchResults(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    notes: List<OrgNote>,
    modifier: Modifier = Modifier,
) {
    val idle = viewModel.query.isBlank() && viewModel.tag == null
    // Orgzly-style query; the selected tag chip adds a t. condition
    val queryText = listOfNotNull(viewModel.query, viewModel.tag?.let { "t.\"$it\"" }).joinToString(" ")
    val context = remember(library) { searchContext(library) }
    val query = remember(queryText) { SearchQuery.parse(queryText) }
    // Template notes left out as set, unless looked for by their tag
    val hidden = viewModel.settings.collectAsState().value.templatesHidden
    val skipTemplates = hidden == TemplatesHidden.AgendaAndSearch && !queryText.contains(Templates.TAG, ignoreCase = true)
    val results = remember(query, notes, context, skipTemplates) {
        if (idle) emptyList() else query.apply(if (skipTemplates) notes.filterNot(Templates::isTemplate) else notes, context)
    }
    // ad.N: the matching notes as an agenda of N days
    val today = LocalDate.now()
    val agenda = remember(query, results, today) {
        query.agendaDays?.takeIf { !idle }?.let { Agenda.build(results, today, it) }
    }
    val listState = rememberLazyListState()
    Box(modifier) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 112.dp)) {
            if (agenda != null) {
                agendaItems(viewModel, library, agenda, today)
                return@LazyColumn
            }
            if (results.isEmpty()) {
                item {
                    Text(
                        stringResource(if (idle) R.string.search_hint else R.string.no_matching_notes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(results, key = { it.fileUri + "#" + it.index }) { note ->
                NoteLine(
                    note = note,
                    subtitle = library.file(note.fileUri)?.title,
                    onClick = { viewModel.openNote(note) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    onOpenLink = viewModel::openLink,
                )
            }
        }
        JumpButton(listState, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
}

/**
 * A small, faint button over the bottom of a long list, to jump to its end: it shows while the
 * list is scrolled down by hand and goes a moment after it stops. The top is a tap on the top bar.
 */
@Composable
fun JumpButton(state: LazyListState, modifier: Modifier = Modifier, enabled: Boolean = true) = JumpButton(
    interactions = state.interactionSource,
    inProgress = { state.isScrollInProgress },
    position = { state.firstVisibleItemIndex * 1_000_000L + state.firstVisibleItemScrollOffset },
    canDown = { state.canScrollForward },
    long = { state.layoutInfo.let { it.totalItemsCount > it.visibleItemsInfo.size * JUMP_SCREENS } },
    onBottom = { state.animateScrollToItem((state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) },
    modifier = modifier,
    enabled = enabled,
)

/** [JumpButton] for a page scrolled as a whole (a note). */
@Composable
fun JumpButton(state: ScrollState, modifier: Modifier = Modifier, enabled: Boolean = true) = JumpButton(
    interactions = state.interactionSource,
    inProgress = { state.isScrollInProgress },
    position = { state.value.toLong() },
    canDown = { state.canScrollForward },
    long = { state.maxValue > state.viewportSize * (JUMP_SCREENS - 1) },
    onBottom = { state.animateScrollTo(state.maxValue) },
    modifier = modifier,
    enabled = enabled,
)

@Composable
private fun JumpButton(
    interactions: InteractionSource,
    inProgress: () -> Boolean,
    position: () -> Long,
    canDown: () -> Boolean,
    long: () -> Boolean,
    onBottom: suspend () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    var down by remember { mutableStateOf(false) }
    var byHand by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(interactions) {
        interactions.interactions.collect { if (it is DragInteraction.Start) byHand = true }
    }
    LaunchedEffect(Unit) {
        var last = position()
        snapshotFlow(position).collect { now ->
            if (now != last) down = now > last
            last = now
        }
    }
    // Only scrolled by hand (a fling included), not when the screen scrolls by itself
    val scrolling = inProgress()
    LaunchedEffect(scrolling, byHand) {
        if (scrolling) {
            if (byHand) shown = true
        } else {
            delay(JUMP_HIDE_MS)
            shown = false
            byHand = false
        }
    }
    val visible = enabled && shown && down && long() && canDown()
    val scope = rememberCoroutineScope()
    AnimatedVisibility(visible, modifier, enter = fadeIn(), exit = fadeOut()) {
        Surface(
            onClick = { scope.launch { onBottom() } },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                stringResource(R.string.scroll_to_bottom),
                modifier = Modifier.padding(6.dp).size(24.dp),
            )
        }
    }
}

/** A tap on a top bar, beside its buttons, brings its screen back to the top with [onTop]. */
fun Modifier.tapToTop(onTop: suspend () -> Unit): Modifier = composed {
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(onTop)
    pointerInput(Unit) { detectTapGestures { scope.launch { current() } } }
}

/** Opens a large app bar folded away by scrolling, as the list is back at its top. */
suspend fun TopAppBarState.expand() {
    animate(heightOffset, 0f) { value, _ -> heightOffset = value }
}

/** How many screens long a list must be for [JumpButton] to show. */
private const val JUMP_SCREENS = 3

/** How long [JumpButton] stays once the scrolling stops. */
private const val JUMP_HIDE_MS = 1500L

/** State, priority, title and a details line (tags, planning, [subtitle]). */
@Composable
fun NoteLine(
    note: OrgNote,
    subtitle: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onOpenLink: ((String) -> Unit)? = null,
    titleModifier: Modifier = Modifier,
) {
    Column((if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).then(modifier)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            note.state?.let {
                StateLabel(it, note.isDone)
                Spacer(Modifier.width(8.dp))
            }
            note.priority?.let {
                Text("#$it", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
            }
            val titleStyle = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (note.isDone && LocalStrikeDone.current) TextDecoration.LineThrough else null,
                color = if (note.isDone) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
            if (onOpenLink != null) {
                // A done note's links are grayed out with the rest of its title
                LinkedText(
                    note.title,
                    titleStyle,
                    onOpenLink,
                    titleModifier,
                    maxLines = 2,
                    linkColor = if (note.isDone) titleStyle.color else Color.Unspecified,
                )
            } else {
                Text(displayTitle(note.title), style = titleStyle, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = titleModifier)
            }
        }
        // Timestamps are parsed once per note, not on every redraw of the list
        val resources = LocalResources.current
        val details = remember(note.tags, note.scheduled, note.deadline, note.content, subtitle, resources) {
            buildList {
                if (Clock.running(note.content) != null) add(resources.getString(R.string.clock_running))
                if (note.tags.isNotEmpty()) add(note.tags.joinToString(" ") { "#$it" })
                note.scheduled?.let { add(resources.getString(R.string.scheduled_at, displayTime(resources, it))) }
                note.deadline?.let { add(resources.getString(R.string.deadline_at, displayTime(resources, it))) }
                subtitle?.let { add(it) }
            }
        }
        if (details.isNotEmpty()) {
            Text(
                details.joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun StateLabel(state: String, isDone: Boolean) {
    val container = if (isDone) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.tertiaryContainer
    val content = if (isDone) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onTertiaryContainer
    Text(
        state,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = content,
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** The launcher icon, drawn at [size] in the app's own screens. */
@Composable
fun AppIcon(size: Dp = 56.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 25))
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        // The adaptive icon canvas is 108dp for a 72dp visible area
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(size * 1.5f))
    }
}

/** Notebook's [icon] (an emoji), or else its initial, in a circle of the theme's color. */
@Composable
fun NotebookAvatar(title: String, icon: String? = null) {
    val container = MaterialTheme.colorScheme.primaryContainer
    val content = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Text(icon, fontSize = 28.sp)
        } else {
            Text(
                title.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = content,
            )
        }
    }
}

/**
 * Text where links ([[id:...]], [[https://...][…]], plain URLs, mailto:, tel:…) are shown by their
 * description and open their target ([onOpenLink] gets it, e.g. "id:…" or "https://…").
 * A tap elsewhere calls [onTextTap] with the position tapped in [text], to edit it from there.
 */
@Composable
fun LinkedText(
    text: String,
    style: TextStyle,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    onTextTap: ((Int) -> Unit)? = null,
    /** Given, Org checkboxes ("- [ ] item") show as boxes; a tap toggles the nth one. */
    onToggleCheckbox: ((Int) -> Unit)? = null,
    /** Color of the links; the theme's link color by default. */
    linkColor: Color = Color.Unspecified,
) {
    // Links keep the line through a struck text
    val struck = style.textDecoration?.contains(TextDecoration.LineThrough) == true
    val linkStyle = TextLinkStyles(
        SpanStyle(
            color = linkColor.takeOrElse { MaterialTheme.colorScheme.primary },
            textDecoration = if (struck) TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough)) else TextDecoration.Underline,
        ),
    )
    val toggle by rememberUpdatedState(onToggleCheckbox)
    val showBoxes = onToggleCheckbox != null
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val targetColor = MaterialTheme.colorScheme.onSurfaceVariant
    // The text as shown, and how its parts map to the raw text (links, checkboxes, emphasis, cookies)
    val (annotated, ranges, boxes) = remember(text, linkStyle, showBoxes, codeBackground, struck, targetColor) {
        val ranges = mutableListOf<ShownPart>()
        fun overlap(a: IntRange, b: IntRange) = a.first <= b.last && b.first <= a.last
        val markup = Markup.find(text)
        // Verbatim and code keep their text as written, an address in it included (=file:=)
        val literal = markup.filter { it.style == Markup.Style.Verbatim || it.style == Markup.Style.Code }
        val links = Links.find(text).filter { link -> link.description != null || literal.none { overlap(it.range, link.range) } }
        val boxes = if (showBoxes) Checkboxes.find(text).filter { b -> links.none { b.box in it.range } } else emptyList()
        // Emphasis around a link is left as written
        val emphasis = markup.filter { e -> links.none { overlap(it.range, e.range) } }
        // Filled-in [2/3] and [66%] cookies, outside links
        val cookies = if ('[' !in text) {
            emptyList()
        } else {
            Cookies.COOKIE.findAll(text)
                .filter { m -> m.groupValues[1].isNotEmpty() && links.none { m.range.first in it.range } }
                .toList()
        }
        // Targets (<<name>>, where links lead) small and grey, as the names of tables
        val targets = if ("<<" !in text) {
            emptyList()
        } else {
            InternalLinks.TARGET.findAll(text)
                .filter { m -> links.none { overlap(it.range, m.range) } && literal.none { overlap(it.range, m.range) } }
                .map { m -> m.range to TargetMark(m.value) }
                .toList()
        }
        val segments = (links.map { it.range to it } + boxes.map { it.range to it } + emphasis.map { it.range to it } + cookies.map { it.range to it } + targets)
            .sortedBy { it.first.first }
        val shown = buildAnnotatedString {
            var last = 0
            segments.forEach { (range, segment) ->
                if (range.first < last) return@forEach
                append(text.substring(last, range.first))
                val start = length
                when (segment) {
                    is Links.Link -> withLink(LinkAnnotation.Clickable(segment.target, linkStyle) { onOpenLink(segment.target) }) {
                        append(segment.shown)
                    }
                    is Checkboxes.Box -> appendInlineContent("box${boxes.indexOf(segment)}", if (segment.checked) "[X]" else "[ ]")
                    is Markup.Emphasis -> withStyle(emphasisStyle(segment.style, codeBackground, struck)) { append(segment.content) }
                    is MatchResult -> appendInlineContent("cookie:" + segment.value, segment.value)
                    is TargetMark -> withStyle(SpanStyle(color = targetColor, fontSize = 0.75.em, fontFamily = FontFamily.Monospace)) { append(segment.text) }
                }
                ranges += ShownPart(start until length, range, editable = segment is Markup.Emphasis)
                last = range.last + 1
            }
            append(text.substring(last))
        }
        Triple(shown, ranges, boxes)
    }
    val boxColor = MaterialTheme.colorScheme.primary
    val cookieContent = if ('[' !in text) emptyMap() else Cookies.COOKIE.findAll(text).filter { it.groupValues[1].isNotEmpty() }.associate { m ->
        "cookie:" + m.value to cookieInline(m, style)
    }
    val inlineContent = cookieContent + boxes.withIndex().associate { (i, box) ->
        "box$i" to InlineTextContent(Placeholder(1.4.em, 1.2.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(
                when {
                    box.checked -> OrgwhalIcons.CheckBox
                    box.partial -> OrgwhalIcons.CheckBoxPartial
                    else -> OrgwhalIcons.CheckBoxBlank
                },
                stringResource(if (box.checked) R.string.checked else R.string.not_checked),
                tint = boxColor,
                modifier = Modifier.fillMaxSize().clickable { toggle?.invoke(i) },
            )
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val tap = if (onTextTap == null) Modifier else Modifier.pointerInput(annotated) {
        detectTapGestures { position ->
            val shown = layout?.getOffsetForPosition(position) ?: annotated.length
            rawOffset(shown, ranges)?.let(onTextTap)
        }
    }
    Text(
        annotated,
        style = style,
        modifier = modifier.then(tap),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        inlineContent = inlineContent,
        onTextLayout = { layout = it },
    )
}

/** A cookie such as [2/3] or [66%] as a small pill, colored when all is done. */
@Composable
private fun cookieInline(m: MatchResult, style: TextStyle): InlineTextContent {
    val (done, total, percent) = m.destructured
    val shown = if (percent == "%") "$done%" else "$done/$total"
    val complete = if (percent == "%") done == "100" else done.isNotEmpty() && done == total && done != "0"
    val container = if (complete) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (complete) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val fontSize = if (style.fontSize.isSpecified) style.fontSize * 0.75f else 12.sp
    return InlineTextContent(Placeholder((shown.length * 0.5f + 1f).em, 1.3.em, PlaceholderVerticalAlign.TextCenter)) {
        Box(
            Modifier.fillMaxSize().padding(horizontal = 1.dp).clip(RoundedCornerShape(50)).background(container),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                shown,
                color = content,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                // Centered in the pill, without the font's extra space above and below
                style = TextStyle(
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ),
            )
        }
    }
}

/** Position in the raw text of position [shown] in the text as displayed; null on a link or box. */
private fun rawOffset(shown: Int, parts: List<ShownPart>): Int? {
    var shift = 0
    for (part in parts) {
        if (shown < part.shown.first) break
        if (shown <= part.shown.last) {
            // In emphasis, the text is the raw one without its first marker; a tap on a link
            // or a box opens or toggles it (they handle that)
            return if (part.editable) part.raw.first + 1 + (shown - part.shown.first) else null
        }
        shift = part.raw.last - part.shown.last
    }
    return shown + shift
}

/** Whether done notes show struck through (a setting). */
val LocalStrikeDone = compositionLocalOf { true }

/** Whether a wide table keeps its first column at the left as it scrolls sideways (a setting). */
val LocalFreezeFirstColumn = compositionLocalOf { false }

/** Whether long table fields wrap onto several lines (a setting). */
val LocalWrapTables = compositionLocalOf { true }

/** Whether notes' content shows in a fixed-width font (a setting). */
val LocalMonospace = compositionLocalOf { false }

/** [style] for notes' content: in a fixed-width font when so set. */
@Composable
fun contentStyle(style: TextStyle): TextStyle = if (LocalMonospace.current) style.copy(fontFamily = FontFamily.Monospace) else style

private val TABLE_LINE = Regex("""^[ \t]*(\||#\+TBLFM:).*$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

/**
 * Text being edited with its table lines (and their formulas) in a fixed-width font, so that
 * columns aligned as Emacs writes them line up.
 */
val TableLinesMonospace = VisualTransformation { text ->
    val styled = AnnotatedString.Builder(text).apply {
        TABLE_LINE.findAll(text.text).forEach { addStyle(SpanStyle(fontFamily = FontFamily.Monospace), it.range.first, it.range.last + 1) }
    }.toAnnotatedString()
    TransformedText(styled, OffsetMapping.Identity)
}

/** A part of the text shown otherwise than written: a link, a checkbox, emphasis. */
/** A target as written, <<name>>. */
private data class TargetMark(val text: String)

private data class ShownPart(val shown: IntRange, val raw: IntRange, val editable: Boolean)

/** How Org emphasis shows ([struck]: within a text struck through). */
private fun emphasisStyle(style: Markup.Style, codeBackground: Color, struck: Boolean): SpanStyle = when (style) {
    Markup.Style.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
    Markup.Style.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
    // In a struck text, the line through stays
    Markup.Style.Underline -> SpanStyle(
        textDecoration = if (struck) TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough)) else TextDecoration.Underline,
    )
    Markup.Style.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    Markup.Style.Verbatim, Markup.Style.Code -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
}

/** Pull down to reload the files from the folder. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PullToRefresh(viewModel: AppViewModel, content: @Composable () -> Unit) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = viewModel.refreshing,
        onRefresh = viewModel::pullToRefresh,
        state = state,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = viewModel.refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        content()
    }
}

/**
 * Text field that looks like the text it edits (same [style], no box or label), focused when
 * shown, with the cursor where [value] has it. The caller saves on [onFocusLost], and on [onDone]
 * (the keyboard's Done key) if given. [placeholder] shows while it is empty.
 */
@Composable
fun InPlaceField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    onFocusLost: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = false,
    /** The key that calls [onDone]: Done, or Next to go on to another field. */
    imeAction: ImeAction = ImeAction.Done,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val textStyle = style.copy(color = style.color.takeOrElse { LocalContentColor.current })
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (onDone != null) imeAction else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }, onNext = { onDone?.invoke() }),
        modifier = modifier
            .focusRequester(focus)
            .onFocusChanged {
                // Only after the field had the focus, not on the first composition
                if (focused && !it.isFocused) onFocusLost?.invoke()
                focused = it.isFocused
            },
        decorationBox = { field ->
            Box {
                if (value.text.isEmpty() && placeholder != null) {
                    Text(placeholder, style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                }
                field()
            }
        },
    )
}

/**
 * Fades the start and end of a scrolling row where there is more to see, so it shows it scrolls.
 */
fun Modifier.fadingEdges(state: LazyListState, width: Dp = 32.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = width.toPx().coerceAtMost(size.width / 2)
        if (state.canScrollBackward) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = w),
                size = Size(w, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (state.canScrollForward) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - w, endX = size.width),
                topLeft = Offset(size.width - w, 0f),
                size = Size(w, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * Clears the focus when the keyboard is closed (back, or its own close key), so the field being
 * edited is saved and no cursor is left behind.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClearFocusWhenKeyboardHides() {
    val imeVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current
    var wasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            wasVisible = true
        } else if (wasVisible) {
            // Moving from one field to another can hide the keyboard for a moment
            delay(300)
            wasVisible = false
            if (!EditingDialog.open) focusManager.clearFocus()
        }
    }
}

/**
 * A dialog opened from the keyboard toolbar: the keyboard goes away while it is open, but the
 * text stays in edit mode, to come back to it after.
 */
object EditingDialog {
    var open by mutableStateOf(false)
}

/** A timestamp as shown, e.g. "Mon, Oct 5, 2026 · 09:00 · every week (+)", in the app's language. */
fun displayTime(resources: Resources, timestamp: String?): String? =
    OrgTime.display(timestamp) { repeatText(resources, it) }

/** A repeater ("+1w") in words: "every week (+)", "every 2 days from done (.+)". */
fun repeatText(resources: Resources, repeater: String): String {
    val repeat = OrgTime.Repeat.parse(repeater) ?: return resources.getString(R.string.repeat_unknown, repeater)
    val interval = resources.getQuantityString(
        when (repeat.unit) {
            'h' -> R.plurals.repeat_every_hour
            'd' -> R.plurals.repeat_every_day
            'w' -> R.plurals.repeat_every_week
            'm' -> R.plurals.repeat_every_month
            else -> R.plurals.repeat_every_year
        },
        repeat.every,
        repeat.every,
    )
    val text = if (repeat.type == ".+") resources.getString(R.string.repeat_from_done, interval) else interval
    return "$text (${repeat.type})"
}

/** Notebook names, keywords and inherited tags that search queries need. */
fun searchContext(library: NoteRepository.Library): SearchQuery.Context {
    val inherited = library.files.associate { it.uri to NoteTree.inheritedTags(it.notes) }
    return SearchQuery.Context(
        now = LocalDateTime.now(),
        keywords = { note -> library.file(note.fileUri)?.doc?.keywords ?: io.github.yighy.orgwhal.core.Keywords.DEFAULT },
        // Orgzly's notebook name: the file name without .org
        notebookName = { note -> note.fileName.removeSuffix(".org") },
        inheritedTags = { note -> inherited[note.fileUri]?.getOrNull(note.index).orEmpty() },
    )
}

/**
 * "12 notes · 3 to do" under a title; tapping "3 to do" runs [onTodoClick]
 * (a search for the notes to do).
 */
@Composable
fun CountsSubtitle(notes: Int?, todo: Int, onTodoClick: () -> Unit) {
    Row {
        notes?.let {
            Text(pluralStringResource(R.plurals.n_notes, it, it), maxLines = 1)
            if (todo > 0) Text(" · ", maxLines = 1)
        }
        if (todo > 0) {
            Text(
                pluralStringResource(R.plurals.n_to_do, todo, todo),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.clickable(onClick = onTodoClick),
            )
        }
    }
}
