# UX-01 — Library labels and album layout

**Status: COMPLETE.** All four UX-01 items are checked in [issue #10](https://github.com/marcus-gt/maekster-android/issues/10), under [parent #31](https://github.com/marcus-gt/maekster-android/issues/31).
Issue #10 stays open for its earlier artwork, caption and alphabet-rail polish notes.

Branch `ux/01-library-layout` is based on `eae64463`. The prior session left the UX workflow and roadmap
setup in `CLAUDE.md` and `docs/ANDROID_ROADMAP.md`. Those two working-tree edits are separate from the
UX-01 implementation commit.

## Implementation

- Albums labels the existing `AlbumSort.Name` option **Title**. `LibraryPaging.kt` orders it by the
  configured album-title key. Saved values, sort direction and tie ordering use the existing code.
- Artists labels its existing **Albums** sort **Album count**.
- The Albums toolbar and search bar offer **Show albums as a list** or **Show albums as a grid**.
  DataStore stores the choice independently of the existing list query. Grid is the default.
- `AlbumsContent` renders the same paged results and letter rail in either mode. The list reuses
  `AlbumListRow` with title, artist, artwork, the download indicator and the existing album actions.
  The library's query still owns search, sort, favorites, downloaded-only and offline filtering.

## Verification

- The Compose regression failed against the original implementation at the missing layout action,
  then passed with grid → list → grid → list and an active query. Tests also cover row activation,
  the two sort labels, legacy title ordering and layout preference readback with the original sort.
- Passed `:app:ktlintFormat`, `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:ktlintCheck`,
  `:app:lintDebug` and `:app:recordRoborazziDebug`. This Studio environment has no Nix executable.
  Runs used the committed wrapper with Studio's bundled JBR per `CLAUDE.md`.
- Reviewed the Albums grid, list and options sheet, and the Artists options sheet at 100% and 200%
  in the local Roborazzi captures under `app/src/test/screenshots/`.
- On API 37 with a populated library, switched to list view, force-stopped the app, then reopened
  Albums in the saved list view. Checked both switch labels in the accessibility hierarchy,
  the enlarged album rows, both renamed sort labels and reachable filters at 200%. Restored the
  emulator to 100% font and its original grid choice.
- API 35 was covered by Robolectric. A separate API 35 emulator and spoken TalkBack output
  were outside the live smoke test.

## Artists grid follow-up

**Status: COMPLETE** on `ux/01-library-layout`, following the Albums commit `10523888`. Artists now has a
three-column grid with artwork, name, album count and download status. The switch is available in
the toolbar and during search. The choice persists separately from Albums, with list as the default.
Both presentations use the same paged results, letter rail and artist actions.

- The Compose regression failed before the switch was added and passed afterward. It checks list →
  grid → list during search, side-by-side tiles and opening the selected artist. The DataStore test
  confirms independent settings across readers.
- Passed format, debug build, full unit tests, ktlint, Android lint, and Roborazzi recording with
  the wrapper and Studio JBR. Reviewed grid and list captures at 100% and 200%.
- On API 37 with real artwork, switched to grid, opened an artist, and reopened Artists in grid
  mode. The emulator is showing the grid. Live verification covered reopening the screen, with
  persistence across readers covered by the DataStore test.

## Next action

When starting the next slice, read the UX-02 checklist in [issue #32](https://github.com/marcus-gt/maekster-android/issues/32)
for Home grid density and the favorite songs carousel.
