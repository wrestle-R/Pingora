# P12: See battery and ring either paired device

**What to build:** Truthful battery/connection states; explicit ring, Stop action, timeout; mute/DND limits explained.

**Blocked by:** P03, P08

**Status:** blocked

**Spec:** [Product plan](../../plan.md)

## Acceptance criteria

- [ ] Truthful battery/connection states.
- [ ] Explicit ring, Stop action, timeout.
- [ ] Mute/DND limits explained.

## Verification

Not started. Record actual commands, observed behavior and limitations here when work begins.

## Comments

Preparation ticket only; status is not authorization to start implementation. No app/release artifact exists yet.

## Implementation checkpoint (2026-11-11)

Source paths now exist for this ticket; see [combined implementation evidence and remaining acceptance](../implementation-verification.md). Build/test evidence does not complete the real-device/desktop/release criteria above. Retain unfinished checkboxes until those exact outcomes are demonstrated.

## Ring cleanup audit, 2026-11-11

Native ring playback now retains its originating peer and rechecks the effective ring permission. Pause, revoke or disconnect stops an active ring instead of waiting for its deadline. Stop/timeout also closes only Pingora's own ring notification. Rust13tests and strictClippy pass. Actual audible playback, mute/DND and physical permission-revoke acceptance remain open.
