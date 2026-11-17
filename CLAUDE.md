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

## Development sessions and handoffs

### Shared development workflow

- This repository is developed using both Claude Code and Codex. Both follow the same coding standards,
  testing requirements, architectural constraints and handoff workflow.
- Agents share the Git repository, roadmap, architecture documentation and handoff files. All important
  development context must be recoverable from the repository, without private conversation history or
  agent-specific memory. Never assume the previous or next session uses the same agent.
- Only one agent may modify the working directory at a time; coordinate the handover before editing.
- Commit only with explicit authorization and passing verification. Never push to GitHub without explicit
  authorization (see Working rules).

### Starting a session

Before making changes:

1. Read `AGENTS.md`, `CLAUDE.md` and the relevant section of `docs/ANDROID_ROADMAP.md`.
2. Read the relevant handoffs in `docs/handoffs/`.
3. Inspect Git status, including staged, unstaged and untracked files.
4. Inspect the latest commit.
5. Determine whether the previous task was completed or interrupted; use the recovery steps below if interrupted.
6. Continue the current roadmap subtask unless explicitly instructed otherwise.

Avoid repeating broad architecture investigations when the existing documentation provides sufficient context.

### Current release-readiness handoff

Milestone 4 is limited to M4.8 GrapheneOS, M4.10 background reliability, M4.9 core accessibility,
and M4.11 GitHub APK distribution. The roadmap links the deferred Android work. Resume from
[`docs/handoffs/M4-release-readiness.md`](docs/handoffs/M4-release-readiness.md) and record physical-device
and release verification in [`docs/RELEASE_READINESS.md`](docs/RELEASE_READINESS.md). Production signing
credentials and release publication require owner approval.

### Handoffs and checkpoints

- Keep one concise, agent-independent handoff per subtask, `docs/handoffs/<slice>-<topic>.md`
  (for example `M2.4-songs.md`), ideally about 60 lines. Include status, what was implemented, key files,
  decisions, test and emulator results, known issues, unverified behaviour, remaining work, Git state and
  the next concrete action (or recommended next task when complete).
- Maintain the subtask's handoff during implementation: update it at meaningful checkpoints, not only at completion.
  Mark unfinished work clearly as `IN PROGRESS` or `BLOCKED`; mark completion only when justified.
- Keep checkpoints lightweight. Update the handoff before ending a session, including unfinished sessions.
  A new agent must be able to recover the task, actual implementation state, remaining work and verification
  requirements using only the repository.
- Never label tests or functionality verified unless they actually were; distinguish verified results from
  assumptions and untested behaviour in both handoffs and reports.

### Completing a subtask

- Work on one well-defined subtask (for example one part of a roadmap slice) at a time.
- Run the checks in Working rules and relevant additional tests, plus emulator verification where practical.
  Record results and any verification gaps in the handoff.
- Create or update the subtask handoff and update roadmap statuses only for actual progress.
- Commit if explicitly authorized and verification passes.
- Stop after completing the subtask; do not automatically start the next one without explicit direction.

### Interrupted tasks and recovery

Either agent may stop unexpectedly because of usage or context limits, expired authentication, IDE
disconnection or another interruption. A usage limit may prevent a final handoff, so both checkpoints
and recovery from Git are required.

When taking over interrupted work:

1. Assume potentially valuable unfinished work exists. Inspect `git status`, `git diff`, staged changes,
   untracked files and recent commits.
2. Check other Git worktrees and whether changes await acceptance in the IDE agent interface.
3. Read existing handoffs and inspect the partially implemented code. Reconstruct the actual state from
   the repository; do not assume the handoff is fully current.
4. Identify what is finished, partial, broken or untested. If no handoff exists, create one from Git and
   the source code.
5. Continue from the interruption, preserving valid existing work. Add missing tests, resolve problems
   and perform verification.
6. Complete the originally assigned subtask before moving on.

Safety rules:

- Never automatically discard, revert, reset, clean, stash or overwrite unfinished changes. Never assume
  uncommitted changes are mistakes.
- Do not restart from scratch merely because another agent began the implementation.
- Do not commit incomplete or unverified work merely to create a checkpoint.
- If continuing existing changes is ambiguous or potentially destructive, explain the problem and request guidance.

### Context and token efficiency

- Prefer one implementation subtask per session and fresh sessions after completed subtasks.
- Use compaction when continuing a large unfinished task with the same agent.
- Do not repeatedly load entire repositories or large logs unnecessarily.
- Keep permanent instructions short and implementation details in handoffs and `docs/`, not in this file.
- Transition between agents through documentation and Git rather than copying large conversation transcripts.
