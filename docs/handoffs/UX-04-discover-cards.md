# UX-04 — Discover: review and refresh the card design

**Status: COMPLETE.** All three items are checked in [issue #34](https://github.com/marcus-gt/maekster-android/issues/34),
under [parent #31](https://github.com/marcus-gt/maekster-android/issues/31).
Branch `ux/01-library-layout`, following UX-03 commit `bb67c619`. Not pushed. The owner approved the
prototype direction on 18 Nov 2026.

## Direction

Following the iOS Discover screenshots in #31, every card has its own saturated gradient instead of
the shared brown `primaryContainer`. There are no new colour tokens. The gradients come from
`artworkColorsFromSeeds`, whose accent stops are capped in luminance so white text stays at 4.5:1 or
better.

- **Smart Shuffle:** a gradient seeded from the theme `primary` and `accentSecondary`, so it follows
  Material You. White text.
- **Stations:** a square tile tinted from the artist's artwork (`rememberArtworkColors`, falling back
  to the name seed), with a round artist image. The name and the new caption "Radio station" sit
  below the tile.
- **Moods:** each mood has its own pair of hues, with a white icon and the radio badge.
- **Song Path:** a blue-teal gradient, as on iOS. The endpoint panel uses `VividPanel`, a 22% black
  glass layer, because a light glass layer failed contrast. The slider is white, and Play is a white
  button with a dark icon.
- **Fresh releases:** the cover sits on a gradient tile seeded from the artist name. An album icon
  sits underneath the cover, so it shows while a cover loads, when there is none, or when covers are
  off. The grey card is gone. Title, artist and "type · date" sit below.
- **Queue Builder:** unchanged. Its chips are already tinted per genre.

The code is in `ui/discover/DiscoverCardColors.kt` (`VividTileCard`, `discoverCardBrush` and
`discoverCardStops`, `VividPanel`, the mood hues, `songPathColors`, `smartShuffleColors`), used from
`DiscoverScreen.kt`, `SongPathCard.kt` and `FreshReleasesUi.kt`. The click actions, `enabled`
states, test tags, spinners and each card's single-button semantics are kept. Section options and
layout come from the unchanged `DiscoverLayout`.

## Verification

- New `DiscoverCardColorsTest`:
  - White text keeps at least 4.5:1 on every gradient stop, and on the Song Path panel. This covers
    every mood, Song Path, both Smart Shuffle palettes, and a sweep of 216 artwork seeds.
  - The Song Path Play icon is readable on its white button.
  - The moods start from hues at least 15° apart.
  - Caveat: this test guards the new helpers. It cannot run against the old code, which didn't have
    them.
- New `DiscoverCardsScreenshotTest` renders the whole page at 100% and 200% (a render smoke test).
- The existing `DiscoverScreenTest` (21 tests) still passes. It covers the actions, Song Path, the
  mood radios and the layout options.
- Passed `ktlintFormat`, `assembleDebug`, `ktlintCheck` and `lintDebug`, using the wrapper with
  Studio's JBR. The full `testDebugUnitTest` run passed: 1936 tests, 0 failures.
- After the final change, the Fresh releases icon fallback, I reran build, ktlint, lint and
  `ui.discover.*`. All passed, but the full suite was not rerun.
- Pixel 9 emulator on API 37, with real data:
  - Moods, Song Path (real "From" song), Fresh releases (real covers, plus icon fallbacks) and Smart
    Shuffle checked at 100% and 200%. Font scale was restored to 1.0.
  - Stations is not shown in this emulator's Discover layout, so the real-artwork station tiles were
    checked in Robolectric only, with name-seeded placeholders.
- Screenshots are in the gitignored folder `app/src/test/screenshots/ux04/`: `before_*`, `after_*`,
  `device_discover_*` (prototype) and `device_rollout_*` (final).

## Next action

UX-05 ([issue #35](https://github.com/marcus-gt/maekster-android/issues/35)): offer Song Path on Home
as an opt-in section. It can reuse the restyled `SongPathCard`.
