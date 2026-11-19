# UX-12 — Home and Discover recommendation audit

**Status: COMPLETE** (18 Nov 2026). [Issue #40](https://github.com/marcus-gt/maekster-android/issues/40) under [#31](https://github.com/marcus-gt/maekster-android/issues/31) (item Home 5). Branch `ux/01-library-layout`, started at `fbb00ecc` with a clean tree.

## What was done

- Compared Artist picks, Rediscover and In heavy rotation on iOS (`../maekster-ios-reference` at `25a7d7fa`) and Android, covering inputs, ranking, windows, limits, rotation and offline behaviour. The findings are in [`docs/RECOMMENDATION_AUDIT.md`](../RECOMMENDATION_AUDIT.md). No app code changed.
- Home and Discover: none of the three features is on Discover in either app. Queue Builder and Song Path are shared by both screens and behave the same. Stations differ slightly between the apps (10 versus 12 artists, and their sources); this is recorded without a follow-up.
- Created follow-ups as sub-issues of #31 and added them to the roadmap as UX-13 to UX-15:
  - [#41](https://github.com/marcus-gt/maekster-android/issues/41): Rediscover cutoff unit bug.
  - [#42](https://github.com/marcus-gt/maekster-android/issues/42): heavy rotation and Rediscover parity. Needs an owner decision.
  - [#43](https://github.com/marcus-gt/maekster-android/issues/43): Artist picks parity.

## Key findings

- **Bug:** `rediscoverCutoff()` is in milliseconds, while `albums.played` is in epoch seconds, so the 60-day window matches every played album. Confirmed on the emulator (Pixel 9, real library): the More list ends with albums played "Last week". The tests use abstract cutoffs and miss it.
- **In heavy rotation:** iOS excludes recently played and starred albums and shows 12. Android shows the top 10 by play count, with no exclusions.
- **Rediscover:** on iOS it means frequent albums that aren't recent, starred or in heavy rotation. On Android it means not played for 60 days (once fixed). This is a product decision for the owner.
- **Artist picks:** iOS ranks by play count (6/4/rest), with adaptive dormancy and a shuffle on the local day. Android uses starred, the oldest dormant (which never rotate) and never-played artists, keyed on the UTC day.

## Verification

- This is a source audit of both repositories. The emulator was used only to confirm the Rediscover bug. Only docs changed. The required checks still pass: `assembleDebug`, `testDebugUnitTest`, `ktlintCheck` and `lintDebug` (Gradle exit 0). The #40 checklist is ticked, and a comment links the follow-ups.

## Unverified

- The iOS behaviour was read from source and not run.
- The offline behaviour of the cached feed on iOS was inferred from `HomeFeedViewModel.load`.

## Next

UX-13 (#41), which is a small, test-first fix.
