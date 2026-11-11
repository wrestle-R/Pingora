# P16: Download verified updates in-app and install with approval

**What to build:** Prior version updates; busy deferral; OS handoff; signature failures keep current version usable; private authentication proven.

**Blocked by:** P15

**Status:** blocked

**Spec:** [Product plan](../../plan.md)

## Acceptance criteria

- [ ] Prior version updates.
- [ ] Busy deferral.
- [ ] OS handoff.
- [ ] Signature failures keep current version usable.
- [ ] Private authentication proven.

## Verification

2026-11-11: update import ownership now runs through UpdateImportCoordinator on the main thread. Starting a new import invalidates the previous candidate; overlapping imports are refused; stale results after destruction are discarded. Files handed to Android's installer survive screen teardown. Three JVM tests exercise these cases with real temporary files. Android debug build/all148tests pass (zero failures/errors/skips); optimized0.1.1 release APK builds and verifies with the preserved signing identity/code1001.

Rust public install coordinator approval/busy gates have a regression check that staged bytes remain untouched; full10tests and Clippy with warnings denied pass.

Actual signed Linux0.1.0 -> locally built/signed0.1.1 installation upgrade passed through the real installer. Both installed GUIs start in an isolated home; a saved paused/clipboard-disabled policy survives loading and upgrade; old executable remains; downgrade is rejected without changing current state/launcher. Reproduce on a display host with `python3 docs/probes/verify-linux-upgrade.py OLD_ASSETS NEW_ASSETS`. Candidate files are ignored under release-private/development-0.1.1. This is a local development candidate, without a0.1.1tag/hosted run/public release.

Android OS installation/upgrades and actual call/transfer busy detection still need device acceptance. Private GitHub authentication is proven for the README installer (P15); the app's published-release downloader remains a separate pending check because current releases are drafts.

## Comments

Full implementation and milestone pushes are authorized. Preserve the published-version/signing identities; keep platform-specific acceptance distinct.

## Implementation checkpoint (2026-11-11)

Source paths now exist for this ticket; see [combined implementation evidence and remaining acceptance](../implementation-verification.md). Build/test evidence does not complete the real-device/desktop/release criteria above. Retain unfinished checkboxes until those exact outcomes are demonstrated.

Re-tested the real signed0.1.0→latest rebuilt/signed0.1.1 Linux candidate: both installed GUIs start, saved paused/disabled policy survives, previous executable is retained and downgrade is rejected. Final Android candidate verifies/installs over current0.1.1; this same-version replacement is not a new prior-version OS-update acceptance claim.
