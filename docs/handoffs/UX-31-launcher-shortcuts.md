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

## Next action

Next Wave B slice: UX-32 (widget, #22). Its gate is an owner decision: Glance (new dependency) or `RemoteViews`.
