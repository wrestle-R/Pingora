# Non-call testing, 2026-11-11

Calls are deferred. The user explicitly skipped real source-app notification/reply checks and physically locking the laptop during this pass. A skipped check is not a pass.

## Automated checks

- Android: 153 JVM/Robolectric tests, zero failures/errors/skips. Debug and optimized release builds checked separately. Folder coverage includes opaque tokens, unknown/traversal rejection, revoked grants, scoped revocation and duplicate-reference preservation.
- Rust: 13 tests and Clippy with warnings denied pass. Coverage includes sessions/replay/default-off policy, source versions/history/OTP retention, transfer approval/multi-chunk/empty-file/early-completion/cancel/disconnect, update signature/tampering/approval/busy preservation.
- React: seven interaction tests and production build pass. Pairing comparison resets, lock-private inbox content, cumulative permission saves, typed input retention, outgoing cancellation and update approval invalidation are covered.
- Next.js: type-check and production build pass.

## Observed integration checks

- Nothing A001, Android16, release app0.1.1: saved pairing reconnects through USB tethering. Phone UI reports one paired/one reachable device and correctly identifies active battery optimization. Sharing defaults are off. Signed replacement APK installs normally over the existing identity.
- Owner Arch/Hyprland/Quickshell: synthetic notification replacement preserves its native ID; close affects only the synthetic item. Passive observation is restricted to the probe's own D-Bus sender; no real notification bodies are logged.
- Installer public boundary: signed fixtures install; corrupted bytes/wrong signatures are rejected while installed bytes remain. Fixture programs are never executed.
- Optimized desktop starts in an isolated fresh profile/session bus without XDG Downloads configuration. Test processes are stopped afterward.
- Latest Linux package verifier passes required resources, public trust, host dependencies, bundled native-library resolution and headless health. Local development artifacts are not a new published release.
- The shared native privacy guard reports `private_hidden=false` on the observed unlocked owner session. No physical locked-session pass is claimed.
- Physical folder picker: user selected only the synthetic acceptance folder. Final scoped revocation/cleanup and updated navigation observation are recorded below when completed.

## Fixes found by testing

- Outgoing transfer progress/cancellation was missing. Premature completion, failed-send cleanup, disconnect cleanup and expired approvals were corrected (5a9204a).
- Android stale notification removal could close a newer conversation mirror; unlocked previews were unreadable. A public test reproduced the failure and passed after correction (dbb8027).
- Native ringing now stops after permission/pause/disconnect and closes its own popup (1098514). Audible playback/revoke timing remains unverified physically.
- Paired device page lacked bridge entry buttons; missing restored-fragment handling and saved destination fallbacks were corrected (1f8afce).
- Folder revocation needed an individual action to preserve unrelated grants. Duplicate root references retain their grant until the final reference is revoked.

## Performance limits

A fresh unpaired profile sampled for60.4seconds after5seconds warmup stayed running: peak summed RSS373.2MiB, CPU0.27% of one core, maximum5processes. Shared RSS is counted repeatedly across processes. See react-desktop-idle-profile.json and docs/probes/profile-desktop.py. This is not a paired burst, long-running reconnect or battery qualification, and no budget pass is inferred.

## Open or skipped acceptance

- User-skipped: calls, real WhatsApp/Instagram/Gmail/SMS notification actions, physical laptop lock/unlock.
- Not available in this session: actual GNOME and Plasma compatibility sessions.
- Not yet qualified: full bidirectional physical clipboard/file transfer and SAF download/revoke flows, process death/reboot/suspend, low-storage failures on actual providers, audible ring/DND, real busy OS updater handoff and long-run paired load.
- Known conservative behavior: native desktop previews remain redacted with show_locked disabled; improving unlocked previews must preserve lock-safe behavior and needs daemon acceptance.

These results do not establish complete friend-beta or production qualification. Existing P03–P17/P20/P22 acceptance remains authoritative.

## Scoped folder cleanup implementation

Individual folder revocation now removes only the selected root, preserving unrelated roots and duplicate references until their last grant reference is removed. Folder selection reuses an existing root ID and checks the32-root bound before persisting a new grant. This avoids acquiring untracked grants at the limit.
