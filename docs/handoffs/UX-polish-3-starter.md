# UX polish 3 — starter handoff

**Status: IN PROGRESS.** Planned on 23 Nov 2026. Wave A merged in PR #61. UX-28 and UX-29 were not needed. Wave B is complete and awaits its PR. The slice list, waves, branches and
statuses are in the [roadmap](../ANDROID_ROADMAP.md#post-m4--ux-polish-3-daily-feel-android-integration-and-visuals).
The parent issue is [#60](https://github.com/marcus-gt/maekster-android/issues/60); tick its checklist as slices
complete. Its sub-issues are #58, #18, #20, #22, #19, #8, #4 and #5. #10 stays a sub-issue of #31 but is listed in #60.

## Workflow

- **Branch:** each wave has one branch, listed in the roadmap. Wave A is `ux/polish-3a-daily-feel`, cut from
  `origin/main` at `9de43f44` (the PR #59 merge). Commit each finished slice on the wave branch. Never push;
  the owner pushes and opens one PR per wave.
- **Starting a session:** read `AGENTS.md`, `CLAUDE.md`, the roadmap section, this file and the handoff of the
  slice in progress. Run `git status`, `git diff`, `git log --oneline -5` and `git worktree list`. If you are on
  a branch other than the current wave branch and it has uncommitted changes, stop and report. Don't switch.
- **Which slice:** first any slice whose status is In progress (resume it, even if another agent started it).
  Otherwise take the first Not started slice in table order whose gate is met. Skip Blocked slices unless the
  owner has answered their question (the answer goes in the handoff).
- **Slice handoff:** create `docs/handoffs/UX-<number>-<topic>.md` at the first checkpoint and set the roadmap
  status to In progress in the same edit. Keep the slice's acceptance criteria in the handoff as a checklist,
  marked done / partial / not started / unverified. Update it after each meaningful step (a test written, the
  implementation compiling, the checks passing, emulator verification) so a new agent can resume from the files alone.
- **Commits:** one commit per finished slice, after the checks pass, containing the code, tests, handoff and
  roadmap row (status Complete). Message style: `maekster: <what changed, user-visible>`. Don't make work-in-progress
  commits; uncommitted work in the tree is the checkpoint, and the handoff describes it.
- **GitHub:** the slice's issue holds its acceptance criteria. Tick a checkbox only once that criterion is verified
  (`gh issue edit <n> --repo marcus-gt/maekster-android --body-file <file>`). Always use `--repo marcus-gt/maekster-android`.
  Don't close issues; the wave PR closes them.
- **Owner decisions:** ask. Record the question and the answer in the handoff, and in the roadmap decisions table
  when it's a product decision. If the owner is unavailable, set the slice to Blocked with the exact question, and
  stop, or take the next unblocked slice only if the prompt allows it.
- **End of a wave:** when every slice in the wave is Complete (or explicitly deferred by the owner), say the wave is
  ready for a PR and stop. After the owner merges it, the next agent marks the wave's slices Merged, cuts the next
  wave branch from a fresh `origin/main` and records its base commit here.

## Recovering interrupted work

A token limit can stop an agent mid-slice, without a final handoff. Trust the files over the handoff:
compare `git diff` and untracked files with the handoff checklist, run the build and tests to see what actually
works, then update the handoff before continuing. Never discard, reset, stash or rewrite uncommitted changes.
If the existing changes look wrong or ambiguous, explain what you found and ask.

## Findings from planning (verify before relying on them)

- **UX-28 scrolling: not needed.** The owner's lag report came from a debuggable build (no R8, no baseline profile,
  debuggable ART). The nightly (`./gradlew :app:assembleNightly`: release settings, debug key, same app ID as debug,
  installs over it and keeps data) scrolls smoothly on the phone (owner, 23 Nov 2026). Judge performance only on
  nightly or release builds, and give the owner the nightly APK for phone testing. An app baseline profile
  (`profileinstaller`, the baselineprofile plugin and a benchmark module) stays optional and needs owner approval.
- **UX-29 play what's shown:** `PlaybackController.playGenre` (`playback/PlaybackController.kt`), `playGenreList` in
  `ui/home/HomeViewModel.kt` and `HomeListViewModel.playGenreAlbums`. The Songs result playback from UX-07 is the
  pattern: Play queues the visible list in its current sort and filter, Shuffle shuffles that same set.
  Audit the other Play/Shuffle buttons (genres, styles, favorites, artist, Home lists) and list each in the handoff.
- **UX-30 output switcher:** no mediarouter dependency is needed. Use the framework
  `android.media.MediaRouter2.getInstance(context).showSystemOutputSwitcher()` on API 34+ and hide the button below that.
- **UX-31 shortcuts:** the manifest has no shortcuts yet. Route both shortcuts through `PlaybackController`. Find out
  what Smart Shuffle means in the existing code and iOS before inventing a definition.
- **UX-32 widget:** the project has no Glance. Ask the owner: Glance (Apache-2.0, new dependency) or an
  `AppWidgetProvider` with `RemoteViews` (no dependency). Bound artwork bitmaps and update only on player changes.
- **UX-33 Predictive Back:** `AndroidManifest.xml` sets `android:enableOnBackInvokedCallback="false"`. `BackHandler` is used
  in `ui/AppRoot.kt` and `ui/library/LibraryScreen.kt`. navigation-compose is 2.10.1. The Now Playing overlay needs
  `PredictiveBackHandler` with progress, and cancelled gestures must leave playback untouched.
- **UX-34 is on hold** (owner, 26 Nov 2026): skip it in wave C until the owner lifts the hold. UX-35 uses its
  assets, so ask the owner whether to do UX-35 without it or hold it too.
- **UX-34 and UX-35 identity:** brand assets come from the iOS `Branding/` folder (read-only reference, check licences;
  fonts are often OFL). There is no `res/font` yet. D4 (system font) changes for the Home title only: record it.
  Rendering PNGs needs a tool. Check what's installed and ask before installing anything.
- **UX-36 and UX-37:** issue #10's leftovers (list view, text below albums, alphabet scroll) and #5 are vague or
  optional. Ask the owner what is still wanted after phone use before starting.

## Verification (every slice)

`./gradlew :app:ktlintFormat :app:assembleDebug :app:testDebugUnitTest :app:ktlintCheck :app:lintDebug` with
`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. Show that the new tests fail without
the change. Check UI changes on the emulator at 100% and 200% font, and restore the font scale afterwards. When a slice
needs the physical phone (performance, widget, output switcher, shortcuts), ask the owner to test it and record
what was and wasn't verified on a device. The phone is GrapheneOS without Play Services.

## Wave W: home-screen widgets (added 26 Nov 2026)

After phone testing UX-32 the owner asked for more widgets. Umbrella [#62](https://github.com/marcus-gt/maekster-android/issues/62),
slices UX-38 to UX-44 (#63 to #69), branch `ux/polish-3w-widgets` after wave B merges; wave C follows wave W.
Shared rules are in #62: `RemoteViews` (D19), Android 12+ only (D20), no sideways scrolling (a platform limit:
only vertical lists, grids and swipeable stacks), offline-first, updates only on data changes. UX-40 builds the
shared list plumbing; record it in its handoff for UX-41 to UX-44. UX-42 generalised the album grid into
`widget/AlbumShelfWidget.kt` (one `AlbumShelf` entry per Home album shelf). Open owner questions sit in each issue.

## Next action

Wave B branch `ux/polish-3b-android-integration` was cut from `origin/main` at `c31d5892` (PR #61).
[UX-30](UX-30-output-switcher.md), [UX-31](UX-31-launcher-shortcuts.md) and [UX-32](UX-32-now-playing-widget.md) are
complete (playback fixes found during UX-32 are in `d0071de5`). [UX-33](UX-33-predictive-back.md) is complete too, so
wave B is ready for a PR (the owner pushes and opens it). After it merges: mark UX-30 to UX-33 Merged and cut
`ux/polish-3w-widgets` from a fresh `origin/main` (wave W comes before wave C).
