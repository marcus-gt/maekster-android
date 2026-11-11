# mækster Android roadmap

Companion to `docs/FEATURE_PARITY.md`. Incremental vertical slices on top of the existing Subtracks
foundations. There is no rewrite and no module split. Each slice is one reviewable change set with its own tests.

Paths: `st:` = `app/src/main/kotlin/com/subtracks/`.

## Ordering rationale (what changed from the initial outline)

- **Identity before features.** `applicationId` and the Subsonic client name (`c=subtracks`) must change before
  anyone installs a build they intend to keep. Changing them later orphans data and creates a second "player" on
  the server. So M1.1 is the first coding slice.
- **Security and data shape early in M2.** Custom headers, encrypted credentials (M2.1) and extended metadata
  (M2.2) are prerequisites for later rows: proxied users cannot use the app at all without headers; ListenBrainz,
  Wrapped, the format line and the discography split need the new columns. Each carries a Room migration, and
  migrations are cheaper before more features depend on the schema.
- **Discover arrives with content.** The Discover tab ships in M3.2 with Smart Shuffle and Stations rather than as an
  empty M1 placeholder.
- **Play-event recording starts in M3.1** (with ListenBrainz), long before Wrapped (M3.6), so there is history to show.
- **Crossfade is demoted** to a product decision (M3.8): Media3 has no built-in crossfade, iOS defaults it to off,
  and building it risks the single-player gapless window.

## Standard verification for every slice

- `./gradlew :app:assembleDebug`, `:app:testDebugUnitTest`, `:app:ktlintCheck :app:lintDebug` (or the Nix `gradle`
  equivalents); `:app:ktlintFormat` before any commit. `:app:assembleRelease` only when build config, R8 or
  dependencies change.
- New behaviour has a test that fails without the change.
- `:app:recordRoborazziDebug` for any UI slice; review the renders at normal and 2× font scale.
- Emulator: Pixel-class AVD, API 35 and API 37 images (the default devshell has platform 37).
- No new dependency without a version and licence check; never anything from `com.google.android.gms` or Firebase.

---

## Milestone 0: establish and protect the baseline

**User-visible outcome:** none. Same app, verified and documented.

**Status (10 Nov 2026):** done by this audit.

| Item | Result |
|---|---|
| `./gradlew :app:assembleDebug` | Passed (JAVA_HOME = Android Studio JBR 25.0.4, Gradle wrapper 9.7.1, AGP 9.4.1, Kotlin 2.3.10) |
| `./gradlew :app:testDebugUnitTest` | Passed: 113 suites, 1,071 tests, 0 failures, 0 skipped |
| `./gradlew :app:ktlintCheck :app:lintDebug` | Passed (lint `warningsAsErrors = true`) |
| Working tree after the runs | Clean (no generated `gradle-daemon-jvm.properties`, no toolchain changes) |
| Integration tests | Not run (need the `nix develop .#integration` servers) |

- **Retained:** everything.
- **Extend:** docs only (`CLAUDE.md`, `docs/FEATURE_PARITY.md`, this file).
- **Data model:** none.
- **Tests:** the existing suite is the baseline.
- **Emulator checks:** optional smoke test (install the debug APK, add the Navidrome demo, play an album).
- **GrapheneOS checks:** record the baseline on device: install, add a server, play, lock-screen controls, download one album, airplane mode.
- **Dependencies:** none.
- **Known risks:** the local machine has no system JDK and uses Android Studio's JBR 25. CI uses Nix (JDK 21). If a
  JDK-25-only failure ever appears, reproduce under `nix develop` before acting.
- **Follow-ups for the first docs pass in M1:** AGENTS.md says "DOM XML parsing" (it is SAX) and refers to `.forgejo/`, which does not exist.
- **Completion criteria:** met: green build, tests and lint, three planning docs, no source changes.

---

## Milestone 1: mækster identity and Android app shell

**User-visible outcome:** the app installs as **mækster** next to Subtracks, opens on a branded welcome screen, and has
a Material 3 bottom navigation (Home, Library, Search) with Settings in the top bar. The mini-player sits above the
navigation bar, Now Playing opens from it, and the app uses mækster colours, type and spacing in light, dark and
artwork-tinted surfaces. No domain-layer rewrite.

**Retain unchanged:** data, sync, Room, queue, playback service and controller, downloads, Koin wiring, the `*Route`/`*Screen` split.

**Extend:** `st:ui/theme/*` (becomes `MaeksterTheme`), `st:ui/AppRoot.kt` (shell), `st:ui/library/LibraryScreen.kt`
(split into hub plus list routes), `st:ui/settings/SettingsScreen.kt` (grouped sub-pages), `st:UserAgent.kt` and
`SubsonicClient.CLIENT` (brand constant), resources (`app_name`, launcher icons, notification small icon).

**Wrap behind a mækster abstraction:** `MaeksterBrand` (name, client ID, User-Agent, repository URL) replaces the
string literals; `MaeksterTheme`, `MaeksterSpacing`, `MaeksterShapes`, `MaeksterType`.

**Redesign at the UI layer only:** navigation shell, Library landing, Settings grouping, mini-player chrome.

### Slices

| Slice | Content | Likely files | Tests |
|---|---|---|---|
| **M1.1 Identity** | `applicationId` `io.github.marcus_gt.maekster` (D1), launcher label "mækster", adaptive icon and monochrome icon from `Branding/logo/svg/maekster-mark*.svg` on Ink `#0E0E0E`, notification small icon from the mono mark, `MaeksterBrand` constant used for User-Agent and Subsonic `c=`, nightly suffix kept. Remove the 27 inherited partial locales (D14: English only). Keep the Kotlin namespace `com.subtracks` (D2, issue #1) | `app/build.gradle.kts` (`applicationId`; `generateLocaleConfig`), `res/mipmap-*`, `res/drawable/ic_launcher_*`, `res/values/strings.xml` (`app_name`), `res/values-<locale>/strings.xml` and `res/resources.properties` (deleted), `st:UserAgent.kt`, `st:data/source/subsonic/SubsonicClient.kt` | `UserAgentTest` and `SubsonicClientTest` assert the new client name; `StringResourcesTest` updated for English only; `MetadataConstraintsTest` updated if metadata changes |
| **M1.2 Design tokens and theme** | `MaeksterTheme` with light and dark `ColorScheme`s derived from brand tokens (accent light `#D96B1F` / dark `#FF8C4B`, Ink, Paper); System/Light/Dark preference (DataStore); Material You optional toggle, off by default (D3); spacing (4/8/12/16/20/24/32/48, section 32), shapes (4/6/8/12/pill), typography roles mirroring the iOS token table on the system font (D4; Lexend, if used at all, only for onboarding and Wrapped display text); artwork surfaces always dark using the existing `ArtworkTheme` with iOS luminance caps; `EmptyState` and skeleton components | `st:ui/theme/*`, `st:ui/components/Components.kt`, `st:data/prefs/UserPreferences.kt` | `ThemeTest`, `ArtworkColorsTest` (luminance ≤ 0.1, white ≥ 7:1), screenshot set (light, dark, artwork, 2× font) |
| **M1.3 Navigation shell** | Onboarding welcome; `NavigationBar` with Home, Library, Search (Discover added in M3.2); per-tab back stacks with state restoration; reselect pops to root; Settings top-bar action on every root; mini-player above the bar; the Now Playing overlay kept; snackbar host for `UiMessage` | `st:ui/AppRoot.kt`, new `st:ui/onboarding/*`, `st:ui/home/HomeScreen.kt` (extracted from the pager), `st:di/AppModule.kt` (message flow instead of Toast) | `SourceSwitchResetTest`, `NowPlayingRouteTest`, a new shell navigation test (tab switch keeps stacks), onboarding screenshot |
| **M1.4 Library hub and Settings grouping** | A Library landing list (Playlists, Albums, Artists; Songs, Genres, Favorites and Downloads arrive in M2); the existing list content as routes; sort and filter in a bottom sheet; Settings split into Server, Application, Playback, Storage, Integrations (empty until M3), About; the server switcher list | `st:ui/library/LibraryScreen.kt`, `AlbumsContent.kt`, `ArtistsContent.kt`, `PlaylistsContent.kt`, `st:ui/settings/*` | `LibraryScreenTest`, `LibraryOptionsTest`, `SettingsScreenTest` updated; every existing setting is still reachable |

**M1.1 status (10 Nov 2026):** done. Build, unit tests, ktlint and lint pass; the emulator
and device checks are still open. Deviation: the launcher icons live in `drawable` and `drawable-anydpi-v26`, not
`mipmap`, because they are all vectors (no bitmap density matters) and `mipmap` folders accept no vector or layer-list
XML (issue #4 moves them back with rendered PNGs). The Ko-fi "Support the developer" row became a Credits line
(Subtracks, Minidisc, Cassette). Left for later: `README.md`, `metadata/` store listings and `.github/FUNDING.yml` still describe Subtracks (M4.11).

**M1.2 status (11 Nov 2026):** done. `MaeksterTheme` (System/Light/Dark, optional Material You),
colour, spacing, shape and text-role tokens, `SkeletonBlock`, and the Settings "Appearance" section are in. Deviations from
the slice text: (1) the playing track's artwork no longer tints the whole app. Subtracks wrapped every screen in
`ArtworkTheme` and tinted the Library header, tab bar and search button itself, which would have overridden the Light and
Dark choice. Now only artwork-led surfaces are tinted and always dark (as on iOS); the tab screens are neutral Ink or
Paper with the orange accent. (2) Always-dark screens request white status bar icons (`WhiteStatusBarIcons`) because the
light theme gives dark ones. (3) No Lexend: system font only (D4). (4) Control sizes are 40/48/56dp, not the iOS 36/44/52, to
keep Android touch targets. Known gaps left for M1.3/M1.4: cover corners are still 2dp (iOS uses 8), the 3-button
navigation bar icons are dark over the dark mini-player in the light theme, and `LibraryScreen` still carries an unused
artwork parameter. Light-theme `onPrimary` is white on orange (about 3.5:1, large text only, as on iOS), to revisit in M4.9.
The screenshot set (light, dark, artwork, 2x font) is not recorded yet.

**M1.3 status (11 Nov 2026):** done. Welcome screen before the server form; the compact Material 3
`ShortNavigationBar` (labels on every tab, accent on the selected one) with Home, Library and Search as nested graphs with saved and restored stacks; reselect pops to the root; back from a tab root
goes to Home, then exits; Settings action on every tab root; mini-player above the bar; the Now Playing overlay unchanged;
`UiMessages` queue with a snackbar host replaces every Toast. The Library tab is the old pager without its Home page, with
the section strip moved to the top (the hub is M1.4). Deviations: the welcome copy is taken from the iOS onboarding (same
product); "Use Local Files" is not offered (no local music on Android yet). Not done: the onboarding screenshot, scroll to
top on reselecting a tab root, and the offline-mode button on Home (it lives on the Library tab only). Rotation and process
death ("Don't keep activities") were not tested on a device yet.

The bar hides while you scroll towards the end of a screen and returns when you scroll back, open a screen, switch tabs or
close the keyboard; the mini-player and snackbars follow it. It never hides for TalkBack users. This is the Android
hide-on-scroll pattern rather than the iOS tab bar minimising into floating pills, which would read as an iOS imitation
(decided with the user on 11 Nov 2026).

**M1.4 status (11 Nov 2026):** done, in two parts. Part 1, the Library hub: the
Library tab opens on a landing list (Playlists, Albums, Artists, in the iOS order, with accent icon tiles); each opens its own
list screen with back, list search and the sort and filter bottom sheet in its top bar, replacing the pager, the section
strip and the floating options button. Offline, the landing list hides sections with no downloads, using the downloaded
lists' own filters. The stored pager tab preference is gone. Part 2, the Settings grouping: the main page keeps the server
switcher list and About, with rows for Application (offline mode, theme, Material You, sort names, troubleshooting logs),
Playback (scrobbling, keep screen on, streaming quality, ReplayGain) and Storage (downloads, library sync), each its own
`settings/{page}` route. Every existing setting is reachable (`SettingsScreenTest.everySettingIsOnItsPage`). Deviation:
Integrations is not shown yet, since an empty page helps no one; it joins with its first integration in M3. Not done: the
settings screenshots in light and at 2x font, and checking offline mode, list search and light mode on a device.

- **Data model and migrations:** none in Room. One new DataStore key (theme). A new `applicationId` means a fresh install
  with no import from Subtracks (D1): the user re-adds servers and the library resyncs.
- **Manual emulator checks:** cold start without a server shows the welcome; add the Navidrome demo; tabs keep their
  stacks across rotation and process death (Developer options → "Don't keep activities"); the mini-player never
  overlaps the bar; system back from a tab root goes to Home, then exits; light, dark and system theme switching;
  2× font scale.
- **GrapheneOS / physical checks:** install side by side with Subtracks; launcher icon (adaptive and themed
  monochrome); notification icon; edge-to-edge with gesture and 3-button navigation.
- **Dependencies:** none new. Brand SVG → VectorDrawable conversion (Android Studio's importer). If Lexend is
  used for display text (D4), bundle the OFL font and add `OFL.txt` to licences.
- **Known risks:** the shell rewrite touches `AppRoot.kt` (874 lines) and the inset logic. Mitigate by keeping the
  overlay and mini-player code paths intact and moving only the `NavHost` structure. Predictive back stays disabled
  (`enableOnBackInvokedCallback="false"`) until M4.1. Release CI (`.github/workflows/release.yml`) still targets the
  Subtracks Play listing and secrets: disable or retarget it in M1.1 so nothing can publish under the old ID.
- **Completion criteria:** a build installs as mækster with its own ID and icon; the Subsonic server sees client
  `maekster`; three-tab navigation with per-tab stacks; light and dark themes; all pre-existing tests pass (updated
  only where strings or structure changed); new screenshot set reviewed; no Room migration.

---

## Milestone 2: core daily-player parity

**User-visible outcome:** everything a daily listener uses on iOS works on Android: proxied and secure server setup,
browsing (including Songs, Genres, Labels and Favorites), hearts, richer album and artist pages, a ranked search,
an iOS-equivalent player and queue, synced lyrics, playlist editing, downloads and storage management, and reliable
restoration.

**Retain unchanged:** `SyncService` streaming and prune, `PendingServerActionSink`, `QueueRepository` resolution,
the bounded window, the Feistel shuffle, `Scrobbler` policy, ReplayGain at the sink, `DownloadRepository` engine.

**Extend:** `SubsonicXml`/`SubsonicSource` (more fields and write calls), `Models.kt` plus migrations,
`LibraryDao`/`LibraryPaging` (new queries and indices), `PlaybackController` (recovery and queue source label),
`SourceRepository` (credential store, headers), `PlaybackService` (heart icon, swipe-away decision).

**Wrap behind interfaces:** `CredentialStore`, `HeaderPolicy` (origin-scoped OkHttp interceptor), `LyricsProvider`,
`PlaylistWriter`.

**Eventually replace:** the plaintext `subsonic_sources.password` column (migrated to ciphertext);
Toast-based messaging (snackbars, M1.3).

### Slices

| Slice | Content | Likely files | Data model / migration | Tests |
|---|---|---|---|---|
| **M2.1 Secure server foundation** | Keystore-encrypted password; custom headers per server (validated), origin-scoped for API, Coil, Media3 and `DownloadManager`; redacted logging; typed connection errors; HTTP warning; default scheme `https://` when omitted (D5); a server whose password cannot be decrypted is marked "needs sign-in" and prompts for it | `st:data/repo/SourceRepository.kt`, new `st:data/security/*`, `st:SubtracksApp.kt`, `st:playback/PlaybackService.kt`, `st:data/download/SystemDownloadEngine.kt`, `st:log/Log.kt`, `st:ui/settings/AddSource*` | Migration: add `subsonic_sources.passwordCiphertext` and a `source_headers` table (FK cascade); move and clear the plaintext; `MigrationsTest` step | Interceptor origin tests (including redirects to another host), MockWebServer header assertions, migration test, log redaction test, `AddSourceViewModelTest` error mapping |
| **M2.2 Extended metadata** | Sync MBIDs, suffix, bit rate, bit depth, sample rate, channels, record labels, release types, compilation flag | `st:data/source/subsonic/SubsonicXml.kt`, `SubsonicSource.kt`, `st:data/model/Models.kt`, `st:data/sync/SyncService.kt` | Nullable columns on `songs` and `albums`; `album_labels` and `album_release_types` child tables | `SubsonicXmlTest` fixtures (Navidrome OpenSubsonic and classic gonic), `SyncServiceTest`, integration on all four servers, `queryTiming` |
| **M2.3 Hearts and Favorites** | Heart iconography everywhere (menus, player, notification); a Favorites page (songs, albums, artists) in the Library hub | `st:ui/components/ItemContextMenu.kt`, `NowPlayingScreen.kt`, `PlaybackService.kt`, new `st:ui/library/FavoritesScreen.kt` | None | `ItemContextMenuStarTest` updated, Favorites screenshot, notification button state test |
| **M2.4 Browse parity** | Songs list; Genres and Labels lists → album collections; letter rail for albums, artists and songs; album page chips and format badge; artist discography split by release type with per-section Play/Shuffle; playlist summary footer | `st:ui/library/*`, `st:data/db/LibraryDao.kt`, `LibraryPaging.kt` | Indices on `songs` for title/artist/added sorts; letter-bucket queries | `LibraryDaoTest`, `ArtistDetailScreenTest`, screenshots, `queryTiming` with 200k songs |
| **M2.5 Home parity** | "Your playlists" first (by `changed`, with an "Updated …" line); Favorite albums; configurable sections (show, hide, reorder; carousel or grid); pins per server | `st:ui/home/*`, `st:data/prefs/UserPreferences.kt`, new `pins` DAO | `pins` table (sourceId FK cascade, kind, refId, order) | `LibraryHomeDaoTest`, `HomeViewModelTest`, `UserPreferencesTest` layout codec, pin cascade test |
| **M2.6 Search parity** | Scope chips; exact > prefix > contains ranking; top result; recent searches per server | `st:ui/search/*`, `st:data/db/LibraryDao.kt` `search_history` Room table per source, FK cascade on server delete (D6) | Ranking tests ported from iOS `LibrarySearchExperienceTests` cases; `SearchViewModelTest` |
| **M2.7 Player and queue parity** | mækster Now Playing (heart, accent artist line, format and source line, "Playing from"); one continuous queue list with Clear upcoming and snackbar Undo; explicit waiting states (700 ms delay), network-regain retry, bounded skip of failing items; persisted queue-source label; optional share sheet | `st:ui/playback/*`, `st:playback/PlaybackController.kt`, `st:data/db/QueueDao.kt` | `playback_cursor.sourceLabel` (nullable) | `PlaybackControllerTest` with `FakePlayer` for error, regain and pause-during-recovery; `QueueViewModelTest`; `NowPlayingTapsTest` |
| **M2.8 Lyrics** | A lyrics pane (synced scroll, tap to seek), a current-line preview, sources Auto/Server/LRCLIB, timing offset, cache, LRCLIB privacy disclosure | new `st:data/lyrics/*`, `st:ui/playback/LyricsPane.kt`, settings | `lyrics_cache` (sourceId, songId, provider, payload, fetchedAt) | LRC parser tests, provider fallback with fakes, MockWebServer LRCLIB request shape, offline cache test |
| **M2.9 Playlist editing** | Add to playlist (sheet with search and recent destinations), create, rename, remove and reorder, save queue as playlist; failure keeps the draft; retry appends only missing songs | new `st:data/repo/PlaylistRepository.kt`, `SubsonicSource.kt` (write calls), `st:ui/library/PlaylistDetailScreen.kt`, new sheet | `playlist_destinations` recents per source | Unit tests on the append-intent logic; `SubsonicWriteIntegrationTest` extended on all four servers |
| **M2.10 Downloads and storage** | A Storage page (downloads by album and playlist, clear, quality, metered toggle), the existing scratch stream cache (D7: a persistent LRU replay cache is deferred to issue #2), artwork cache clear, library counts plus resync; Downloads screen states; offline Home card | `st:ui/downloads/*`, `st:ui/settings/*`, `st:data/repo/DownloadRepository.kt` | None | `DownloadRepositoryTest` shared-song removal, `DownloadsScreenTest` |
| **M2.11 Restoration and playback settings** | Swipe-away: keep playing if playing, stop only when paused (C11, D8); stream quality presets plus Custom; restore edge cases (deleted song, changed source) | `st:playback/PlaybackService.kt`, settings UI | None | `PlaybackControllerTest` restore cases; manual process-death test |

- **Manual emulator checks:** Navidrome behind a reverse proxy that requires a header (a local Caddy or nginx); streaming,
  art and downloads all succeed; turn the header off and confirm a clear auth error. Airplane mode mid-song (recovery,
  skip to downloaded). Process death during playback, then restore. Lyrics with Navidrome-embedded LRC and LRCLIB fallback.
  Playlist edits on all four integration servers.
- **GrapheneOS / physical checks:** Wi-Fi ↔ mobile handover mid-song (quality switch, no stall); Bluetooth headset and
  car controls; phone call interruption; screen-off playback for an hour (no doze kill); the notification heart; per-app
  network permission revoked (offline behaviour); downloads to app storage with the screen off.
- **Dependencies:** none new expected. JSON for LRCLIB through `org.json` (platform).
- **Known risks:** the M2.1 credential migration. If the Keystore key becomes unusable (device-to-device transfer,
  a Keystore reset, an interrupted migration) the stored password cannot be decrypted. Only the password is lost:
  the server is marked "needs sign-in" and the mirror, downloads, queue and outbox survive. The migration writes and
  verifies the ciphertext before clearing the plaintext. Also: header
  leakage through redirects (tests); M2.2 sync payload growth on large libraries (measure); M2.7 touches playback
  core (keep edits inside `PlaybackController` and covered by `FakePlayer` tests).
- **Completion criteria:** every M2 row in `FEATURE_PARITY.md` reads Present or Present but behaviour differs (documented);
  all integration tests pass on the four servers; the device checklist is signed off on a Pixel with GrapheneOS.

---

## Milestone 3: product differentiation

**User-visible outcome:** the mækster extras: a Discover tab with Smart Shuffle, stations and Instant Mix; endless
autoplay; ListenBrainz; fresh releases; local folders; AudioMuse moods and Song Path; Wrapped; internet radio.
Lidarr is out of scope (D11).

**Retain unchanged:** queue engine, sync, Room mirror rule (C1), playback service.

**Extend:** `Scrobbler` (fans out to ListenBrainz and play events), `QueueKind` (local items), `sources` (a Local kind).

**Wrap behind interfaces:** `ListenSink`, `RecommendationProvider`, `MixBuilder`, `AudioMuseClient`,
`LocalLibraryScanner`. Every client has a fake for tests, and none shares the server OkHttp interceptors.

### Slices

| Slice | Content | Data model | Key tests | Notes |
|---|---|---|---|---|
| **M3.1 ListenBrainz scrobbling and play events** | Token setup and validation; now-playing and listens; offline queue; `play_events` recorded per source from scrobble submissions | `pending_listens`, `play_events` (indexed by time and source) | Fake server: submit, retry, invalid token; event recording only on a real submission | Token through `CredentialStore` |
| **M3.2 Discover v1: Smart Shuffle, Instant Mix, stations, auto-extend** | The Discover tab appears; Smart Shuffle (local, offline-capable); artist stations; Instant Mix (`getSimilarSongs2` plus local fallback); "Autoplay similar" toggle; "Similar to …" labels; Queue Builder (local) | Up-next or context tail entries; `playback_cursor.extensionStart` | Trigger threshold, disable on repeat or shuffle, truncation; bounded batch sizes | D9: on-demand similarity calls allowed as explicit actions, cached in Room, with a local fallback offline |
| **M3.3 Recommendations and fresh releases** | Fresh releases for library artists (MusicBrainz), Similar Artists shelves, optional external artist images | `recommendation_cache` with a TTL | Rate limiting (1 req/s), offline served from the cache, User-Agent from `MaeksterBrand` | D10: every third-party lookup opt-in per integration and disclosed in Privacy |
| **M3.4 Local folders** | SAF folder picker, persisted permissions, foreground rescan, local landing, local-only onboarding, local items in the queue | A `sources` Local kind; `local_files` (uri, folderId, relativePath, mtime, tags) | Scanner tests on a fake document tree; revoke and missing-file handling | GrapheneOS Storage Scopes compatible by design |
| **M3.5 AudioMuse-AI** | Per-server AudioMuse URL and token; Moods on Discover (mood radios); Song Path | `mood_scores` cache | Fake server; Song Path does not auto-extend | |
| **M3.6 Wrapped** | Year and all-time recap from `play_events` (optionally the ListenBrainz history); story pager; share card | Aggregation queries only | Aggregation, streak and insight tests ported from the iOS fixtures | |
| ~~M3.7 Lidarr~~ | Dropped (D11): legacy from Mixtape, unused in the iOS product | n/a | n/a | Slice number kept so later IDs stay stable |
| **M3.8 Crossfade (prototype, D15)** | 0–12 s, off by default, disabled on gapless albums | None | Gapless unaffected; transition timing | Prototype first; drop if it destabilises the window |
| **M3.9 Internet radio** | Station list (synced into Room), live playback outside the queue, the queue resumes afterwards | `radio_stations` | Controller live-mode tests | |

- **Manual emulator checks:** Discover offline (only Smart Shuffle from downloads is visible); ListenBrainz listens appear
  on the website; AudioMuse Song Path end to end; local folder on emulated
  storage; Wrapped with seeded events.
- **GrapheneOS / physical checks:** SAF on real storage and on a USB/SD document provider; Storage Scopes; network
  permission off for third-party calls; long ListenBrainz offline backlog replay.
- **Dependencies:** none new expected (OkHttp and `org.json`). Any JSON library addition needs justification.
- **Known risks:** third-party rate limits and outages (cache and degrade); a schema change for the local source kind (M3.4)
  touches queue resolution; feature creep. Keep each integration hidden until configured.
- **Completion criteria:** each shipped slice's matrix row reaches Present; every integration can be fully disabled and
  then makes zero requests; `PRIVACY.md` lists every third-party host and the data sent.

---

## Milestone 4: Android-specific excellence

Each item is assessed; items marked **decision** may never ship.

| Slice | Item | Assessment | Recommendation |
|---|---|---|---|
| M4.1 | Predictive back | Currently disabled in the manifest. Needs the shell from M1.3 and the Now Playing overlay to use `PredictiveBackHandler` | Do it; low risk once the shell is stable |
| M4.2 | Adaptive layouts | The iOS app is phone-portrait only. Foldables and tablets benefit from list-detail (library ↔ album) and a side-by-side player and queue | Do a medium/expanded-width pass with window size classes; phone-first stays the target |
| M4.3 | Widgets | Not on iOS. A Glance now-playing widget is useful | **Decision**; nice to have |
| M4.4 | Shortcuts and automation | iOS has App Intents and Siri. Android: launcher shortcuts (Smart Shuffle, Resume, Downloads), pinned shortcuts to playlists, `MEDIA_PLAY_FROM_SEARCH` | Do it; it is the Android translation of the App Intents work |
| M4.5 | Android Auto | Needs `MediaLibraryService` with a browse tree (the service is `MediaSessionService` today). Works without Play Services only on Android Automotive or AAOS; the phone projection app is a Google app | **Decision**; architecturally clean (browse from Room) but needs testing hardware |
| M4.6 | Media output routing | `MediaRouter2.showSystemOutputSwitcher()` on API 34+ | Do it (small) |
| M4.7 | Casting / UPnP / DLNA | Google Cast requires Play Services, so it could only be an optional, isolated flavour. UPnP/DLNA is a large standalone feature | Wanted, deferred (D16; issue #3). Pick Cast flavour vs UPnP/DLNA when the work starts; ordinary playback must never depend on Play Services |
| M4.8 | GrapheneOS test plan | A checklist: no sandboxed Play, network permission toggle, Storage Scopes, hardened memory allocator with bundled SQLite and Media3 natives, background restrictions | Do it, and run it each milestone |
| M4.9 | Accessibility pass | TalkBack across every flow, 2× font, switch access, contrast | Do it |
| M4.10 | Battery and background | Foreground service type `mediaPlayback` declared; `AutoSync` is foreground-only; downloads through `DownloadManager`. Check behaviour under battery saver and "Restricted" app standby | Verify and document |
| M4.11 | Distribution | F-Droid (reproducible builds already considered: `dependenciesInfo.includeInApk = false`), Obtainium from GitHub releases, optionally Play | **Decision**; F-Droid and Obtainium first. Requires a new signing key, fingerprints in `signing/README.md`, and new metadata under `metadata/` |

- **Data model:** none, except M4.5 (none: the browse tree reads Room).
- **Known risks:** M4.5 and M4.7 can pull in proprietary components; keep any such flavour separate and optional.
- **Completion criteria:** per item, as agreed when it is accepted.

---

## Product decisions

All decided on 10 November 2026.

| ID | Decision | Outcome |
|---|---|---|
| D1 | Android `applicationId`, and whether to offer an import from an existing Subtracks install | **Agreed.** `io.github.marcus_gt.maekster` (mirrors iOS `io.github.marcus-gt.maekster`; hyphen not allowed) with `.nightly` for debug. No import: the user re-adds servers and the library resyncs |
| D2 | Rename Kotlin packages and namespace `com.subtracks` → `…maekster` | **Later.** One isolated, mechanical change set (issue #1) |
| D3 | Material You dynamic colour | **Agreed.** Off by default (the mækster palette is the identity); optional toggle |
| D4 | Typography | **Agreed.** System font for UI; Lexend only for onboarding and Wrapped display text, if at all |
| D5 | Default URL scheme when omitted | **Agreed.** `https://`, plus the HTTP warning |
| D6 | Search history storage | **Agreed.** Room per source (cascade on server delete) |
| D7 | Persistent stream cache (iOS-style LRU replay cache) | **Agreed.** Keep the scratch cache for now; persistent cache after M2 (issue #2) |
| D8 | Swipe app away from recents | **Agreed.** Keep playing if playing; stop only when paused |
| D9 | On-demand server calls for Instant Mix, similar songs and lyrics | **Agreed.** Browsing stays local-only; explicit actions may call the server, cached in Room, with offline fallbacks (see `FEATURE_PARITY.md` C1 and principle 5) |
| D10 | Third-party lookups (external artist images, MusicBrainz fresh releases) | **Agreed.** Opt-in per integration, disclosed in Privacy |
| D11 | Lidarr | **Out of scope.** Legacy from Mixtape and unused in the iOS product; M3.7 dropped |
| D12 | ReplayGain default | **Agreed.** Android Auto mode, on by default |
| D13 | Multi-server UI | **Keep.** The existing Android server list and switching stay; the feature is wanted on iOS later too |
| D14 | Translations | **English only.** No translation workflow; the 27 inherited partial locales are removed in M1.1 |
| D15 | Crossfade | **Agreed.** Prototype in M3.8, drop if it compromises gapless |
| D16 | Casting | **Wanted, later.** Deferred to M4.7 (issue #3); ordinary playback must never depend on Play Services |

## Smallest sensible first implementation slice

**M1.1 Identity**: change `applicationId` to `io.github.marcus_gt.maekster`, `app_name` "mækster", adaptive and
monochrome launcher icons and the notification icon from the brand mark, and a `MaeksterBrand` constant feeding the
User-Agent and Subsonic `c=`. Remove the inherited partial locales (D14). Disable or retarget `release.yml` so it cannot
publish to the Subtracks listing. No Room changes, no package rename, no navigation change. Verification:
`UserAgentTest` and `SubsonicClientTest` updated to assert the new client name (each fails before the change);
`StringResourcesTest` updated; install side by side with Subtracks on the emulator; the server's player list shows
"maekster".
