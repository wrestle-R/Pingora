# Architecture preparation

The product contract is [product plan](plan.md). This describes the proposed implementation, not an implemented system.

## Reuse and application boundaries

Prefer a maintained, minimal KDE Connect derivative rather than rewriting discovery, TLS, pairing and every plugin. Android stays native Kotlin/Java using upstream AndroidX, views/Compose and Gradle conventions. Desktop uses a Tauri shell (local UI + Rust boundary). KDE Connect Qt/C++ service/plugin reuse can sit behind a native service/sidecar adapter; validate build, packaging and IPC before choosing it. Do not silently switch back to a Qt-only desktop UI or assume source compatibility across Rust/C++. Foner Bluetooth support is a separate optional capability.

Use distinct Android package, desktop application ID, D-Bus names, config paths, service names and notification origin identity so Pingora and KDE Connect can coexist. Exact identities must be locked before the first signed beta. Prevent importing unwanted upstream plugins: remote input/slides, media, arbitrary commands and full SMS client are out.

Use the requested root directories: next/ (Next.js site), app/ (native Android), tauri/ (Tauri desktop) and docs/ (planning/progress). The site and Tauri frontend are separate applications: the public site never has privileged desktop commands. Shared fixtures/release tooling can be introduced when needed without cluttering the root. Directory READMEs record boundaries; they are not runnable app scaffolds.

## Proposed internal responsibilities

- Pairing/transport: device identity, capability negotiation, approved sessions, authenticated encryption and revoke handling. Do not design custom cryptography.
- Notification normalization: app identity, conversation identity, sender, body, source ID/version, available actions and event origin. Stable source data wins over parsing display labels.
- Notification lifecycle: replace/group/deduplicate, retain bounded text, reconcile current state and safely apply versioned actions.
- Platform adapters: Android listeners/actions/storage grants/clipboard; Linux notification observation/presentation, lock-aware privacy, folder browser and Bluetooth audio.
- Local UI: pairing/setup, health, per-feature settings, native notifications, inbox search/clear, transfer approval and widget/shade controls.
- Release/update: verified metadata/artifacts, installation ownership, approval and busy-session deferral.

## Event rules

No echo loops. Track origin/source/device/event identity; receiving a mirror is not a new original. Pairing/session identity scopes actions. A notification version changes on a source update; an offline dismissal names the exact version and is discarded if stale. Read/reply actions must be valid for the current source item. Popup timeout never becomes remote dismissal. Reconnect reconciles current active state and at most one summary; do not claim messages that were never observed.

Per-feature toggles are enforced at the receiving boundary as well as the UI. Unpair cancels queued remote work and revokes sessions. Pause must not silently erase history or break pairing. OTPs use ordinary retention and redaction policy.

## Bounded resources

History: 24h or 500 saved entries, first limit reached; up to 16 KiB per body is a starting engineering proposal, not a confirmed performance target. Bound metadata/queues/retries separately. Stream files to approved destinations rather than holding entire files in RAM. Stop activity when permission or transport is lost; distinguish cancelled from failed transfers and clean partial files deliberately. Never claim low RAM or battery use without measurements.

## Decision records

[ADR-0001](adr/0001-scope-and-reuse.md) records the confirmed scope. [ADR-0002](adr/0002-notification-lifecycle.md) records notification decisions. Tauri native-service adapter selection, package formats, complete signing/update trust design and exact minimum OS versions remain to be verified in the preparation probes; see readiness.

## Website vs device data

The Next.js site serves marketing/docs and approved GitHub release/issue information. It is not a notification relay, account requirement, clipboard store or pairing backend. Device sharing works on LAN independently of site availability. While the repo is private, public output must not reveal private issue/release metadata, credentials or private authenticated API caches. See website.md.

Tauri serves local bundled UI. Do not navigate a privileged webview to the public site. Scope Rust commands narrowly; no arbitrary shell execution or unsanitized notification HTML. Reused service lifecycle/IPC and Tauri update packaging are explicit P01/P15 checks.
