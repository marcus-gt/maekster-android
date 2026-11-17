# Release readiness

Record the build or commit, Android/GrapheneOS version, device model, server and audio route for each run. Leave a box open until there is actual evidence. Use a test account and redact its URL and credentials in any shared logs.

## M4.8 — GrapheneOS without Play Services

Use a physical GrapheneOS Pixel in a user profile with no sandboxed Google Play installed, with default exploit protections. Use the debug APK for development checks, then repeat the release
checks with an approved signed APK.

- [ ] Launch, add a Navidrome server, sync and browse the Room mirror. Search tracks and albums, and stream an original and a transcode on Wi-Fi.
- [ ] Download an album, turn off network connectivity, enable app offline mode, reopen the app, and play several downloaded songs. Remove and re-download the album after restoring connectivity.
- [ ] Deny the app's Network permission in GrapheneOS App info. Check that a new stream or sync reports its failure and downloaded playback works. Restore permission, reconnect, and check that retry succeeds without re-adding the server.
- [ ] Verify downloads, artwork and Room work with default storage permissions. If testing Storage Scopes, enable it and repeat these app-private storage checks. Local folder import is on hold (M3.4).
- [ ] Repeat after an in-place signed upgrade. Keep exploit protections enabled and record any native crash with its symbolized stack.

A dependency group with a Google name alone does not imply Play Services (for example, Guava). Review the resolved `releaseRuntimeClasspath` and the merged manifest for Play Services, Firebase or required Google packages. The Pixel test remains the runtime gate. [GrapheneOS Network permission](https://grapheneos.org/features#network-permission-toggle) · [Storage Scopes](https://grapheneos.org/usage#storage-scopes)

## M4.10 — Background playback and battery

Run on the same Pixel with both a long track and a queue that crosses multiple track boundaries. Test both streamed and downloaded tracks. Use the normal battery setting first, then repeat the applicable scenarios with Battery Saver and the app set to Restricted.

- [ ] Background the app and lock the screen for at least 20 minutes. Check continuity, track advancement and lock-screen controls. Swipe the app away while playing, then again while paused. Active playback should continue, while a paused or finished service can stop.
- [ ] Disconnect wired headphones or Bluetooth during playback. Observe a safe pause for a noisy route loss. Reconnect and resume deliberately if paused. Check Bluetooth transport controls and repeat while the app is backgrounded.
- [ ] Interrupt music with a call and a short navigation prompt. Check that audio focus pauses or ducks as appropriate, and that ending the interruption does not resume intentionally paused playback.
- [ ] Stream across Wi-Fi and mobile data with equal quality preferences, then with different preferences. Drop both networks briefly and restore one. Confirm an active stream recovers near the saved position without restarting the queue or resuming after a user pause.
- [ ] Start a bulk download, background and lock the phone, and exercise network loss and the metered-data preference. Verify eventual completion or an actionable retry, download reconciliation after process death, and playback of the completed download.
- [ ] Kill the process during playback and reopen. Check that the queue and last saved position restore paused. Repeat while a download is pending. Force-stop behavior is a separate OS case and requires an explicit relaunch.
- [ ] Compare 30 minutes of screen-off playback with a measured paused baseline using Android battery stats. Confirm the media foreground service and playback wake lock are released when no longer needed. Verify paused idle does not poll or sync in the background. Record power observations instead of using a fixed battery percentage threshold.

Playback uses a Media3 foreground service with a local wake lock during active playback or buffering. Audio focus and noisy-route handling stay with Media3. Downloads use platform DownloadManager and auto-sync runs only with the app in the foreground. For reverse proxies, see [issue #6](https://github.com/marcus-gt/maekster-android/issues/6) before testing downloads with sensitive custom headers and redirects. [Media3 wake mode](https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer.Builder#setWakeMode(int))

## M4.9 — Core accessibility

- [ ] At 200% system font and with display size increased, navigate Home, Library, Search and Settings. Add or edit a server, find a song, play it, open the queue, and download and play offline. Check reachability and readability in portrait with no dead ends or clipped primary controls.
- [ ] With TalkBack, check traversal order, labels and state for the tabs, Back, play/pause, previous/next, seek, queue, shuffle and repeat. Verify that opening or dismissing Now Playing and its sheets returns focus to a useful control.
- [ ] Check a 48 dp target for primary controls and contrast of labels and controls across light, dark and artwork-derived themes. Check focus and navigation with Android accessibility settings enabled.

Secondary flows, fuller assistive-technology coverage and advanced accessibility are tracked in [issue #27](https://github.com/marcus-gt/maekster-android/issues/27).

## M4.11 — GitHub APK and Obtainium

- [ ] Owner approves and provisions the signing material and pinned certificate fingerprint.
- [ ] A manual workflow run on the approved tag produces a signed, fingerprint-verified APK and checksum manifest.
- [ ] Install the first APK and update in place from a second release with a higher version code using Obtainium. Confirm package name, signer, settings, downloaded files and queue are retained.

See [GitHub APK release procedure](RELEASING_ANDROID.md).
