# UX-16 — Always show the back button on hero pages

**Status: COMPLETE.** [#46](https://github.com/marcus-gt/maekster-android/issues/46), parent
[#45](https://github.com/marcus-gt/maekster-android/issues/45). Branch `ux/45-artist-now-playing` (not pushed).

## What changed

- `ui/components/HeroDetailScaffold.kt`: new `HeroBackButton(onBack, barFraction)`. A 48 dp `IconButton`
  with a 40 dp black disc at 40% alpha behind a white arrow. As the top bar fades in, the disc fades
  out and the arrow lerps to the bar's `LocalContentColor`. The arrow itself never fades.
- `HeroDetailScaffold` (album, playlist) and `ui/library/ArtistDetailScreen.kt` use it instead of an
  `IconButton` faded with `alpha = barFraction`.
- No `…` button sits over the hero on these pages (album/playlist have it in the controls row under
  the art, artist in the bio row), so that criterion needed no change.

## Decisions

- The label stays the existing `navigation_back` string, "Back". The issue text says "Navigate back",
  but the criterion is to keep the label, so the string was not changed.
- Follows the iOS idea (first artist screenshot in #45: round dark translucent button, light arrow)
  with a Material `IconButton` and ripple.

## Verification

- New `app/src/test/.../ui/components/HeroBackButtonTest.kt` (4 tests): pixel checks that the button
  area differs from the hero over white, black and the artist's missing-art placeholder before scrolling;
  label, 48 dp target and click. Against the old behaviour (button faded with `barFraction`) all 4 failed
  ("0 of 1600" pixels differ; width 40 dp). All 4 pass with the change.
- `ktlintFormat`, `assembleDebug`, `ktlintCheck`, `lintDebug`, full `testDebugUnitTest`: pass
  (`./gradlew`, Studio JBR).
- Emulator (Pixel 9, debug build over the existing install): album "Waltz for Debby", playlist
  "Jazz tunes" and artist "Bill Evans" show the disc and arrow at the top; scrolled album shows the plain
  arrow in the bar; back works. Checked at 100% and 200% font; font scale restored to 1.0. Queue untouched.

## Not verified

- An artist with no image on the emulator (covered only by the Robolectric placeholder test).
- Light theme (the scrim keeps a white arrow over art, so it should hold; not checked).

## Next action

UX-17 ([#47](https://github.com/marcus-gt/maekster-android/issues/47)): artist photo fades into the page.
It needs owner feedback on a before/after screenshot before rollout.
