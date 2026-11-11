# Non-call testing, 2026-11-11

Calls are deferred. The user explicitly skipped real source-app notification/reply checks and physically locking the laptop during this pass. A skipped check is not a pass.

## Automated checks

- Android: 153 JVM/Robolectric tests, zero failures/errors/skips. Debug and optimized release builds checked separately. Folder coverage includes opaque tokens, unknown/traversal rejection, revoked grants, scoped revocation and duplicate-reference preservation.
- Rust: 15 tests and Clippy with warnings denied pass. Coverage includes sessions/replay/default-off policy, source versions/history/OTP retention, transfer approval/multi-chunk/empty-file/early-completion/cancel/disconnect, update signature/tampering/approval/busy preservation.
- React: seven interaction tests and production build pass. Pairing comparison resets, lock-private inbox content, cumulative permission saves, typed input retention, outgoing cancellation and update approval invalidation are covered.
- Next.js: type-check and production build pass.

## Observed integration checks

- Nothing A001, Android16, release app0.1.1: saved pairing reconnects through USB tethering. Phone UI reports one paired/one reachable device and correctly identifies active battery optimization. Sharing defaults are off. Signed replacement APK installs normally over the existing identity.
- Owner Arch/Hyprland/Quickshell: synthetic notification replacement preserves its native ID; close affects only the synthetic item. Passive observation is restricted to the probe's own D-Bus sender; no real notification bodies are logged.
- Installer public boundary: signed fixtures install; corrupted bytes/wrong signatures are rejected while installed bytes remain. Fixture programs are never executed.
- Optimized desktop starts in an isolated fresh profile/session bus without XDG Downloads configuration. Test processes are stopped afterward.
- Latest Linux package verifier passes required resources, public trust, host dependencies, bundled native-library resolution and headless health. Local development artifacts are not a new published release.
- The shared native privacy guard reports `private_hidden=false` on the observed unlocked owner session. No physical locked-session pass is claimed.
- Physical folder picker: the user selected a test grant named Pingora and explicitly confirmed it was safe to revoke when its label differed from our fixture directory. On the updated signed app, its individual revoke button removed that row. No global revoke was used. Only our known45-byte fixture and its empty directory were removed; no selected-folder contents were read or deleted.

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
- Desktop preview implementation now reveals escaped text when unlocked and refreshes or closes tracked native previews when privacy/policy changes. Locked/unknown states redact content unless explicitly opted in; source actions remain guarded. Failed closes are retried. Physical daemon lock-transition acceptance remains unverified because the user skipped locking.

These results do not establish complete friend-beta or production qualification. Existing P03–P17/P20/P22 acceptance remains authoritative.

## Scoped folder cleanup implementation

Individual folder revocation now removes only the selected root, preserving unrelated roots and duplicate references until their last grant reference is removed. Folder selection reuses an existing root ID and checks the32-root bound before persisting a new grant. This avoids acquiring untracked grants at the limit.

## Final observed build/install checks

The frozen-source Android release build and all153tests pass. The final APK verifies with the existing signer and installs normally over0.1.1 on the physical phone. The updated paired page visibly contains Sharing controls and Inbox; tapping Sharing controls opens it. After scoped grant revocation, the saved laptop remains reachable and sharing stays default-off.

The final Linux package passes its verifier. Its signed0.1.0→rebuilt0.1.1 upgrade passes real installer execution and both installed GUI starts in an isolated home: saved paused/disabled policy survives, previous executable is retained and downgrade is rejected without changing the working installation. Test homes/processes are removed. Updated signed artifacts/metadata stay ignored under release-private/noncalls-checked-0.1.1; no tag or release was published.

## Desktop preview implementation continuation

Native delivery now checks current local policy instead of a stale queued show_locked value. Initial and replacement payloads share one projection: unlocked content is escaped, locked/unknown content is generic by default, and locked source actions are omitted. Tracked previews are refreshed quietly on privacy changes; disabled/revoked entries retire. Expired native IDs are not deliberately recreated on unlock. Failed redaction/close attempts retain tracking for retry rather than forgetting a potentially visible preview.

Privacy refresh is sampled at500ms plus adapter/daemon response time; this is not an instantaneous lock-screen guarantee. Fifteen Rust tests and strict Clippy pass, including payload content/action redaction and markup escaping. Actual physical lock/unlock/daemon-failure acceptance remains distinct from this automated evidence.

A native D-Bus integration fixture now runs on its own dedicated daemon/address (never the user session): the actual Rust Notify adapter sends escaped readable→redacted→readable payloads using the same native ID and closes that ID. All15Rusttests and strictClippy pass. The dedicated test daemon/thread are stopped after the check. This does not substitute for physical lock/daemon acceptance.

The privacy-enabled rebuilt Linux candidate also passes the real signed0.1.0→0.1.1 upgrade probe: both installed GUIs start, paused/disabled policy survives, the previous executable remains and downgrade is rejected. Temporary profiles/processes are cleaned up. No new release was published.
