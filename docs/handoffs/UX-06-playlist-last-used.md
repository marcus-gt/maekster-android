# UX-06 — Playlist Last used sort

**Status: COMPLETE.** All three acceptance criteria are checked. [Issue #36](https://github.com/marcus-gt/maekster-android/issues/36), under
[parent #31](https://github.com/marcus-gt/maekster-android/issues/31).
Branch `ux/01-library-layout`, starting from UX-05 commit `6e29de47`. Both listed worktrees were clean.

## Behavior trace

- Android **Updated** orders by `playlists.changed`, a server value parsed by `SubsonicXml.playlist`
  and mirrored by sync and playlist edit readback. Server changes (including edits or automatic
  regeneration when the server advances its timestamp) move the sort. Playback alone does not.
  An absent server timestamp is stored as zero.
- iOS `PlaylistListSort.lastUsedDate` prefers the local per-server activity date from
  `PlaylistUsageStore`. Playback starts, additions and successful removal through the membership
  picker record use. Opening a playlist does not. Without local use, ordinary playlists use the
  server's `changed` date. iOS-generated Mood and Wrapped playlists need local activity because
  their server dates were advanced automatically. Unknowns are last, with name and ID tie-breaks.

## Implementation and verification

- Added Last used without changing Updated or the saved default. Schema 63 stores one local
  timestamp per playlist. Explicit playback and successful membership operations record it.
  Descending starts with the newest usage. Both directions place unknown dates last.
- The DAO regression covers downloaded-only paging, source isolation, stable ties, missing dates,
  and generated playlists. It failed with Updated substituted for descending Last used, then passed
  with Last used restored. An initial full run also caught an incorrect direction in the test's
  Updated comparison. The assertion now uses the existing Updated direction.
- Passed format, debug build, all 1,941 unit tests including the migration chain, ktlint and Android
  lint using Studio JBR and the wrapper. Nix was unavailable.
- Upgraded the populated emulator and verified that Library → Playlists → List options shows
  both sorts and that Last used is selectable. Live playback and playlist writes were not exercised.

## Next action

Start UX-07 from its checklist in [issue #37](https://github.com/marcus-gt/maekster-android/issues/37).
