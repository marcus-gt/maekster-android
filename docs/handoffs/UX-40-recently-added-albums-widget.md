# UX-40 — Recently added albums widget

**Status: COMPLETE.** Branch `ux/polish-3w-widgets`, starting at `f080b747`.

## Acceptance criteria ([issue #65](https://github.com/marcus-gt/maekster-android/issues/65))

- [x] Bounded local album grid in Home's Recently added order, with artwork and titles.
- [x] Album tap plays through PlaybackController. Header opens the Recently added list.
- [x] Refresh after sync and active-server changes. Metadata comes from Room.
- [x] Empty and no-server states explain what to do.
- [x] Document reusable collection rendering, thumbnails, update triggers and tap intents.
- [x] Disable the provider below Android 12.
- [x] Fail-before and pass-after tests and emulator resize verification.
- [x] Owner phone check.

## Owner decision

- What default widget size and maximum album count should UX-40 use? Owner selected 4×2 and up to 12 albums.

## Checkpoint

Wave B merged in PR #70. UX-40 follows the UX-38 and UX-39 commits.
The local query is the same bounded album query used by Home. It observes Room changes and switches
subscriptions when the active server changes. An explicit widget refresh restarts observation for
launcher requests and deletion, with no album observation when the launcher has no instances.

For UX-41 to UX-44, see `RecentlyAddedAlbumsWidget.kt` for the API 31 `RemoteCollectionItems`
grid, mutable explicit broadcast template and row fill-in intents. The receiver checks the current
source before dispatching playback. The header uses an immutable activity intent and the existing
Home section route. `WidgetArtwork` shares Coil's source-specific disk keys and bounds decoded
collection covers to 96 pixels on their longest side. The launcher adapts column count to width.
The `library_widgets_enabled` resource disables collection providers below API 31.

The focused regression failed with a deliberate 13-album request. Restored 12 and verified the
test passes, including refresh on shelf changes, active-source switching and ignoring changes to
play count. Formatting, debug assemble, full unit suite, ktlint, Android lint and `git diff --check`
passed using the Studio JBR fallback because Nix was absent from the IDE shell PATH.

The provider is registered on the API 37 emulator. Installed the verified debug APK on the
owner's Pixel 9 at their request. Asked them to confirm default 4×2 placement, resize, album
playback and header navigation on the installed build. Owner replied “All four passed on the
installed build.”

On the API 37 emulator the picker showed 4×2. The placed widget rendered four columns with
covers and titles and reflowed to three columns when narrowed. Checked the narrowed grid at
200% font and restored 100%. The header opened the Recently added list with the same first
album. The empty and no-server data states are covered by the regression and their copy was
checked in resources. No separate launcher run used an empty library or Android 11.

Next: start UX-41 in a separate session.
