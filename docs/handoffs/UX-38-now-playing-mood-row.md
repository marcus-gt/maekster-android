# UX-38 — Now Playing widget mood row

**Status: COMPLETE.** Recovered on `ux/polish-3w-widgets`, based on `bf9407f0` (Wave B merge PR #70). Implementation and tests were already uncommitted when the session resumed.

## Acceptance criteria ([issue #63](https://github.com/marcus-gt/maekster-android/issues/63))

- [x] Mood row appears below the player from 4×2 upward. The size and renderer tests cover the unchanged 4×1 and 2×1 layouts.
- [x] A mood tap starts radio in the background through `MoodRadioPlayer` and `PlaybackController`, including from a stopped app. Verify that the launcher stays in the foreground and no app activity opens.
- [x] Availability and renderer tests cover hiding the row when AudioMuse is unavailable or the app is offline.
- [x] Three renderer tests failed with mood rendering temporarily disabled. The gradient test failed against placeholder colors. Emulator font scale was checked at 100% and 200% and restored.
- [x] Owner verified radio playback after swiping the app away on the Pixel 9.

## Recovery checkpoint

- The working tree adds mood intents and command dispatch to the existing Now Playing widget, availability and starting-state rendering, font-scale refresh, mood resources, and renderer, updater, command and color tests. Recovery corrected placeholder gradients and formatting.
- The existing row has five fixed moods: Chill, Energetic, Focus, Night and Workout.
- Owner confirmed the five fixed moods and confirmed that tapping one must start playback in the background, leaving the launcher visible.
- The first attempt to run the Nix build, unit tests and lint hit an automatic approval review timeout. The retry timed out in the Android Studio tool after 300 seconds. Nix and Gradle were unavailable on the IDE shell PATH, so verification used the CLAUDE.md fallback (wrapper with Studio JBR). The inherited code had ktlint violations in AppModule, NowPlayingWidget and NowPlayingWidgetRendererTest, which were corrected. WidgetMoodColorsTest failed against five all-black placeholder gradients. The drawables now use the existing Discover color stops. The widget suite passed on rerun. The full run assembled the APK and passed both lint tasks, with 1 failure in 2,077 tests: PlaybackControllerTest.enteringShuffleOrRepeatTrimsTheGeneratedTail timed out. The same test passed alongside the widget suite in a focused rerun.
- Installed the debug APK on the API 37 emulator. The launcher accessibility tree showed all five buttons below the player at 100% and 200% font. At 200% all five accessible descriptions remained and visible labels were hidden. Restored the original 100% scale.
- Tapped Chill on the emulator. The widget dispatched the broadcast, MoodRadioPlayer built a 198-song mix, the media session reached PLAYING, and the launcher remained the resumed activity. A forced cold-process tap, small sizes and offline transitions were not tested on a device.
- Phone question sent: With AudioMuse configured, swipe the app away from Recents, tap a mood on the 4×2 Now Playing widget, and report whether radio starts while the launcher stays visible. The verified debug APK was installed successfully on the owner's connected Pixel 9 via `adb install -r`. The owner confirmed that tapping a radio starts playback after removing the app from Recents.

- Full retry passed `assembleDebug`, `testDebugUnitTest`, `ktlintCheck` and `lintDebug` with the Studio JBR fallback.

## Next action

After restoring mood rendering, `ktlintFormat` passed. A repeat of the full suite hit the same playback timeout. The final full run with `--max-workers=2` passed the debug build, all 2,077 unit tests, ktlint, and Android lint. `git diff --check` passed.

Continue with the next eligible widget slice in a new session.
