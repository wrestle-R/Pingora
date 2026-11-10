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
