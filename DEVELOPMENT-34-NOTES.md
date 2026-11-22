# Development 34 — Android Build and CI Readiness

## Changes
- Updated GitHub Actions to use JDK 17, Android SDK setup, and Gradle 8.9 explicitly.
- CI now runs JVM unit tests and Android lint before assembling the debug APK.
- Added a non-empty APK check before artifact upload; missing APKs now fail the workflow rather than silently producing an empty artifact.
- Added a 14-day artifact retention policy, read-only repository permission, and per-branch concurrency control.
- Added a static build-readiness regression script covering CI toolchain/tasks, Android SDK levels, plugin versions, Room migration registration, and launcher activity export configuration.

## Verification performed locally
- `tools/verify_dev34_build_readiness.py` — PASS.
- Existing Development 33 regression scripts — run as part of this package validation.
- Android manifest XML parse — PASS.
- ZIP integrity — PASS after packaging.

## Limitations
- This environment does not have Gradle, a Gradle Wrapper, or Android SDK installed. Therefore no Gradle compilation, Android lint execution, emulator/device test, or APK build was performed here.
- The GitHub Actions workflow is configured to perform those checks when pushed to the repository or manually dispatched. Its result must be observed in GitHub Actions before treating the APK as build-verified.
