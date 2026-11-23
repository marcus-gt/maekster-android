# UX-31 — Resume and Smart Shuffle launcher shortcuts (#20)

**Status: COMPLETE (23 Nov 2026).** Branch `ux/polish-3b-android-integration`, after `4b2272ec` (UX-30).
Committed on the wave branch, not pushed. The launcher popup itself still needs a phone check (see below).

## Acceptance criteria (issue #20 has prose criteria, no checkboxes)

- [x] Resume restores the correct queue and position without duplicating it. Unit test (process death → new
  controller → `resume()` plays the saved song at the saved position, with the same queue size and cursor).
  Emulator: a cold-start Resume played the restored item and opened Now Playing.
- [x] Smart Shuffle starts a bounded queue (the existing 50-song `SmartShuffle` mix) or reports why it is
  unavailable: "needs downloaded songs while offline" or "needs songs in your library". Emulator: a warm start
  played a mix titled "Smart Shuffle". The failure messages are unit-tested only.
- [x] Absent server: neither shortcut does anything; the welcome screen shows (unit test). Empty queue: "Nothing to
  resume yet" (unit + controller test). Offline: Smart Shuffle uses downloads only (existing `SmartShufflePlayerTest`).
- [x] Both use the existing entry points: `PlaybackController.resume()` (new, restore-then-play) and
  `SmartShufflePlayer.play()`.

## Implementation

- `PlaybackController.resume()`: `connect()`, waits for `ready` (restore done), returns false with no current item,
  does nothing if already playing or retrying the network, otherwise `togglePlayPause()`. Never touches the queue.
- `LauncherShortcuts.kt`: `LauncherShortcut` enum (ids, `io.github.marcus_gt.maekster.action.*` actions, labels,
  icons), `publishLauncherShortcuts` (dynamic shortcuts via `ShortcutManagerCompat` from androidx.core, already a
  dependency; static XML would need a per-variant `targetPackage`), and `LauncherShortcutRunner` (Koin single).
- `MainActivity`: publishes on the io scope at each launch (labels follow the language) and runs a shortcut from
  `onCreate` (only without saved state, not when `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) or `onNewIntent`, on the
  playback scope. It reports shortcut use to the launcher.
- Icons: `ic_shortcut_resume` and `ic_shortcut_smart_shuffle`, Material Rounded glyphs (Apache-2.0) in the dark
  accent `#FF8C4B` on an ink `#0E0E0E` circle. No dependency added.
- Decision (mine, worth an owner check): a successful shortcut opens Now Playing; iOS intents run in the background,
  but an Android launcher shortcut always opens the app, so showing what started seemed best.

## Tests and checks

- New: `LauncherShortcutRunnerTest` (6) and 3 `PlaybackControllerTest.resume*` tests. They failed under
  mutations: a no-op `resume()` returning true, a naive toggle `resume()`, and a runner without the server check
  or with a generic message. They passed again with the real code restored.
- Full run (wrapper, Android Studio JBR, `-Pkotlin.incremental=false`): ktlintFormat, assembleDebug,
  testDebugUnitTest (2,035 of 2,035), ktlintCheck and lintDebug all passed.
- Emulator (API 37, `emulator-5554`): `dumpsys shortcut` lists both dynamic shortcuts. The intents were sent with
  `am start -f 0x10008000` (launcher flags). Synthetic gestures could not open the launcher's app drawer, so the
  long-press popup was **not** seen on the emulator. Cold Resume played the restored track. Warm Smart Shuffle
  replaced the queue with a Smart Shuffle mix. Rotating twice afterwards did not rerun the shortcut (same track,
  position advanced).
- Not checked: font scale (no new in-app UI besides snackbar text), and the offline or empty messages on a device.

## Owner check on the phone

Long-press the mækster icon: are both shortcuts there with readable icons? Do Resume after a reboot and Smart
Shuffle in airplane mode with and without downloads behave as described? Should a successful shortcut open Now Playing?

## Follow-up: swipe-away stops playback (D8 changed, 23 Nov 2026) — COMMITTED, phone check pending

The owner tested on the Pixel (debug APK installed 17:37) and asked that swiping the app away from recents
stop playback, as Spotify does ("I want that for mækster as well"). Changes:

- `PlaybackService.onTaskRemoved` always calls `pauseAllPlayersAndStopSelf()`, then releases the player and
  session. Without the release, the app's own `MediaController` kept the service bound and a paused media
  notification stayed (seen on the emulator). Removed `keepPlayingOnTaskRemoved` and its `TaskRemovedTest`.
- Found while doing this, a UX-31 bug: `ready` stays true after the service stops, so `resume()` reported
  "nothing to resume" if the process survived. `resume()` now waits (10 s at most) for a private `connected`
  flag that turns false on disconnect.
- A pause from outside the app (notification, headset, the swipe) now saves the position, so Resume continues
  where it stopped instead of up to 15 s earlier.
- Tests: `resumeAfterTheServiceStoppedReconnectsAndPlaysTheSameQueue` and
  `aPauseFromOutsideTheAppSavesThePositionForTheNextResume`. Both fail when `resume()` waits for `ready` and the
  save is removed; both pass with the fix. `FakePlayerConnection.disconnect()` simulates the service stopping.
- Docs: D8 in the roadmap, C11 in `FEATURE_PARITY.md`, the swipe check in `RELEASE_READINESS.md`.
- Emulator (API 37): with `pauseAllPlayersAndStopSelf()` alone, a playing swipe paused but left the service and a
  paused notification. With the release: the log reads "task removed: pausing and stopping", and afterwards there
  is no PlaybackService record and no app notification. The Resume shortcut then reconnected and continued about
  where the swipe stopped (4:21 → 4:24 after 3 s). Recents swipe needed a fast flick:
  `input touchscreen swipe 540 1400 540 100 60`.
- Full checks passed after the release change: ktlintFormat, assembleDebug, testDebugUnitTest (2,033 of 2,033;
  +2 new, -4 removed with `TaskRemovedTest`), ktlintCheck, lintDebug.
- Installed on the owner's Pixel at 18:22 (debug APK). Swipe-away on the phone is **not verified yet**: waiting
  for the owner to confirm that music and the notification stop.

## Next action

Next Wave B slice: UX-32 (widget, #22). Its gate is an owner decision: Glance (new dependency) or `RemoteViews`.
