# Releasing oneMind

How to cut a release, and the things that will bite you if you skip a step.

Read [The signing key](#the-signing-key) before your first release. Losing it means no existing user can ever install an update.

---

## Table of contents

- [Before your first release](#before-your-first-release)
- [The signing key](#the-signing-key)
- [Versioning](#versioning)
- [Release checklist](#release-checklist)
- [Schema changes](#schema-changes)
- [Writing the release notes](#writing-the-release-notes)
- [Publishing](#publishing)
- [If something goes wrong](#if-something-goes-wrong)
- [Automating it later](#automating-it-later)

---

## Before your first release

You need a signing key. Android identifies an app by its package name *and* its signing certificate, so:

- An APK signed with a different key than the installed one **will not install over it**. Android reports a signature mismatch, and the user's only route forward is to uninstall — which deletes every memory they have saved.
- There is no recovery. There is no authority that can re-issue your key.

So the key is the single most important artifact in this project, more than the source, which is public anyway.

## The signing key

### Generating it (once, ever)

```bash
keytool -genkey -v \
  -keystore onemind-release.jks \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -alias onemind
```

- **4096-bit RSA** — this key has to remain trustworthy for as long as the app exists.
- **10000 days** validity (~27 years). An expired key cannot sign updates, and the consequence of expiry is the same as losing it.
- Use a long, random password. Store it in a password manager, not in your head.

### Storing it

Create `keystore.properties` in the repository root:

```properties
storeFile=/absolute/path/to/onemind-release.jks
storePassword=...
keyAlias=onemind
keyPassword=...
```

This file and `*.jks` are both git-ignored. **Verify that before your first commit after creating them:**

```bash
git check-ignore -v keystore.properties onemind-release.jks
```

If that prints nothing, stop and fix `.gitignore`. A key pushed to a public repository is compromised permanently — anyone holding it can publish an update that Android will happily install over a user's oneMind.

### Backing it up

Keep the keystore and its passwords in **at least two places that do not fail together**. An encrypted password-manager attachment plus an offline copy is a reasonable pair. A single laptop is not.

### In CI

Do not commit the keystore. Base64 it into a secret and reconstruct it at build time, and supply the passwords as environment variables:

| Variable | Meaning |
|---|---|
| `ONEMIND_KEYSTORE_PATH` | Path to the reconstructed `.jks` |
| `ONEMIND_KEYSTORE_PASSWORD` | Keystore password |
| `ONEMIND_KEY_ALIAS` | Key alias |
| `ONEMIND_KEY_PASSWORD` | Key password |

`app/build.gradle.kts` reads `keystore.properties` first, then falls back to these. When neither is present it produces an **unsigned** APK rather than failing — deliberately, so a contributor can verify a release build compiles without holding the key. It then fails visibly at install time rather than silently shipping something unverifiable.

## Versioning

Two numbers in `app/build.gradle.kts`, doing different jobs:

```kotlin
versionCode = 1        // integer Android compares. MUST increase every release.
versionName = "0.1.0"  // string humans read.
```

**`versionCode` must strictly increase.** Android refuses to install an APK whose `versionCode` is lower than the installed one. Shipping the same number twice means the second build will not install as an update for anyone. Just increment it by one, every time, with no exceptions and no meaning attached to the value.

**`versionName` follows semver**, adapted to what actually matters here:

| Bump | When |
|---|---|
| **Patch** `0.1.0 → 0.1.1` | Bug fixes only. No schema change, no new capability. |
| **Minor** `0.1.0 → 0.2.0` | New features, or a **database migration**. See [Schema changes](#schema-changes). |
| **Major** `0.x → 1.0.0` | First release considered stable. After that, breaking changes to something users depend on. |

While the app is pre-1.0, a minor bump is the normal case.

## Release checklist

### 1. Verify on a clean tree

```bash
git status                      # must be clean
./gradlew clean
./gradlew testDebugUnitTest     # 603 tests
./gradlew lintDebug
./gradlew assembleDebug
```

`assembleDebug` is not optional. It is what validates the Hilt dependency graph; `compileDebugKotlin` does not, so a broken injection compiles fine and crashes at launch.

### 2. Run the instrumented tests on a device

```bash
./scripts/emulator.fish start
./gradlew connectedDebugAndroidTest   # 56 tests
./scripts/emulator.fish stop
```

These are the ones that matter most for a release, because they cover **Room migrations against real SQLite**. A migration bug is the only class of defect in this app that destroys user data instead of merely annoying them.

### 3. Test the upgrade path by hand

Automated migration tests prove the schema survives. They do not prove the app is usable afterwards.

```bash
# Install the CURRENTLY RELEASED version
adb install -r onemind-<previous-version>.apk

# Use it: save a few memories, let them enrich, run a search

# Install the new build OVER it — note: no -r reinstall, this is an upgrade
./gradlew assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

Then check: are the old memories still there? Do they still open? Does search still find them?

Skipping this is how a release that passes every test still corrupts real collections.

### 4. Bump the version

Edit `app/build.gradle.kts` — `versionCode` **and** `versionName`.

### 5. Build the release APK

```bash
./gradlew clean assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

Confirm it is actually signed. If the filename says `app-release-unsigned.apk`, your credentials were not picked up and the APK cannot be installed:

```bash
# Should print a certificate, not an error
$ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

Also sanity-check the size. It should be around **42MB**. A sudden jump to ~150MB means the ABI filter or the native-library exclusion in `app/build.gradle.kts` has been lost — see the comments there for what each one removes and why.

### 6. Rename and checksum

```bash
VERSION=0.2.0
cp app/build/outputs/apk/release/app-release.apk onemind-$VERSION.apk
sha256sum onemind-$VERSION.apk > onemind-$VERSION.apk.sha256
```

Publish the checksum alongside the APK so users can verify the download.

### 7. Tag

```bash
git commit -am "Release 0.2.0"
git tag -a v0.2.0 -m "Release 0.2.0"
git push origin main --tags
```

Tag the commit the APK was actually built from. If they diverge, nobody can reproduce the build later.

### 8. Publish

See [Publishing](#publishing).

### 9. Archive the APK

Keep every released APK somewhere you can find it. You will need the previous one for step 3 of the *next* release, and you cannot reliably rebuild a byte-identical APK months later.

## Schema changes

Room's schema version lives in `OneMindDatabase.kt`. Currently **4**.

If your release changes anything about the database, all of the following are required:

1. **Bump `version`** in the `@Database` annotation.
2. **Write the migration by hand** in `data/local/Migrations.kt` and add it to `Migrations.ALL`. Never use `fallbackToDestructiveMigration` — it deletes every memory the user has.
3. **Commit the exported schema JSON.** `app/schemas/` is deliberately not git-ignored: migration tests validate against the schema of the version they migrate *from*, so those files are source, not build output.
4. **Add a `MigrationTest`** covering:
   - the new hop (`n → n+1`), asserting existing memories and their derived data survive
   - the **full chain** (`1 → n`), for a user upgrading from the first release
5. **Bump `versionName`'s minor number.** A schema change is never a patch release.

### If your change touches the search index

`memory_search_index` is an **FTS4 virtual table**, which has two consequences that have already caused bugs:

- **No foreign keys.** SQLite does not support them on virtual tables, so it does *not* cascade when a memory is deleted. Deletion must remove the index row explicitly.
- **Two implementations of one rule.** The Kotlin indexer (`SearchDocument`) and the migration's backfill SQL both decide what text is searchable, because a migration cannot use the DAOs of the database it is migrating. `BackfillParityTest` exists to catch them drifting. **If you change one, change both**, and let that test confirm it.

### Amending an unreleased migration

If a migration has not shipped, editing it in place is correct. Once it has shipped to any user, it is frozen — write a new migration instead. There is no way to know which version a given install is coming from other than trusting that the old migration still does what it did.

## Writing the release notes

Write for someone deciding whether to install this, not for someone reading a commit log.

**Structure:**

```markdown
## What's new
- Things a user will notice, in their language

## Fixed
- Bugs that were affecting people, described by symptom

## Upgrading
- Anything they need to know. Say so explicitly if a migration runs.

## Verify your download
sha256: <checksum>
```

**Guidelines:**

- Lead with what changed for the user. "Search now finds memories by meaning" beats "added VectorSearcher".
- Describe fixes by **symptom**, not cause. "Memories saved with the back gesture were sometimes lost" tells them whether they were affected; "added BackHandler to ComposerScreen" does not.
- If a migration runs, say so, and say that memories are preserved. People are right to be nervous about updates to something holding their data.
- Note anything that is *still* broken. A known issue you have documented is a limitation; the same issue undocumented is a nasty surprise.
- Do not list internal refactors. Nobody installing an APK cares.

## Publishing

### GitHub Releases

```bash
gh release create v0.2.0 \
  onemind-0.2.0.apk \
  onemind-0.2.0.apk.sha256 \
  --title "oneMind 0.2.0" \
  --notes-file release-notes.md
```

Without `gh`, use the web UI: **Releases → Draft a new release**, pick the tag, attach both files, paste the notes.

### Pre-releases

Mark anything you want tested but not adopted as a pre-release (`--prerelease`). GitHub will not present it as "Latest", so casual visitors get the stable one.

### Google Play

Not currently used. If you ever do:

- Play requires an **AAB** (`./gradlew bundleRelease`), not an APK
- Play App Signing means Google holds the final signing key and yours becomes an upload key — read their documentation carefully before enrolling, because it is not reversible
- The `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_MEDIA_PROJECTION` and screen-capture behaviours will all need declaring in the data-safety form and may attract review

## If something goes wrong

### A release is broken

Do not delete the release or move the tag. People may already have installed it, and a vanished version makes their situation harder to diagnose.

Instead:

1. Mark the GitHub release as a pre-release, so it stops being offered as "Latest"
2. Edit its notes to describe the problem at the top
3. Fix forward: new patch version, new `versionCode`, new release

`versionCode` only ever goes up. There is no way to publish a "downgrade" that Android will install.

### The signing key is lost

There is no recovery. Existing installs can never be updated.

The only path is a new package name (`applicationId`) and a new key, which Android treats as an entirely different app. Existing users must install it separately and will not carry their memories across.

This is why [backing up the key](#backing-it-up) is the most important step in this document.

### The signing key is compromised

Rotate immediately and treat every build signed with the old key as untrusted. Because Android's trust model is the certificate, this has the same practical consequence as losing it — plan for a new package name.

## Automating it later

Worth doing once releases are frequent. Two things to get right:

**Keep the manual upgrade test.** CI can run every automated test and still not tell you the app is usable after a migration. That step needs a human and a real previous build.

**Never expose the keystore in logs.** Reconstruct it from a base64 secret into a path outside the workspace, and delete it in a step that runs even on failure. A leaked key in CI output has the same consequence as one committed to the repository.

A reasonable division: CI verifies every push (`testDebugUnitTest`, `lintDebug`, `assembleDebug`) and builds an unsigned release APK on tags; a human signs, tests the upgrade, and publishes.
