# UX-45: Now Playing widget, choose the row below the player

**Status: Owner approved artwork and authorized the slice commit.** Branch `ux/polish-3w-widgets`, started after `2a4a4fc2` (UX-44). The `UX-45-starter.md`
handoff belongs to issue #45 (UX polish 2) and is unrelated.

Issue: [#71](https://github.com/marcus-gt/maekster-android/issues/71), parent #62.

## Owner decisions (27 Nov 2026)

- The choice is per widget, set from the widget's own settings (long-press, then settings). Today the only
  setting-like behaviour is the mood row on the large Now Playing size.
- Choices: Mood radios (default), Recently added albums, Recently played albums, Favorite songs, Artist radio,
  Nothing.
- Favorite songs is a short list of titles; the other choices are rows of tiles.

## Plan

- `NowPlayingWidgetRow` enum, stored per `appWidgetId` in `UserPreferences` and removed in `onDeleted`.
- A configuration activity: `android:configure` with `widgetFeatures="reconfigurable|configuration_optional"`,
  in `xml-v31` only, so older Android never asks and keeps the mood row (D20).
- Tiles send their taps to the receivers that already exist: the album shelf widgets (play or pause an album),
  `FavoriteSongsWidget` (play favourites from a song) and `RadioStationsWidget` (artist radio with play/pause). No
  new playback code.
- The renderer groups widget ids by their choice and pushes one size map per group. The row goes in the large
  (Moods) layout; without content the player takes the room (the Tall layout).

## What was built

- `widget/NowPlayingWidgetRows.kt`:
  - `NowPlayingWidgetRow` (the choices, default `Moods`), `NowPlayingRowItem` and `NowPlayingRows` (choice per widget
    id and items per chosen row).
  - `nowPlayingRows(...)`: reads only the rows some widget chose. Covers load when a list changes; the play state
    follows playback and the queue label. `withPlayState` marks the held album, song or artist radio.
  - `nowPlayingRowSources(...)`: Home's album shelves (5), favourites in Library order (3; downloads only in offline
    mode) and Discover's station artists (5).
- `NowPlayingWidgetContent.rows`, collected by `NowPlayingWidgetUpdater` (`rows` flow).
- `NowPlayingWidgetRenderer`:
  - On API 31+, groups widget ids by their choice and pushes one size map per group.
  - `views(..., row)` uses the large (Moods) layout only when the row has content; otherwise the Tall layout.
  - `setRow` shows the mood row, five tiles (album covers rounded, artists round, pause/play on the held one) or
    three song lines (the held song in the accent).
- Taps reuse the existing receivers: `AlbumShelfWidget.playAlbumIntent`, `RadioStationsWidget.artistRadioIntent`
  and `FavoriteSongsWidget.songIntent` (the held song toggles).
- `widgetArtistBitmap` moved out of the Radio stations updater so both widgets share it.
- `NowPlayingWidgetConfigureActivity` (Compose radio list, `MaeksterTheme`). It only accepts ids of this app's Now
  Playing widget, and returns RESULT_CANCELED unless a choice is made. It is exported for the launcher's
  `APPWIDGET_CONFIGURE`.
- `xml-v31/now_playing_widget_info.xml` adds `android:configure` and `reconfigurable|configuration_optional`.
  Before API 31 nothing asks, and the widget keeps the mood row.
- `UserPreferences.nowPlayingWidgetRows`, `setNowPlayingWidgetRow` and `removeNowPlayingWidgetRows` (cleared in
  `NowPlayingWidget.onDeleted`).

## Verification

- Tests: `NowPlayingWidgetRowsTest` (9: default, tiles, taps go to the right widget, artist names, the favourites
  list, the player taking the room, play states, only chosen rows read, the settings screen), a preferences test and
  an updater test.
- Mutations: 5 (rows treated as moods, all rows read, artist play state ignored, album tap to the wrong shelf, rows
  dropped by the updater), together causing 6 failures. All restored.
- Full checks: build, all unit tests, ktlint and lint pass. The flaky `PlaybackControllerTest` timing test failed once
  in one run and passed in the next full run.
- Emulator (Pixel_9 AVD, API 37, 27 Nov 2026), settings opened with `am start` for widget id 6:
  - The screen lists the six choices, with Mood radios as the default.
  - Recently added: five covers; a tap played the album and its tile showed pause.
  - Favorite songs: three lines; a tap played that song and its line turned to the accent colour.
  - Artist radio: five round pictures (one fallback star); a tap started the radio and its tile showed pause.
- Not checked: the launcher's own long-press entry into the settings (the owner's phone), Nothing and Recently
  played on a device, offline, and 200% font size.

## Issue #71 checklist

- [ ] Per-widget choice and mood default are covered by tests. Launcher Customize and two widget instances await owner review.
- [ ] Recently added played on the emulator and showed Pause. Recently played remains unverified on a launcher.
- [x] Favorite songs shows a short list and plays from the tapped song on the emulator.
- [x] Artist radio shows tiles and starts the selected radio, which shows Pause on the emulator.
- [x] Nothing gives the player the available room in the renderer test.
- [ ] Small sizes and offline behavior still need review.
- [ ] API 31 resource split is in place. The older launcher path has not been checked on a device.
- [ ] Row-change verification is partial. The previous handoff records five mutations and three emulator flows.
- [ ] Owner approved the artwork on the Pixel 9. Phone verification of widget configuration remains unreported.

Ticked Favorite songs, Artist radio and Nothing in issue #71. The remaining boxes await verification.

## Artwork background follow-up

- Owner asked for roughly 50% less blur across the widgets. Recovered the in-progress change on
  `ux/polish-3w-widgets`: the shared background samples at 48 × 48 instead of 24 × 24 and renders at
  128 × 128 instead of 64 × 64, with the same dark scrim.
- `WidgetArtworkBackgroundTest` measures the softened edge and checks bitmap reuse. Re-running the
  build, unit suite and lint on the recovered working tree. The first run found the renderer test's
  old 16 KiB ceiling. Updated it to enforce the new 64 KiB ceiling for the shared bitmap and kept
  its reuse, fallback and contrast checks.
- With the old 24 px sample and 64 px output, the edge regression failed as expected: the softened
  edge covered 3.1% of the image width. Restored the sharper settings for the final checks.

- Final debug build, all 2,151 unit tests, ktlint, Android lint and `git diff --check` passed.
  Used the Gradle wrapper with Android Studio's JBR because Nix was unavailable in the IDE shell.
- Installed the checked debug APK on the owner's Pixel 9 with `adb install -r`.

## Unblurred artwork follow-up

- Owner's Pixel screenshot showed a pixelated album background. The lock-screen reference had clear lettering under a dark overlay.
- Removed the blur pass. Now Playing, album shelves, Favorites, Radio Stations and Queue Builder draw up to 256 px artwork under the same contrast scrim. List tiles retain their smaller thumbnails.
- The fine-detail regression failed with the old blur and passed with the unblurred renderer. The debug build, full unit suite, ktlint, Android lint and diff check passed.
- Installed the unblurred build on the owner's Pixel 9. The owner said it looks good and asked to commit.

## Lock-screen media badge follow-up

The owner asked to replace the cassette badge on the system Now Playing card in the widget wave PR.
The tracked Media3 small-icon drawable now wraps the existing monochrome maekster mark. Android
SystemUI uses the media notification's small icon for this badge. The debug build, ktlint, Android
lint and diff check passed. Both full unit runs had one timeout in
`PlaybackControllerTest.enteringShuffleOrRepeatTrimsTheGeneratedTail`. The test passed in isolation.
Confirmed the new mark on the emulator's system media card in the notification shade during playback.
The owner requested the non-debug build and the widget PR. Nightly and release builds passed.
Installed nightly 3.1.0 over the debug build on Pixel 9 and verified its package is non-debuggable.
The badge was checked on the emulator. Pixel lock-screen verification remains unreported.
All 2,151 unit tests, ktlint format/check and Android lint passed on the final run with `--max-workers=1`.

## Next action

Owner prepares the wave W PR from `ux/polish-3w-widgets`. Issue #71 still tracks launcher Customize,
Recently played, play/pause, and two independently configured widgets on the Pixel, plus the other unchecked
criteria above. After wave W merges, start wave C from fresh `origin/main`. UX-34 remains on hold.
Ask whether to do UX-35 with existing assets or defer it, and confirm the scope of UX-36 and UX-37.
