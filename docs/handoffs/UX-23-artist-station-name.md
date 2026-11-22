# UX-23 — One name for the artist station

**Status: COMPLETE.** Owner decision recorded below (19 Nov 2026).
[#53](https://github.com/marcus-gt/maekster-android/issues/53), parent #45. Branch `ux/45-artist-now-playing`,
based on `ed8742f9`.

## Findings (code)

- The artist … menu (`ItemContextMenu` → `MixMenuViewModel` → `InstantMixPlayer.play(MixSeed.Artist(id), sourceId)`)
  and the Discover "Stations for you" tiles (`DiscoverViewModel.station` → the same `InstantMixPlayer` singleton,
  same call) start **the same mix**: same seed (`MixSeed.Artist`), same `InstantMix.build` sizing, the same
  `QueueCollection.InstantMix` (`endless = true`, so the same refill) and the same queue label "Instant Mix".
- The only differences are the words: menu `start_station` "Start station", Discover caption
  `discover_station_caption` "Radio station", queue/Now Playing `instant_mix_title` "Instant Mix".
- iOS (`../maekster-ios-reference` @ `25a7d7fa`): the artist menu says "Instant Mix" (shared with songs and
  albums), the Discover tile says "Radio Station" under "Stations for You", and the queue source for an
  artist-seeded mix from either entry point is `artistRadio`, titled "<Artist> radio" (`QueueSource.swift`).
- Emulator (Pixel 9) before the change was not checked separately; the code paths above are identical.

## Owner decision (19 Nov 2026, roadmap D18)

Asked: which single name for the artist-seeded mix in the artist menu, the Discover caption and the
queue/Now Playing label ("radio", "Instant Mix" or "station")? Answer: **radio**, "Use 'Artist radio'".
Follow-up (where does "Artist radio" go?): the artist … menu item says **Artist radio**; the queue and
Now Playing say **"<Artist> radio"** (e.g. "Bob Dylan radio", as on iOS); the Discover tiles need **no
caption** under the artist name (the header implies it), and the Discover shelf "Stations for you" is
renamed **Artist radio**. Song and album mixes stay "Instant Mix". UX-24 uses this.

## Implementation

- Strings: `artist_radio` "Artist radio" (menu item, `QueueCollection.ArtistRadio.title`), `artist_radio_queue`
  "%1$s radio", `discover_stations` now "Artist radio" (also the Home/Discover layout dialog's shelf name),
  `discover_stations_empty` says "artist radio"; `start_station` and `discover_station_caption` removed.
- `QueueCollection.ArtistRadio` (endless) stores `"\u0000maekster:collection:artist-radio:" + name`;
  `fromStored` matches the prefix, `artistRadioLabel(name)` builds it, `displayName(label, resources)` reads
  "<artist> radio" ("Artist radio" when the name is unknown). `PlaybackController.sourceHeading` uses it, so
  the queue heading and Now Playing say "Bob Dylan radio" and keep it when autoplay refills.
- `PlaybackController.play/start/PendingPlay` now carry the stored label string instead of the collection;
  `playMix` takes an optional `sourceLabel` (default `collection.storedLabel`).
- `InstantMixPlayer` takes `artistName` (AppModule: artists row, else credited name) and labels any
  `MixSeed.Artist` mix as that artist's radio. New `playArtistRadio(sourceId, artistId)` is what
  `DiscoverViewModel.station` calls; the menu goes through `MenuTarget.mixRequest()` and `play`.
- Discover tiles: `VividTileCard.caption` is nullable; the Artist radio tiles pass none.
- Owner follow-up: the shelf description wrapped to two lines, so `discover_stations_description` is now
  "Pick an artist and see where it takes you." (one line on the Pixel 9 emulator at 100% font; it still
  wraps at larger font sizes, which is fine). Committed as `ff3fd244`. Checked after the change:
  `:app:assembleDebug`, `:app:ktlintCheck`, `:app:lintDebug` (no issues) and `DiscoverScreenTest`
  (22 tests, 0 failures); the full unit suite was not rerun for this one-string change.

## Verification

- New: `ArtistRadioTest` (menu path via `mixRequest` + `play`, Discover path via `playArtistRadio`: same
  `MixSeed.Artist` seed, same label, "Bob Dylan radio", endless; song mixes stay "Instant Mix"),
  `PlaybackControllerTest.anArtistRadioIsNamedAfterTheArtistAndKeepsItsNameWhenExtended`,
  `DiscoverScreenTest.artistTilesSitUnderArtistRadioWithoutACaption`; updated `ItemContextMenuMixTest`.
- Mutations (artist label → Instant Mix, heading ignores the name, menu says Instant Mix, tile caption back)
  failed the matching test each; restored, all pass. `DiscoverViewModel` itself has no test harness (12
  dependencies), so the Discover path is tested at `InstantMixPlayer.playArtistRadio`, which it calls.

- Full run: debug build, ktlint and lint (no issues) passed; `:app:testDebugUnitTest` ran 2004 tests with 18
  failures, all 5 s Compose timeouts in Library paged screens (`AppScreenshotTest`, `LibraryScreenTest`,
  `LibraryOptions*`, `LibrarySearchScreenshotTest`). Rerun alone, all 42 tests in those classes passed. This
  slice does not touch them (known load flake).
- Emulator (Pixel 9): Discover shows the "Artist radio" shelf with name-only tiles; tapping Bob Dylan played
  a mix whose Now Playing header and queue ("Playing from Bob Dylan radio") say Bob Dylan radio. Bill Evans'
  … menu shows "Artist radio"; it started a mix titled "Bill Evans radio". This replaced the emulator queue
  (needed by the slice; it was "Monkey Bars" by Jurassic 5, set by someone else since the last session). The
  artist-page album layout was found on list, also changed since UX-22, and was left as found.
- Not checked: offline start, autoplay refill on the device (covered by the controller test), TalkBack on
  the captionless tiles (the shelf heading gives the context).
- Still "NOW PLAYING: SONG" above the title in Now Playing: that line is UX-24's scope.

## Git state

Branch `ux/45-artist-now-playing`: UX-23 is `c74964e3` plus the copy follow-up `ff3fd244` and this handoff
update. Nothing pushed; the owner opens one PR for #45 at the end.

## Next action

UX-24 ([#54](https://github.com/marcus-gt/maekster-android/issues/54)): static "Now playing" header and the
source type line, using "Artist radio" (D18) for artist-seeded mixes.
