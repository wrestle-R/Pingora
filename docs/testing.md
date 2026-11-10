# Approved core test boundaries

The user approved these six public boundaries and listed real-device acceptance checks. No tests have been implemented or run; do not ask for the same approval again. New unrelated boundaries need their own review. Prefer a public behavior test at each boundary, then the minimum implementation; do not write tests for private helpers or cosmetic edits.

| Boundary | What it catches | What it misses |
| --- | --- | --- |
| Android notification input → normalized event | sender/body extraction, summaries, Unicode, app allowlist, ambiguous thread IDs | OEM omission/redaction unless exercised on-device |
| notification events/actions → public inbox + outbound actions | dedupe, versions, stale dismissal protection, no expiry dismissal, echo prevention | native daemon UI and actual source-app actions |
| history public API → retained/searchable entries | 24h/500-entry cap, OTP parity, body limits, clear semantics | device storage/keystore behavior |
| paired protocol session → allowed remote capabilities | unauthenticated rejection, revoke, disabled-feature enforcement, bounded queues | full network-stack/certificate behavior without real integration |
| transfer/folder APIs → user approval and sandboxed filesystem outcomes | reject/cancel/collision/path traversal/grant revocation | actual Android document-provider quirks |
| update coordinator → verified staged install/handoff | signature rejection, busy-session deferral, explicit approval, failed-download preservation | OS installer/package manager without integration testing |

## Real-device acceptance

Run on Android and every launch Linux desktop; record exact OS, notification daemon and app versions. Do not equate compilation, a VM smoke check or an emulator pass with actual phone compatibility.

- WhatsApp direct + group, Instagram messages, Gmail subjects/previews, SMS and OTPs. Several messages in one thread replace only that thread; summaries do not duplicate children. Redact fixture exports before sharing.
- Native popup actions, sidebar replacement, lock/unlock privacy, Pingora inbox, clear-history behavior and separate read/dismiss actions. Unknown lock state suppresses private content.
- Disconnect, update a conversation, dismiss old version, reconnect: newest message survives; a single quiet summary appears; no echo flood.
- Clipboard shade action and widget when phone foreground/background/locked; no immediate echo or stored clipboard history.
- Notification access revoked, battery restrictions, app force-stop/relaunch, reboot and laptop suspend/resume yield truthful health/recovery.
- File acceptance/rejection, network loss, disk full, unsafe names, collisions and folder grant revocation. No automatic URL opening or file execution.
- Pause and per-feature toggles reject new activity at endpoints; clearly document active-transfer handling. Unpair leaves no delayed remote actions.
- Bluetooth incoming SIM call: explicit answer/decline, both audio directions, volume/mute controls, network loss independent of Bluetooth, Bluetooth loss fallback to phone. Unsupported calling must not disable LAN features.
- Signed update over previous version, altered artifact rejection, data/schema migration, failed installation, installation deferred while busy.

## Performance evidence

Measure idle and burst behavior: event throughput, resident memory, CPU, Android battery/service wakeups, history disk size, retry queue sizes and transfer buffering. Include a long-running reconnect test. No invented pass thresholds: collect baseline, document a budget and fail regressions against that budget.

## Tooling direction

Use upstream-compatible Android/JVM/instrumentation tools and Tauri/Rust/frontend tooling plus CMake/CTest/Qt tooling for any reused native service selected after build probes. Tests cannot be considered runnable until actual source/build targets exist. No synthetic green CI or placeholder test commands.

## Website extension

Website scope was added after the six core boundaries were approved. Proposed additional boundary: GitHub issue/release responses → public page/view model. It should catch private-data exposure, token leaks, stale/rate-limited/unavailable states, incorrect assets and misleading unreleased download buttons; it does not prove a GitHub permission setup or signed artifact validity. Approve this boundary before TDD tests for it. Accessibility/navigation/link checks remain appropriate site acceptance checks.
