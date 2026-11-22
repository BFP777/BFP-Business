# Dev68 – Permanent Android APK signing

This source config signs Debug and Release APKs with one stable key when the four `BFP_*` environment variables are provided. The GitHub Actions workflow `android-dev68.yml` requires these repository Actions secrets:

- `BFP_KEYSTORE_BASE64`: base64 text of the private JKS keystore file supplied separately.
- `BFP_KEYSTORE_PASSWORD`: keystore password.
- `BFP_KEY_ALIAS`: `bfp-business-pro`
- `BFP_KEY_PASSWORD`: key password.

Do not commit the `.jks` file or expose its passwords. Keep an offline backup of the keystore and passwords. Losing them means future APKs cannot update installations signed with this key.

Package identity remains `in.bhajan.bfpbusinesspro`; version is bumped to code 520 / name 5.20.0. Android can update an existing install only when its installed signing certificate matches this key and versionCode is higher. If the currently installed app was signed with a different key, a one-time uninstall/reinstall (after backing up app data) may still be necessary.
