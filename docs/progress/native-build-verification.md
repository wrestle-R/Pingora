# Native build and adapter probes

Date: 2026-11-10. P01 is in progress; this is not a native-app completion report.

## Desktop shell milestone

New Rust/Tauri 2 source is in `tauri/`, serving only local bundled files with an explicit CSP. Cargo.lock pins dependencies. The single native `health` command takes no frontend arguments and runs a fixed, three-second-bounded QuickShell Slumber status query. It returns selected typed fields, never arbitrary upstream error text. Pairing and transport report unimplemented.

- `cargo build --locked --manifest-path tauri/src-tauri/Cargo.toml`: passed on rustc/cargo 1.99.1, GTK 3.24.52 and WebKitGTK 2.54.2.
- `cargo fmt --manifest-path tauri/src-tauri/Cargo.toml -- --check`: passed.
- `node --check tauri/ui/app.js`: passed.
- `cargo clippy --locked --manifest-path tauri/src-tauri/Cargo.toml -- -D warnings`: passed.
- `pingora-desktop --probe-health`: returned `paired:false`, unimplemented transport, pending notification adapter, `slumber.available:true` and a valid current lid preference. No side effects requested.
- Native binary stayed running through a ten-second window-launch smoke check with no stderr, then was terminated by timeout (exit 124). This is a lifecycle smoke check, not proof of rendered webview accessibility or Android connectivity.

## Candidate source evidence

Exact upstream revisions remain in `docs/upstream-candidates.json`. Source archives were extracted only under `/tmp/pingora-upstream/`, not imported as app implementations.

- KDE desktop: CMake/Ninja configuration passed against installed Qt 6.12 / KF6 dependencies. Full upstream build passed (603 Ninja steps); isolated offscreen D-Bus-session CTest passed 7/7. Its full probe includes upstream plugins that Pingora will exclude from shipping.
- Foner: CMake/Ninja configuration and build passed. `QT_QPA_PLATFORM=offscreen ctest --test-dir /tmp/pingora-upstream/build-foner --output-on-failure`: 4/4 passed. No call was made and Bluetooth audio is unverified.
- Android: Gradle wrapper 9.6, AGP 9.5.0, Kotlin 2.4.21, compile/target SDK 37, minimum SDK 23 read from pinned source. Java 21 resolved build plugins; `assembleDebug` then failed with “SDK location not found”. Local SDK tooling download is in progress, with the official SHA-256 pinned for verification. No Android APK exists yet.

## Actual file-notice inventory

[Machine-readable inventory](native-license-inventory.json), reproduced by `python3 docs/probes/audit-upstream.py /tmp/pingora-upstream`, records relative paths, SHA-256 and detected source headers: 239 Android files, 409 KDE desktop files, 41 Foner files. It does not inspect binary resources, resolved dependencies or prove distribution clearance.

216 Android code headers and 327 KDE desktop headers use the KDE GPL alternatives expression. Foner’s 41 inspected files declare GPL-3.0-or-later. Other KDE files include BSD/Apache/LGPL/MIT expressions. The scanner marks 23 Android and 60 KDE desktop files for manual header review, mostly build files/tests, plus helpers and bundled code. Manual inspection confirms `core/backends/lan/mdns.h` has a public-domain statement despite no SPDX header. Do not silently infer missing notices or drop their review flags. No reused candidate code has been shipped in Pingora.

## Native notification evidence

`org.freedesktop.Notifications` belongs to QuickShell on this machine. GetServerInformation returns quickshell/quickshell with spec 1.2; GetCapabilities advertises persistence, body/markup/hyperlinks/images and actions. An explicit synthetic fixture created, replaced and closed only its own test notification; the replacement preserved its ID. `docs/probes/notify-native.py` reproduces this test without reading existing contents.

Actual clicked action/dismiss callbacks, passive observation, lock/unlock privacy and Plasma/GNOME still require P02 evidence. No desktop service was replaced or restarted. Slumber’s status/activate/idleSuspend methods were identified, but only status invoked. See [Slumber contract](../slumber.md).

## Own native service milestone

The isolated KDE-derived service build in `tauri/native/` passed all 192 Ninja steps. `source.json` pins the tarball SHA-256. A short-lived test with temporary XDG_CONFIG_HOME proved ownership of `io.github.wrestle_r.pingora`, only `LAN|lan|enabled` exposed, and Rust readback reporting `native_service:true`/`paired:false`. It was stopped afterward. Config/certificate state was isolated; no keys or arbitrary daemon errors are included in this report.

The service’s plugin registry is deliberately empty and does not scan the installed KDE plugin directory. Bluetooth transport is compiled out. Nothing was installed or registered for autostart. This proves a concrete Qt/D-Bus/Rust integration path; it does not prove phone pairing or packaged lifecycle behaviour.

An additional sender-restricted D-Bus monitoring probe observed its own synthetic Notify call. Its match rule includes the probe client’s unique sender, so unrelated notification contents are not subscribed to or logged. Reproduce with `/usr/bin/python docs/probes/observe-synthetic-notifications.py`.

Android SDK tooling was checksum-verified and installed in `~/.cache/pingora/android-sdk`. Java’s IPv4 preference resolved stalled manifest fetching. Platform37.0 and build-tools37 were explicitly installed; AGP then selected build-tools36 and platform-tools37.0.1 automatically. The Pingora Android probe fork has its own application ID/icon, no registered device plugins and no plugin manifest entrypoints; build verification is in progress. Concurrent original/probe Gradle transforms contended for a cache lock; the redundant original build was stopped and only the Pingora build is being retried. A rebranding typo in the TV banner resource reference was fixed before retrying; no APK or test pass is claimed yet.

## Android build milestone

The Pingora fork now passes `assembleDebug testDebugUnitTest` with Java21/Gradle9.6/AGP9.5 and the local SDK. XML test results: 24 suites, 140 tests, zero failures/errors/skips. These include inherited source-level plugin fixtures, not enabled Pingora features or real device acceptance. APK: `app/build/outputs/apk/debug/pingora-android-debug.apk` (development/debug only, ignored build output).

`aapt2 dump permissions` confirms no SMS, contacts, microphone, broad-storage or Bluetooth permission in the packaged app. The registry has no loadable plugins; removed-feature components are absent from the manifest. Application ID is `io.github.wrestle_r.pingora.debug`, separate from KDE Connect. Original source headers/COPYING/LICENSES are retained. Upstream CI/store publishing metadata and public debug key are excluded; trailing whitespace/blank EOF fixes do not change source wording.

ADB installation was attempted after build, but ADB reported “no devices/emulators found”. No app was installed, no phone permissions were granted, and phone compatibility/pairing remain unverified. User was asked to reconnect while independent desktop work continues.
