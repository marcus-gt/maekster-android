# S1: Settings > Integrations

**Status: DONE.** Clean start at 950528cf on main, 49 commits ahead and
one behind origin/main.

## Implemented

- Settings lists Integrations between Storage and About. The puzzle-piece row uses the existing
  `settings/{page}` route and back behavior.
- ListenBrainz moved from Playback. AudioMuse, Fresh releases and recommended artist photos moved
  from Application. AudioMuse is shown for the active server and uses the same connection form and
  test action. Both opt-in switches keep their disclosures. Quick Playlists, server scrobbling and
  lyrics stay in their existing groups.
- Existing ViewModels, preferences, dialog routes and networking are reused. Android opens Fresh
  releases in the browser. There was no existing Android release-destination setting to relocate.
- Updated the roadmap, parity matrix and privacy policy. Key files are `SettingsScreen.kt`,
  `strings.xml`, `SettingsScreenTest.kt` and `AppScreenshotTest.kt`.

## Verification

- SettingsScreenTest and both Settings screenshot tests passed. With the Fresh releases switch
  callback temporarily disconnected, its relocated opt-in test failed, then passed after restoration.
- Recorded and visually reviewed `settings.png` and `settings_integrations.png` in
  `app/src/test/screenshots/`. The PNGs are local outputs.
- ktlintFormat, assembleDebug, ktlintCheck and lintDebug passed using Android Studio's JBR and the
  Gradle wrapper (Nix unavailable). The full unit run passed 1,914 of 1,917 tests. The three known
  Home screenshot timeouts passed in isolation.
- Deployed to emulator-5554. Verified Settings > Integrations, all four controls, the AudioMuse form
  and its Test connection action, the ListenBrainz form, and Back. Settings were left as found.
  Live third-party requests were outside the smoke check.

## Next

- Stop after S1. M3.4 Local folders and M3.6 Wrapped remain on hold. M4.1 Predictive back is the
  next numbered implementation candidate.
