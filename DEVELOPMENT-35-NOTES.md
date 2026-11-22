# Development 35 — Build reproducibility and regression gate

## Changes
- Configured Room KSP schema export to `app/schemas` so generated database schema snapshots can be retained and reviewed after a successful Android build.
- Added `tools/verify_all.py` as one deterministic entry point for the project's offline/source-level regression checks.
- Added the source-level regression suite to GitHub Actions before Android unit tests, lint, and APK assembly.
- Added a separate CI artifact for generated Room schema snapshots (warning-only if the build has not generated any snapshots).

## Verification performed in this environment
- All six existing Python source/SQL regression scripts passed.
- AndroidManifest XML parsed successfully.
- CI and Room schema export configuration assertions passed.
- ZIP archive integrity checked after packaging.

## Limitations
- This environment has JDK 21 and Kotlin compiler 1.9, but no Gradle executable, Gradle wrapper JAR, or Android SDK. The Android Gradle build, Room KSP generation, Android Lint, emulator/device install, and APK runtime tests were therefore not run here.
- The GitHub Actions workflow has been configured to run those checks when pushed to the repository, but no remote workflow run was triggered from this environment.
