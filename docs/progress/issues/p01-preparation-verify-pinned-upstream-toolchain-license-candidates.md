# P01: Preparation: verify pinned upstream/toolchain/license candidates

**What to build:** Android and Tauri/native-service build paths and dependency requirements documented; actual file license audit; no unsupported compatibility promises.

**Blocked by:** None

**Status:** done

**Spec:** [Product plan](../../plan.md)

## Acceptance criteria

- [x] Android and Tauri/native-service build paths and dependency requirements documented.
- [x] Actual file license audit.
- [x] No unsupported compatibility promises.

## Verification

2026-11-10: native build paths and concrete bridge demonstrated. Tauri build/fmt/Clippy pass; native CLI reads Slumber status and the own KDE-derived LAN service over D-Bus. Service builds with its own bus/config identity, Bluetooth off and no plugin registry; short-lived isolated-config test exposes only LAN. Full upstream KDE build +7 CTests pass; Foner build +4 CTests pass. Android fork debug APK builds and 140 JVM tests pass (24 suites, no failures/errors/skips). Dependency requirements and reproducible commands are documented.

Actual file-notice audit records 239 Android,409 KDE desktop,41 Foner code/build files with SHA-256 and detected expressions. Missing-header/resource/dependency distribution review remains explicit rather than silently inferred. Original notices are retained in the imported Android source; no signed distribution/license clearance is claimed. See [complete evidence and limitations](../native-build-verification.md).

## Comments

This completes the build/source-audit probe ticket, not P03 pairing or native feature acceptance. P02 and P03 can proceed. Phone installation is currently blocked because ADB lost the device; no app was installed. P02 still needs actual privacy/actions/observation checks on all launch desktops. Signed releases remain blocked until source/dependency notice review, identities/signing and device compatibility gates are satisfied.
