# UX-27 — Lyrics: blur, reading mode and inline line as on iOS

**Status: COMPLETE (reduced scope by owner decision).** [#57](https://github.com/marcus-gt/maekster-android/issues/57),
parent #45. Branch `ux/45-artist-now-playing`, based on `3f33dce3`.

## Owner decision (22 Nov 2026)

After UX-26 made timed lyrics appear, the owner checked the device: the current-line preview in the player
and the pane (current line bright, others dimmed) are enough. **No blur and no separate reading mode.** The
only change wanted: the preview line in the player was cut off with "…" ("♪ And admit that the waters
around you have gr…", The Times They Are A-Changin'); it should shrink to fit as on iOS.

## Implementation

- `CurrentLyricLine` (`ui/playback/LyricsPane.kt`): measures the line with a `TextMeasurer` and scales the
  font down just enough to fit, to at least 70% (iOS `CurrentLyricLineView`: `lineLimit(1)`,
  `minimumScaleFactor(0.7)`, tail truncation). A line still too long at 70% is ellipsized. The box keeps its
  fixed height, so the controls never move. New internal `onTextLayout` parameter for tests.
- `TextAutoSize.StepBased` was tried first and rejected: with `TextOverflow.Ellipsis` it counts an
  ellipsized line as fitting (stays at full size, still cut off), and it snaps short lines to its step grid
  (16sp became 15.7sp).

## Tests

- `CurrentLyricLineTest` (native graphics, 300 dp width): the owner's Dylan line shrinks (between 70% and
  100%) and is not ellipsized; a short line keeps 16sp. With the shrink disabled the first test fails
  (ellipsized at 16sp). Default Robolectric graphics measure text far too narrow, so the test needs
  `@GraphicsMode(NATIVE)`; the semantics `GetTextLayoutResult` ignores auto-sizing, hence `onTextLayout`.
- Existing `NowPlayingLyricsTest` already covers: preview hidden without synced lyrics, tap opens the pane.
- Full run: see Verification below.

## Verification

- `:app:ktlintFormat`, `:app:assembleDebug`, `:app:testDebugUnitTest` (2023 tests, 1 failure: the known
  `PlaybackControllerTest.enteringShuffleOrRepeatTrimsTheGeneratedTail` flake, which passed 2/2 alone; no
  playback code changed), `:app:ktlintCheck`, `:app:lintDebug`, `git diff --check` pass with Studio's JBR.
- Emulator (Pixel 9, 22 Nov 2026): installed; on the owner's paused "The Times They Are A-Changin'" at 0:31
  the short line "♪ For the times, they are a-changin' ♪" shows at full size, centred. The long line from
  the owner's screenshot (around 0:09) was not checked on the device, to avoid seeking the owner's player;
  it is covered by the unit test. 200% font and TalkBack not re-checked (labels unchanged).

## Not done (dropped by the owner)

Blur of non-current lines (`Modifier.blur`), the reading state that removes it, and their tests. Dimming to
40% and the Follow button stay as they are.

## Next action

UX polish 2 is complete apart from owner review. Next: whatever the owner assigns (the roadmap has no
further UX-45 slice).
