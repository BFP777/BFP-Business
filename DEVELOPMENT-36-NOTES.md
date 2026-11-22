# Development 36 — Source Packaging Hygiene

## Changes
- Added ignore rules for Python bytecode/cache directories, temporary files, and SQLite restore staging/rollback files.
- Removed generated `__pycache__` directories from the source tree so they cannot pollute the deliverable ZIP.
- Added `tools/verify_package_hygiene.py` to check essential Android project files, parse the manifest, and reject generated Python cache files.
- Added the packaging check to the single offline verification entry point (`python3 tools/verify_all.py`).

## Verification
- All offline regression scripts passed.
- AndroidManifest XML parsed successfully.
- Package hygiene checks passed.
- ZIP integrity checked after packaging.

## Limitations
This is source/package hygiene verification only. It does not replace Gradle compilation, Android Lint, APK installation, or on-device workflow tests. The current execution environment has JDK and kotlinc but no Gradle executable, Gradle wrapper JAR, or Android SDK, so a genuine Android APK build cannot be run here.
