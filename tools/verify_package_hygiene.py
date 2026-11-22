"""Verify release-source packaging and essential Android project files."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
required = [
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle.properties",
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt",
    "app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt",
    "app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt",
    "app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt",
    ".github/workflows/android-build.yml",
    "tools/verify_all.py",
]
missing = [name for name in required if not (ROOT / name).is_file()]
assert not missing, f"Missing required project files: {missing}"

manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
assert manifest.tag == "manifest"

for path in ROOT.rglob("*"):
    if not path.is_file():
        continue
    assert "__pycache__" not in path.parts, f"Python cache leaked into source: {path}"
    assert path.suffix not in {".pyc", ".pyo"}, f"Compiled Python file leaked into source: {path}"

ignore = (ROOT / ".gitignore").read_text()
for pattern in ("__pycache__/", "*.py[cod]", "local.properties", "**/build/"):
    assert pattern in ignore, f"Missing ignore rule: {pattern}"

print("Development 36 package hygiene checks: PASS")
