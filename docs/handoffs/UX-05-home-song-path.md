# UX-05 — Home: offer Song Path as an opt-in section

**Status: COMPLETE.** All three acceptance criteria are checked in
[issue #35](https://github.com/marcus-gt/maekster-android/issues/35), under
[parent #31](https://github.com/marcus-gt/maekster-android/issues/31).
Branch `ux/01-library-layout`, following UX-04 commit `6452c4fc`.

## Implementation

- Home layout settings list Song Path after Queue Builder by default, with visibility off and
  no grid option. Decoding a layout from before Song Path inserts it hidden and preserves
  the existing relative order, visibility and grid choices. DataStore remembers later changes.
- Home and Discover use `rememberSongPathCard` from `ui/discover/SongPathRoute.kt` for the
  draft, picker, search, playback and capability gate. The picker lives above the lazy list,
  preserving the draft when the card scrolls away. Losing availability dismisses the picker.
- The online Home route subscribes to Song Path only when its shelf is enabled. The card requires
  an active server and readable AudioMuse. Offline Home omits it. Discover has its own layout.

## Verification

- Six focused assertions failed when the legacy visibility handling, Home card, editor label and
  capability gate were temporarily disabled. All four changes were restored before the full run.
- Passed `ktlintFormat`, `assembleDebug`, `testDebugUnitTest` (1,940 tests, zero failures),
  `ktlintCheck` and `lintDebug` with the wrapper and Android Studio JBR. Nix was unavailable.
- API 37 emulator with a populated library and working AudioMuse: upgraded over the saved layout,
  confirmed Song Path was off, enabled it, opened the endpoint picker on Home, and confirmed the card
  was still present after a force-stop and relaunch. Offline Home omitted the enabled shelf and it
  reappeared going online. Restored the original online and Song Path visibility settings.
- Unconfigured AudioMuse and missing active server were verified in unit tests. Endpoint search and
  playback regressions passed in the suite. Live verification stopped at opening the picker so it
  would preserve the existing playback queue.

## Next action

For a new session, read the UX-06 checklist in [issue #36](https://github.com/marcus-gt/maekster-android/issues/36)
and trace Android's Updated and iOS's Last used playlist sort.
