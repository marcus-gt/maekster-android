# UX-24 — Now Playing: static header and playback source type

**Status: COMPLETE.** [#54](https://github.com/marcus-gt/maekster-android/issues/54), parent #45.
Branch `ux/45-artist-now-playing`, based on `b816c5d0`.

## Findings

- `NowPlayingScreen` top bar: line 1 `now_playing_header` "Now playing: %1$s" with the current entry's
  `QueueKind` (so radios read "NOW PLAYING: SONG"), line 2 `PlaybackController.sourceTitle` (album or
  playlist name, collection title, or the album name as fallback).
- The cursor's `sourceLabel` stores either a `QueueCollection` label or a plain source *name* (album,
  playlist, artist, genre, or the album of a single song). The type of a named source is not stored, so
  it cannot be recovered after process death; this slice stores it with the name.
- iOS's Now Playing (screenshot in #45) has no header; the Android behaviour comes from #45/#54.

## Owner decisions (19 Nov 2026)

- Asked: what line 2 says while songs added by autoplay play after a finite source ends. First answer
  "Autoplay", then, after examples, changed to: **Album ended: "Similar to <album>"; Playlist ended:
  "Similar to <playlist>"** (same text as the queue screen). Applied to every finite named source.
  Unnamed collections read "Similar to Favorites/Songs/Downloads"; a queue with no name reads "Autoplay".
- Artist radio keeps D18: line 2 "<artist> radio". Mixes and radios keep their own name after refills.
- A single song is labelled by its album (as on iOS), so its type is "Album".

## Implementation

- `playback/QueueSourceType.kt`: `NamedQueueSource` stores `"\u0000maekster:source:<QueueKind>:" + name`;
  `nowPlayingSourceType(label, contextKind, generated, resources)` maps it. Collections show their title
  (artist radio "<artist> radio"); finite ones read "Similar to …" when generated. Named sources show the
  kind's type (a single song is "Album"), or "Similar to <name>" when generated. Old plain-name labels and
  unlabelled queues use the current entry's kind; with nothing at all: "Queue", or "Autoplay" when generated.
- `PlaybackController.sourceLabelFor` stores named labels; `QueueCollection.displayName` strips the prefix,
  so the queue heading ("Playing from Love and Theft", "Similar to …") is unchanged.
  `PlaybackController.nowPlayingSource(context, resources, generated)` reads the stored cursor label.
- `NowPlayingScreen`: line 1 is the static `now_playing_header` "Now playing" (shown uppercase); line 2 is
  `nowPlayingSource`. The `context_kind_*` strings and `QueueKind.label()` are gone.

## Verification

- New: `QueueSourceTypeTest` (every `QueueKind` and every `QueueCollection`, autoplay, old labels, fallbacks,
  heading name), `NowPlayingHeaderTest` (static header, no "NOW PLAYING: …"),
  `PlaybackControllerTest.nowPlayingNamesTheTypeOfSourceFromItsStoredLabelWhileTheQueueKeepsTheName`
  (stored label, type, heading name, similar, and a type read from the stored cursor alone). Updated
  `queueSourceLabelIsCapturedAndSurvivesClearAndUndo` for the new stored format.
- Mutations (plain name stored, kind in the header, name instead of type, no "Similar to") failed every
  targeted test; restored, they pass.

- Full run: `:app:ktlintFormat`, then `:app:assembleDebug`, `:app:testDebugUnitTest` (2012 tests, 0 failures),
  `:app:ktlintCheck` and `:app:lintDebug` (no issues), with Studio's JBR.
- Emulator (Pixel 9): the queue found on the device (an artist queue saved before this change as the plain
  name "Bob Dylan") read "NOW PLAYING / Songs", the old-label fallback to the current entry's kind. Playing
  Bob Dylan's Top songs read "NOW PLAYING / Artist"; after `am force-stop` and a relaunch it still read
  "Artist". The queue screen kept "Playing from Bob Dylan". This replaced the emulator's queue (someone had
  changed it to Bob Dylan's "It Ain't Me Babe" since UX-23).
- Not checked on the device: albums and playlists (unit and controller tests), an autoplay continuation, and
  200% font (the line is one `maxLines = 1` ellipsized `Text`, unchanged).

## Known limitation

Queues saved before this change store a plain name, so until a new queue starts they show the current
entry's kind (an artist's top songs read "Songs" rather than "Artist"). No migration, by design.

## Next action

UX-25 ([#55](https://github.com/marcus-gt/maekster-android/issues/55)): the album line under the cover and the
iOS text hierarchy in Now Playing.
