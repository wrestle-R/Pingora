# Native implementation checkpoint — 2026-11-11

This records source implementation and observed checks. It is not full release or friend-beta qualification.

## Implemented source paths

- Android: the sole enabled Pingora bridge carries bounded envelopes over the KDE-derived paired LAN link. Default-off endpoint policies, pause/revoke handling, session rotation and replay checks guard features. Notification listener/normalizer, source-version-aware reply/read/dismiss, bounded inbox/search/clear, reconnect reconciliation and offline dismissal acknowledgements are wired. OTP retention matches ordinary messages; redacted content is not fabricated.
- Desktop: local Tauri UI connects to the isolated Qt service. Rust session, history, native notification/lock adapters and a selected-app-only D-Bus observer support both directions. Unknown lock state hides content; diagnostics omit content by default.
- Clipboard: foreground send, shade tile and widget; received text is explicit, with native clipboard integration where available. Transfers require approval, stream chunks and verify hashes before collision-safe commit. SAF roots/token browsing are read-only and revocable. Received URLs/files require explicit open. Battery and bounded ring/stop paths are wired.
- Optional calls: fixed PipeWire telephony interfaces from the pinned Foner reference, explicit answer/decline/hangup and routing controls. An absent backend reports unavailable. No real Bluetooth audio pass is claimed.
- Slumber: paired/default-off status and activation use the existing lock-safe IPC. A finite sleep/slumber lid action is wired. `docs/releases/tools/slumber-adapter.py` dry-run recognizes the installed source; `--apply` adds only the narrow setter, with a backup. It has not changed or restarted the live shell. No unlock command exists.
- Updates: desktop signed metadata/check/download, explicit approval, busy deferral and portable install/handoff. Android imports a signed ZIP downloaded through the recipient’s own GitHub/browser account, verifies Ed25519 metadata, size/hash, package/version/APK signer and opens the OS installer only after approval and idle-state checks. This is a browser-download/import handoff, not an embedded private GitHub-token client.

## Observed checks

- Android offline `assembleDebug testDebugUnitTest`: passed; 145 tests, zero failures/errors/skips. The compiler reports existing upstream obsolete-resource/deprecation warnings; no warning-free Android claim.
- Rust: nine tests passed, including session replay/default capabilities, conversation/version/history/OTP policy, markup/Unicode, transfer approval/traversal/collision/corruption and update signature/tamper/failed-install preservation.
- Rust Clippy with warnings denied passed again with the portable-package adapter.
- All desktop UI JavaScript parses with `node --check`.
- Signed installer fixture check passes: valid signed package installs without executing fixture programs; changed archive and invalid signature are rejected while existing installed bytes are preserved. Reproduce with `python3 docs/probes/verify-installer.py`. This uses temporary test keys, not the production signer.
- Linux optimized package build passed. Extracted-package checks passed: desktop health command executes, no host libraries are missing on this qualified host, the LAN service resolves its own bundled library through relative RPATH, and installer/trust resources exist. Packaged desktop stayed running through a controlled six-second launch test. Initial RPATH/source-directory issues were found and corrected before this pass.
- Device panel now recognizes an Android16 emulator. Pingora installs/opens, but the emulator reports `Application Not Responding: com.android.systemui`; its event log records System UI startup failure. UI acceptance is not passed. A relaunch retry still shows the blocked System UI surface. Device UI verification is stopped; no alternate ADB UI automation is used.

## Release boundaries

No tag or release was published. Signing material exists only in ignored `release-private/` with private permissions; public update anchors are tracked. No secret values or real environment files were created in this pass. No Varte sync is pending.

The tag workflow targets a dedicated `pingora-release` self-hosted runner and `native-release` environment. Those must be provisioned/qualified with protected signing storage before the workflow can run. The entrypoint requires a clean exact stable tag, matching runtime versions and Android version-code mapping, builds/tests/signs both artifacts, includes corresponding source, and only creates a complete draft. It does not claim CI has run.

The Linux tar package depends on compatible host GTK/WebKitGTK/Qt/KF libraries; it is not a universal AppImage or proof of other-distro compatibility. GNOME/Plasma lock/sidebar checks, real source-app notification/action checks, phone storage/provider/lifecycle checks, call audio in both directions, signed prior-version upgrade and long-run resource profiling remain required acceptance evidence. Internal progress is never imported into the website.

Android optimized release build initially failed because a release-only pinned Compose artifact was missing from the offline cache. An online retry is in progress; no release APK/signature or CI publication is claimed yet.
