# UX-44: Queue Builder widget

**Status: BLOCKED (owner decision on the layout).** Nothing implemented yet. Branch `ux/polish-3w-widgets`, based on
`bd7b949e` (UX-43).

Issue: [#69](https://github.com/marcus-gt/maekster-android/issues/69), parent #62. Gate: UX-40 (met) and the owner's
choice of layout. Widgets cannot scroll sideways, so the app's chip rows cannot be copied.

## Open owner question (asked 27 Nov 2026, not answered yet)

The owner was asleep and had asked agents to move on to the next issue whenever they need input. So the question
is written down here and has not been put to the owner yet.

1. **Layout.** Two options:
   - **A. Tall toggle grid.** Every genre, decade and mood chip wraps into a grid of toggles, with Play and Refresh
     underneath. The whole feature works from the home screen. It needs a 4×4 or larger widget, and a large library has
     too many genres to fit, so the chips would have to be capped (for example the top 8 main genres, the decades the
     library has, and the six moods).
   - **B. Compact card.** The widget shows the current picks as a summary line (the same text as `queueBuilderSummary`)
     with Play and Refresh. Tapping the picks opens the Queue Builder sheet in the app. A 4×2 widget is enough, and it
     matches the Home card's hierarchy.
   - The agent recommends **B, with the six mood toggles as one wrapped row** (they always fit, and they need no
     index). It is the smallest design that keeps "one size for the whole card" and avoids a cap on genres.
2. If A: which chips to show when they don't all fit (top main genres by song count is the obvious rule).

## Reuse once the layout is chosen

- Picks: `UserPreferences.queueBuilderPicks(sourceId)` / `setQueueBuilderPicks`. This one preference serves the app
  and the widget in both directions.
- Play: `QueueBuilderPlayer` (Koin) with `queueBuilderMessage(outcome)` for the toasts. Refresh means the same as
  Refresh in `QueueBuilderViewModel`.
- Offline and AudioMuse: `AudioMuseScoreAccess` ("audioMuseAccess") and the `downloadedOnly` path as on Home.
- Widget plumbing: the API 31 `RemoteCollectionItems` and fill-in intents in `RecentlyAddedAlbumsWidget.kt`, the
  mood row and toggles in `RadioStationsWidget.kt` and `widget_mood_row.xml`, and the `goAsync()` pattern with the
  `named("playback")` scope.

## Git state

The handoff and the roadmap status (Blocked) are committed on `ux/polish-3w-widgets`. Nothing is pushed.

## Next action

Ask the owner question 1 (and 2 if they pick A), record the answer here, then implement it on `ux/polish-3w-widgets`.
