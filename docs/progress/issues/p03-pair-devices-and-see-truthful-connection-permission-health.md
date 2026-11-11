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

Final signed build installed normally over0.1.1. Physical paired page now visibly contains Sharing controls and Inbox; tapping Sharing controls opens the correct screen. Saved pairing remains reachable after install and scoped folder revocation.


2026-11-11 owner-reported connection repair: split Pingora discovery onto UDP1816/TCP1816–1864 and `_pingora._udp` on both endpoints; remove upstream pairing notification that opened KDE settings (including its comparison-bypassing Accept action). Desktop starts its owned LAN service on launch. Both endpoints must be upgraded together; existing0.1.2 release bytes are unchanged; preparing matching0.1.3 builds. New paired desktop policies are persisted, frontend and Android sharing defaults enabled per latest owner request; saved disables are retained. System grants and locked-preview privacy remain explicit. Native rebuilt successfully, Android Kotlin compilation, Next and React production builds, Rust15 and strict Clippy pass. Physical pairing with these new bytes is not yet verified.


2026-11-11 UX continuation (owner screenshot): reorganized Android Sharing into Share/Activity/Controls/Settings, selected-peer header and selector, action-level availability, outlined link validation, persistent link drafts, one Snackbar result instead of appended duplicate errors, view-scoped status/transfer refresh, human transfer states and optional Slumber disclosure. Paired launcher/device selection goes to Share, toolbar names destinations, Inbox/Connection Back returns to Share. Health/Inbox now use Material controls. Android0.1.4/code1004 optimized build + signature verification + normal physical install-r pass; Linux0.1.4 optimized package verifier passes. Physical UI check BLOCKED by DEVICE_IN_USE sessiont3-5d152b52439b579750e1b35c even after documented10secwait; did not reuse/close another session or fall back to ADB input. User asked to reopen manually via async question. Ownerdesktop0.1.3 signed install + ownedLANservice running confirmed; systemKDEdaemon untouched. Matching0.1.4publication in progress.
