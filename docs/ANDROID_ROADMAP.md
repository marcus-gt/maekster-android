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
| **M2.9 Playlist editing** | Add to playlist (sheet with search and recent destinations), create, rename, remove and reorder, save queue as playlist; failure keeps the draft; retry appends only missing songs; quick playlists (long-press a track heart for a menu of chosen playlists, adding also favorites the song by default, "Choose Quick Playlists…", chosen per server, managed under Settings > Application) | new `st:data/repo/PlaylistRepository.kt`, `SubsonicSource.kt` (write calls), `st:ui/library/PlaylistDetailScreen.kt`, new sheet | `playlist_destinations` recents per source | Unit tests on the append-intent logic; `SubsonicWriteIntegrationTest` extended on all four servers |
| **M2.10 Downloads and storage** | A Storage page (downloads by album and playlist, clear, quality, metered toggle), the existing scratch stream cache (D7: a persistent LRU replay cache is deferred to issue #2), artwork cache clear, library counts plus resync; Downloads screen states; offline Home card | `st:ui/downloads/*`, `st:ui/settings/*`, `st:data/repo/DownloadRepository.kt` | None | `DownloadRepositoryTest` shared-song removal, `DownloadsScreenTest` |
| **M2.11 Restoration and playback settings** | Swipe-away: keep playing if playing, stop only when paused (C11, D8); stream quality presets plus Custom; restore edge cases (deleted song, changed source) | `st:playback/PlaybackService.kt`, settings UI | None | `PlaybackControllerTest` restore cases; manual process-death test |

**M2.1 status (11 Nov 2026):** in progress, in three parts. Part A, the encrypted password, is done: `CredentialStore` (Keystore AES-256-GCM, a fake for tests), schema 39 adds `subsonic_sources.passwordCiphertext`,
and `SourceRepository` seals new and edited passwords and moves old plaintext across at startup, verifying before it clears.
A password that no longer decrypts marks the server "needs sign-in" (a message, a note in the Settings server list, and the
edit form asks again). Checked on the emulator: an existing server migrated to a `v1:` ciphertext with the plaintext
cleared, and streaming still worked. The plaintext column stays (always empty) until a later table rebuild. Part B, custom
headers per server, is done: schema 40 adds `source_headers` (values sealed), the server form
has a Custom headers section with inline validation, and one `ServerHeaders` holder feeds a network interceptor on the API,
Coil and Media3 clients, so only requests to the server's own origin get them, redirects included (MockWebServer tests).
Downloads pass them to DownloadManager, which re-sends them on any redirect, including to another host; a download engine on
OkHttp would close that gap. Not checked: a real header-protected server (the Caddy or nginx check below). Part C is done: logs also redact `apiKey` and `token` parameters, `Bearer`/`Basic` credentials and, wherever they appear,
the active server's password and header values (`Log.setSecrets`); `connectionErrorMessage` turns unknown hosts, refused
connections, timeouts, untrusted certificates, failed TLS handshakes and HTTP 401/403/404/5xx into messages that say what
to check, for both Test connection and sync; the server form warns under a plain `http://` address; and an address typed
without a scheme now gets `https://` (D5). With part C, M2.1 is complete apart from the device checks.

**M2.2 status (11 Nov 2026):** done. Schema 41 adds MusicBrainz IDs (artists, albums, songs),
song `suffix`, `bitRate`, `bitDepth`, `samplingRate` and `channelCount` (0 reads as unknown), album `compilation` (null when
the server does not say), and the positional child tables `album_labels` and `album_release_types`, written with the album
like `album_artists` and removed with it. Release types arrive as element text, so the XML entity reader now passes child
text too; they are stored lowercase because tags vary ("album" and "Album"). Checked against a real Navidrome library
(18,569 songs): every song has its format details, 70% of albums an MBID, 1,726 albums labels, and the full sync took 10 s
as before. Navidrome sends `isCompilation` only when true, so the UI should read null as "not a compilation". Not run: the
integration tests on gonic, LMS and Nextcloud Music, and the 200k-song `queryTiming` measurement.

**M2.3 status (11 Nov 2026):** done. Hearts replace stars in item menus, Now Playing, the artist
page, the Home section and the media notification (Media3 `ICON_HEART_*`), and the wording follows iOS ("Add to Favorites",
"Favorites"); the server's star API and the stored `starred` columns are unchanged. The Library landing list gains a Favorites
entry (`LibraryHubEntry`, since it is a page rather than a list) opening favorite songs, albums or artists, newest first, through
a switcher; songs play and shuffle as the favorites queue, and offline shows only downloaded favorites. It reuses the Home list
screen with a `favorites` request. iOS's letter index on Favorites is left for the M2.4 letter rail. Not done: the Favorites
screenshot.

**M2.4 status (11 Nov 2026):** in progress. Part A is done: every album and playlist track has a heart before its duration
that adds or removes the favorite, in the accent colour when set. iOS's long-press on that heart (quick playlists) is part of
M2.9. Part B is done: a Songs list in the Library (after Artists) sorts by title, artist, album or date added, filters by
favorites and downloads, searches, and offline shows downloaded songs. Each row has a heart. A tap plays the list exactly as
shown from that row, and the button shuffles it: a new `Library` queue kind stores the list's sort, filters and search as its
reference and reads the same SQL as the list, so nothing is copied. Schema 42 adds song indices for the title, artist and added
sorts. Checked on the emulator against the real library (`docs/handoffs/M2.4-songs.md`). Not run: offline with real
downloads, and the 200k-song `queryTiming` measurement.

**M2.4 Part C status (12 Nov 2026):** done. Genres and Labels open paged album collections from the Library hub,
with album counts, artwork, name/count sorting, in-list search and downloaded filtering. Membership and offline availability
come entirely from Room; genres include every song tag and labels include every album label, grouped case-insensitively
with distinct album counts. Collections show albums newest first. No schema or dependency change. Database and UI tests,
normal/2× font renders, build and lint pass. Pixel 9 API 37 verified genre sorting and genre/label → albums → album detail.
API 35 and offline with real downloads remain unverified; see `docs/handoffs/M2.4-genres-labels.md`. Part D is complete for Library lists (see below).

**M2.4 Part D status (12 Nov 2026):** done for Library lists. Library Albums, Artists and Songs have local letter-bucket queries
and letter buttons for name/title sorts, respecting direction, filters, downloads and sort-name mode. The database returns
at most 27 bucket positions; lists remain paged, with Artists now also using placeholders. Query tests compare bucket positions
to actual list order, and an off-by-one mutation fails the test. On review, the Songs rail gained bottom clearance so the
shuffle button no longer covers a letter (with a test that fails without it). Build, 1,190 tests and lint pass; normal/2x
font rail renders reviewed. Pixel 9 API 37 verified jumps on all three lists, including a far jump on Songs. Large-library timing, API 35 and real offline downloads remain
unverified; Favorites/Home lists are unchanged. See `docs/handoffs/M2.4-letter-rail.md`.

**M2.4 Part E status (12 Nov 2026):** done. The album page shows "year · songs · length" with up to two format badges
(lossless in the lossless colour) and, below, up to three genre chips (its songs' tags, most tagged first) and the first record
label. Each chip opens the Part C collection, and offline the genre chips count only downloaded songs, so the collection still holds
the album. Room only; no schema or dependency change. DAO, UI and format tests (with a mutation check), build, 1,197 tests and
lint pass. Pixel 9 API 37 verified chips and badges on real albums and both chip kinds opening their collections. Offline with
real downloads, API 35, TalkBack and 2× font remain unverified; see `docs/handoffs/M2.4-album-chips.md`.

**M2.4 Part F status (12 Nov 2026):** done. The artist page splits its own releases into Albums, EPs, Singles, Live albums,
Compilations and Other releases (iOS mapping of the synced release types and compilation flag), then Appears on. Each shelf
folds and has Play (in order) and Shuffle. Shelves queue one album reference per release; Appears on uses a new
`ArtistAppearances` queue kind that plays only the artist's credited tracks. No copied queue, schema or dependency change. Model,
repository, controller and screen tests (with a mutation check), build, 1,204 tests and lint pass. Pixel 9 API 37 verified
Albums and Live albums shelves, shelf Play and folding on a real artist. EP/Single shelves on the emulator, offline, API 35,
TalkBack and `queryTiming` remain unverified; see `docs/handoffs/M2.4-discography.md`.

**M2.4 Part G status (12 Nov 2026):** done, which completes M2.4. A playlist shows "N songs · length" under its last track,
counted in Room over the rows the list shows (offline, the downloaded ones), and its header now gives the owner and "Updated …"
as on iOS. DAO and screen tests, build, 1,207 tests and lint pass; Pixel 9 API 37 verified header and footer on a real
playlist. Offline with real downloads, API 35 and TalkBack remain unverified; see `docs/handoffs/M2.4-playlist-footer.md`.
M2.4's 200k-song `queryTiming` measurement is still outstanding across its parts.

**M2.5 status (12 Nov 2026):** in progress. Part A is done: Home opens with "Your playlists" (up to 10, newest change first,
falling back to the creation date, as on iOS), large covers with an "Updated …" line and the song count; More opens the
Playlists list. DAO and screen tests (with a mutation check), build, 1,210 tests and lint pass; Pixel 9 API 37 verified the
shelf, More and opening a playlist. See `docs/handoffs/M2.5-your-playlists.md`.
Part B adds Favorite albums after Your playlists: up to 10 locally mirrored favorites, newest star first, with album menus
and More opening a paged favorites list. Build, full unit suite, lint and screenshot recording pass; normal/2× font UI tests
include a mutation check. See `docs/handoffs/M2.5-favorite-albums.md`.
Part C adds show/hide/reorder for existing online Home sections from Application > Home sections, persisted in DataStore,
with Reset, Save and Cancel. Default order and offline downloaded shelves are unchanged. Build, full unit suite and lint
pass; the order test fails when Home ignores the saved layout. See `docs/handoffs/M2.5-home-sections.md`.
Part D adds per-shelf carousel/grid choices for the six online album/playlist cover shelves in the Home sections editor.
Choices persist in DataStore and older saved layouts keep carousels. Grids wrap bounded Home data; playlist dates/counts,
card actions and More remain available. Build, full unit suite, lint and screenshot recording pass; grid and editor
normal/2× renders reviewed, with a mutation check for the grid. See `docs/handoffs/M2.5-home-grid.md`.
Part E adds pin persistence: Room schema 43, typed album/playlist/artist references scoped to server, ordered bounded reads,
duplicate-safe pin/unpin and source-deletion cascade. This is the storage foundation only; context-menu pin actions and
Home pins were left for Part F. See `docs/handoffs/M2.5-pin-storage.md`.
Part F adds shared Pin/Unpin menu actions for albums, playlists and artists and a paged mixed Pinned carousel on Home.
Metadata resolves only from Room; missing refs stay stored and reappear when mirrored again. The section can be hidden/reordered.
Build, full unit suite, lint and focused screenshot recording pass; normal/2× shelf renders reviewed and menu mutation checked.
Pin grid/order management and offline shelf visibility remain unfinished. See `docs/handoffs/M2.5-pins-ui.md`.
Part G lets Pinned use the carousel/grid choice like the cover shelves. The grid is rows of the outer Home list,
so a long pin list stays paged and only nearby rows compose; columns fit the width. Build, 1,237 tests and lint pass;
normal/2× renders reviewed and a forced-carousel mutation fails the grid tests. Three order-sensitive paged UI fixtures
now use replaying flows. Pin ordering and offline Pinned remain.
See `docs/handoffs/M2.5-pin-grid.md`.
Part H puts Pinned first on offline Home, in pin order, with the same carousel/grid and hidden choices, showing only
pins that can play offline (an album or playlist with a completed download, an artist with downloaded songs). iOS
shows every pin offline; Android keeps its downloaded-only offline rule. iOS has no pin reorder screen, so none was
added. DAO and UI tests with mutation checks, build, full unit suite and lint pass. M2.5 now lacks only Artist picks,
which needs M3.2 inputs. See `docs/handoffs/M2.5-offline-pins.md`.

**M2.6 status (12 Nov 2026):** complete. Part A is done: search results in each type rank by title, exact, then
prefix, then contains, then a match only in another field, in SQL ahead of the 50-result limit, so an exact title is
never cut off. Matching is unchanged (FTS5 trigram). The rank folds ASCII case only, not diacritics (iOS folds both).
DAO tests with a mutation check, build, full unit suite and lint pass. Next: scope chips and a top result (Part B),
then recent searches per server (Part C). See `docs/handoffs/M2.6-search-ranking.md`.
Part B is done: single-choice scopes (All, Songs, Albums, Artists, Playlists) replace the type toggles. All leads with
one Top result across types, then each type in order of its best match, five each with See all. Cross-type ranking
ports iOS's score with case and diacritic folding. Ranking and screen tests with mutation checks, build and lint pass.
See `docs/handoffs/M2.6-search-scopes.md`.
Part C is done: as on iOS, Search remembers the albums, artists and playlists opened from it (50 per server,
newest first) and lists them as Recent when the field is empty, with Clear behind a confirmation. Room schema 44
adds `search_history` (cascade on server delete, D6); names resolve from the mirror. DAO, ViewModel, screen and
migration tests with mutation checks. See `docs/handoffs/M2.6-search-history.md`.
Part D is done: offline Search results and Recent now show only completed downloads, using offline Library
availability rules and SQL filters before limits. Mode and download changes update the flows. The regression
failed before the fix; debug build, full unit suite, formatting and lint pass. No layout or schema change.
See `docs/handoffs/M2.6-offline-search.md`.

**M2.7 status (12 Nov 2026):** in progress. Part A is done: actual decoder encoding is now part of
`PlaybackState`, refreshed on player events, including encoding changes and unknown values; it clears when
there is no current item. This prepares the format line without guessing transcoding from synced metadata.
Controller regression with a failing mutation, debug build, full unit suite, formatting and lint pass.
See `docs/handoffs/M2.7-audio-encoding-state.md`.
Part B is done: Now Playing shows the loaded item's source (Streaming, Transcoding or Downloaded) and
actual decoder codec, bitrate and sample rate below the scrubber. Origin travels in media metadata across
the session, so current preferences/download status cannot mislabel a loaded item. Unknown details are
omitted; no source-file bit depth is claimed for decoded audio. Media-item/controller/UI tests, mutation,
full build/tests/lint and normal/2× font renders pass. See `docs/handoffs/M2.7-format-line.md`.
Part C is done: the existing manual Up next clear action now offers snackbar Undo, restoring queue
references, shuffle and cursor. Clearing a paused playing block stays paused; Undo restores its position.
Mutation checks, full build/tests/lint and normal/2× font renders pass.
See `docs/handoffs/M2.7-clear-up-next-undo.md`. Still open: clearing the context's upcoming tracks,
continuous queue presentation, persisted source labels and playback recovery.
Part D is done: end-to-start swipe removes queue rows through the existing position-based action and
snackbar Undo; duplicate songs stay distinct, including in shuffled context. Remove buttons and drag
handles remain, and swiping is disabled during drag. Gesture regressions fail with swiping disabled;
full build/tests/lint rerun passes after existing paged screenshot timeouts, and normal/2× font renders
are reviewed. See `docs/handoffs/M2.7-queue-swipe.md`.
Part E is done: queue rows form one continuous list with a single Playing from line in the top bar;
the manual Clear up next action is there too. No repeated context or Up next headings. Cursor opening
uses direct row indices; internal drag boundaries and bounded loading are retained. UI regression with
mutation, full build/tests/lint/recording and normal/2× font renders pass.
See `docs/handoffs/M2.7-continuous-queue.md`.
Part F is done at the repository layer: unshuffled Clear upcoming keeps history/current track by trimming
reference ranges, including manual-block boundaries. Queue rows and cursor update atomically; no songs
are resolved. Tests cover offsets, duplicates, position/mode preservation, no-ops, shuffle rejection and
a synthetic billion-track range, with an off-by-one mutation. Full build/tests/format/lint pass.
No UI action yet; shuffled truncation and controller/Undo/UI integration remain open.
See `docs/handoffs/M2.7-clear-upcoming-repository.md`.
Part G is done: unshuffled Clear upcoming is available in Queue options, with snackbar Undo only after
a successful controller edit. It keeps the playing source/position/pause state, trims the bounded player
window and restores context/manual ranges on Undo. Availability comes from the full queue snapshot;
shuffle and end-of-queue are guarded. Manual Clear up next remains in the menu. Mutation, controller/UI
regressions, full build/tests/lint/recording and normal/2× font renders pass.
See `docs/handoffs/M2.7-clear-upcoming-action.md`.
Part H is done: network playback errors arm one same-track retry at the saved position when the existing
network-availability flow notifies an available route. Pause, seek, stop, offline mode and stale source/track
checks prevent unwanted recovery; play intent is rechecked after async window loading. Refused requests
are not retried. No timer loop or automatic skipping. Mutation/seek regressions and full build/tests/lint
pass. See `docs/handoffs/M2.7-network-recovery.md`. Shuffled truncation, source labels, explicit waiting
states/700ms delay, bounded failing-item skips and remaining player styling are still open.
Part I is done: playback that should be running but is not reports Preparing (buffering at the track start),
Buffering or Reconnecting (an armed network retry for the same track), shown only once the wait passes 700 ms as
on iOS. Changing reason within one track wait keeps the delay; a new track restarts it; pause clears it. Now
Playing shows the status under the format line (polite live region) and the play control a spinner whose
description stays "Pause" with the status as state; the mini-player spinner follows too. The old 1 s buffering
delay is replaced. Virtual-time, controller and UI tests with mutations; full build/tests/lint pass.
See `docs/handoffs/M2.7-waiting-states.md`. Still open: shuffled truncation, persisted source labels, bounded
failing-item skips and remaining player styling.
Part J is done: player errors carry a typed failure. Item-specific ones (HTTP 404/410, a missing file, an
undecodable or unsupported stream) skip forward with the normal Next rules while playback is wanted, at most 5 in a
row, then pause with an explanation; a track that plays, or a user transport action, ends the run. Auth and server
errors never skip. One skip message per run, and no claim that a song was deleted (iOS needs a server not-found for
that; Android makes no request). Controller and classification tests with three mutation checks.
See `docs/handoffs/M2.7-unplayable-skip.md`. Still open: shuffled truncation, persisted source labels and
remaining player styling.
Part K adds source-label storage: nullable `playback_cursor.sourceLabel` (schema 45), passed to queue
replacement and retained by cursor edits. Existing queues migrate with no label; unlabeled replacement
clears the previous label. Controller, Undo and UI integration remain unfinished.
See `docs/handoffs/M2.7-source-label-storage.md`.
Part L captures single-reference source names on normal/shuffled playback start, retains them through
Undo and prefers the saved title on restoration. Mixed references and older queues retain the existing
local title fallback. Typed Library/Favorites/Downloads labels and mixed shelf origin labels remain.
See `docs/handoffs/M2.7-source-label-playback.md`.
Part M adds stable collection labels for Songs, Favorites and Downloads, resolved from resources
in Now Playing and Queue. New queues persist the collection kind; older collection queues use the
same resource-backed title. Mixed shelf origin labels, shuffled truncation and player styling remain.
See `docs/handoffs/M2.7-collection-labels.md`.
Part N gives artist discography shelves a persisted artist-origin title for normal and shuffled
playback, including requests waiting for connection. Album references and gain context are retained;
Undo keeps the title. Generic mixed references without an explicit origin retain their fallback.
See `docs/handoffs/M2.7-shelf-origin.md`. Shuffled truncation and player styling remain.
Part O accents the Now Playing artist credit with the artwork theme's primary color, matching
its active controls and retaining the established artist touch target. A new 2× font render
joins the existing normal player screenshot. See `docs/handoffs/M2.7-player-accent.md`.
Part P adds the bounded ShufflePrefix mapping foundation for shuffled truncation: original domain
and sparse removals retained, nullable forward/inverse lookups reject the discarded tail, repeated
truncation cannot regrow it. No queue persistence or user-facing action yet; shuffled Clear upcoming
remains disabled. See `docs/handoffs/M2.7-shuffle-prefix.md`.
Part Q persists sparse shuffle removals in schema 46, with queue references and the manual anchor
updated atomically; edited shuffle order now survives repository recreation. Corrupt removal data
falls back safely. See `docs/handoffs/M2.7-shuffle-removal-persistence.md`.
Decision (13 Nov): after shuffled Clear upcoming, turning shuffle off keeps retained tracks in
played order; it must not restore discarded tracks or materialize the retained queue.
Part R adds the strict versioned retained-prefix persistence format, preserving seed, original
domain, cutoff and sparse removals. Round-trip and malformed-data tests include a mutation check.
Runtime cutoff integration remains; see `docs/handoffs/M2.7-shuffle-prefix-codec.md`.
Part S adds source-scoped immutable queue order storage (schema 47): prefix plus reference
ranges/lengths, with cascading cleanup. DAO/migration tests include huge synthetic ranges and a
cleanup mutation. No runtime action yet; see `docs/handoffs/M2.7-stored-orders.md`.
Part T resolves Order references through stored prefixes, preserving original album context,
source scope, restart behavior and nested dependencies. Invalid records play nothing; queue
replacement prunes unreachable orders. Mapping mutation/full checks pass. The shuffled Clear
upcoming action was still disabled at that checkpoint; see `docs/handoffs/M2.7-order-resolution.md`.
Part U enables shuffled Clear upcoming using a persisted retained-order reference and identity shuffle
state (schema 48). Current track/time and manual-block boundaries survive; unshuffle keeps played
order, and Undo restores the exact sparse shuffle. Pre-fix regression, full build/tests/lint and
normal/2× UI renders pass. Emulator clear preserved the paused track/time; timed Undo was not
activated before expiry. See `docs/handoffs/M2.7-shuffled-clear-upcoming.md`.
M2.7 implementation review (13 Nov): required slice behavior is covered by Parts A–U. Queue
remains a separate Android route (documented platform interpretation); optional track sharing is
deferred. Device recovery/process-death, API 35/37 matrix and physical checks remain open; this is
not a device-validation sign-off.

**M2.8 status (13 Nov 2026):** in progress. Part A adds plain Kotlin Lyrics/LyricsLine values and
an LRC parser for repeated leading timestamps, millisecond fractions, signed provider offsets,
enhanced word-tag stripping, Unicode and stable equal-time ordering. Metadata and malformed
entries are ignored; no usable timed text returns null. Provider/cache/UI integration remains.
See `docs/handoffs/M2.8-lrc-parser.md`.
Part B adds SAX decoding for structured multilingual timed/plain lyrics and classic getLyrics
text (including embedded LRC). Provider offsets and stable timing order are retained; invalid timed
lines are skipped. It reuses the existing response/error and XXE protections. Network/cache/UI
wiring remains; see `docs/handoffs/M2.8-server-lyrics-decoding.md`.
Part C adds LyricsProvider and the source-scoped SubsonicLyricsProvider. Song-ID lookup is preferred;
empty/unsupported responses fall back to classic artist/title lookup. Auth, HTTP server errors,
malformed data, transport failures and cancellation do not trigger fallback. No DI/UI/cache wiring
yet; see `docs/handoffs/M2.8-server-lyrics-provider.md`.
Part D adds provider-specific Room storage (schema 49), versioned strict JSON payloads and typed
cache read/write/invalidate. Source deletion cascades; corrupt/unknown data is a cache miss.
Offline orchestration and UI were still pending at that checkpoint; see `docs/handoffs/M2.8-lyrics-cache.md`.
Part E adds cache-first Server/LRCLIB/Auto selection with explicit offline and opt-in inputs,
provider provenance, positive-only cache writes and cancellation-safe fallback. Auto prefers synced
server lyrics, then permitted external timings over server plain; cached disabled providers stay
excluded. DI/settings/UI and an actual LRCLIB client were still pending there; see `docs/handoffs/M2.8-lyrics-orchestration.md`.
Part F implements the isolated LRCLIB HTTPS provider: four-field metadata query and branded
identification, cancellable requests, sequential pacing, Retry-After cooldown with jitter, typed
errors and plain/LRC/instrumental decoding. Instrumental markers round-trip through the cache;
older payloads remain readable. No runtime/opt-in/settings/UI wiring yet; see `docs/handoffs/M2.8-lrclib-provider.md`.
Part G adds language/variant selection (locale, explicit choice, synced preference, ISO-3 and
script/region normalization) and precomputed overflow-safe lyric timing. Highlighting and seek use
one timeline; provider offset advances lyrics, listener correction delays them. This follows the
OpenSubsonic sign convention rather than the opposite sign in the iOS reference viewmodel.
See `docs/handoffs/M2.8-lyrics-selection-timing.md`. Preferences/player wiring was still pending.
Part H persists atomic lyric settings in DataStore: Auto source, LRCLIB opt-in off by default,
current-line preview on, correction clamped to ±3000ms on read/write. Choosing LRCLIB never grants
permission; disabling it resets an LRCLIB-only choice to Auto. No UI/runtime wiring yet;
see `docs/handoffs/M2.8-lyrics-preferences.md`.
Part I adds Settings > Playback > Lyrics: source, explicit LRCLIB opt-in with the privacy disclosure
beside it (LRCLIB source disabled until allowed), current-line preview and ±3000ms correction.
Normal/2× font dialog tests with a gate mutation; emulator checked. No player wiring yet;
see `docs/handoffs/M2.8-lyrics-settings-ui.md`.
Part J wires lyrics at runtime: Koin provides the cache, the server provider (per song's server) and
an isolated LRCLIB client; LyricsLoader/LyricsViewModel follow the playing song, settings and
connectivity, reload only when those change, and look nothing up unless the pane or preview shows
lyrics. Loader/ViewModel tests with two mutations; full checks pass. No pane UI yet;
see `docs/handoffs/M2.8-lyrics-runtime.md`.
Part K adds the Now Playing lyrics pane (Lyrics button beside Queue; synced auto-scroll centred on
the current line, Follow after a manual scroll, tap to seek, language menu, offline/missing/failed
states, LRCLIB credit) and the current-line preview. Back closes the pane before Now Playing
(hoisted to AppRoot). UI/back tests with mutations, normal/2× renders, full checks pass; emulator
showed real server plain lyrics. Synced and LRCLIB lyrics on a device remain unchecked.
See `docs/handoffs/M2.8-lyrics-pane.md`.
M2.8 implementation review (13 Nov): every slice item (pane, preview, sources, offset, cache,
LRCLIB disclosure) is in place. Open decision: prefetching lyrics when a song is downloaded was only
a suggested technique in FEATURE_PARITY; iOS does not do it and it would batch LRCLIB requests.
Decided 15 Nov 2026: follow iOS, so no prefetch. Device checks (embedded LRC, LRCLIB fallback) remain open.

**M2.9 status (13 Nov 2026):** in progress. Part A adds the playlist write foundation: a
`PlaylistWriter` interface and its Subsonic implementation (create, rename, comment, append, remove,
replace, delete, fresh read) with batched song lists and highest-first removal, plus the
retry-safe `PlaylistAppendIntent` ported from iOS. MockWebServer and intent tests with two mutations;
no UI, DI or schema change yet. See `docs/handoffs/M2.9-playlist-writer.md`.
Part B adds `PlaylistRepository`: server first, then the playlist is read back and mirrored into
Room in one transaction. Additions and creations keep their state across retries (no duplicate
songs or playlists); remove/reorder refuse to act on a playlist that changed elsewhere and refresh
it instead. Room + fake-server tests with two mutations; no schema change, no UI yet.
See `docs/handoffs/M2.9-playlist-repository.md`.
Part C adds recent playlist destinations (schema 50, `playlist_destinations`, cascade on server
delete): the newest five per server, recorded only after a successful addition, resolved through
the mirror. DAO/repository tests with two mutations. See `docs/handoffs/M2.9-playlist-destinations.md`.
Part D's add-to-playlist sheet is committed and verified (status at the end of this section). Its Recent destinations now wait for the signed-in owner and exclude other owners,
matching the paged list's eligibility rule; unknown-owner playlists remain compatible.
See `docs/handoffs/M2.9-recent-ownership.md` for this bounded follow-up.
Duplicate choices and partial-write/creation retries now have ViewModel-to-Room/fake-server tests,
including success messages, dismissal, retained drafts and cancellation of the duplicate prompt.
Mutation checks catch swapped choices, lost creation identity and broken prompt dismissal.
See `docs/handoffs/M2.9-playlist-retries.md`.
Destination DAO tests now cover owner/source isolation, Recent exclusions, escaped substring search,
and 125 rows across seven-row pages with case-insensitive name order and ID ties. A broken-query
mutation fails all three tests; see `docs/handoffs/M2.9-destination-paging.md`.
Sheet controls now have Compose tests (creation/search callbacks, Recent and deep-list picks,
busy gating, duplicate choices, and scrolling back to creation). The whole sheet scrolls together;
Create sits below a full-width name field to prevent floating-label overlap at 2× font size.
Normal and 2×/320×480 renders reviewed; see `docs/handoffs/M2.9-sheet-ui.md`.
Repository/ViewModel search integration is now covered through a real Room-backed Paging presenter:
query changes, literal `%`/`_`/backslash, trimmed/case-insensitive search, Recent separation,
owner/source filtering, empty results and mirror rename invalidation. Removing percent escaping
fails the test; see `docs/handoffs/M2.9-search-integration.md`.
API 37 emulator verification found a stale sheet ViewModel: reopening the same song retained a
cancelled draft. Each opening now owns/clears its ViewModel store, matching the queue overlay.
Lifecycle tests (with cleanup mutation) and emulator retest pass; see `docs/handoffs/M2.9-sheet-lifecycle.md`.
An isolated HTTP creation/retry test now connects ViewModel → repository → Subsonic writer → Room:
503 append failure keeps the draft without success, retry reuses the created ID, refreshes the mirror,
records Recent and emits completion. Skipping mirror refresh fails; see `docs/handoffs/M2.9-http-creation.md`.
API 37 portrait IME smoke check passes in a debug-only in-memory sheet preview: fields/results
stay above the full keyboard, Back hides IME then dismisses. No personal-source writes; see
`docs/handoffs/M2.9-sheet-ime.md`. AppRoot-specific back behavior still needs an isolated source.
Stateful route failure/retry dismissal now has a Compose regression (mutation catches missing
completion callback). API 37 with user-authorized configured server: created ONLY a new
`maekster development test` playlist, observed success snackbar/dismissal, reopened fresh form
and verified Recent/1 song. See `docs/handoffs/M2.9-route-completion.md`.
API 37 existing-destination add and duplicate choices verified on that playlist: Skip keeps the count,
Add anyway keeps the duplicate, a distinct song adds without a prompt (now 3 songs). Add anyway exposed
a playlist-detail crash (rows keyed by song id); rows now key by entry position, with a regression.
See `docs/handoffs/M2.9-device-duplicates.md`.
Real network failure/retry verified on API 37: offline add keeps the sheet with error and retry hint
and sends no write; after reconnecting, the same row retries with exactly one `updatePlaylist`,
snackbar and dismissal (4 entries). See `docs/handoffs/M2.9-device-failure-retry.md`.
AppRoot Back/IME verified on API 37 with the real route, from album detail and from Now Playing:
Back 1 hides the keyboard (query/draft kept), Back 2 closes only the sheet, Back 3 pops the route or
closes Now Playing; reopening starts fresh; no writes. See `docs/handoffs/M2.9-approot-back-ime.md`.
**Part D status (13 Nov 2026):** done. Unverified: API 35, landscape, TalkBack, real IME at large
font, and a lost server response on device (JVM-tested only). Membership toggles are a later part.
Part E is done: "Remove from playlist" in a song's menu on its playlist page, for the user's own
playlists outside offline mode. It removes that exact entry (duplicates safe), refusing when the
mirror or server order changed, with a snackbar either way. Verified on API 37 on the test playlist;
see `docs/handoffs/M2.9-remove-from-playlist.md`.
Part F is done: "Rename…" in the playlist page menu (same edit rule), a prefilled dialog that sends
the trimmed name, needs a real change, and keeps the draft on failure. Verified on API 37 by renaming
the test playlist and back; see `docs/handoffs/M2.9-rename-playlist.md`.
Part G is done in code and tests: "Delete playlist" with an iOS-style confirmation (keep or remove
downloads, purged only after the server confirms), snackbar and page close. On API 37 only the
dialog and Cancel were checked; a real delete awaits user authorization. See
`docs/handoffs/M2.9-delete-playlist.md`.
Reorder entry foundation adds a position-based repository move, preserving duplicate occurrences and
unresolved mirror entries and refusing stale edits; see `docs/handoffs/M2.9-reorder-entry.md`.
The playlist song menu now offers Move up/down with ownership/offline and busy gating, exact occurrence
positions and snackbar feedback; see `docs/handoffs/M2.9-reorder-menu.md`.
Editable playlist rows now have 48dp drag handles: move among visible rows, highlight the drop target,
and submit one exact-position move on release through the same recovery flow. No drag auto-scroll;
scroll to the destination first. See `docs/handoffs/M2.9-drag-reorder.md`.
Failed reorders now retain their intent in the repository: a matching retry can recover an ordered
prefix or a lost success response, while conflicting edits refresh instead of writing. Retry state
is process-local; see `docs/handoffs/M2.9-reorder-retry.md`.
Failed adjacent moves now offer a retained Retry/Cancel dialog. Retry uses the original repository
intent even after mirror refresh; preflight failures retain an unverified intent that must pass
normal server validation. See `docs/handoffs/M2.9-move-recovery.md`.
Cancel now discards the retry intent and reads the current server playlist back into Room without
sending an edit. Failed readback closes with an error; sync can reconcile later. See
`docs/handoffs/M2.9-move-abandonment.md`.
Playlist mirror writes now invalidate queue caches, including conflict and not-found refreshes, so
subsequent snapshots see changed lengths/order without a sync. See `docs/handoffs/M2.9-playlist-queue-cache.md`.
Save-queue foundation: a bounded cold export of song-ID batches in captured playback order,
including shuffle/up-next/duplicates, with source and mutation checks. Dialog and server-save
workflow remain open; see `docs/handoffs/M2.9-queue-export.md`.
The writer now also provides bounded, backpressured fresh playlist readback for that future saver,
without materialising the destination. See `docs/handoffs/M2.9-playlist-readback.md`.
Save-queue operation: the export is staged into a Room draft (schema 51), then created, appended in
batches of 100 and verified by bounded readback; a retry resumes the same playlist without resending
lost batches, also after process death. Dialog/menu remain open; see `docs/handoffs/M2.9-queue-save.md`.
"Save queue as playlist…" is now in the queue options menu (online, non-empty): a name dialog captures
the queue on Save, keeps the capture for Retry after a failure, and discards it on close. Verified on
API 37: offline Save fails with no server write, Retry creates one 12-song playlist in queue order with
snackbar, Home and Recent updated without sync. See `docs/handoffs/M2.9-save-queue-ui.md`.
Quick playlists data layer: per-server ordered list (schema 52, `quick_playlists`, names from the mirror),
toggle/move/remove, and a quick add that skips songs already there and then favorites the song
(DataStore preference, on by default). Menu, picker and Settings UI remain; see
`docs/handoffs/M2.9-quick-playlists-data.md`.
Touch and hold on any song heart (rows and Now Playing, not in offline mode) now opens a quick playlist
sheet: "Add to <name>" per quick playlist (snackbar result), "Choose quick playlists…" (a ticked picker of
the user's own playlists) and "Add to playlist…". Settings page remains; see
`docs/handoffs/M2.9-quick-playlists-menu.md`.
Settings > Application > Quick playlists manages them: reorder (drag or accessibility actions), remove,
choose (online only) and the "Also add to Favorites" switch. Verified on API 37, including a
user-authorized quick add (one append, one star; repeat says "Already in"); see
`docs/handoffs/M2.9-quick-playlists-settings.md`.
**Decision (13 Nov 2026, user):** M2.9 server verification is Navidrome-only for now. Playlist-write
integration tests on gonic, LMS and Nextcloud Music are deferred to GitHub issue #11 and do not
block M2.9. Remaining M2.9 work: membership toggles in the add-to-playlist sheet, read from the
local mirror (decided with the user), with a server check before any write.
Membership toggles are done: rows show none/some/all marks from the mirror; tapping a full one
removes every occurrence (positions from a fresh server read, with confirmation for several songs),
and any other tap fills in only the missing songs. The duplicate prompt is gone, as on iOS. Marks were
verified on API 37; an on-device removal awaits authorization. See `docs/handoffs/M2.9-membership-toggles.md`.
**M2.9 status (14 Nov 2026):** implementation complete (Navidrome-only verification, issue #11).

**M2.10 status (14 Nov 2026):** in progress. Part A adds download collection ownership (schema 53,
`download_collections`, backfilled for fully downloaded albums/playlists): removing or cancelling an
album, playlist or artist keeps the songs another downloaded collection holds, while removing one song
still deletes it (iOS `OfflineLibraryRemovalPlanner`). See `docs/handoffs/M2.10-download-ownership.md`.
Part B adds a Transfers section to the Downloads screen (iOS DownloadActivitySection): every song not yet
on the device with Downloading (progress, bytes), Queued or the failure reason, plus Retry for failures
and Cancel. See `docs/handoffs/M2.10-download-transfers.md`.
Part C extends Settings > Storage: cover cache size with "Clear artwork cache" (confirmed; download
artwork kept), and under Library sync the active server's counts, database size and "Sync library now".
See `docs/handoffs/M2.10-storage-page.md`.
Part D leads offline Home with an offline card (iOS OfflineHomeCard): "Offline mode", the number of songs
available offline (opening them when there are any) and "Turn off offline mode". See
`docs/handoffs/M2.10-offline-home-card.md`.
**M2.10 status (14 Nov 2026):** complete. The emulator check passed for Storage, Transfers (progress,
bytes, cancel) and the offline Home card. It also found and fixed a crash: the downloaded-artist-art
refresh, a `@RawQuery` UPDATE outside a transaction, ran on a read-only pooled connection (regression
from b2a1716b). See `docs/handoffs/M2.10-emulator-check.md`.

**M2.11 status (14 Nov 2026):** in progress. Part A (C11, D8): swiping the app away keeps music playing.
A paused, ended or empty player still stops the service. Emulator-verified. See
`docs/handoffs/M2.11-swipe-away.md`.
Part B: the Wi-Fi, Mobile and Download quality dialogs lead with iOS's presets (Original, MP3 320, MP3
192) plus Custom, which reveals any bitrate and format; rows name the preset. Storage is unchanged. See
`docs/handoffs/M2.11-quality-presets.md`.
Part C: the cursor now records which song its saved time belongs to (schema 54, `positionSongId`).
Restore follows that song within one bounded window when a sync shifted the queue. When the song was
deleted, the next track starts from 0:00 instead of at the old song's time. A queue from another source
still stops (existing test). The process-death restore was checked on the emulator. See
`docs/handoffs/M2.11-restore-edge-cases.md`.
**M2.11 status (15 Nov 2026):** complete. Open follow-up: an adb media key replaced the queue on the
emulator (cause unknown; see the Part C handoff).

**Milestone 2 status (15 Nov 2026):** every slice is implemented, and `FEATURE_PARITY.md` statuses have
been refreshed. Decisions: M2.8 follows iOS (no lyrics prefetch on download). The M2.9 on-device
removal was authorized and verified. Airplane-mode recovery was checked on the emulator; the media-key
queue swap could not be reproduced. Still open: the proxy, lyrics and physical-device checks, and the
other servers (issue #11). See `docs/handoffs/M2-closeout.md`.

**M3.1 status (15 Nov 2026):** in progress.
- Part A: plays that reach the scrobble threshold are kept as local `play_events` (schema 55).
- Part B: a ListenBrainz client (validate-token, submit-listens) with transient and permanent errors.
- Part C: a sealed token and account in preferences, plus the `pending_listens` offline queue
  (schema 56). `ListenBrainzRepository` handles connect, `playing_now`, `single`, and import in batches
  of 1,000. The `Scrobbler` fans out to the server and ListenBrainz independently.
- Part D: a ListenBrainz row on the Playback page (Off, On, Paused, waiting count) and a dialog to
  connect with a token (optional server), switch sending, see the queue, and disconnect with
  confirmation.

**M3.1 status (16 Nov 2026):** complete. Verified by the user on their device: listens reached
listenbrainz.org (doubled alongside Navidrome's own relay, as the dialog warns). The offline-queue replay
against the real service is still unchecked.

**M3.2 status (16 Nov 2026):** in progress. Part A: Smart Shuffle (iOS three-source blend: favourites,
recent taste, rediscovery; at most 3 per artist; recent plays skipped), built from the mirror with bounded
queries and limited to downloads when offline, and played as a labelled mix (`playMix`). No UI yet.
Part B is complete: Instant Mix and artist-station engine/playback, explicit server similarity cached in
Room (schema 57), with downloaded-only cache/local fallback offline. UI entry points remain Part C.
Next: the Discover tab. See `docs/handoffs/M3.2-instant-mix.md` and `docs/handoffs/M3.2-smart-shuffle.md`. See `docs/handoffs/M3.1-play-events.md`, `M3.1-listenbrainz-client.md`,
`M3.1-listenbrainz-listens.md` and `M3.1-listenbrainz-settings.md`.

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
| **M3.8 Crossfade (prototype, D15)** | **On hold (16 Nov 2026), tracked in issue #13.** 0–12 s, off by default, disabled on gapless albums | None | Gapless unaffected; transition timing | Prototype first; drop if it destabilises the window |
| **M3.9 Internet radio** | **Dropped (16 Nov 2026): the user does not need internet radio.** Was: station list, live playback outside the queue | — | — | |

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
| D15 | Crossfade | **Agreed.** Prototype in M3.8, drop if it compromises gapless. On hold since 16 Nov 2026 (issue #13) |
| D16 | Casting | **Wanted, later.** Deferred to M4.7 (issue #3); ordinary playback must never depend on Play Services |
| D17 | Internet radio | **Dropped (16 Nov 2026).** Not needed; M3.9 is skipped |

## Smallest sensible first implementation slice

**M1.1 Identity**: change `applicationId` to `io.github.marcus_gt.maekster`, `app_name` "mækster", adaptive and
monochrome launcher icons and the notification icon from the brand mark, and a `MaeksterBrand` constant feeding the
User-Agent and Subsonic `c=`. Remove the inherited partial locales (D14). Disable or retarget `release.yml` so it cannot
publish to the Subtracks listing. No Room changes, no package rename, no navigation change. Verification:
`UserAgentTest` and `SubsonicClientTest` updated to assert the new client name (each fails before the change);
`StringResourcesTest` updated; install side by side with Subtracks on the emulator; the server's player list shows
"maekster".
