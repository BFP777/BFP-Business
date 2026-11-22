"""Static checks for the Development 34 Android CI/build configuration."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
workflow = (root / ".github/workflows/android-build.yml").read_text()
root_gradle = (root / "build.gradle.kts").read_text()
app_gradle = (root / "app/build.gradle.kts").read_text()
db = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt").read_text()
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()

# CI must use a compatible, explicit toolchain and run tests/lint before packaging.
assert "gradle-version: 8.9" in workflow
assert "java-version: '17'" in workflow
assert ":app:testDebugUnitTest" in workflow
assert ":app:lintDebug" in workflow
assert ":app:assembleDebug" in workflow
assert "if-no-files-found: error" in workflow

# Project plugin versions and SDK declarations must remain explicit.
assert 'id("com.android.application") version "8.7.3"' in root_gradle
assert 'id("org.jetbrains.kotlin.android") version "2.0.21"' in root_gradle
assert 'id("com.google.devtools.ksp") version "2.0.21-1.0.27"' in root_gradle
assert "compileSdk = 35" in app_gradle
assert "minSdk = 24" in app_gradle
assert "targetSdk = 35" in app_gradle

# Room migrations remain explicit and destructive fallback stays disabled.
assert "version = 10" in db
assert ".addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)" in db
assert "fallbackToDestructiveMigration" not in db

android_ns = "http://schemas.android.com/apk/res/android"
activities = manifest.findall("application/activity", {})
assert any(a.get(f"{{{android_ns}}}name") == ".MainActivity" and
           a.get(f"{{{android_ns}}}exported") == "true" for a in activities)

print("Development 34 build-readiness checks: PASS")
