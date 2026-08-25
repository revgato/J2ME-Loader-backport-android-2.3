# J2ME-Loader for Android 2.3

This repository contains a backport of [J2ME-Loader](https://github.com/nikita36078/J2ME-Loader) for Android 2.3 (API 10). It is intended for legacy devices that cannot run the current upstream application.

The backport supports installing and running local JAR/JAD games from external storage. The launcher and MIDlet run in separate processes so that a game process can be terminated without losing the launcher or its icon cache. The validation device used for this backport is the Sharp AQUOS IS14SH.

This project is based on [J2meLoader](https://github.com/NaikSoftware/J2meLoader). Special thanks to [woesss](https://github.com/woesss) for the open-source Mascot Capsule implementation used by the upstream project.

## Build requirements

- JDK 17. JDK 21 or newer is not supported.
- Gradle wrapper 8.7 and Android Gradle Plugin 8.5.1.
- Android SDK Platform 34 and the corresponding build tools.
- `minSdk=10`, `targetSdk=10`, and Java 8 bytecode/desugaring.
- No NDK or native build is required.

Build and verify the legacy artifacts with:

```sh
export JAVA_HOME=/path/to/jdk-17
sh gradlew clean testDebugUnitTest lintDebug assembleDebug assembleRelease
sh gradlew verifyLegacyArtifact
```

Release keystores are not stored in Git. Without release signing credentials, a local release build uses the debug key for APK validation only and must not be used for distribution.

## Recommended game conversion workflow

Prefer converting and building each game with J2ME-Loader on a device running a newer Android version, then copy the generated game directory to the Android 2.3 device. Game conversion on Android 2.3 can fail easily because of the legacy platform and should be treated as a fallback.

1. Install a current J2ME-Loader on the newer Android device.
2. Import the game's JAR/JAD and run the game once so J2ME-Loader generates its converted artifacts.
3. Copy the complete `J2ME-Loader/converted/<game>/` directory to the same `converted` directory on the Android 2.3 device. Keep `converted.dex`, `res.jar`, and `converted.dex.conf` together.

When updating an existing game, replace only its directory under `converted`; keep the corresponding directories under `configs` and `data` so that settings and RMS data are preserved.

## Installing and running a game

Build output is written to `app/build/outputs/apk/release/J2ME-Loader-Android-2.3-1.8.3.apk`.

```sh
adb install -r app/build/outputs/apk/release/J2ME-Loader-Android-2.3-1.8.3.apk
adb shell mkdir -p /sdcard/J2ME-Loader/incoming
adb push game.jar /sdcard/J2ME-Loader/incoming/
adb push game.jad /sdcard/J2ME-Loader/incoming/   # optional
```

Open the application, choose `Install JAR/JAD`, browse to the game on the device, and select it from the catalog. A JAD file must reference a JAR in the same directory using a relative filename. Remote URLs, absolute paths, and parent-directory traversal are rejected.

## Data layout

The backport keeps game data in the following layout for compatibility with existing installations:

```text
/sdcard/J2ME-Loader/
  converted/<game>/converted.dex
  converted/<game>/res.jar
  converted/<game>/converted.dex.conf
  configs/<game>/...
  data/<game>/...       # RMS data
  fs/...
```

Updating a game replaces its directory under `converted` without removing `configs` or `data`, so RMS data survives updates, force-stops, and orientation changes.

## Current scope

Supported features include local JAR/JAD installation, JAR-to-DEX 035 conversion, software Canvas rendering, GLES2 rendering where available, RMS and game configuration storage, and MIDI, Tone, WAV, and MP3 playback.

The following features are intentionally unsupported in this backport: M3G, Mascot Capsule 3D, native MIDI, the J2ME camera API, Bluetooth, Location, HTTP/HTTPS installation, MMF/ADPCM, crash reporting, and Google Play integration. Unsupported APIs return controlled errors.

## Compatibility

- [Tested Java games with touchscreen](https://github.com/nikita36078/J2ME-Loader/wiki/List-of-Tested-Java-Games-(Touchscreen))
- [Tested Java games without touchscreen](https://github.com/nikita36078/J2ME-Loader/wiki/List-of-Tested-Java-Games-(Non-Touchscreen))
- [Java games with known bugs](https://github.com/nikita36078/J2ME-Loader/wiki/List-of-Java-Games-with-Bugs)

## License

Copyright 2017-2024 Nikita Shakarun. Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0). See [LICENSE](LICENSE) for the complete license text.
