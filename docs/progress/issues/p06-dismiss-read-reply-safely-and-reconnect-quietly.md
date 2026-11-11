# P06: Dismiss/read/reply safely and reconnect quietly

**What to build:** Source-supported actions work; timeout does not dismiss; stale offline dismissal cannot clear newer message; one reconnect summary.

**Blocked by:** P05

**Status:** blocked

**Spec:** [Product plan](../../plan.md)

## Acceptance criteria

- [ ] Source-supported actions work.
- [ ] Timeout does not dismiss.
- [ ] Stale offline dismissal cannot clear newer message.
- [ ] One reconnect summary.

## Verification

Not started. Record actual commands, observed behavior and limitations here when work begins.

## Comments

Preparation ticket only; status is not authorization to start implementation. No app/release artifact exists yet.

## Implementation checkpoint (2026-11-11)

Source paths now exist for this ticket; see [combined implementation evidence and remaining acceptance](../implementation-verification.md). Build/test evidence does not complete the real-device/desktop/release criteria above. Retain unfinished checkboxes until those exact outcomes are demonstrated.

## Notification-version regression, 2026-11-11

A public Android inbox test reproduced stale removal closing a newer conversation mirror. Mirror cancellation now checks the current active conversation version before removing its native slot. Unlocked Android mirrors show readable synthetic content while retaining private lock-screen visibility. The new Robolectric test failed before the fix and passes after it; full151JVMtests and debug build pass. Physical source-app action/lock checks remain separate.
