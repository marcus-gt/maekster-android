# Milestone 4 — PR #28 CI investigation

**CI follow-up verified locally.** PR #28 at `bb4ff6ad` failed in the regular test job. Commit
`253f3357` cleared those failures on GitHub, then the reconnect test failed while reading Room
before the replay collector finished deleting the delivered action.

- The first GitHub run reported three Home screenshot timeouts and a playlist batch assertion
  (expected one completed batch, received zero). Both failure patterns predate Milestone 4.
- Home screenshot fixtures now use replaying StateFlows so Paging Compose can seed the static list
  synchronously. The three render scenarios and assertions are unchanged.
- The initial playlist publisher could cancel a consuming coroutine by throwing after a successful
  send. Close the producer channel with the parse/read failure so the consumer processes the
  preceding batch before it sees the error. Preserve caller cancellation and missing-playlist rules.
- Both GitHub attempts failed on the Home and playlist tests. Attempt 2 also timed out in
  `QueueViewModelTest.removingWhileShuffledReconcilesTheRows`. The queue timeout reproduced locally
  after a passing full run. The revised test reads the repository and rows on the shared dispatcher,
  requires that one item was removed, and checks every remaining row against the saved queue.
  The QueueViewModel suite passed with this change. No production queue code changed.
- The slow-collector regression reproduced the CI assertion before the fix and passed after it.
  Both affected test classes and QueueViewModelTest passed. Formatting, debug build, lint and
  all 1,919 tests passed in the final local run with `--no-parallel --max-workers=2`.
- The reconnect assertions now wait for both delivery and the durable outbox drain before asserting
  the delivered calls. The next local full run also caught SQLite closing during playlist fixture
  teardown. That fixture now waits for the previous ViewModel to finish cancelling before replacing
  it or closing Room. Both targeted classes and all 1,919 unit tests passed after the two test-only
  fixes, along with formatting, assembleDebug, ktlintCheck and lintDebug.
- GitHub tested `8975bdd3`. The earlier failures cleared, but `PlaylistSearchIntegrationTest`
  failed during `Dispatchers.resetMain()` while a coroutine was still completing. The regular test
  job stopped at 1 failure out of 1,919 tests and the real-server integration step was skipped.
- Join the paging observer and ViewModel job before closing Room or resetting the main dispatcher.
  Apply the same ordering to other fixtures that replace `Dispatchers.Main` and own a ViewModel
  or a dedicated coroutine scope. The fix only changes test teardown.
- Targeted playlist, ViewModel, storage and DI fixture tests passed. Formatting, assembleDebug,
  ktlintCheck and lintDebug passed. The full local unit run passed all 1,919 tests with zero
  failures or skips (`--no-parallel --max-workers=2`, Android Studio JBR and `./gradlew`).
- Wait for a passing GitHub check on the amended PR before merging.
