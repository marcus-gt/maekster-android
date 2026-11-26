# UX-42 — Recently played albums widget

**Status: COMPLETE** (owner phone check passed, 26 Nov 2026). Wave W, `ux/polish-3w-widgets`, starting at `38f3f72d` (UX-41).

## Acceptance criteria ([issue #67](https://github.com/marcus-gt/maekster-android/issues/67))

- [x] Same albums and order as the Home "Recently played" shelf (`LibraryRepository.recentlyPlayedAlbums`), including hiding without play data.
- [x] Tapping an album plays it through `PlaybackController` (shared UX-40 receiver; tap again pauses).
- [ ] Updates after plays and syncs without per-second churn. Partly verified: the Room regression covers reordering after a
  newer play and `distinctUntilChanged` covers churn; the tile indicator updated live on the emulator. Reordering after a real
  scrobble was not watched on a launcher, so the box on #67 stays open.
- [x] Empty state; Android 12+ only (D20, `library_widgets_enabled`).
- [x] Tests fail without the change; emulator check.
- [x] Owner check on the phone.

## Implementation

UX-40's provider became the shared `widget/AlbumShelfWidget.kt` (renamed with `git mv`). The `AlbumShelf` enum holds each
shelf's provider class, Home section, empty text, header open action and the same `LibraryRepository` query Home uses.
`RecentlyAddedAlbumsWidget` and the new `RecentlyPlayedAlbumsWidget` are thin subclasses. `AlbumShelfWidgetUpdater`
(one Koin single per shelf, named `albumShelfWidget.<Shelf>`) builds the grid, play/pause tile, artwork background and header.
`MainActivity.ACTION_OPEN_RECENTLY_PLAYED_ALBUMS` opens the Home list through `HomeSectionLauncher`. The grid moved to
`widget_album_shelf_grid.xml`, included by `widget_recent_albums.xml` (runtime, and the Recently added preview) and
`widget_recently_played_albums_preview.xml` (picker preview and initial layout with the right header). The header text is set
from code. Same 4×2 default, 12-album bound and 96 px covers as UX-40. Home shows 10 albums and the widget 12, in the same order.
A further album-shelf widget needs only an `AlbumShelf` entry, a subclass, a manifest receiver and a provider info XML.

Hiding without play data: Home hides the shelf when no album has a play count or a played date. The query already requires
`played IS NOT NULL`, so it is empty in exactly that case; the widget then says "Albums you play will show up here."
`LibraryDao.recordPlay` bumps `albums.played` on scrobble, so the Room flow reorders the grid. `distinctUntilChanged` on the
visible ids, titles and covers (plus transport state) skips updates when a play doesn't change the order.

## Bug found and fixed: widget headers reused a stale list

Opening Recently played from its widget, then Recently added from the other widget, showed the "Recently added" title over the
Recently played albums. `AppRoot` navigated with `launchSingleTop`, which kept the top `home/more/{section}` entry and its
`HomeListViewModel` (built from the old request). The launcher now uses `navigateDetail(Routes.HOME_SECTION, "section", …)`:
it pops back to an existing list for the same section, or pushes a new entry with its own ViewModel. There is no unit test for
this path (it lives in the `SubtracksRoot` composable; `focusPops` is covered by `FocusPopsTest`). The bug was seen on the
emulator before the fix, and the owner confirmed the fixed sequence on the Pixel 9.

## Verification

- `RecentlyPlayedAlbumsWidgetTest` (Room-backed) checks four things: the widget mirrors Home's shelf and order, a newer play
  moves an album to the front, the widget stays empty without play data, and the header maps to the Recently played list.
  Two of its three tests failed with the shelf pointed at the Recently added query; all pass with the fix. `AlbumShelfWidgetTest`
  (formerly `RecentlyAddedAlbumsWidgetTest`) still passes.
- Ran ktlintFormat, assembleDebug, the full testDebugUnitTest suite (2,086 tests, 0 failures), ktlintCheck and lintDebug.
  All passed before and after the navigation fix (Studio JBR + wrapper; Nix is absent from the IDE shell). Lint's
  `MergeRootFrame` is suppressed on the included grid, which must stay a `FrameLayout` to carry the weight.
- API 37 emulator: the picker listed "Recently played · 4 × 2" with the right preview. The widget showed 8 covers in Home's order.
  Tapping The Program started its first track (PLAYING), with a Pause tile and the art background; a second tap paused it.
  The header opened Recently played. At 200% font the header and first row stayed intact; the font was restored to 100%.
  Recently added kept rendering after the refactor. The emulator then dropped off adb.
- Pixel 9 (owner, debug APK installed with approval): same albums and order as Home, tap plays and pauses, the header opens
  Recently played, and the Recently added header then shows Recently added. The owner answered "All four passed".
- Not checked: an empty library on a launcher, Android 11 (provider disabled by resource), reordering after a real scrobble.

## Next action

Committed on `ux/polish-3w-widgets`, not pushed. Recommended next slice: UX-43 Radio stations widget (owner picks stack or grid).
