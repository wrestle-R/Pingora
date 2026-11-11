# P11: Browse explicitly shared phone folders read-only

**What to build:** Choose/revoke phone folder, laptop lists/downloads, write/delete denied, traversal rejected.

**Blocked by:** P10

**Status:** blocked

**Spec:** [Product plan](../../plan.md)

## Acceptance criteria

- [ ] Choose/revoke phone folder, laptop lists/downloads, write/delete denied, traversal rejected.

## Verification

Not started. Record actual commands, observed behavior and limitations here when work begins.

## Comments

Preparation ticket only; status is not authorization to start implementation. No app/release artifact exists yet.

## Implementation checkpoint (2026-11-11)

Source paths now exist for this ticket; see [combined implementation evidence and remaining acceptance](../implementation-verification.md). Build/test evidence does not complete the real-device/desktop/release criteria above. Retain unfinished checkboxes until those exact outcomes are demonstrated.

## Public folder boundary check, 2026-11-11

Added a Robolectric test using a selected synthetic tree and mocked document provider. Folder browsing issues opaque tokens instead of exposing provider URIs. Unknown/traversal tokens cause no provider query; revoking the read grant blocks previously issued list/read tokens without opening a stream. Full152AndroidJVMtests/debug build pass. This validates the API boundary, not an OEM provider or physical SAF picker.
