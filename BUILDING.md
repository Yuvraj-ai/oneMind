# Building oneMind

## Toolchain

| Component | Version | Why pinned here |
|---|---|---|
| JDK | 17 (Temurin) | AGP 8.13 requires 17+ |
| Gradle | 8.13 | AGP 8.13.2 requires 8.13+ |
| Android Gradle Plugin | 8.13.2 | Latest 8.x; AndroidX libs below require ≥ 8.9.1 |
| Kotlin | 2.1.0 | Matches the KSP version in the catalog |
| compileSdk / targetSdk | 36 | Highest SDK available in the **stable** sdkmanager channel |
| minSdk | 30 | Android 11+, per the product decision |

## Version ceiling: why not the newest of everything

`compileSdk 37` and AGP 9.x exist, and the newest Compose BOM (`2026.08.00`,
Compose 1.12.x) requires both. Platform 37 ships only in sdkmanager's **canary**
channel, so building against it would make the build depend on preview tooling.

The project therefore sits one tier back on purpose:

- Compose BOM `2026.06.01` (Compose 1.11.4) — newest that compiles against SDK 36
- `activity-compose` 1.12.4 — 1.13.0 requires AGP 9.1+ / SDK 37

Revisit when platform 37 reaches the stable channel: bump AGP to 9.x, compileSdk
to 37, then the Compose BOM and activity to latest.

## Local setup

Nothing needs root. Everything installs under `$HOME`.

```fish
# JDK 17
mkdir -p ~/.local/jdks
curl -fL -o /tmp/jdk17.tar.gz \
  "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
tar xzf /tmp/jdk17.tar.gz -C ~/.local/jdks
ln -sfn ~/.local/jdks/jdk-17* ~/.local/jdks/current

# Android SDK command-line tools
mkdir -p ~/Android/Sdk/cmdline-tools
curl -fL -o /tmp/cmdtools.zip \
  "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
unzip -q /tmp/cmdtools.zip -d ~/Android/Sdk/cmdline-tools
mv ~/Android/Sdk/cmdline-tools/cmdline-tools ~/Android/Sdk/cmdline-tools/latest

# Environment (fish; use export in bash/zsh)
set -Ux JAVA_HOME $HOME/.local/jdks/current
set -Ux ANDROID_HOME $HOME/Android/Sdk
set -Ux ANDROID_SDK_ROOT $HOME/Android/Sdk
fish_add_path -U $HOME/.local/jdks/current/bin
fish_add_path -U $HOME/Android/Sdk/platform-tools
fish_add_path -U $HOME/Android/Sdk/cmdline-tools/latest/bin

# SDK packages
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

`local.properties` (holding `sdk.dir`) is generated per machine and is not
committed.

## Commands

```fish
./gradlew compileDebugKotlin   # fastest check that it compiles
./gradlew testDebugUnitTest    # unit tests (JVM, no device)
./gradlew assembleDebug        # full debug APK; exercises Hilt's graph validation
./gradlew lintDebug            # Android lint

./scripts/emulator.fish start        # boot the headless test emulator
./gradlew connectedDebugAndroidTest  # instrumented tests (Room integration)
./scripts/emulator.fish stop
```

Run `assembleDebug` before trusting a change: `compileDebugKotlin` alone does not
run Hilt's full dependency-graph check, so a broken DI wiring compiles but fails
there.

## Emulator for instrumented tests

Instrumented tests (`app/src/androidTest`) need a device. Hardware acceleration
is required or the emulator is unusably slow — check `/dev/kvm` is readable and
writable by your user.

One-time setup:

```fish
sdkmanager --install "emulator" "system-images;android-36;google_apis;x86_64"
avdmanager create avd -n onemind_test \
  -k "system-images;android-36;google_apis;x86_64" -d pixel_6
```

`google_apis` rather than `default`, because ML Kit (arriving with the OCR stage)
can need Play Services.

Then `./scripts/emulator.fish start`, which boots headless and blocks until
`sys.boot_completed`. Gradle finds the device through adb on its own.

## Current verification status

As of v0.1.3.

| Suite | Count | Status |
|---|---|---|
| Unit (JVM, incl. Robolectric) | 598 | passing |
| Instrumented (emulator, API 36) | 62 | passing |
| Lint | — | 0 errors |

The instrumented suite is the one that is easy to leave broken, because it needs a
device and so does not run as part of an ordinary check. It stopped compiling once
already — a field inserted into the middle of an entity shifted every positional
constructor argument in a DAO test, and nothing noticed until the next time
someone booted an emulator. Run it before a release, not just `testDebugUnitTest`.
