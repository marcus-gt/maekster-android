# Milestone 2 close-out

**Status:** DONE for everything that can be done here. Based on 11163b65. All of Milestone 2's slices
(M2.1–M2.11) are implemented. What remains is listed under "Still open".

## Decisions (user, 15 Nov 2026)

- **M2.8 lyrics prefetch: follow iOS.** Lyrics are not fetched when a song is downloaded. They are
  fetched when shown and cached in Room (as now), so downloads never batch requests to LRCLIB.
- **M2.9 on-device removal: authorized and done.** See `M2.9-membership-toggles.md`: Illusion was
  removed from `maekster development test` (4 → 3 songs, empty circle on reopen).

## Investigated on the emulator (API 37, Navidrome)

- **Airplane mode mid-song:** the current track keeps playing from what is already buffered or cached.
  Skipping to an uncached track while offline fails to connect, and the mini-player shows a loading
  spinner (it still intends to play) rather than an error. Turning the network back on resumes
  playback by itself within seconds (new stream requests, state READY). No downloads were present,
  so "skip to downloaded" was not exercised.
- **Media key that replaced the queue (M2.11 follow-up):** could not reproduce. With the app on Home
  and playing, `adb shell input keyevent KEYCODE_MEDIA_PAUSE` paused normally and the queue was
  unchanged. The app has no key handling, no media-button receiver and no `onPlaybackResumption`.
  The one-off happened right after a force-stop and relaunch. The emulator also has the old upstream
  app `com.subtracks.nightly` installed, whose stale media session is still registered as active. Media
  keys may have been routed oddly then. Left open as unexplained; watch on a real device.
- **Process death and restore:** done in M2.11 Part C (same song and time after force-stop).

## Parity doc refreshed

`FEATURE_PARITY.md` statuses for the M2 rows now match what shipped: Present for server setup,
credentials, metadata, Albums/Album detail/Artist detail/Playlists/Songs/Genres and labels, Home
personalisation, the full-screen player, and playlist editing (Navidrome only, issue #11). Rows marked
"Present but behaviour differs", with the reason in the cell:
- custom headers (DownloadManager redirect behaviour; a real proxy is unchecked);
- Home shelves (Artist picks wait for M3.2);
- download vs stream (local-first by design; no stream-instead toggle).

## Still open (needs the user, other servers, or a physical device)

- A header-protected reverse proxy (Caddy or nginx) in front of Navidrome; nothing was set up here.
- Lyrics on a device: Navidrome-embedded synced LRC and the LRCLIB fallback.
- M2.7 device matrix (API 35/37) and the M2.4 200k-song `queryTiming` measurement.
- gonic, LMS and Nextcloud Music integration (issue #11).
- The GrapheneOS/physical checklist in the roadmap: Wi-Fi ↔ mobile handover, Bluetooth and car,
  calls, an hour with the screen off, the notification heart, network permission revoked, and
  screen-off downloads.
- Known flaky tests: three HomeScreenshotTest timeouts (full suite only), and SaveQueueViewModelTest
  (once).

## Notes

- Scrobbling was on during the airplane-mode and media-key tests, so "now playing" updates were probably
  sent to Navidrome. Both tracks played well under half their length, so no play counts should have
  been submitted (the log buffer that would confirm this had been cleared).
- adb text input drops letters on this emulator; type one character at a time.

## Next

Milestone 3 starts with M3.1 (ListenBrainz scrobbling and play events).
