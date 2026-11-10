# CLAUDE.md

mækster for Android. This repository started as a fork of Subtracks (a native Kotlin/Compose
Subsonic client) and is now an independent project.

## Read first

1. `AGENTS.md`: toolchain, commands, conventions and gotchas. It still applies in full.
2. `docs/FEATURE_PARITY.md`: what the iOS app does, what Android does today, and the gaps.
3. `docs/ANDROID_ROADMAP.md`: the milestone and slice order. Work on the current slice only.
4. `docs/architecture.md`: how the existing playback, queue, sync, Room and download layers work.

## Product goal

Build the Android version of mækster with functional and experiential parity with the iOS app
(`https://github.com/marcus-gt/Minidisc`, read-only clone at `../maekster-ios-reference`).

**Same product, Android-native design.** Parity means the same capabilities, data semantics,
priorities, privacy and playback reliability, and a music- and artwork-first character. It does
not mean copying iOS visuals. Use Compose, Material 3, Android navigation and back, sheets,
snackbars, media notifications and audio focus, then give them a deliberate mækster look (tokens,
artwork-tinted dark surfaces, the orange accent). It must not look like an iOS imitation or like an
unmodified Material sample.

Parity priority: behaviour and data semantics > user workflows > information hierarchy and personality >
interaction quality > visual interpretation.

The iOS repository is a **behavioural reference**, not code to translate line by line. Read its source
and tests to learn intent and edge cases, then implement them idiomatically in Kotlin (coroutines,
Flow, Room, Media3, ViewModel, Koin). Never modify, commit to or copy that clone into this repository.

## Naming (from the iOS project)

- In-app text, strings in every language, docs, metadata and commit messages: **maekster**, all lowercase,
  even at the start of a sentence. Never "Maekster" in prose, never "MAEKSTER".
- The system app name (launcher label, `app_name`) and the Home header are **mækster** with æ (the Home header reads
  `app_name`; decided by the user on 10 Nov 2026). Everything else in the UI says maekster.
- Kotlin types use `Maekster…` (`MaeksterTheme`, `MaeksterSpacing`). Identifiers, keys, preference names and
  package or ID segments use `maekster`.
- In the app, use the brand mark only, never the wordmark. Brand assets come from the iOS `Branding/`
  folder. Treat them as finished sources and do not redraw them.
- Names sent to external services (User-Agent, Subsonic `c=`, ListenBrainz client) come from one constant.
- Credit the lineage: Subtracks (Austin Ried) for this codebase, and Minidisc and Cassette for the iOS app.

## Protect the foundations

Keep these unless a parity requirement cannot be met by extending them:

- Media3 `PlaybackService` + `PlaybackController` as the only UI entry to playback.
- The reference-based queue (`queue_entries`, `up_next_entries`, `playback_cursor`), the bounded
  player window and the seekable Feistel shuffle. Never materialise a whole queue or table in memory.
- The full Room 3 library mirror, hand-written migrations (no destructive fallback), Paging 3 lists,
  FTS5 search and the offline-first rule: browsing, filtering, sorting and search never fetch metadata on demand.
  Explicit actions (Instant Mix, stations, lyrics) may call the server, but cache the result in Room and show a
  clear offline fallback (decision D9).
- `SyncService`/`SyncManager`/`AutoSync` semantics, `PendingServerActionSink`, `DownloadRepository`.

New external integrations (ListenBrainz, LRCLIB, AudioMuse, MusicBrainz) go behind Kotlin
interfaces with fakes for tests. Keep UI state separate from networking and persistence. No module split yet.

## Working rules

- Small, reviewable vertical slices. One slice per change set, with its completion criteria from the roadmap.
- Behavioural changes need tests that fail without the change and pass with it (see AGENTS.md).
- Before handing work back, run: `gradle :app:assembleDebug`, `gradle :app:testDebugUnitTest`,
  `gradle :app:ktlintCheck :app:lintDebug`, and `gradle :app:ktlintFormat` before any commit.
  Outside Nix, use `./gradlew` with the same tasks (Android Studio's bundled JBR works as `JAVA_HOME`).
- No casual build-tool or dependency changes: keep the committed AGP, Gradle, Kotlin and JDK targets.
  Don't run upgrade assistants, don't generate `gradle/gradle-daemon-jvm.properties`, and don't add
  dependencies the existing stack can cover. Any addition must be free/libre and its version verified.
- Core functionality must not require Google Play Services or any proprietary Google component. Optional
  integrations that do must be isolated, documented, and never needed for ordinary playback. No telemetry.
- This project does not merge, rebase or cherry-pick from upstream Subtracks.
- Licensing: this repo is GPL-3.0; the iOS app is MPL-2.0 and some assets have their own licences (for
  example Lexend is OFL). Review licensing and attribution before copying any source text, string or asset.
- Never commit or push unless explicitly asked.
