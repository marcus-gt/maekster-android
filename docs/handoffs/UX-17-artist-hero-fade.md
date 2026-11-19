# UX-17 — Artist photo fades into the page

**Status: COMPLETE.** [#47](https://github.com/marcus-gt/maekster-android/issues/47), parent
[#45](https://github.com/marcus-gt/maekster-android/issues/45). Branch `ux/45-artist-now-playing` (not pushed).

## iOS reference

`ArtistDetailView.artistHero` → `ImmersiveCoverHero` (500 pt, content over the photo) →
`PlaylistThemedBackground`: the photo, then a 16 pt blurred copy masked in from ~32% to 85% height, and a
gradient to the body colour from 30% to 80%. Name centred, then round Favorite / Play / Shuffle / Info buttons.

## Owner decision (19 Nov 2026)

Before/after screenshots were shown, along with two options: A, fade only, keeping the current Android
layout; B, an iOS-like centred name with a row of round buttons. **Answer: A.** The layout stays as it is
(name bottom-left, hanging Shuffle pill, heart and `…` beside the bio); B is not wanted.

## What changed

- `ui/library/ArtistDetailScreen.kt`: the hero photo and the name scrim sit in one box with
  `fadeIntoPage()`, an offscreen `DstIn` mask (opaque to 45%, 55% alpha at 72.5%, transparent at the bottom).
  The page's `HeroGradient` shows through, so there is no edge and no colour to match. The box carries the
  tag `ARTIST_HERO_ART_TAG`. Nothing else moved; the name, actions, bio, scroll estimate and top bar are untouched.
- No blur layer as on iOS (`Modifier.blur` is API 31+ and the fade alone removes the edge).

## Verification

- New `ArtistHeroFadeTest` (2 tests): it compares pixels 2 dp inside and 2 dp below the hero's bottom at the
  left margin, with tinted artwork and with no artwork (placeholder). Without the mask both fail (jumps of
  0.08 and 0.29, threshold 0.04); with it both pass.
- `ktlintFormat`, `assembleDebug`, `ktlintCheck`, `lintDebug` and the full `testDebugUnitTest` pass (264 classes,
  including the existing `ArtistDetailScreenTest` bio and title tests). `./gradlew` with Studio's JBR.
- Pixel 9 emulator screenshots (gitignored), in `app/src/test/screenshots/ux17/`: `before-bill-evans{,-scrolled}`,
  then `after-a-` for Bill Evans (normal and scrolled), John Coltrane (dark), Ellen Allien (light), Daniel Avery
  (colourful), Various Artists (placeholder star) and Chet Baker at 200% font. The name and actions are legible in
  all of them. The collapsing top bar still fades in when scrolled. Font scale restored to 1.0; queue untouched.

## Not verified

- Scroll restoration after process death was not exercised on the emulator; it relies on the unchanged
  `rememberRestorableLazyGridState` and the existing tests.
- Light app theme (the artist page always uses the dark artwork theme).

## Next action

UX-18 ([#48](https://github.com/marcus-gt/maekster-android/issues/48)): the Latest release card.
