# UX-09 — Favorites sorting and playback

**Status: COMPLETE.** [Issue #38](https://github.com/marcus-gt/maekster-android/issues/38),
under [#31](https://github.com/marcus-gt/maekster-android/issues/31). Branch `ux/01-library-layout`,
starting at `4fe69c9c` with both worktrees clean.

Android has Songs, Albums and Artists views, ordered newest favorite first. iOS offers Name,
Recently Favorited and Recently Played, with Play and Shuffle on Songs and detail navigation
for Albums and Artists. Keep Android's existing default and add its three sort choices.
Reuse the paged Songs query and reference queue for the song actions and offline filter.

## Implementation

- Added a shared DataStore Favorites sort, with Recently favorited as the existing Android default.
  Name uses the existing sort-name setting. Date sorts are newest first with missing dates last.
- Favorites Songs reuses the single reference-based Songs query for row taps, Play and Shuffle.
  It always selects favorites, intersects completed downloads offline, and keeps the Favorites
  queue heading. Albums and Artists use the corresponding local library sorts and open detail pages.
- The category chips and playback actions wrap at larger text sizes. An empty Songs result
  has no playback buttons.

## Verification

- Both targeted regressions failed as intended with the actions omitted and the queue forced to
  title order. Restored production code and passed the full tests, including the 200% font check,
  empty state, offline favorite order, shuffle membership and Favorites queue heading.
- Passed format, debug build, 1,946 unit tests, ktlint and Android lint using the wrapper and
  Studio JBR. The API 37 emulator showed the options and playback controls across category
  switches. Restored Recently favorited. Actual playback was exercised with the fake player.
- As with Library Songs, the reference resolves against live Room rows and later scrobbles or sync
  can change the Recently played ordering keys during playback.

## Next action

Start UX-10 from its checklist in [issue #9](https://github.com/marcus-gt/maekster-android/issues/9).
