# UX-30 — System media output switcher (#18)

**Status: COMPLETE (23 Nov 2026).** Branch `ux/polish-3b-android-integration`, based on `c31d5892` (Wave A merge, PR #61).
Committed on the wave branch, not pushed.

## Acceptance criteria

- [x] Now Playing opens the Android system switcher on a supported physical Pixel. The owner used the local debug APK
  on their GrapheneOS Pixel 9 and connected AirPods. On the API 37 emulator, the system dialog (`SystemUIDialog`) also
  opened at 100% and 200% font scale.
- [x] Playback continues through route selection on that Pixel. The owner confirmed the same track kept playing
  through AirPods without pressing Play again.
- [x] Older Android versions have no dead output action. The route supplies the callback on API 34+ only. A Compose
  test checks that the button is omitted when no callback is supplied. No older physical device was tested.

## Implementation

- `NowPlayingScreen`: an accessible top-app-bar action, shown only when `onOpenOutputSwitcher` is non-null. The route
  calls `MediaRouter2.getInstance(context).showSystemOutputSwitcher()` on API 34+. The system and Media3 handle
  routing and playback. No dependencies were added.
- Route indicator (owner request): `NowPlayingAudioOutput.kt` reads the media-audio route
  (`AudioManager.getAudioDevicesForAttributes`). It refreshes when devices are added or removed and when the window
  regains focus (the system dialog can switch between devices that are already connected). Bluetooth/LE/hearing-aid
  routes show `BluetoothAudio`, wired/USB routes show `Headphones`, and the built-in speaker shows `Speaker`. An
  external route also uses the theme accent and adds the device name to the label (`change_audio_output_with_device`).
- The "NOW PLAYING" header line now ellipsizes. The action narrows the title slot, so this keeps longer translations
  readable at large font sizes.

## Tests and checks

- `NowPlayingHeaderTest`: covers click dispatch, the device-name label and omission when unavailable. The click test
  failed (missing button) before the implementation. The label test failed before the indicator was wired up.
- Final run (wrapper with Android Studio's JBR; Nix is not available in the IDE): ktlintFormat, assembleDebug,
  testDebugUnitTest, ktlintCheck and lintDebug all passed. Tests: 2,026 of 2,026 passed, with 0 failures. Kotlin
  incremental compilation was disabled (`-Pkotlin.incremental=false`) to avoid the stale-cache error.
- Earlier full-suite failures: two earlier runs each had one different `PlaybackControllerTest` failure
  (`enteringShuffleOrRepeatTrimsTheGeneratedTail`, then `offlineUpNextPlaysAndIsConsumed`), and both passed when
  retried on their own. This slice does not touch playback code. Investigation on 23 Nov 2026:
  - The slice's full suite then passed 2,026 of 2,026.
  - A baseline worktree at `c31d5892` passed the full suite once and `PlaybackControllerTest` alone five times in a row.
  - Conclusion: an intermittent, load-dependent flake that was not reproduced on the baseline or the slice. It is
    not caused by UX-30. Treat it like the known paged-test flakes: rerun before investigating.
- Emulator (API 37): the speaker action is visible and does not overlap the title at 200% font, and tapping it opens
  the system dialog. The font scale was restored to 1.0.
- Physical device: the owner's Pixel 9 (GrapheneOS) showed the Bluetooth icon with AirPods, and playback continued.
  Switching back to the speaker icon was not reported (unverified on a device).

## Next action

The slice is done. The next Wave B slice in table order is UX-31 (launcher shortcuts, #20).
