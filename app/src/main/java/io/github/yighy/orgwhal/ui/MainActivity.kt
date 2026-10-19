package io.github.yighy.orgwhal.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import android.content.Context
import io.github.yighy.orgwhal.data.AppLanguage
import io.github.yighy.orgwhal.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.toArgb
import android.graphics.drawable.ColorDrawable
import androidx.activity.SystemBarStyle
import io.github.yighy.orgwhal.data.ThemeMode
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import io.github.yighy.orgwhal.core.IdLinks
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.yighy.orgwhal.reminders.ReminderNotifications
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.yighy.orgwhal.data.NoteRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // The language chosen in the settings, before Android 13
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    private val viewModel: AppViewModel by viewModels()

    /** Refreshed on resume, since the user can change it in system settings. */
    private var notificationsAllowed by mutableStateOf(false)

    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = ReminderNotifications.canPost(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // While shown, files changed by other apps (Syncthing…) are picked up within seconds
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(CHANGES_CHECK_MS)
                    viewModel.checkForChanges()
                }
            }
        }
        handleNotificationIntent(intent)
        // Not again when the screen is recreated (rotation…)
        if (savedInstanceState == null) handleShareIntent(intent)
        setContent {
            val settings by viewModel.settings.collectAsState()
            OrgwhalTheme(settings.themeMode, settings.dynamicColor) {
                CompositionLocalProvider(LocalStrikeDone provides settings.strikeDone, LocalMonospace provides settings.monospace, LocalWrapTables provides settings.wrapTables, LocalFreezeFirstColumn provides settings.freezeFirstColumn) {
                    OrgwhalApp(viewModel, notificationsAllowed, ::askForNotifications)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
        handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        notificationsAllowed = ReminderNotifications.canPost(this)
        // Pick up changes made by other apps (Syncthing, editors…)
        viewModel.refresh()
    }

    /** Asks for the permission the first time, then opens the system settings (the dialog won't show again). */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (!viewModel.askedNotificationPermission || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
        ) {
            viewModel.askedNotificationPermission = true
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        }
    }

    /** Text shared from another app becomes a note. */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        viewModel.receiveShared(
            intent.getStringExtra(Intent.EXTRA_SUBJECT),
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
        )
        intent.action = null
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val uri = intent?.getStringExtra(ReminderNotifications.EXTRA_FILE_URI) ?: return
        viewModel.openFromNotification(
            uri,
            intent.getIntExtra(ReminderNotifications.EXTRA_INDEX, -1),
            intent.getStringExtra(ReminderNotifications.EXTRA_TITLE),
        )
        intent.removeExtra(ReminderNotifications.EXTRA_FILE_URI)
    }
}

/**
 * Light or dark as set ([mode]), with the wallpaper's colors when [dynamicColor] (Android 12+);
 * the system bars and the window behind the screens follow.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OrgwhalTheme(mode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val activity = context as? ComponentActivity
    val background = colors.surface.toArgb()
    LaunchedEffect(dark, background) {
        activity ?: return@LaunchedEffect
        // Bar icons light on dark, dark on light, even when the theme differs from the system's
        val bars = if (dark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        activity.window.setBackgroundDrawable(ColorDrawable(background))
    }
    MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive(), content = content)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun OrgwhalApp(viewModel: AppViewModel, notificationsAllowed: Boolean, onAllowNotifications: () -> Unit) {
    val library by viewModel.library.collectAsState()

    // A note requested from a notification opens once the notes are loaded
    LaunchedEffect(library.loaded) { viewModel.openPendingNote() }

    // Ask once for notifications, so reminders work out of the box (after the guide's offer)
    LaunchedEffect(library.loaded, viewModel.guideChoicePending) {
        if (library.loaded && !viewModel.guideChoicePending && !notificationsAllowed && !viewModel.askedNotificationPermission) {
            onAllowNotifications()
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // A short, fixed-length slide: while the drawer still moves, a tap on it is taken as the start
    // of a drag (and lost), and the default spring takes about a second to settle
    fun moveDrawer(to: DrawerValue) {
        scope.launch { drawer.animateTo(to, tween(DRAWER_MS, easing = FastOutSlowInEasing)) }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { m ->
            // A message with an action (undo) stays longer
            val result = snackbar.showSnackbar(
                m.text,
                m.actionLabel,
                duration = if (m.action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) m.action?.invoke()
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.setFolder(uri)
    }

    BackHandler(enabled = viewModel.stack.size > 1) { viewModel.back() }

    if (viewModel.offerGuide) {
        AlertDialog(
            onDismissRequest = viewModel::dismissGuideOffer,
            title = { Text(stringResource(R.string.guide_offer_title)) },
            text = { Text(stringResource(R.string.guide_offer_text)) },
            confirmButton = { TextButton(onClick = viewModel::addGuide) { Text(stringResource(R.string.add)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissGuideOffer) { Text(stringResource(R.string.no_thanks)) } },
        )
    }

    viewModel.missingLink?.let { link ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMissingLink,
            title = { Text(stringResource(R.string.missing_link_title)) },
            text = { Text(stringResource(R.string.missing_link_text, link.title, library.file(link.fileUri)?.title.orEmpty())) },
            confirmButton = { TextButton(onClick = viewModel::createMissingLink) { Text(stringResource(R.string.create)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissMissingLink) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (viewModel.guideExists) {
        AlertDialog(
            onDismissRequest = viewModel::dismissGuideOffer,
            title = { Text(stringResource(R.string.guide_exists_title)) },
            text = { Text(stringResource(R.string.guide_exists_text)) },
            confirmButton = { TextButton(onClick = viewModel::replaceGuide) { Text(stringResource(R.string.guide_replace)) } },
            dismissButton = { TextButton(onClick = viewModel::openGuide) { Text(stringResource(R.string.open)) } },
        )
    }

    if (library.folderUri == null) {
        WelcomeScreen(onPickFolder = { pickFolder.launch(null) })
        return
    }

    val top = viewModel.stack.last()

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = top == Screen.Notebooks,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 16.dp),
                )
                fun close(action: () -> Unit) {
                    moveDrawer(DrawerValue.Closed)
                    action()
                }
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.notebooks)) },
                    icon = { Icon(Icons.Default.Home, null) },
                    selected = top != Screen.Agenda && top != Screen.Searches,
                    onClick = { close(viewModel::openHome) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.agenda)) },
                    icon = { Icon(Icons.Default.DateRange, null) },
                    selected = top == Screen.Agenda,
                    onClick = { close(viewModel::openAgenda) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                // Saved searches, shown once there is one
                val searches by viewModel.savedSearches.collectAsState()
                if (searches.isNotEmpty()) {
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.searches)) },
                        icon = { Icon(Icons.Default.Search, null) },
                        selected = top == Screen.Searches,
                        onClick = { close(viewModel::openSearches) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 28.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.settings_title)) },
                    icon = { Icon(Icons.Default.Settings, null) },
                    selected = false,
                    onClick = { close(viewModel::openSettings) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
        val motion = MaterialTheme.motionScheme
        // Screens below the top one keep their scroll position and app bar when shown again;
        // the state of screens left (no longer in the stack) is dropped
        val screenStates = rememberSaveableStateHolder()
        val kept = remember { mutableSetOf<String>() }
        val inStack = viewModel.stack.map { it.toString() }.toSet()
        LaunchedEffect(inStack) {
            (kept - inStack).forEach(screenStates::removeState)
            kept.retainAll(inStack)
            kept += inStack
        }
        // App background behind the screens while they cross-fade
        SharedTransitionLayout(Modifier.background(MaterialTheme.colorScheme.surface)) {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
                // What #+transclude: lines show, in every screen
                LocalTransclusions provides remember(library) { Transclusions(library.notes) },
                // A new state for each screen change: a cut transition does not carry over
                LocalSharedCut provides remember(top) { mutableStateOf(false) },
            ) {
                AnimatedContent(
                    targetState = top,
                    transitionSpec = {
                        val fade = motion.defaultEffectsSpec<Float>()
                        if (isContainerTransform(initialState, targetState)) {
                            // The card or row grows into the next screen (shared bounds); just cross-fade the rest
                            fadeIn(fade) togetherWith fadeOut(fade)
                        } else {
                            val slide = motion.defaultSpatialSpec<IntOffset>()
                            val dir = if (viewModel.navigatedForward) 1 else -1
                            (slideInHorizontally(slide) { dir * it / 3 } + fadeIn(fade)) togetherWith
                                (slideOutHorizontally(slide) { -dir * it / 3 } + fadeOut(fade))
                        }
                    },
                    label = "screens",
                ) { screen ->
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        screenStates.SaveableStateProvider(screen.toString()) {
                            ScreenContent(
                                screen, viewModel, library, snackbar, notificationsAllowed, onAllowNotifications,
                                onOpenDrawer = { moveDrawer(DrawerValue.Open) },
                                onPickFolder = { pickFolder.launch(null) },
                            )
                        }
                    }
                }
            }
        }
    }

    viewModel.searchDraft?.let { draft ->
        SavedSearchDialog(
            name = draft.name,
            query = draft.query,
            onDismiss = viewModel::dismissSearchDraft,
            onSave = viewModel::confirmSearchDraft,
            onDelete = viewModel::deleteSearchDraft.takeIf { draft.index != null },
        )
    }

    // A note was renamed: links to it that still show the old title can follow
    viewModel.linkRename?.let { rename ->
        AlertDialog(
            onDismissRequest = viewModel::keepRenamedLinks,
            title = { Text(stringResource(R.string.update_links_title)) },
            text = {
                Text(
                    pluralStringResource(
                        if (rename.name) R.plurals.update_name_links_text else R.plurals.update_links_text,
                        rename.notes,
                        rename.notes,
                        IdLinks.description(rename.oldTitle),
                        IdLinks.description(rename.newTitle),
                    ),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::updateRenamedLinks) { Text(stringResource(R.string.update)) } },
            dismissButton = { TextButton(onClick = viewModel::keepRenamedLinks) { Text(stringResource(R.string.keep)) } },
        )
    }
}

@Composable
private fun ScreenContent(
    screen: Screen,
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    snackbar: SnackbarHostState,
    notificationsAllowed: Boolean,
    onAllowNotifications: () -> Unit,
    onOpenDrawer: () -> Unit,
    onPickFolder: () -> Unit,
) {
    when (screen) {
        Screen.Agenda -> AgendaScreen(viewModel, library, snackbar, notificationsAllowed, onAllowNotifications)

        Screen.Notebooks -> HomeScreen(viewModel, library, snackbar, onOpenDrawer)

        is Screen.NewNote -> NoteScreen(viewModel, library, viewModel.draftNote(screen), snackbar, newNote = screen)

        Screen.Searches -> SearchesScreen(viewModel, snackbar)

        Screen.Settings -> SettingsScreen(viewModel, library, onPickFolder, notificationsAllowed, onAllowNotifications)

        is Screen.Notebook -> {
            val file = library.file(screen.fileUri)
            if (file != null) {
                NotebookScreen(viewModel, library, file, snackbar)
            } else if (!library.loading) {
                LaunchedEffect(screen) { viewModel.back() }
            }
        }

        is Screen.Tree -> {
            val file = library.file(screen.fileUri)
            if (file != null) {
                TreeScreen(viewModel, library, file, snackbar)
            } else if (!library.loading) {
                LaunchedEffect(screen) { viewModel.back() }
            }
        }

        is Screen.Note -> {
            val note = library.note(screen.fileUri, screen.index)
            if (note != null) {
                NoteScreen(viewModel, library, note, snackbar)
            } else if (!library.loading) {
                // Note disappeared (file changed elsewhere): closes its screen, only if it is still
                // the one shown (not one leaving, e.g. a note moved and shown at its new place)
                LaunchedEffect(screen) { if (viewModel.stack.lastOrNull() == screen) viewModel.back() }
            }
        }
    }
}

/** Notebook card ↔ notebook, and outline row ↔ note, share their container. */
private fun isContainerTransform(a: Screen, b: Screen): Boolean {
    fun pair(x: Screen, y: Screen) =
        (x == Screen.Notebooks && y is Screen.Notebook) ||
            (x is Screen.Notebook && y is Screen.Note && x.fileUri == y.fileUri) ||
            (x is Screen.Tree && y is Screen.Note && x.fileUri == y.fileUri) ||
            // The new note's screen becomes the note's screen once it has a title
            (x is Screen.NewNote && y is Screen.Note)
    return pair(a, b) || pair(b, a)
}

/** How often files are checked for changes made by other apps while the app is shown. */
private const val CHANGES_CHECK_MS = 4000L

/** How long the navigation drawer takes to open or close. */
private const val DRAWER_MS = 250
