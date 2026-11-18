# UX-03 — Home and Discover: consistent section arrows

**Status: COMPLETE.** All three items are checked in [issue #33](https://github.com/marcus-gt/maekster-android/issues/33),
under [parent #31](https://github.com/marcus-gt/maekster-android/issues/31).
Branch `ux/01-library-layout`, following UX-02 commit `ab930537`. Not pushed.

## Inventory

- Home `HomeSectionHeader` had a "More" text button, now an arrow. It covers every album, artist,
  genre, decade, favorite songs and Your playlists shelf. It also covers the offline downloaded
  albums, artists, playlists and songs.
- The Queue Builder header (Home and Discover) already had the title with an inline arrow and the
  label "Open Queue Builder". It now uses the shared arrow and touch target.
- These sections have no destination and were left unchanged:
  - Home: Pinned, Artist picks.
  - Discover: Fresh releases, Stations, Moods, Smart Shuffle.
- Song Path endpoint rows use an arrow to open a picker, not a section, so they were left unchanged.
  Overflow menus, long-press menus, Reset and Play were not touched.

## Implementation

- New `ui/components/SectionOpen.kt` contains:
  - `SectionOpenArrow`: an auto-mirrored `KeyboardArrowRight`, `onSurfaceVariant`, 24 dp.
  - `Modifier.sectionOpenAction(label, onClick)`: Button role, a click label, and a minimum height of 48 dp.
- In `HomeSectionHeader` the whole header row opens the section. It has a trailing arrow, and its
  click label is `Open <section>` from the new string `section_open`. The "More" button is gone.
  Headers without a destination stay plain but still have a 48 dp row.
- The `QueueBuilderSection` header uses the same arrow and modifier. It was 44 dp tall before.
- Destinations are unchanged: the same `onMore` and `actions.open` callbacks.

## Verification

- New `HomeSectionArrowsTest` checks:
  - "More" is gone.
  - The favorite albums and favorite songs headers have the Button role, the section-specific
    label, a height of at least 48 dp, and route to their sections.
  - Artist picks has no action and only actionable headers get arrows.
  - The arrow comes first in RTL and stays on screen at 200% font.
  - The Queue Builder header has the right label, target size and click.
- `HomeFavoriteAlbumsTest` and `HomeYourPlaylistsTest` now open the section through its header.
- I copied the new test onto HEAD `ab930537` in a temporary worktree, since removed. All 4 tests
  failed there: "More" was present, no header had a click action, and Queue Builder was 44 dp.
- Passed `ktlintFormat`, `assembleDebug`, `ktlintCheck` and `lintDebug`, using the wrapper with
  Studio's JBR. `testDebugUnitTest` passed in full: 1931 tests, 0 failures.
- Pixel 9 emulator on API 37:
  - The accessibility hierarchy shows each section header as one 48 dp full-width button with a
    trailing arrow. Artist picks is plain.
  - Tapping Your favorite songs opened the favorites list.
  - At 200% font both arrows are visible.
  - Font scale was restored to 1.0.
- Not tested: spoken TalkBack output (only the semantics were checked), and RTL on a device
  (Robolectric only).

## Known issues

- The emulator hierarchy flags the existing Queue Builder Play button as NAF (not accessibility
  friendly), even though its icon has a content description. This is outside this slice. Check it
  during the M4.9 accessibility work.

## Next action

UX-04 ([issue #34](https://github.com/marcus-gt/maekster-android/issues/34)): Discover card design.
Review the visual prototype with the user before rolling it out.
