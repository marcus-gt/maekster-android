# UX-43 — Radio stations widget

**Status: COMPLETE** (owner phone checks passed, 27 Nov 2026). Wave W, `ux/polish-3w-widgets`, starting at `2b796179` (UX-42).

## Owner decisions (asked and answered 26 Nov 2026)

- Layout: stack or grid? **Vertical grid**, like the album-shelf widgets.
- One widget for both kinds of station, or two? **One widget**: mood row on top (hidden when AudioMuse isn't
  available), artist radio grid below, like Discover.
- Offline? **Keep the artist tiles, hide the moods.** Artist tiles stay tappable and use the existing offline
  fallback; the mood row hides and "Offline: artist radio plays downloaded songs." shows.
- After the first phone check (all passed) the owner asked for **play/pause on each radio, as on the album widgets**. Added (below).

## Acceptance criteria ([issue #68](https://github.com/marcus-gt/maekster-android/issues/68))

- [x] Shows artist radio and mood radio stations as Discover picks them, from cached data (`MixDao.stationArtists`, Room test).
- [x] Tapping a station starts it through `InstantMixPlayer.playArtistRadio` / `MoodRadioPlayer` and `PlaybackController`,
  from a stopped app too (owner, Pixel 9, first build).
- [x] Offline or without AudioMuse: moods hide, offline note, tiles still play downloads or toast `mix_empty` (tests + owner).
- [x] Android 12+ only (D20): receiver behind `library_widgets_enabled`, updater registered on S+ only.
- [x] Tests that fail without the change. Emulator not used: the owner chose the Pixel 9 instead.
- [x] Owner check on the phone: the first build passed all five checks and the play/pause build all three.

## Implementation

- `widget/RadioStationsWidget.kt`: provider; `RadioStationsWidgetCommands` (artist tap → artist radio, one at a time, stale-server
  taps ignored, `mix_empty`/`mix_failed` toasts, tap on the playing radio toggles play/pause); `radioStationsContent`
  (Discover's query; offline from `audioMuseAccess`; moods from `widgetMoodsAvailable`, never offline); `withTaps`;
  `radioStationsFrame`/`radioStationTile`; the S+ updater (artist art via `artistArt` with fallbacks, playing-art background).
- Play/pause: `QueueDao.observeSourceLabel` + `QueueRepository.sourceLabels()` (the stored `playback_cursor.sourceLabel`).
  A tile whose `artistRadioLabel(name)` matches, or a mood whose `QueueCollection.forMood` label matches, shows pause or
  play (and says so in its content description). The tap carries the artist name so the receiver can match it.
- Mood row moved to `layout/widget_mood_row.xml` (icons now have ids), included by the Now Playing 4×2 layout and the radio
  widget; `setWidgetMoodButtons` is shared, so **the Now Playing widget's mood row gets the same play/pause marking and
  toggle** (`NowPlayingWidgetContent.playingMood`, `NowPlayingWidgetCommands` `queueLabel`). Mood taps still go through
  `NowPlayingWidget` with one guard; `NowPlayingWidgetUpdater.moodStarting` dims both rows.
- 4×3 default (`radio_stations_widget_info.xml`, min 250×200 dp, resize to 140 dp high). Header opens the app (no Discover deep
  link; possible follow-up). Font-scale change refreshes this widget too. `showWidgetToast` shared in `AppModule`.

## Verification

- New tests: `RadioStationsWidgetTest` (10), `RadioStationsWidgetCommandsTest` (5), plus one each in
  `NowPlayingWidgetCommandsTest`, `NowPlayingWidgetUpdaterTest` and `NowPlayingWidgetRendererTest`.
- Mutation checks (each run, then restored): 9 for the first build (offline dropping artists, moods offline, no offline note,
  mood row always shown, wrong no-server text, no stale-server check, no guard, background from another server, no dimming)
  and 6 for play/pause (no artist toggle, no mood toggle, tile never current, mood row ignoring the playing mood, updater
  dropping it, label ignoring the server). Every mutation failed its tests (16 failures in total).
- ktlintFormat, assembleDebug, testDebugUnitTest (2,104 tests, 0 failures), ktlintCheck, lintDebug: all pass (Studio JBR + wrapper).
- Pixel 9 (owner, first build, debug APK installed with approval): grid and order match Discover, artist and mood taps start,
  playing art shows, offline behaviour, start after swipe-away. All passed.
- Pixel 9 (owner, play/pause build): the playing artist tile and mood button show pause or play and toggle, on the radio widget
  and the Now Playing mood row; other tiles stay unmarked; album widgets and the player behave as before. All passed.
- Not checked: 200% font; an empty library on a launcher; Android 11 (provider disabled); the API 37 emulator.

## Next action

Committed on `ux/polish-3w-widgets`, not pushed. Recommended next slice: UX-44 Queue Builder widget (owner picks the layout).
