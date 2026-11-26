# UX-33 — Predictive Back (#19)

**Status: COMPLETE (26 Nov 2026).** Branch `ux/polish-3b-android-integration`, after `afd9dbb1` (UX-32).
Committed on the wave branch, not pushed. Verified on the API 37 emulator and by the owner on the Pixel 9.

## Acceptance criteria (issue #19, prose)

- [x] Completed gestures match Back navigation: routes, Now Playing, lyrics, queue and sheets (emulator, both nav modes).
- [x] Cancelled gestures preserve navigation and playback (route, Now Playing, queue and sheet; playback stayed PLAYING).
- [x] Verified on gesture and three-button navigation (emulator `emulator-5554`, Pixel_9 AVD, API 37).

## Implementation

- Manifest: `android:enableOnBackInvokedCallback="true"` (was `false`).
- `ui/OverlayBackCallback.kt` (new): an `OnBackPressedCallback` for the overlays. On gesture start it captures the top
  overlay; progress calls `onPreview`, cancel calls `onCancel`, and release (or a three-button Back) calls `onCommit` for
  the captured overlay. `nowPlayingBackPreview()` maps gesture progress to the Now Playing open fraction (down to 0.8).
- `overlayBack()` returns null when no overlay is open (the `Route` case is gone). `AppRoot`'s callback is enabled only
  while an overlay is open, so **route back now belongs to the NavHost**, which has a built-in predictive pop (the page
  shrinks over the previous one). The callback is still re-registered on every navigation so it stays above the NavHost's.
- Previews: Now Playing slides down 20% of its travel. The queue slides down 15% of its height
  (`QUEUE_BACK_PREVIEW`; reset when the queue opens again). A cancel animates back. Lyrics close on release with no
  preview (the pane lives inside Now Playing; a preview there was not worth the plumbing).
- Sheets (`ModalBottomSheet`) and dialogs use their own Material 3 predictive back; no changes. The `BackHandler`s in
  `SubtracksRoot` (add server) and `LibraryScreen` (search) still work, commit on release, no preview.
- Nothing in the callback touches playback.

## Tests and checks

- `OverlayBackTest`: 6 tests (order, NavHost gets Back without an overlay, overlay keeps the route, a gesture previews
  and a cancel keeps Now Playing, release closes the overlay the gesture started on, the preview mapping). With
  `handleOnBackStarted` skipped (the old commit-only behaviour) the two gesture tests fail; restored, all pass. The
  "no overlay" test mirrors the AppRoot enable rule; it does not compose `AppRoot`.
- Full checks in one clean run (`./gradlew -Pkotlin.incremental=false`, Studio JBR): ktlintFormat, assembleDebug,
  testDebugUnitTest (2,063 tests, 0 failures), ktlintCheck and lintDebug: BUILD SUCCESSFUL. Earlier overlapping runs
  (the IDE shell reported time-outs but started them anyway) failed on clobbered caches and are void.
- Emulator, gesture nav (edge swipes via `input motionevent`): playlist route previews Home underneath, a cancel keeps
  it, a release pops to Home. Now Playing slides down mid-gesture, springs back on cancel, playback kept running
  (61.3 s → 66.3 s). Queue: cancel keeps it, release returns to Now Playing. Lyrics: release returns to Now Playing.
  Release on Now Playing over a playlist closes Now Playing and keeps the playlist. Context menu sheet: cancel keeps
  it, release closes it.
- Emulator, three-button nav (`KEYCODE_BACK`): queue over lyrics → lyrics → Now Playing → playlist → Home, one Back
  each; Now Playing over a playlist closed without popping the playlist; playback PLAYING throughout. Gesture nav restored.
- Not checked: font scale (no new text), three-button navigation on the phone, and the NavHost preview on tab roots
  other than Home.

## Owner check on the phone

Debug APK of `158afcd6` installed on the owner's Pixel 9 at 12:04 on 26 Nov 2026. Owner: "It works fine!"
(26 Nov 2026), after the explanation of a cancelled gesture (slide back to the edge before lifting the finger).

Does the swipe-back preview feel right on Now Playing (slides down 20%), the queue and pages? Is it acceptable that
the lyrics pane closes without a preview?

## Next action

Wave B (UX-30 to UX-33) is complete and ready for a PR. The owner pushes and opens it.
