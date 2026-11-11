# P02: Preparation: validate native notification + privacy adapters

**What to build:** Existing sidebar identified; notification observation/replacement/actions/lock privacy demonstrated on all three desktops.

**Blocked by:** None (P01 completed)

**Status:** ready-for-agent

**Spec:** [Product plan](../../plan.md)

## Acceptance criteria

- [ ] Existing sidebar identified.
- [ ] Notification observation/replacement/actions/lock privacy demonstrated on all three desktops.

## Verification

P01 identified Quickshell, verified synthetic replacement/close and sender-restricted passive observation without reading existing content. Actual clicked actions/dismissals, lock/unlock privacy and Plasma/GNOME evidence remain.

## Comments

Preparation ticket only; status is not authorization to start implementation. No app/release artifact exists yet.

## Desktop privacy implementation continuation

Fixed always-redacted unlocked previews. Native delivery reads current local policy, escapes displayed content, omits locked source actions and refreshes tracked previews when lock/unknown/opt-in state changes. Disabled or removed previews retire; failed closes remain tracked for retry. Expired notifications are not intentionally resurrected by unlock. Sampling is500ms plus native adapter/daemon latency. Rust15tests and strictClippy pass. The user skipped physical locking, so actual daemon lock-transition acceptance remains open.

A native D-Bus integration fixture now runs on its own dedicated daemon/address (never the user session): the actual Rust Notify adapter sends escaped readable→redacted→readable payloads using the same native ID and closes that ID. All15Rusttests and strictClippy pass. The dedicated test daemon/thread are stopped after the check. This does not substitute for physical lock/daemon acceptance.
