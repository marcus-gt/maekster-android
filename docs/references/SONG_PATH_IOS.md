# Song Path: iOS visual reference

Reference supplied by the user in the development conversation on 17 November 2026.
This records the attached screenshot's visible layout; the original image bytes are not available
through the IDE file tools and have not been saved in the repository.

## Visible hierarchy

- One rounded, blue-to-teal card titled **Song Path**, with a path icon at the leading edge and a
  swap-direction action at the trailing edge.
- Two full-width endpoint rows inside a shared inset surface, separated by a divider.
- The selected **From** row has square cover art, the song title **Viva Emiliano Zapata**, the
  secondary line **From · Gato Barbieri**, and a trailing chevron.
- The empty **To** row has a plus tile, **Choose a song or mood**, the secondary label **To**,
  and a trailing chevron.
- The footer puts **25 songs** beside a length slider, then a circular Play action at the trailing edge.
  Play appears disabled in this incomplete state.

## Android interpretation for M3.5 E

Preserve this information hierarchy and compact choose-endpoints → set-length → play workflow.
Use Compose, maekster theme tokens, accessible controls and Android-native picker sheets; the iOS
gradient, icons and exact geometry are visual context, not assets or pixel-matching requirements.

- Each endpoint row opens a song/mood picker. Show selected-song artwork and artist credit, or a clear
  selected-mood label. The screenshot shows one selected song and one empty endpoint; it does not
  establish the complete picker design or slider range.
- Provide an accessible Swap endpoints action. Both songs are allowed; at most one endpoint may be a
  mood, matching the existing AudioMuse transport contract.
- Show the selected song count and a length slider. Verify bounds against the iOS behavioral reference
  and AudioMuse before implementing the UI; do not infer them from this screenshot.
- Disable Play until both endpoints are valid. Show generation progress and a recoverable error;
  stale results must not replace a newer playback command.
- Resolve generated tracks against the local mirror, retain the first occurrence of each song in path order,
  and play a finite queue without auto-extension. This action requires AudioMuse and is hidden offline.
- Verify the selected/empty states, both endpoint directions, swap, loading/error states, offline
  fallback, TalkBack labels and 2× text. The footer actions must stay reachable at large text sizes.

Behavioral source: read-only iOS reference, Maekster/SharedViews/Discover/SongPathViews.swift and
Maekster/Services/Implementations/SongPathService.swift. Read these when implementing E.
