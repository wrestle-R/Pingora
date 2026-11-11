# P03: Pair devices and see truthful connection/permission health

**What to build:** Phone/laptop approve pairing; unauthorized connection rejected; revoke works; missing permission has a useful fix.

**Blocked by:** None (P01 completed)

**Status:** in-progress

**Spec:** [Product plan](../../plan.md)

## Acceptance criteria

- [ ] Phone/laptop approve pairing.
- [ ] Unauthorized connection rejected.
- [ ] Revoke works.
- [ ] Missing permission has a useful fix.

## Verification

2026-11-10: Added Tauri LAN service start/stop, device listing, pairing request/accept/cancel and revoke controls. Acceptance rechecks the current verification value against the user-confirmed value. Only the child process started by this window can be stopped.

Verification: `cargo build --locked`, `cargo clippy --locked -- -D warnings`, `cargo fmt`, `node --check tauri/ui/app.js`, and `git diff --check` pass. Earlier session reports successful physical-phone pairing and revoke through native probes; this continuation has not independently repeated that device check.

Remaining: exercise the complete webview pairing flow on the phone, unauthorized-peer rejection, reconnect/process death, and actionable permission health. No completion or release claim.

## Comments

Full implementation authorized by user. Development builds exist; release qualification remains pending.

### Health implementation continuation

Added Android Connection health navigation with observed paired/reachable counts, notification permission, Android 17 local-network permission, battery restriction status and settings recovery links. Feature availability is explicit. Removed inherited accessibility remote-input service from manifest. Desktop refresh failures clear stale controls and verification confirmations. Android offline assembleDebug passes; JavaScript syntax and diff checks pass. Permission settings navigation still requires device acceptance; no grants automated.

### Unpaired packet boundary

Added explicit cancellation, payload cleanup and immediate return before feature dispatch for unpaired Android peers. Regression first failed on the missing cancellation assertion, then the implementation was applied. Desktop service stop now retains child ownership if stop fails and safely clears already-exited children. Clippy with warnings denied passes. Shared Device tool reports Android SDK not found despite the build SDK path; UI acceptance remains unavailable through that tool.

Android assembleDebug and complete testDebugUnitTest pass after the boundary change. Desktop build and JS syntax pass; action failures now remain visible after the automatic refresh. Runtime device/UI and multi-desktop qualification remain pending.

### Physical reconnect check, 2026-11-11

After the user restored USB debugging and USB tethering, the Nothing A001 Android16 device tool opened the release app. Sharing controls reported archlinux reachable and default-off features. Connection health UI reported one paired/one reachable device, app notifications allowed, and correctly warned that battery optimization is active. The isolated native daemon was absent before this check and was started explicitly; native health then reported paired/service available. This proves reconnect/visible health for the saved pair, not revoke, new pairing, all feature permissions or source-app notification acceptance.

## Paired-page navigation fix, 2026-11-11

Physical testing exposed an empty paired page: the bridge had no plugin entry buttons. Added direct Sharing controls and Inbox buttons. Startup no longer assumes a restored fragment exists when FragmentManager returns null; saved health/sharing/inbox destinations now have explicit fallbacks. Full152Androidtests/debug build pass; updated release installation/physical navigation check follows.
