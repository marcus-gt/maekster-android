# UX-44: Queue Builder widget

**Status: COMPLETE.** Checked on the emulator and by the owner on the Pixel 9 (27 Nov 2026). Committed on
`ux/polish-3w-widgets` after `618a60da`; not pushed.

Issue: [#69](https://github.com/marcus-gt/maekster-android/issues/69), parent #62. Gate: UX-40 (met) and the owner's
choice of layout (answered, see below). Widgets cannot scroll sideways, so the app's chip rows cannot be copied.

## Owner decisions (27 Nov 2026)

- Q: Use a compact card (summary line, Play, Reset, picks open the app) or a toggle grid?
  A: The compact card is rejected: "The point of the widget is to set the Queue Builder without opening the app."
  Use a toggle grid with fewer choices:
  - **Genres:** main genres only (no sub-genres), limited to the library's top 4 or 5. The agent uses 5, ordered by
    song count as in the app.
  - **Decades:** five buckets: 2000+, 90s, 80s, 70s, and 60s and before.
  - **Moods:** the six Queue Builder moods, unchanged.
- The agent set these rules, recorded here for the owner to check:
  - A bucket is shown as picked when any of its decades is picked. A tap picks every decade in the library that
    falls in the bucket, or clears all of them.
  - Picks the widget cannot show (a sub-genre, or a genre outside the top 5) are kept, and the summary line still
    names them.
  - A genre counts as picked when it is picked whole or through some of its sub-genres, as on the app card. A tap
    on a picked genre clears it together with its sub-genres.
  - Play first always built a new queue; later replaced by Pause and continue-or-rebuild (see the follow-ups below).

## Reuse

- Picks: `UserPreferences.queueBuilderPicks(sourceId)` and `setQueueBuilderPicks`. The app and the widget share
  this one preference.
- Play: `QueueBuilderPlayer`, with `queueBuilderMessage(outcome)` for the toasts. Reset clears the picks, as the
  app's Reset button does.
- Index: `QueueBuilder.index(sourceId, offline)`. It reloads when a sync succeeds.
- Widget plumbing: the `goAsync()` pattern with the `named("playback")` scope from `RadioStationsWidget.kt`.

## What was built

- `widget/QueueBuilderWidget.kt`:
  - Provider and tap broadcast: genre, decade group, mood, Reset or Play, each with the source id.
  - `DecadeGroup`, `widgetGenres()`, `isGenrePicked` and `toggleGenre`.
  - `QueueBuilderWidgetCommands`: edits run one at a time under a `Mutex`, and one build runs at a time.
  - `queueBuilderWidgetContent` (flow), `queueBuilderViews` (renderer) and `QueueBuilderWidgetUpdater` (API 31+).
- Layout `widget_queue_builder.xml`:
  - Header (opens the app), Reset (only when something is picked) and Play.
  - Genres in rows of 3 + 2, five decade groups, and moods in rows of 3 + 3 (only with AudioMuse).
  - The summary line, an offline note and an empty state.
- Chips use `widget_chip_background`, tinted per state. A picked genre uses its app colour; picked decades and
  moods use the accent. Chip text shrinks to fit at large font sizes.
- `queue_builder_widget_info.xml` is 4×3 (min 250×250 dp). It is registered in the manifest
  (`library_widgets_enabled`), in Koin (S+) and started in `SubtracksApp`.
- The index reloads on `SyncStatus.Success` and on changes to offline mode or the network.

## Verification

- Tests: 18 new, in `QueueBuilderWidgetTest` (11, Robolectric) and `QueueBuilderWidgetCommandsTest` (7).
- Mutations, each restored afterwards: 7, together causing 9 test failures:
  - a group picks only one decade;
  - sub-genres are ignored;
  - no edit `Mutex`;
  - Play ignores `available()`;
  - no reload after a sync;
  - genre colour replaced by the accent;
  - Other is offered as a genre.
- Full checks: format, build, all unit tests (2,122, 0 failures), ktlint and lint.
- Emulator (Pixel_9 AVD, API 37, the owner's server), 27 Nov 2026:
  - Placed from the picker search. The widget showed the app's saved picks (Jazz, 2010s as 2000+, Sad, Relaxed).
  - Picks changed in the app appeared on the widget.
  - Widget taps on Rock, 90s and Reset updated the widget and the app's card.
  - Play built a queue (log: "queue builder: 55 songs") and started playback without opening the app.
  - Taps sent while the launcher was still animating back from the app hit the wrong place: the launcher opened the
    app. With the launcher at rest every tap worked. This is the emulator's input timing, not the widget.
- Not checked: the owner's phone, 200% font size, offline with downloads, no AudioMuse on a device, and the
  picker preview after the chip-colour fix.

## Owner phone check 1 (27 Nov 2026) and fix

- Owner report: chips change colour. Play first did nothing. On a later try music started after a while, but
  the app "crashed" after a few seconds of playing.
- Cause, from the ANR traces in `/data/anr` (09:16, 09:22 and 09:28): Android flagged the app as not
  responding during the `QUEUE_BUILDER_TAP` broadcast. A widget tap is a foreground broadcast with a 10 s limit. The
  receiver held the broadcast with `goAsync()` until the build finished, and a build with moods takes longer on the
  phone. The main thread was idle. Android killed the process, and the playback that had just started died with it.
  The app's own info logs don't show on the phone because verbose logging is off.
- Fix: `releaseWithin(work, BROADCAST_BUDGET_MS = 8 s)` releases the broadcast once the work finishes or the
  budget runs out. The build keeps going in the app's `playback` scope.
- Owner request: show that the queue is loading. While building, a spinner (`widget_qb_building`) takes Play's
  place.
- Tests: 2 new (a slow build releases at 8 s and still plays; a quick tap releases straight away) and the spinner
  test. Each failed under its mutation.
- Still to check on the phone: that playback started after the 8 s release keeps running in the background.
  Starting a foreground service from the background is restricted.
- Follow-up idea: the other widget receivers (radio, albums) also hold `goAsync()` for the whole start. They have
  been fast enough so far, but they could use the same budget.

## Owner phone check 2 and the build service (27 Nov 2026)

- With the 8 s release the build never finished. The spinner kept turning, and the app's own log
  (`files/logs`, read with `run-as`) showed the mood pool query at 10:00:35 but no `queue builder: N songs`.
  Once the broadcast ended, Android froze the idle cached process partway through the build.
- Fix: `QueueBuilderBuildService`, a `shortService` foreground service (API 34+; on 31–33 an untyped FGS), runs
  Play builds. It shows a quiet "Queue Builder · Building…" notification on the `queue_builder` channel
  (low importance) and stops when the build ends. Android allows starting it from a widget tap. If Android refuses,
  the receiver falls back to building within the 8 s broadcast budget. Chip taps still use `goAsync`.
- Owner phone check (10:18 build): Play with moods works; playback keeps running; Play works after a swipe-away.

## Cosmetic follow-ups (owner request, 27 Nov 2026)

- Pause: while the player plays this server's Queue Builder queue (stored label `QueueCollection.QueueBuilder`),
  Play becomes Pause, and a tap pauses (`Kind.Pause`). Owner-checked on the phone.
- Continue or rebuild (owner chose option A, 27 Nov 2026, after finding that Play after Pause built a new queue):
  - `QueueBuilderPlayer` records what each started queue was built from, from the app's card and the widget
    alike, in `UserPreferences.queueBuilderBuiltPicks` (keys `queue_builder_built_*`, per server).
  - While the Queue Builder queue is paused and the current picks (available selection and length) equal the
    built picks, Play continues it (`Kind.Resume` → `PlaybackController.resume()`, which falls back to building
    when nothing can continue). Once the picks have changed, Play builds a new queue.
  - While paused, a status line says which will happen: "Paused · Play to continue" or
    "Picks changed · Play builds a new queue".
  - To start over with the same picks, change a chip and change it back, or build from the app.
- Background: the softened cover of what plays on this server, as the other widgets show it
  (`WidgetArtworkBackground`, `widget_art_background`).
- Background while paused (owner request, 27 Nov 2026): all widgets now keep the cover of what the player holds
  for the active server, playing or paused, instead of blanking it on pause. Changed in the album shelves,
  Favorites, Radio stations and the Queue Builder; Now Playing already kept it. The 4 existing tests that asserted
  the old rule failed with the change and now assert the cover.
- Mood buttons' play/pause (owner request, 27 Nov 2026): the playing mood radio's button showed the dark ink
  play/pause made for the orange Play button. It now uses white `ic_widget_mood_play`/`ic_widget_mood_pause`,
  like the mood icons, on the Now Playing and Radio stations widgets (`setWidgetMoodButtons`). A Radio stations test
  checks the drawables; the old code showed the ink icons.
- Tests: 3 (Pause tap, `withPlayback` rules, Pause icon) and the background test. Each failed under its mutation.

## Owner sign-off and gaps

- Owner on the Pixel 9: chips, Play with moods, playback after a swipe-away, Pause, continue-or-rebuild, the
  background kept while paused and the white mood icons all work.
- Final checks: format, build, all unit tests, ktlint and lint pass.
- Not checked: 200% font size, offline with downloads, a device without AudioMuse, and Android 12–13 (the untyped
  build service path).

## Next action

Committed, not pushed. Next slice: UX-45, a choice per Now Playing widget of the row below the player (owner
request, 27 Nov 2026).
