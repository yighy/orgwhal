# Orgwhal

<img src="docs/icon.svg" alt="" width="96">

An Android app for [Org mode](https://orgmode.org) notes, inspired by
[Orgzly](https://github.com/orgzly/orgzly-android).

<p>
  <img src="docs/screenshots/notebook.png" alt="A notebook" width="200">
  <img src="docs/screenshots/note.png" alt="A note with its sub-notes, formatting, checkboxes, a table with formulas and blocks" width="200">
  <img src="docs/screenshots/edit.png" alt="Editing a note, with the toolbar over the keyboard" width="200">
  <img src="docs/screenshots/agenda.png" alt="The agenda" width="200">
</p>

## Features

- **Your `.org` files, nothing else.** Orgwhal works on a folder you pick. Changes made elsewhere
  show up within seconds, and what Orgwhal doesn't change stays as it was, so the same files open
  in Emacs.
- **Outline and tree.** Notebooks fold as in Org (`#+STARTUP`, `VISIBILITY`) and stay as you
  leave them; a tree view draws them as boxes to move around. Select notes to move, copy, archive
  or refile them.
- **Links.** Type `id:` or `[[` to pick a note, a target or a named table; links follow a renamed
  note, a link to a missing note offers to make it, and each note lists those linking back. A
  `#+transclude:` line shows another note's content, a table or a paragraph in place, as
  org-transclusion does.
- **Agenda and reminders.** The week ahead with repeating tasks, and exact reminders you can mark
  done, snooze or start clocking from.
- **Org at hand.** Tables with formulas, checkboxes and `[/]` cookies, blocks and drawers,
  template notes (`%u`, `%U`… as in capture templates), clocking (with a notification while the clock runs), archiving, search with Orgzly's syntax,
  sharing from other apps.

## Download

Signed APKs are attached to each [release](https://github.com/yighy/orgwhal/releases); see
[CHANGELOG.md](CHANGELOG.md) for what changed.

## Build

Requires JDK 17+ and the Android SDK (platform 37).

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Release builds are signed when a `keystore.properties` file at the project root (kept out of
git) gives `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.

## License

GPLv3. Includes a copy of [org-java](https://github.com/orgzly/org-java) (GPLv3), see
[org-java/README.md](org-java/README.md).
