package io.github.yighy.orgwhal.ui

import androidx.annotation.StringRes
import io.github.yighy.orgwhal.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.yighy.orgwhal.core.Placement
import io.github.yighy.orgwhal.data.NoteRepository

/** App bar while notes are selected: cut, copy, paste, move, and delete in the overflow menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(viewModel: AppViewModel, library: NoteRepository.Library, file: NoteRepository.LoadedFile) {
    val count = viewModel.selection?.indices?.size ?: 0
    val single = count == 1
    var pasteMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var pickNotebook by remember { mutableStateOf(false) }

    // Move mode: the actions give way to the move arrows, like in Orgzly
    var moving by remember { mutableStateOf(false) }
    BackHandler(enabled = moving) { moving = false }

    TopAppBar(
        colors = blendedTopBarColors(),
        navigationIcon = {
            if (moving) {
                IconButton(onClick = { moving = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.done_moving)) }
            } else {
                IconButton(onClick = viewModel::clearSelection) { Icon(Icons.Default.Close, stringResource(R.string.clear_selection)) }
            }
        },
        title = { Text(if (moving) stringResource(R.string.move) else pluralStringResource(R.plurals.n_selected, count, count)) },
        actions = {
            val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AnimatedContent(
                targetState = moving,
                transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
                label = "selection actions",
            ) { isMoving ->
                Row {
                    if (isMoving) {
                        IconButton(enabled = single, onClick = { viewModel.moveSelection(AppViewModel.Move.Up) }) {
                            Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.move_up))
                        }
                        IconButton(enabled = single, onClick = { viewModel.moveSelection(AppViewModel.Move.Down) }) {
                            Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.move_down))
                        }
                        IconButton(onClick = { viewModel.moveSelection(AppViewModel.Move.Promote) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.promote))
                        }
                        IconButton(onClick = { viewModel.moveSelection(AppViewModel.Move.Demote) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.demote))
                        }
                        IconButton(onClick = { pickNotebook = true }) {
                            Icon(OrgwhalIcons.ToNotebook, stringResource(R.string.move_to))
                        }
                    } else {
                        IconButton(onClick = viewModel::cutSelection) { Icon(OrgwhalIcons.Cut, stringResource(R.string.cut)) }
                        IconButton(onClick = viewModel::copySelection) { Icon(OrgwhalIcons.Copy, stringResource(R.string.copy)) }
                        if (single && viewModel.clipboard.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { pasteMenu = true }) { Icon(OrgwhalIcons.Paste, stringResource(R.string.paste)) }
                                DropdownMenu(expanded = pasteMenu, onDismissRequest = { pasteMenu = false }) {
                                    Placement.entries.forEach { placement ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(pasteLabel(placement))) },
                                            leadingIcon = { Icon(placementIcon(placement), null) },
                                            onClick = { pasteMenu = false; viewModel.pasteAtSelection(placement) },
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(onClick = { moving = true }) { Icon(OrgwhalIcons.Move, stringResource(R.string.move)) }
                        Box {
                            IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.select_all)) },
                                    onClick = { moreMenu = false; viewModel.selectAll(file.notes) },
                                )
                                if (!file.isArchive) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.archive)) },
                                        onClick = { moreMenu = false; viewModel.archiveSelection() },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    onClick = { moreMenu = false; viewModel.deleteSelection() },
                                )
                            }
                        }
                    }
                }
            }
        },
    )

    if (pickNotebook) {
        RefileDialog(
            library = library,
            moving = viewModel.selectedNotes(),
            onDismiss = { pickNotebook = false },
            onConfirm = { uri, parent ->
                pickNotebook = false
                viewModel.refileSelection(uri, parent)
            },
        )
    }
}

private sealed interface ToolbarDialog {
    data object Scheduled : ToolbarDialog
    data object Deadline : ToolbarDialog
    data object State : ToolbarDialog
}

/** Floating toolbar at the bottom while notes are selected: planning, state, done, new note. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectionToolbar(viewModel: AppViewModel, file: NoteRepository.LoadedFile, modifier: Modifier = Modifier) {
    val selected = viewModel.selectedNotes()
    val single = selected.singleOrNull()
    var dialog by remember { mutableStateOf<ToolbarDialog?>(null) }
    // "New note" mode: the bar shows where to add it (above, under, below)
    var choosingPlacement by remember { mutableStateOf(false) }
    if (single == null) choosingPlacement = false
    BackHandler(enabled = choosingPlacement) { choosingPlacement = false }
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    HorizontalFloatingToolbar(expanded = true, modifier = modifier) {
        AnimatedContent(
            targetState = choosingPlacement,
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            label = "toolbar actions",
        ) { placing ->
            Row {
                if (placing) {
                    IconButton(onClick = { choosingPlacement = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_to_actions))
                    }
                    Placement.entries.forEach { placement ->
                        IconButton(onClick = {
                            choosingPlacement = false
                            viewModel.startNewNoteNearSelection(placement)
                        }) { Icon(placementIcon(placement), stringResource(newNoteTitle(placement))) }
                    }
                } else {
                    IconButton(onClick = { dialog = ToolbarDialog.Scheduled }) { Icon(OrgwhalIcons.Scheduled, stringResource(R.string.schedule)) }
                    IconButton(onClick = { dialog = ToolbarDialog.Deadline }) { Icon(OrgwhalIcons.Deadline, stringResource(R.string.deadline)) }
                    IconButton(onClick = { dialog = ToolbarDialog.State }) { Icon(OrgwhalIcons.State, stringResource(R.string.state)) }
                    IconButton(onClick = viewModel::toggleDoneOfSelection) { Icon(Icons.Default.Check, stringResource(R.string.done)) }
                    if (single != null) {
                        IconButton(onClick = { choosingPlacement = true }) { Icon(Icons.Default.Add, stringResource(R.string.new_note)) }
                    }
                }
            }
        }
    }

    when (dialog) {
        null -> Unit
        ToolbarDialog.Scheduled -> TimestampDialog(
            initial = single?.scheduled,
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; viewModel.setScheduledOfSelection(it) },
        )
        ToolbarDialog.Deadline -> TimestampDialog(
            initial = single?.deadline,
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; viewModel.setDeadlineOfSelection(it) },
        )
        ToolbarDialog.State -> ChoiceDialog(
            title = stringResource(R.string.state),
            options = listOf(null) + file.doc.keywords.all,
            selected = single?.state,
            onDismiss = { dialog = null },
            onSelect = { dialog = null; viewModel.setStateOfSelection(it) },
        )
    }
}

fun placementIcon(placement: Placement) = when (placement) {
    Placement.Above -> OrgwhalIcons.NewAbove
    Placement.Under -> OrgwhalIcons.SubNote
    Placement.Below -> OrgwhalIcons.NewBelow
}

@StringRes
private fun pasteLabel(placement: Placement) = when (placement) {
    Placement.Above -> R.string.paste_above
    Placement.Under -> R.string.paste_under
    Placement.Below -> R.string.paste_below
}
