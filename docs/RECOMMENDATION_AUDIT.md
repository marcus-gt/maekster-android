# Home and Discover recommendation audit (UX-12)

Audited on 18 Nov 2026 for [#40](https://github.com/marcus-gt/maekster-android/issues/40) (item Home 5 in [#31](https://github.com/marcus-gt/maekster-android/issues/31)).
iOS reference: `../maekster-ios-reference` at `25a7d7fa`. Android: `ux/01-library-layout` at `fbb00ecc`.

## Where the features appear

| Feature | iOS | Android |
|---|---|---|
| In heavy rotation | Home only | Home only (`HomeShelf.MostPlayedAlbums`) |
| Rediscover | Home only. Discover's Smart Shuffle card is labelled "Rediscover Your Library" | Home only |
| Artist picks | Home only, online only (`if isOnline { ArtistShelf() }`) | Home only |

None of the three is on Discover in either app, so Home and Discover can't disagree about them. The features that do appear on both screens are Queue Builder and Song Path. On Android each is one shared route backed by the same repository, and the saved Queue Builder picks are observed by both placements (M3.C), so they behave the same on both screens. Discover-only features with a related data model:

- **Smart Shuffle:** both apps blend favourites, recent taste and random rediscovery songs. iOS fetches from the server; Android draws from Room (D9). Equivalent.
- **Stations:** iOS shows 10 stations, from starred artists, then the artists of favourite songs, then recent, frequent and favourite albums, deduplicated. Android shows 12 credited artists, starred first, then by most recent play. The ordering and limit differ slightly. This is minor and gets no follow-up.

## In heavy rotation

| | iOS (`HomeFeedViewModel.heavyRotation`) | Android (`LibraryDao.mostPlayedAlbums`) |
|---|---|---|
| Input | Server `getAlbumList2 type=frequent`, 40 albums | Room `albums.playCount` (synced, plus local play bumps) |
| Ranking | Play count, as the server returns it | `playCount DESC`, then name and ID |
| Exclusions | Albums among the last 20 recently played and the first 40 starred albums | None. It overlaps Recently played and Your favorite albums |
| Limit | 12 | 10 (`HOME_ROW_LIMIT`) |
| Rotation | None | None |
| Offline | The last cached feed | Live from Room. Hidden in explicit offline mode, where Home shows downloads only |

## Rediscover

| | iOS (`HomePersonalization.rediscovery`) | Android (`LibraryDao.rediscoverAlbums`) |
|---|---|---|
| Input | The same 40 frequent albums | Room `albums.played` |
| Selection | Frequent albums not in recently played (20), favourite albums (40) or heavy rotation. In practice, roughly the 13th to 40th most played albums | Albums last played before the cutoff (now minus 60 days), oldest play first |
| Time window | None. "Recent" means the last 20 played albums | 60 days, **but it is broken** (see below) |
| Limit | 12 | 10, with More paging through all of them |
| Rotation | None | None |
| Offline | The last cached feed | As for heavy rotation |

**Bug, confirmed:** `rediscoverCutoff()` returns milliseconds (`System.currentTimeMillis() - REDISCOVER_WINDOW_MS`), while `albums.played` holds epoch **seconds** (`IsoDate.parse` returns `toEpochSecond()`, and `Scrobbler` records `event.time / 1000`). So `played < cutoff` matches every played album. On the emulator, the Rediscover list included albums played "Last week". The DAO tests pass abstract values (`cutoff = 500`), so they can't catch the unit mismatch. The bug affects both the Home row (`HomeViewModel`) and the More list (`rediscoverAlbumsPage`). Because the order is oldest first, the Home row still looks plausible on large libraries.

## Artist picks

| | iOS (`ArtistShelfPicker`) | Android (`LibraryRepository.artistPicks`, `MixDao.home*Artists`) |
|---|---|---|
| Input | Server frequent albums (200), recent albums (100) and all artists. Plays are derived from album counters | Room `artists` (`starred`, `played`, `credited`) |
| "Favourites" | Top 18 artists by play count with more than 2 plays; 6 picked | **Starred** artists; 4 picked at random daily |
| "Dormant" | Up to 20 of the most played artists not played since the cutoff and not among the favourites; 4 picked | Unstarred artists last played before now minus 90 days, **oldest first, so it never rotates**; 4 |
| Dormancy window | Adaptive: 90 days, or half the play history, but at least 14 days | Fixed at 90 days |
| "New to you" | Artists with at most 2 plays, shuffled; they fill the rest | Never-played artists (`played IS NULL`), 4 picked at random daily |
| Exclusions | "Various Artists", "[unknown artist]" and artists with no albums | `credited = 1` only |
| Size and order | 12. Small libraries fill from the remaining pools, then the result is shuffled | Up to 12, interleaved favourite, dormant, new, with no fill-up |
| Day key | Local calendar day, per server | UTC epoch day (`now / 86_400_000`), not local midnight |
| Offline | Hidden | Shown from Room. This is an intentional offline-first choice |

The M3.C handoff already recorded this as partial parity.

## Intentional Android choices (keep)

- Everything is read from the Room mirror rather than fetched when Home loads (D9, offline-first).
- Explicit offline mode replaces Home with downloads-only shelves. iOS instead shows its last cached feed.
- Artist picks still work offline, because the data is local.

## Owner decision (18 Nov 2026)

Rediscover and Artist picks follow iOS. Rediscover was implemented in UX-14, and the iOS rule replaced the broken time window. Artist picks are UX-15. In heavy rotation was not part of the decision.

## Follow-ups

Sub-issues of #31, in this order:

1. UX-13 [#41](https://github.com/marcus-gt/maekster-android/issues/41): fix the Rediscover cutoff unit (bug).
2. UX-14 [#42](https://github.com/marcus-gt/maekster-android/issues/42): align In heavy rotation and Rediscover selection with iOS. Needs an owner decision on the Rediscover definition. Depends on #41.
3. UX-15 [#43](https://github.com/marcus-gt/maekster-android/issues/43): align Artist picks ranking, dormancy, exclusions and the local-day key with iOS.
