# Distributing Android APKs through GitHub Releases

The release package is `io.github.marcus_gt.maekster`. Keep that ID and the APK signing key consistent
for every release. Debug builds use `.nightly` and the public development key. A local release build
without signing configuration produces an unsigned APK for build verification.

## One-time setup after owner approval

1. Use the approved production keystore or obtain approval before generating one. Back it up offline
   and keep passwords in an approved password manager. The public debug keystore is for development.
2. Create a GitHub environment named `release`, restrict deployments to `main`, and configure required
   reviewers where the repository plan supports them. In the environment, set these secrets:
   - `MAEKSTER_RELEASE_KEYSTORE_BASE64` — approved keystore file encoded as one line of Base64.
   - `MAEKSTER_RELEASE_KEYPROPERTIES_BASE64` — Base64 of a text file with a single `keyAlias=<approved alias>` line.
   - `MAEKSTER_RELEASE_STORE_PASSWORD` — the keystore password.
   - `MAEKSTER_RELEASE_KEY_PASSWORD` — the private key password.
3. Read the public certificate fingerprint from the approved keystore with interactive `keytool -list -v -keystore /path/to/approved.keystore -alias approved-alias`.
   Set the environment variable `MAEKSTER_RELEASE_CERT_SHA256` under GitHub environment **Variables**
   to its SHA-256 fingerprint, using 64 hexadecimal characters without colons. Compare it with the
   expected fingerprint from the owner before enabling a build.

Enter secrets through GitHub environment settings or securely stream them into `gh secret set --env release`.
Keep keystores, property files, passwords and Base64 values out of commits, logs and command arguments.

## Each release

1. Increase `versionCode` monotonically in `app/build.gradle.kts` and set the stable `versionName`
   to `MAJOR.MINOR.PATCH`. Write release notes and complete the release readiness checklist.
2. After owner approval, tag the intended `main` commit `vMAJOR.MINOR.PATCH` and push that exact tag.
   Dispatch **Build signed GitHub release APK** from that same `main` commit with the tag input. The
   workflow checks the tag, signer, package and versions, then saves `maekster.apk` and `SHA256SUMS`
   in the `maekster-release-apk` artifact. It has read-only repository permissions.
3. Download the artifact. Run `shasum -a 256 -c SHA256SUMS` in the extracted artifact directory,
   then run the same APK check as CI from the repository root. Supply the approved fingerprint,
   version code and version name from the release record.

   ```sh
   nix develop --command bash tools/verify-release-apk.sh /path/to/maekster.apk CERT_SHA256 VERSION_CODE VERSION_NAME
   ```

   Test a clean install and a signed in-place update. With publication approval, create a GitHub
   release for the same existing tag and attach the APK and checksum as individual assets. For review
   before publication, use:
   ```sh
   gh release create vMAJOR.MINOR.PATCH --verify-tag --draft --notes-file release-notes.md maekster.apk SHA256SUMS
   ```
   Publish the draft only after reviewing the tag, asset names, fingerprint and notes.
4. In Obtainium add `https://github.com/marcus-gt/maekster-android` as a GitHub source. Select
   `maekster.apk` if asset filtering is needed. Check that a new GitHub release with a higher version
   code updates the app in place. Keep one stable APK asset name across releases.
