# Development 68 – Permanent APK signing

- Added environment-driven stable signing configuration for debug and release variants.
- Added a GitHub Actions workflow that restores the permanent keystore from Actions secrets and verifies APK signature.
- Kept applicationId `in.bhajan.bfpbusinesspro` unchanged.
- Increased versionCode from 519 to 520 and versionName from 5.19.0 to 5.20.0.
- Added setup notes for required repository secrets and key custody.
- Important: updates install over an existing app only if the signing certificate matches the installed app. If the currently installed app was signed with a different debug key, a one-time reinstall may be required after backing up data.
