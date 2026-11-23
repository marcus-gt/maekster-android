# UX-29 — Play buttons play what's shown (#58)

**Status: NOT NEEDED (23 Nov 2026).** Branch `ux/polish-3a-daily-feel`. No code changes.

## Findings

- Reproduction on the emulator (API 37, debug build from `fbda477d`): Library → Genres → Electronic → Techno
  (273 albums, the saved genre album sort) → Play. The queue starts with the first album shown (Ellen Allien,
  *New Life*, track 1), plays its tracks in disc/track order, then the second album shown (*New Life Rmxs 1*). That
  matches the list. The path is `HomeListViewModel.playGenreAlbums` → `PlaybackController.playLibrarySongs` with the
  same `SongListSpec` (album genre tags and album sort) that pages the visible albums.
- The owner retested on the GrapheneOS phone, found that it works, and asked to skip the issue. The report on
  20 Nov probably came from an earlier build or another path. Nobody remembers which.

## Not fixed (possible follow-up, needs an owner decision)

- A genre or style queue is labelled as a generic Songs list: Now Playing's header says "Songs" and the queue says
  "Playing from Songs", not "Techno". Cause: `playLibrarySongs` stores a `QueueKind.Library` entry without a genre
  source label. The fix would be a small labelling change, in the same place as the UX-24 type line.

## Verification

Emulator check only (above). No build checks were needed, since nothing changed. The owner's queue on the emulator
was replaced by the Techno queue.

## Next action

Wave A has no remaining slices. The owner pushes `ux/polish-3a-daily-feel` and opens the wave PR (docs only), or
merges it together with wave B. Close #58 as not reproducible with the owner's approval.
