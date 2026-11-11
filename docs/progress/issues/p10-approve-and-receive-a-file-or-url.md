# P10: Approve and receive a file or URL

**What to build:** Approval every transfer; no auto-open; safe destination/collision/cancel; URL requires Open action.

**Blocked by:** P03, P08

**Status:** blocked

**Spec:** [Product plan](../../plan.md)

## Acceptance criteria

- [ ] Approval every transfer.
- [ ] No auto-open.
- [ ] Safe destination/collision/cancel.
- [ ] URL requires Open action.

## Verification

Not started. Record actual commands, observed behavior and limitations here when work begins.

## Comments

Preparation ticket only; status is not authorization to start implementation. No app/release artifact exists yet.

## Implementation checkpoint (2026-11-11)

Source paths now exist for this ticket; see [combined implementation evidence and remaining acceptance](../implementation-verification.md). Build/test evidence does not complete the real-device/desktop/release criteria above. Retain unfinished checkboxes until those exact outcomes are demonstrated.

## Transfer completion audit, 2026-11-11

Fixed missing outgoing progress/cancellation on both Android and desktop. The public transfer snapshot now includes direction, bytes sent and waiting/sending/sent states. Sender completion requires approval plus all bytes sent, including zero-byte files. Failed native offers/chunks release handles; disconnected peers cancel partial transfers and clear stale folder listings. Expired incoming approvals disappear at two minutes. Existing files remain untouched.

Verification: Rust13 tests/strict Clippy; Android150 JVM tests and debug build; React outgoing cancellation/terminal receipt regression plus existing UI safety tests. Multi-chunk, empty, premature completion, cancellation and disconnect cleanup use synthetic bytes/real temporary files. Physical phone remains absent from both device panel and ADB after the user reconnected, so actual LAN file-provider/device checks remain open. Calls are explicitly deferred by the user for this pass.
