# Changelog

All notable changes to Orgwhal (named Narwhal up to 0.16.0). Versions follow [Semantic Versioning](https://semver.org).

## [Unreleased]

## [0.30.0] - 2026-10-19

### Added
- A link to a note not there yet ([[Title]], [[*Title]]) offers to make it, as Emacs does, at
  the end of the link's notebook (Move to… puts it elsewhere); the links to it in that notebook
  become id: links, their text kept, so that they follow the note when it is renamed

### Changed
- Renaming a note can also update the links that lead to it by its title ([[Title]],
  [[*Title]]), as set in Settings, Links to a renamed note; a link's own text is kept, and links
  that led elsewhere (another note with that title, a <<target>>) are left as they are

## [0.29.0] - 2026-10-18

### Added
- A drawer's content shows as the rest of the note: a table in it is a grid whose fields are
  edited in place, its lists and checkboxes work

### Changed
- In a notebook's list, tables no longer scroll sideways (a swipe is the note's): one wider than
  the screen is cut, faded at the right; it scrolls once the note is open

### Fixed
- A swipe on a note's table now reaches the note (its actions): a table scrolls sideways only
  when wider than the screen
- A note's lone logbook shows above the content placeholder, not over it

## [0.28.0] - 2026-10-18

### Added
- Template notes: a note tagged :template: is offered when making a new note (the Template
  chip), which starts as a copy of it (title, state, tags, priority, properties, content; not its
  clocking); as in Emacs' capture templates, %u is written as today's inactive date, %U with the
  time, %t and %T active, and %? goes. The templates show as the note would be made, with a
  search (accents aside); a title already typed stays
- Setting for where template notes are left out (Settings, Template notes): nowhere, the agenda
  (and reminders), or the agenda and the search (the default; t.template still finds them)

## [0.27.0] - 2026-10-18

### Added
- The toolbar over the keyboard also shows while a note's title is edited: emphasis, id: link,
  date, undo and redo (no lists nor indents); its date button writes an inactive date alone, as
  a journal's entries are titled ([2026-10-13 Tue]), and Today writes today's

## [0.26.0] - 2026-10-18

### Added
- Settings, Add the guide notebook, with the guide already in the folder: open it as it is, or
  replace it with this version's guide (its changes lost); a guide from when the app was Narwhal
  is found too

## [0.25.0] - 2026-10-18

### Added
- Every other table row is tinted, so as not to lose one's row; the header has a color of its own
- In a note, the header of a table taller than the screen stays at the top as it is scrolled
  past
- Setting to keep a wide table's first column at the left as it scrolls sideways (Settings,
  Freeze the first column; off by default)

### Fixed
- Wrapped table columns no longer get narrower than their longest word, which was cut in two

## [0.24.0] - 2026-10-18

### Added
- Setting to wrap long table fields onto several lines, the table fitting the screen instead of
  scrolling sideways (Settings, Wrap table fields; on by default); the file keeps one line per
  row, and a field being edited wraps as well
- Typing id: in a table field suggests notes to link to, as in the note's text
- In a long notebook, agenda, search or note, scrolling down brings up a faint button that goes
  straight to the bottom; a tap on the top bar goes back to the top

## [0.23.0] - 2026-10-15

### Changed
- A note's Clock line shows, beside the total, the last time clocked; while the clock runs, it
  shows the time since it started, with the total beside
- The Clock line animates as the clock starts or stops

## [0.22.0] - 2026-10-14

### Added
- A long press on a table field in a note adds a row above or below, a column left or right,
  or deletes its row or column; the formulas' references follow, as in Emacs
- Setting to show notes' content in a fixed-width font (Settings, Fixed-width font), as in Emacs
- While a note's text is edited, its table lines show in a fixed-width font, so that they line up

### Fixed
- A tap on nothing in a note saves the table field being edited, which stayed open
- A tap right after editing a table field no longer opens the note's text, which could write the
  old value back

## [0.21.0] - 2026-10-14

### Added
- In a table field edited in place, Enter goes on to the field below, as in Emacs; on the last
  row it adds a new row (the formulas' row references follow)
- In a note, the :LOGBOOK: drawer folds like the others: a tap shows each time clocked, the
  Clock line still giving the total

### Fixed
- Tapping beside a table while editing a field no longer loses the value typed

## [0.20.1] - 2026-10-14

### Fixed
- A fling ending at the top of a notebook no longer leaves the list drifting slowly there, the
  large top bar half open, for over a second
- While selecting, scrolling to the top no longer opens the folded top bar again, which left an
  empty space under the selection bar
- When a note moves, its highlight no longer jumps to the neighbouring note for a moment

## [0.20.0] - 2026-10-14

### Added
- Table fields edit in place in a note: a tap turns a field into a text field right in the
  table, its value selected (a keyboard of digits for a number); Enter, another field or closing
  the keyboard saves it, and the table realigns as Emacs writes it. Fields a formula computes do
  not open. A setting turns it off (Settings, Edit table cells on tap)

### Changed
- Selecting notes folds the large top bar away instead of leaving its empty space under the
  selection bar, and opens it again afterwards if it was open

### Fixed
- Pulling the notebook down right after moving notes no longer sends the whole list sliding off
  the screen
- The first move of a note slides it from where it was, instead of bringing it in from the end
  of the list

## [0.19.0] - 2026-10-12

### Added
- While a clock runs, a silent notification shows the note, where it is and the time clocked,
  with a Stop button, and Done for a to-do note; it follows the files, so a clock started or
  stopped elsewhere (Emacs, another device) shows or takes it away, and it comes back after a restart
- Clock in button on reminder notifications, in place of Tomorrow, which a setting brings back
  (Settings, Third button): the note's clock starts, stopping another one, and its notification
  follows
- Marking a note done stops its running clock, as Emacs does by default (also for a repeating
  task, which stays to-do)

### Changed
- New icon: the Org headline asterisk, its upper-right arm grown into a narwhal tusk with three spiral
  grooves; the themed and notification icons have the same drawing

## [0.18.1] - 2026-10-11

### Changed
- Icon: on a teal blue, with the themed icon's bolder cuts (a wider gap around the tusk, its
  stripe cut through)

## [0.18.0] - 2026-10-11

### Added
- Setting to recalculate tables with formulas as soon as a note shows them, in the notebook
  and on the note screen: never (the default, with the Recalculate button), on screen only
  (the file keeps its values until "Save values"), or recalculated and saved; tables read with
  remote() are recalculated first, so a total read from another note is up to date

## [0.17.1] - 2026-10-11

### Changed
- The icon's drawing sits a little higher, so it looks centered

## [0.17.0] - 2026-10-11

### Changed
- Narwhal is now Orgwhal, with the application ID `io.github.yighy.orgwhal`: it installs as a
  new app, beside Narwhal (pick the notes folder again, then uninstall Narwhal), signed with a
  new key of its own

## [0.16.0] - 2026-10-11

### Changed
- Icon: the narwhal's head, now flat underneath, over a dot and a bar in white (a list's line)
  instead of the wave; its eyes are cut out in the background's green
- Reminder notifications show the note's parent next to the time; opened, they show the whole
  path (notebook › parents) and the start of the note's content as plain text: lists with
  bullets, checkboxes as ☐ / ☑, a table's rows with their fields side by side

## [0.15.0] - 2026-10-11

### Changed
- The `id:` suggestions also find notes by their notebook's and parents' titles: "Meals" offers
  the note Meals, then the notes under it, and "meals rice" finds Rice under Meals

## [0.14.0] - 2026-10-11

### Changed
- New icon: the narwhal's head over a wave, its striped tusk up; the themed and notification icons
  follow it
- In the tree view, a note opens out of its box and closes back into it, as from a notebook's
  list

## [0.13.1] - 2026-10-11

### Fixed
- Leaving a new note that has content, tags or dates but no title now warns first: a new note is
  saved once it has a title, so what was written would be lost; "Add a title" goes back to it

## [0.13.0] - 2026-10-10

### Added
- Archive a note (its ⋮ menu, or the selection's): it goes with its sub-notes to the end of the
  notebook's archive file (gtd.org_archive, next to it), with the properties Emacs adds
  (ARCHIVE_TIME, ARCHIVE_FILE, ARCHIVE_OLPATH, ARCHIVE_CATEGORY, ARCHIVE_TODO); the message
  offers to undo it
- Links in properties (:FATHER: [[id:…][Paul]]) show as links on the note screen and open with a
  tap; a tap beside them edits the value

### Changed
- Folded notes stay folded when the app is closed; a notebook shown for the first time folds as
  Org would show it, after its #+STARTUP (overview, show2levels…) and its notes' VISIBILITY
- The guide notebook also explains the tree view and archiving, with a note to archive

### Fixed
- A copied note, pasted, no longer keeps the ID of the note it was copied from (with two notes
  of one ID, id: links could lead to either); cut notes keep it where they are first pasted

## [0.12.0] - 2026-10-10

### Added
- Tree view of a notebook (its ⋮ menu): the notes drawn as boxes from left to right, moved and
  zoomed with the fingers; a tap opens a note, the round button at its side folds its sub-notes
  and shows how many are hidden, and the view moves to show the ones unfolded; a button shows the
  whole tree
- In the tree view, a long press selects notes, with the same bars as in the notebook: notes
  can be moved up, down, a level up or down, or to another notebook, and slide to their new places
- In the tree view, a double tap between the boxes zooms in around it, then back to the whole
  tree; the tree is shown again as it was left (zoom, place, folded notes), also after the app
  is closed

## [0.11.0] - 2026-10-10

### Added
- A guide notebook (in English) explaining Narwhal with examples to try: offered once the
  folder is first chosen, and in Settings, About; its example dates are set around the day it
  is added, without times, so it rings no reminders

### Changed
- Once a shared note is moved elsewhere, the Inbox notebook is deleted if nothing is left in it
  (not even a title line); the next share makes it again
- Libraries updated: Material 3 1.5.0-beta01 (out of alpha), Activity 1.13.0, Core 1.19.1,
  coroutines 1.11.0

### Fixed
- A list item whose text goes on to the next line no longer gets a fold chevron; only items with
  sub-items fold
- Folding and unfolding (all notes, or one with many sub-notes) moves the notes below once,
  instead of a second time a moment later
- The buttons behind a swiped note stay in the part of it on screen, also on a note with a long
  content and while scrolling
- An address written as verbatim or code (=file:=, ~https://…~) shows as such, not as a broken link

## [0.10.0] - 2026-10-10

### Added
- Statistics cookies, [/] and [%], kept up to date as in Emacs: in a list item they count its
  sub-items' checkboxes, in a title the note's checkboxes or else its sub-notes done; a parent
  checkbox shows [-] or [X] with its sub-items; cookies show as small pills
- Table formulas (#+TBLFM): a "Recalculate" button under the table computes the common ones as
  Emacs does (column and field formulas, references and ranges, + - * / ^, vsum, vmean, vmin,
  vmax, vcount, formats such as %.2f) and realigns the table; others are left as they are
- Columns of numbers align right in tables
- Tables and blocks named with #+NAME show their name; links without a scheme lead within the
  notes as in Org: [[name]] (a <<target>>, a named element, or a title), [[*Title]], [[#custom-id]]
- remote(name, ref) in table formulas reads a field or range of another table, named with #+NAME
  or by the ID of its note

### Changed
- Text shared from other apps always goes to the Inbox notebook; on the new note, a tap on the
  notebook at the top moves it elsewhere (the setting is gone)

### Fixed
- A note moved to another notebook from its screen stays shown at its new place

## [0.9.0] - 2026-10-09

### Added
- Text shared from other apps (a page, a selection) becomes a note in an "Inbox" notebook, or
  in the notebook chosen in the settings
- Deleting notes no longer asks first: the message offers to undo it; delete comes first in
  the swipe actions
- Move notes to any notebook, under the note of your choice, from the selection or from a note's
  menu
- Agenda: filters by notebook and by tag; swipe left to put a time off by a day, a week, or to
  a date; notes of the same time are sorted by priority
- Tables, source and example blocks, quotes and centered text show as such; drawers fold to
  their name and open with a tap (the :LOGBOOK: shows as the clock's time)
- List items with sub-items fold and unfold with a chevron at the end of their line
- "Now" button in the date dialog of the keyboard toolbar: inserts the current time, inactive
- Clocking: start and stop a clock on a note, with the time spent; it is written to the note's
  :LOGBOOK: as in Emacs, and a running clock shows in the lists

## [0.8.0] - 2026-10-09

### Added
- Property names and values already in use are suggested while typing a property
- Swiping a note left also offers to delete it

### Changed
- The keyboard toolbar and the `id:` and `file:` suggestions go as soon as the keyboard starts
  to close, instead of following it down

## [0.7.1] - 2026-10-08

### Changed
- Notebook initials and icons sit in a plain circle, of the same color for all notebooks

### Fixed
- In a done note's title, links are grayed out like the rest

## [0.7.0] - 2026-10-08

### Added
- Setting to strike through done notes or not

### Fixed
- In a done note's title, links and underlined text are struck through too

## [0.6.0] - 2026-10-07

### Added
- French translation, and a "Use English language" setting that ignores the phone's language
  (on Android 13+, the app's language can also be chosen in the system settings)
- Moved notes slide to their new place in the outline, and the list scrolls to keep them in view

## [0.5.3] - 2026-10-07

### Changed
- New icon: a round narwhal head with its tusk, without the sea; the themed icon has the same drawing
- Swiping a note left shows plain icons, as swiping right does

### Fixed
- A save can no longer leave the end of a longer previous version at the end of the file; each
  file is read back after it is written

## [0.5.2] - 2026-10-07

### Changed
- No property is hidden by default: the ID shows on the note screen unless added to the hidden
  properties

## [0.5.1] - 2026-10-07

### Changed
- Themed icon (Android 13+): the narwhal is centered

## [0.5.0] - 2026-10-07

### Added
- Hidden properties setting: the properties not shown on the note screen (the ID by default),
  in place of the switch for the ID

### Changed
- New notes no longer get an ID by default: linking to a note gives it one
- New icon: the narwhal rises from the sea with its tail, its tusk on its head

### Fixed
- The list no longer jumps when a note or a notebook gets selected
- The keyboard toolbar goes away with the keyboard
- On a new note, going from the title to the content keeps the keyboard up

## [0.4.0] - 2026-10-06

### Added
- Formatting buttons in the keyboard toolbar: bold, italic, underline, strike-through, code
- The toolbar's date button opens the full date dialog (time, end time, repeater), and changes
  the date at the cursor when there is one
- Snooze length setting for the reminder notification's button
- About section in the settings: version, source code, license, credits
- Space between notes setting for the notebook outline: comfortable, compact or dense
- Setting to show the ID property on the note screen

### Changed
- Undo and redo stay in view at the end of the keyboard toolbar
- The drawer shows the app name without the icon
- The ID property is hidden on the note screen by default
- New notes get an ID by default, so they can be linked to right away
- Notebooks show each note's content under its title by default
- Folding and unfolding notes takes half the time, the notes coming one after the other

## [0.3.0] - 2026-10-06

### Added
- New name and icon: the app is now Narwhal (application ID `io.github.yighy.narwhal`), with a
  narwhal launcher icon, themed icon and notification icon, also shown on the welcome screen and
  in the drawer
- Multiple selection in notebooks, as in Orgzly: move, cut, copy, paste, delete, set state,
  scheduled and deadline times; select and delete or rename notebooks
- Note screen: edit tags, state and priority in place; notebook icons; share a note;
  a menu to add notes above, under or below the current one
- Orgzly search syntax, plus `ad.N` (agenda view), `e.` (events), `cr.` (creation date) and the
  `o.e` / `o.cr` sort orders; saved searches with their own screen (select, delete, reorder)
- End times for scheduled and deadline times; repeater symbols (`+`, `++`, `.+`)
- Org links of any kind (`http`, `mailto`, `tel`, `geo`, `file:`…) open as in Orgzly;
  `file:` suggestions from the notes folder; backlinks to the current note
- When a note is renamed, links showing its old title can be updated (ask, always or never)
- Checkboxes that can be ticked; Org markup (bold, italic, code…) rendered
- Typing help: list and checkbox continuation, bracket pairs, and a toolbar over the keyboard
  (list, checkbox, indent, link, date, undo, redo)
- Live reload: files changed by another app are picked up while Narwhal is open
- Reminders: snooze (in 1 hour, tomorrow) from the notification, optional alarm clock alarms
- Option to add a `CREATED` property to new notes
- Theme setting (system, light, dark) and dynamic color
- Signed release builds

### Changed
- Shared element transitions between screens; a swipe cuts a transition short, and each screen
  keeps its scroll position
- Titles, content and the notebook preface save without buttons; back saves the note
- New notes open in the full note screen instead of a dialog
- Joda-Time removed, for a smaller app

## [0.2.0] - 2026-10-04

### Added
- Agenda: overdue tasks, today and the next 6 days, with repeating tasks
- Reminders at scheduled and deadline times, with a Done action, kept across reboots
- Redesigned interface with Material 3 Expressive

## [0.1.0] - 2026-10-04

### Added
- First prototype: notebooks from a folder of `.org` files, outline, note editing, search, tags,
  and `id:` links to other notes

[Unreleased]: https://github.com/yighy/orgwhal/compare/v0.30.0...HEAD
[0.30.0]: https://github.com/yighy/orgwhal/compare/v0.29.0...v0.30.0
[0.29.0]: https://github.com/yighy/orgwhal/compare/v0.28.0...v0.29.0
[0.28.0]: https://github.com/yighy/orgwhal/compare/v0.27.0...v0.28.0
[0.27.0]: https://github.com/yighy/orgwhal/compare/v0.26.0...v0.27.0
[0.26.0]: https://github.com/yighy/orgwhal/compare/v0.25.0...v0.26.0
[0.25.0]: https://github.com/yighy/orgwhal/compare/v0.24.0...v0.25.0
[0.24.0]: https://github.com/yighy/orgwhal/compare/v0.23.0...v0.24.0
[0.23.0]: https://github.com/yighy/orgwhal/compare/v0.22.0...v0.23.0
[0.22.0]: https://github.com/yighy/orgwhal/compare/v0.21.0...v0.22.0
[0.21.0]: https://github.com/yighy/orgwhal/compare/v0.20.1...v0.21.0
[0.20.1]: https://github.com/yighy/orgwhal/compare/v0.20.0...v0.20.1
[0.20.0]: https://github.com/yighy/orgwhal/compare/v0.19.0...v0.20.0
[0.19.0]: https://github.com/yighy/orgwhal/compare/v0.18.1...v0.19.0
[0.18.1]: https://github.com/yighy/orgwhal/compare/v0.18.0...v0.18.1
[0.18.0]: https://github.com/yighy/orgwhal/compare/v0.17.1...v0.18.0
[0.17.1]: https://github.com/yighy/orgwhal/compare/v0.17.0...v0.17.1
[0.17.0]: https://github.com/yighy/orgwhal/compare/v0.16.0...v0.17.0
[0.16.0]: https://github.com/yighy/orgwhal/compare/v0.15.0...v0.16.0
[0.15.0]: https://github.com/yighy/orgwhal/compare/v0.14.0...v0.15.0
[0.14.0]: https://github.com/yighy/orgwhal/compare/v0.13.1...v0.14.0
[0.13.1]: https://github.com/yighy/orgwhal/compare/v0.13.0...v0.13.1
[0.13.0]: https://github.com/yighy/orgwhal/compare/v0.12.0...v0.13.0
[0.12.0]: https://github.com/yighy/orgwhal/compare/v0.11.0...v0.12.0
[0.11.0]: https://github.com/yighy/orgwhal/compare/v0.10.0...v0.11.0
[0.10.0]: https://github.com/yighy/orgwhal/compare/v0.9.0...v0.10.0
[0.9.0]: https://github.com/yighy/orgwhal/compare/v0.8.0...v0.9.0
[0.8.0]: https://github.com/yighy/orgwhal/compare/v0.7.1...v0.8.0
[0.7.1]: https://github.com/yighy/orgwhal/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/yighy/orgwhal/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/yighy/orgwhal/compare/v0.5.3...v0.6.0
[0.5.3]: https://github.com/yighy/orgwhal/compare/v0.5.2...v0.5.3
[0.5.2]: https://github.com/yighy/orgwhal/compare/v0.5.1...v0.5.2
[0.5.1]: https://github.com/yighy/orgwhal/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/yighy/orgwhal/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/yighy/orgwhal/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/yighy/orgwhal/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/yighy/orgwhal/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/yighy/orgwhal/releases/tag/v0.1.0
