# Pingora Android

Native Android build-path probe based on pinned KDE Connect source. Provenance and modifications are in `UPSTREAM.json`; original project notes are preserved in `UPSTREAM.md`, with original source headers, COPYING and LICENSES. Internal source namespaces remain upstream names for traceability; the APK application ID is `io.github.wrestle_r.pingora.debug`, distinct from KDE Connect. Release identity is not yet finalized for signed beta.

## Build

Java 21, Gradle 9.6 wrapper, compile/target SDK 37, Android Build-Tools 36 (selected by AGP 9.5), and platform-tools are required. SDK 37 is packaged as `platforms;android-37.0`. This machine’s local SDK is at `~/.cache/pingora/android-sdk`; use your actual installed path.

```sh
ANDROID_HOME=/path/to/android-sdk JAVA_HOME=/path/to/jdk21 ./gradlew --no-daemon assembleDebug
```

No `.env` or `local.properties` is needed. Debug signing uses Android’s ordinary local debug key; do not commit any keystore. No release key or signed-beta identity has been provisioned.

## Probe boundary

The APK intentionally advertises **no loadable device plugins**. The plugin registry filters out all candidate classes before instantiating them. Unsupported plugin components (keyboard, media, remote commands, SMS and other plugin entrypoints) and broad permissions are absent from the manifest. Bluetooth transport is disabled. Only the upstream LAN pairing/build infrastructure is being evaluated; Android/Linux approved pairing, rejection/revocation and feature behaviour are not accepted merely because the APK builds.

This is not a finished phone companion, and it must not be presented as a release. Plugin source retained for provenance is not permission to enable removed features. Add approved capabilities only with their corresponding ticket and endpoint checks; the original KDE clipboard/transfer/notification defaults do not automatically satisfy Pingora’s stricter contract.

See [native evidence](../docs/progress/native-build-verification.md), [architecture](../docs/architecture.md), [approved boundaries](../docs/testing.md) and [progress](../docs/progress/README.md).
