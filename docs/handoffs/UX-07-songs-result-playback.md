# UX-07 — Play and shuffle the Songs result

**Status: COMPLETE.** All four acceptance criteria are checked. [Issue #37](https://github.com/marcus-gt/maekster-android/issues/37),
under [parent #31](https://github.com/marcus-gt/maekster-android/issues/31). Branch
`ux/01-library-layout`, starting from the clean UX-06 commit `f9e66461`. The detached M1/M2
worktree was also clean.

## Scope

Android already uses `SongListSpec` to share the Room filter and sort between paging and
reference-based queue playback. Row activation can start at any result index and the existing
shuffle FAB uses that reference. The iOS reference places labeled Play and Shuffle controls above
the list, and Play starts at index zero. Additional sort options are already tracked in #7 (UX-08).

## Implementation

- Library Songs shows labeled Play and Shuffle above nonempty results, including during search.
  A single source/spec value drives the Pager and its callbacks. A changed query gets a fresh
  paging collector. Row clicks still use their original ordinal and shuffle behavior.
- Play explicitly clears shuffle and starts at ordinal zero. Shuffle uses the existing queue path.
  Playback checks the source and whether the library result still exists before replacing the queue.
- Added regressions for changing results, multi-page order and shuffle membership, and offline
  search plus favorites plus downloads.

## Verification

- The Play visibility, in-order playback, and offline queue membership assertions failed when Play
  and the corresponding queue options were temporarily disabled. The restored implementation
  passed. A multipage controller test plays the last match beyond the first page, checks that the
  Media3 window stays bounded, and verifies that the shuffled queue contains exactly the filtered
  IDs. The offline test covers the intersection of search, Favorites and completed downloads.
- Passed `ktlintFormat`, `assembleDebug`, `testDebugUnitTest` (1,943 tests, zero failures),
  `ktlintCheck` and `lintDebug` with the wrapper and Studio JBR. The Songs letter-rail test now
  uses a replaying Paging fixture after its one-shot fixture could remain loading under Robolectric.
  An earlier run had a timeout in the unchanged Favorites UI test, which passed on the full retry.
- API 37 emulator with a populated library: Play and Shuffle were visible on Songs, during search
  and with the Favorites filter. Both disappeared for a search with no matches and returned after
  clearing search. Cleared the temporary Favorites filter. Playback and the offline queue were
  verified in tests, leaving the emulator's existing queue intact.

## Next action

Start UX-08 from the Recently played and Released checklist in
[issue #7](https://github.com/marcus-gt/maekster-android/issues/7).
