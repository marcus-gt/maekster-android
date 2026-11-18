# UX-10 — Genre and style browsing

**Status: COMPLETE.** All three UX-10 items are checked in [issue #9](https://github.com/marcus-gt/maekster-android/issues/9) under [#31](https://github.com/marcus-gt/maekster-android/issues/31). Branch `ux/01-library-layout`, starting from `b6bc5b8a` with both worktrees clean.

## Implementation

- Library → Genres groups the active server's synced song tags through the existing Discogs taxonomy. Room pages the main genre, style, and final album lists. Groups count distinct albums, including albums whose tracks carry several matching tags. Genre search matches style names as well as the main genre.
- A main genre opens **All** followed by the styles represented in the library. Genres without styles show only **All**. All and each style open the matching albums. Album detail genre chips continue to open their exact-tag collections.
- iOS groups mirrored album genre tags. Android mirrors all song genre tags and derives an album's membership from its songs, consistent with Android's existing collection data. Offline, an album is available when any track has a completed download.
- Play uses the displayed album order and disc/track order. Shuffle uses the same bounded reference queue and collection membership. In offline mode the queue only contains completed downloads. The reference stores the selected tag spellings and resolves against the current mirrored album and track rows.

## Verification

- Verified the iOS drill-down and terminology against the local iOS reference source and the parent issue text. The GitHub screenshot attachments did not load.

- Deliberately inverted album membership and misrouted the style tap. The Room and Compose regressions failed at those assertions, then passed after both changes were restored. Coverage includes distinct albums, source isolation, downloaded queue membership, album/track order, large-font interactions, and genres without styles.
- Passed `:app:ktlintFormat`, `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:ktlintCheck`, and `:app:lintDebug` with the committed wrapper and Android Studio JBR. Nix was unavailable in Studio.
- On API 37, opened Jazz → Modal, used system Back, and opened All Jazz. Play started a track from the first listed album. Shuffle started a different Jazz track. Left the emulator paused with the Jazz queue. Offline membership was tested against Room data, with no live offline playback or physical-device check.

## Next action

Start UX-11 from the rotating genre album grid checklist in [issue #39](https://github.com/marcus-gt/maekster-android/issues/39).
