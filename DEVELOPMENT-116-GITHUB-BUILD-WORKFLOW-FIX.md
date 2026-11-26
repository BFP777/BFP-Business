# DEV116 — GitHub Build Workflow Alignment

Base: DEV115 source.

## Changes
- Replaced the stale signed-build workflow checks (which still expected version 5.58.0 / code 558) with DEV115 version 5.60.0 / code 560.
- Updated workflow labels and artifact names to DEV115.
- Kept stable signing secret checks and APK signature/package-version verification.
- Updated the debug CI artifact label to DEV115.

## Validation
- Workflow text assertions and ZIP integrity passed.
- GitHub Actions execution, secrets availability, APK compilation, and device installation are not verifiable from this local packaging step. Run the workflow in the repository to confirm.
