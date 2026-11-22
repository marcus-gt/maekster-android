# UX polish 2 (#45) — starter handoff

**Status: IN PROGRESS.** UX-16 to UX-25 done. The slices UX-16 to UX-27 are sub-issues #46–#57 of
[parent #45](https://github.com/marcus-gt/maekster-android/issues/45), listed in the
[roadmap](../ANDROID_ROADMAP.md#post-m4--ux-polish-2-artist-album-and-now-playing).

## Workflow

- Branch `ux/45-artist-now-playing`, cut from `origin/main` at `b7592881` (the PR #44 merge) with no
  upstream. Commit each finished slice there. Do not push; the owner opens one PR at the end.
- One slice per session. Create `docs/handoffs/UX-<number>-<topic>.md` at the first checkpoint and set
  the roadmap row to "In progress" and later "Complete". Tick sub-issue checkboxes only once verified
  (`gh issue edit <n> --repo marcus-gt/maekster-android --body-file …`).
- The iOS screenshots are attached to #45. Download them with
  `curl -sL -H "Authorization: token $(gh auth token)" -o shot.png <url>`, then view them.
- Owner decisions: ask, record the answer in the handoff (and in the roadmap decisions where relevant), then continue.

## Findings from planning (verify before relying on them)

- **UX-16:** `ui/components/HeroDetailScaffold.kt` (album, playlist) and `ui/library/ArtistDetailScreen.kt`
  both draw the back `IconButton` with `alpha = barFraction`, so it is invisible at the top.
- **UX-17:** the artist hero is `ArtistHeroArt` plus a name scrim and `HeroGradient` in `ArtistDetailScreen.kt`.
  iOS reference: `Maekster/Views/Browse/ArtistDetailView.swift` (`artistHero`).
- **UX-18:** iOS `latestRelease` is `max(by: year)` over the artist's own albums, and the card is hidden offline.
- **UX-19:** iOS `ArtistDetailViewModel.loadTopSongs` calls `getTopSongs(artist: name, count: 25)` online only.
  Android has no `getTopSongs` yet. Follow the similar-artists cache pattern
  (`data/recommendations/SimilarArtistsCache.kt`, `db/SimilarArtistsCacheDao.kt`). The Home favorite
  songs carousel (UX-02) lives in `ui/home/`; extract it for reuse.
- **UX-20:** iOS matching is in `Maekster/Models/Domain/ArtistBestOf.swift` (`matches`, most recently starred first).
- **UX-21:** `ui/library/SimilarArtistsSection.kt` and `SimilarArtistsViewModel.kt`; the button is
  `R.string.similar_artists_refresh`.
- **UX-23:** the artist menu label is `start_station` in `ui/components/ItemContextMenu.kt`, the Discover
  caption is `discover_station_caption` in `ui/discover/DiscoverScreen.kt`, and the queue label is
  `QueueCollection.InstantMix`. iOS `ArtistStation` seeds an Instant Mix from the artist.
- **UX-24:** `NowPlayingScreen.kt` top bar uses `now_playing_header` ("Now playing: %1$s") with the
  `QueueKind` label, above the source title.
- **UX-25:** the title and artist block sits beside `FavoriteHeart` in `NowPlayingScreen.kt`; there is no album line.
- **UX-26:** `LyricsRepository` already asks LRCLIB for timed lyrics when the server's are plain, but only
  when `LyricsPreferences.allowLrclib` is on (default `false`). iOS has no such setting. This is the
  likely cause, but it is unconfirmed.
- **UX-27:** `ui/playback/LyricsPane.kt` already follows and has a Follow button; other lines use 40% alpha.
  iOS reference: `DesignSystem/Components/Lyrics/LyricsView.swift` and `LyricsLineView.swift`.

## Verification (every slice)

Run `:app:ktlintFormat`, `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:ktlintCheck` and
`:app:lintDebug` with `./gradlew` and Studio's JBR as `JAVA_HOME` (no Nix here). Show that the new tests
fail without the change. Check on the emulator at 100% and 200% font where the UI changes, and restore the
font scale afterwards. Don't replace the owner's queue on the emulator unless the slice needs it.

## Next action

UX-16 to UX-24 are complete (see their handoffs; the artist mix is "Artist radio", D18; Now Playing's
header is static with a type line, UX-24; album line and text hierarchy, UX-25). UX-26 is complete: LRCLIB
is on by default (D10) and retries without the album after a miss. UX-27 is complete with reduced scope
(no blur or reading mode, by owner decision; the player's lyric line shrinks to fit). UX polish 2 is done.
