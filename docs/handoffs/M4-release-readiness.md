# Milestone 4 — Release readiness

**IN PROGRESS.** The baseline is committed locally as `22619ab5` on
`milestone-4-release-readiness`, based on merged milestone 3 (`b6a3b3b1`). The four P0 gates
remain active. Follow-up subtasks have their own handoffs. Nothing was pushed.

## Implemented and verified

- **Scope:** The roadmap preserves the M4 numbers and links P1/P2 work. GitHub issues #18–#27
  were created after checking existing issues. #3 was reused and extended with the BluOS HTTP
  and Navidrome authentication investigation for the NAD C658. No deferred features were built.
- **M4.8:** Resolved `releaseRuntimeClasspath` contains no Google Play Services or Firebase
  dependencies. The unsigned APK's manifest shows standard Android network, notification,
  wake-lock and media-service permissions, with no Google permissions. A physical, no-Play
  GrapheneOS test remains open.
- **M4.10:** `PlaybackService` uses Media3's local wake mode with the existing media foreground
  service, audio focus and noisy-route handling. On emulator-5554, playback advanced with the
  screen asleep after leaving the app, and an in-app pause released the ExoPlayer wake lock.
  The emulator has Play Services installed. Its simulated asleep media-pause key did not pause
  the player, so validate headset and Bluetooth commands on a physical device. The rest of the
  route, network, background download, recovery and battery checklist remains open.
- **M4.9:** Now Playing names the seek bar, retains transport target space on narrow screens,
  and scrolls to reach lower controls at 200% font. The Compose test checks seek and transport
  controls at 2×. The seek-label test failed before the fix. The expanded test also caught a
  clipped control before the scroll fix. TalkBack, contrast and full core navigation remain
  physical-device checks.
- **M4.11:** The former Play publishing workflow now builds an APK artifact on manual dispatch
  from main. It checks tag, signer fingerprint, APK package, and versions. An ordinary local
  release build has no debug signing fallback. YAML and Bash blocks parsed successfully.
  The locally built unsigned APK has package `io.github.marcus_gt.maekster`, version 3.1.0
  and version code 14. The approved signer and an Obtainium in-place upgrade remain unverified.

## Build and tests

- Android Studio's JBR and the Gradle wrapper were used because Nix is unavailable locally.
- ktlintFormat, debug build, unsigned release build, ktlintCheck and lintDebug passed.
- Focused run: 278 Now Playing, controller and download tests passed.
- Full run: 1,915 of 1,918 passed. The three known Home screenshot timeouts passed in isolation.

## Follow-ups

- [M4.9 Server setup at 200% text](M4.9-server-setup-accessibility.md) verifies that both server form actions remain reachable and clickable.

- [M4.11 APK verification](M4.11-apk-verification.md) shares the same signer and metadata checks between CI and local release review.

## Resume

- Run [the physical GrapheneOS checklist](../RELEASE_READINESS.md) on a Pixel without
  sandboxed Play. Keep the checkboxes open until evidence is recorded. Review issue #6 if a
  reverse proxy supplies sensitive custom download headers.
- Follow [the Android release procedure](../RELEASING_ANDROID.md) after owner approval. Configure
  the protected release environment, approved keystore and pinned fingerprint, then verify a signed
  artifact and Obtainium upgrade. No production credentials were generated or changed, no signing
  secrets were provisioned, and no release was published.
