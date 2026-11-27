# UX-39 — Now Playing widget artwork background

**Status: COMPLETE.** Implemented on `ux/polish-3w-widgets` after UX-38 (`4057888b`).

## Acceptance criteria ([issue #64](https://github.com/marcus-gt/maekster-android/issues/64))

- [x] Render a softened and darkened copy of the current cover. Reuse it while the cover stays the same.
- [x] Keep bitmap memory bounded across all four widget layouts.
- [x] Preserve legibility over light and dark covers. Clear the image and use the dark surface without artwork.
- [x] Run the regression before and after the fix, and check the widget on the emulator.
- [x] Owner approved the final build on the Pixel 9.

## Implementation and verification

- `NowPlayingWidgetRenderer` reuses one 64×64 ARGB (16 KiB) background across the four layouts. It uses a filtered 24×24 cover sample and a 68% black scrim. Artist text is brighter over artwork. On API 24–30 a 5 dp inset keeps the artwork inside the rounded corners. API 31+ uses the full widget outline.
- Before renderer wiring, the regression failed because the background stayed hidden. Native graphics assertions cover softening, brightness, the memory bound, bitmap reuse and 4.5:1 artist contrast. The test also caught a stale drawable when artwork disappeared. Explicit resource clearing fixed it.
- The API 37 emulator showed the tall widget with legible text and transport controls at 100% and 200% font scale. Restored 100%. The regression covers all four layouts and missing artwork.
- The owner first verified artwork changes and legibility, then asked twice for less blur and darkness. They approved the final adjustment on the Pixel 9, saying “Looks good!” The APK on the phone matched the final local SHA-256 (`57d7f6b5…`).
- Formatting, debug build, Android lint, ktlint, all 2,078 unit tests and `git diff --check` passed for the final adjustment. The earlier run hit the known intermittent playback timeout and passed on retry. Used the repository's Studio JBR fallback because Nix was unavailable on the IDE shell PATH.

## Next action

UX-40 is the next widget slice. Start it in a separate session.
