# Pingora implementation plan

Status: implementation-preparation baseline. No app code implemented or runtime compatibility proven. Original 17-item backlog and six test boundaries approved; progress tracking uses local Markdown. Latest user correction removes all remote input. See [readiness](readiness.md) for evidence, [backlog](backlog.md) for the approved core sequence and website extension and [testing](testing.md) for approved core test boundaries and proposed website coverage.

## Product and supported release

Android phone + Linux laptop companion for the owner and friends. One phone and one laptop per user initially. Launch targets Hyprland, KDE Plasma and GNOME. Record tested desktop/OS versions; installation alone is not evidence of feature support.

Repository stays private until the owner makes it public. Version tags build and publish Android and Linux downloads to GitHub Releases. Private-release downloads require authenticated access. README will include a tested download/install script without embedded credentials once real release artifacts exist.

## Repository layout and website

- `next/`: Next.js marketing, documentation, downloads/releases and GitHub issue/reporting website.
- `app/`: native Android application using the KDE Android ecosystem.
- `tauri/`: Linux desktop application; native backend integration remains behind explicit adapters.
- `docs/`: plans, decisions, glossary, agent configuration and local progress tracking.

Keep the repository root lean. Progress lives in local Markdown, not GitHub Issues. Website GitHub integration is for user-facing reports/feature requests and release/download information; it is separate from internal progress tracking. See [website scope](website.md). No website or app code is implemented by creating the folder map.

## Agreed features

- LAN: bidirectional notifications, text clipboard, file/photo/link sharing, phone battery and connection status, ring devices, read-only browsing of shared phone folders.
- Bluetooth only for optional SIM calling on verified setups, including laptop microphone/speakers and explicit Answer/Decline. No automatic answering. Root is not required for the core app. Outgoing dialling is outside the initial calling milestone.
- No touchpad, keyboard or slide control, media control, remote commands, full SMS conversation client, iOS, Windows or macOS in this release.


## Approved usability features

- Connection-health page with feature-level permission/connection status and actionable fixes.
- Pause sharing without unpairing. Proposed semantics: reject new LAN sharing and folder access while paused; keep pairing, settings and local history; calls are controlled separately. Active-transfer pause/cancel policy must be defined before implementation.
- Per-feature toggles on each endpoint; a disabled capability cannot be invoked remotely. Pairing does not enable every feature implicitly.
- Explicit sanitized diagnostic export: versions, connection events, permission states and error categories, without message/OTP/clipboard contents, filenames, folder paths, URLs or credentials by default.
- Inbox search over retained local text and Clear history. Clearing saved history is separate from dismissing active source notifications.

## Notification contract

Phone sources: WhatsApp, Instagram, Gmail and SMS. Desktop sources: explicitly selected apps. No reliance on displayed app names for identity when stable identifiers are available.

Native notification centre/right sidebar is the primary surface. Pingora also has an inbox. Show one latest-message entry per conversation, approximately three preview lines with expansion where the notification service supports it. Preserve sender, group/conversation name and message as distinct fields. Parse structured messaging data before falling back to plain notification text. Never invent truncated or withheld content.

Handle grouped summaries without duplicating their child messages. Replace entries when a conversation updates; keep different conversations separate. Unknown/ambiguous conversation identities must not collapse unrelated senders. Preserve Unicode, line breaks and readable text; do not forward raw markup as presentation.

Reply and separate Mark as read actions exist only when the source exposes working actions. Opening or dismissing a notification does not itself mark a message as read. Expired/revoked source actions become unavailable.

Explicit dismissal synchronizes in both directions. Native popup timeout is not a dismissal. After an offline dismissal, clear the original only if its version is unchanged; protect newer messages. Tag origin and event identity to prevent loops, duplicated forwarding and replayed actions.

Reconnection reconciles current state and gives one quiet summary, never a burst of stale popups. A summary must not imply that unseen notification contents were recovered.

## History, OTPs and privacy

Ordinary and OTP-bearing notifications follow the SAME retention policy: up to 24 hours or 500 entries, whichever limit is reached first. No separate OTP expiry or automatic post-copy removal. Copy OTP is available when an actual code can be identified; it does not bypass Android redaction.

Bound individual message size, in-memory queues, disk history, transfer buffering and retry queues. Initial implementation proposal: 16 KiB per stored notification body, bounded title/metadata, no notification attachment contents; profile before claiming a memory budget. Count limits apply to saved entries, not just visible conversation rows. Discard oldest saved entries when over cap.

No saved clipboard history initially. Copy actions are explicit. Never include notification text, clipboard contents, files or pairing credentials in diagnostic logs by default.

Friends default to hiding messages and OTPs while locked, with explicit opt-in to show content. Owner laptop may show all content. Validate native notification-server privacy behavior; if a lock-safe native preview cannot be guaranteed, suppress it rather than claim it is hidden. Lock-screen policy also applies to the inbox and actions. System/app stores outside Pingora may retain their own copies; Pingora cannot promise to erase them.

## Permissions and feature boundaries

- Require explicit approved pairing, authenticated encrypted communication, and easy revoke/unpair. A shared network alone grants no access.
- Explain Android notification access and background requirements. Offer targeted battery guidance where needed and connection-health diagnostics. Android stopping the app remains an accepted limitation.
- Clipboard: automatic where supported; otherwise Android notification-shade Send clipboard, share action or foreground action. Widget shows status and Send clipboard; opening Pingora may be necessary.
- Phone storage browsing: owner-selected shared folders only, read/download. No upload, rename, delete or broad-storage-access workaround. Validate access revocation and path traversal boundaries.
- Every incoming file transfer needs approval. Save into a dedicated destination with safe filenames and collision handling. Never execute/open automatically. Storage-full, interruption, rejection and cancellation are explicit states. Approvals do not survive a changed transfer identity.
- Received URLs require an explicit Open link action; validate schemes before handing them to another app.
- Ring: audible despite app mute where OS permits, clear Stop button and bounded timeout. Do not promise to override system Do Not Disturb.

## Architecture direction and feasibility gates

Android uses KDE Connect’s native Kotlin/Java + AndroidX/Gradle ecosystem, with existing views and Compose where appropriate. Pin a verified upstream revision before choosing toolchain versions. Reuse plugins/protocol behavior where useful; maintain a distinct application identity so it can coexist with KDE Connect.

Desktop: Tauri application in tauri/, with Rust commands and a local web UI. Evaluate reusable KDE Connect Qt/C++ service/plugin code and Foner Bluetooth integration behind narrow native adapters or a sidecar. A service bridge is an engineering proposal to validate, not proof that Qt plugins run inside Rust unchanged. Do not replace the user’s notification daemon or silently require root.

Before a full build, demonstrate:
1. Notification observation, replacement, dismissal and actionable content across Hyprland/Plasma/GNOME. Identify the owner’s actual right-sidebar service. Native layout/history varies by daemon.
2. Lock-aware notification privacy on each required desktop. Suppress native previews when the configured privacy policy cannot be enforced.
3. Readable Android structured notifications, manual clipboard and allowed background operation on the owner phone; test other vendors before claiming broad support.
4. Bluetooth SIM audio in BOTH directions, explicit answering, audio routing, disconnect and phone fallback on verified setups. Calls remain an optional capability, with unsupported status explained.

If a required core capability fails on a launch desktop, report the exact limitation and revisit scope before promising launch parity.

## Releases and updates

- Tag workflow builds/tests both applications from a pinned source revision and publishes only complete verified artifacts. Document version mapping and supported architectures.
- Stable Android package identity, monotonically increasing version code and securely managed signing key; ordinary updates must install over prior releases. No secrets in source or logs.
- Evaluate AppImage for portable in-app updates, while testing its notification/Bluetooth integration. Select Linux package formats after compatibility/build probes; respect package-manager ownership. Do not use a universal updater to overwrite system-owned installations.
- In-app release check and download; explicit approval before install. Respect Android installer confirmation and Linux elevation/package-manager requirements. Where automatic installation is unsupported, provide a clear handoff.
- Defer installation during active calls or transfers. Authenticate/verify update metadata and artifacts with a defined trust mechanism; a checksum from the same compromised channel is not independent authenticity. Preserve the existing installation if update fails.
- Preserve applicable KDE/Foner/dependency notices and provide corresponding source under applicable licenses to recipients. Audit actual reused files before distribution.

## Build milestones

1. Compatibility probes and pinned upstream/license review; document proven capabilities and desktop-specific adapters.
2. Pairing, secure LAN transport, connection health, normalized notification model and real-device formatting prototype.
3. Native sidebar integration, inbox/history, reply/read/dismissal actions, offline reconciliation and privacy handling.
4. Clipboard shade action/widget, transfers/links, battery/ringing, shared-folder browser and the approved usability features.
5. Optional Bluetooth calling with a verified support matrix.
6. Reproducible tag builds, signing, README installer and approved in-app updates.
7. Website marketing/docs/issue navigation and GitHub-backed downloads, then friend beta qualification.

## Acceptance checks

- Two senders remain two entries; group sender/content remain readable; newest message replaces only its own conversation. Test WhatsApp/Instagram/Gmail/SMS using real supplied notifications.
- No duplicated grouped notifications, forwarding loops, unreadable markup or reconnect popup flood.
- Explicit dismissal syncs; popup expiry does not. Offline stale dismissals never remove newer messages.
- Reply/read actions operate on the correct source notification and degrade clearly when unavailable.
- OTPs follow ordinary retention; withheld codes are not fabricated. Locked-device notification privacy works on each launch desktop.
- History ages out and caps at 500; large bodies, rapid bursts and long-running reconnect cycles keep queues bounded. Profile memory/CPU while idle and under load.
- Revoke pairing, folder grants and feature permissions immediately; reject unauthorized access and path traversal.
- Transfer rejection/cancellation, filename collision, interrupted networks and low storage preserve existing files.
- Android process death/reboot/background restrictions produce truthful connection status and recover without stale commands.
- Bluetooth calls prove both audio directions and safe fallback; unsupported machines keep core features usable.
- Tagged Android/Linux releases install cleanly and update existing installs; wrong signatures, failed downloads and busy-device updates fail safely.

## Source references

- KDE Android: https://github.com/KDE/kdeconnect-android
- KDE desktop: https://github.com/KDE/kdeconnect-kde
- Foner: https://github.com/AminBlg/kaller
- Linux notifications: https://specifications.freedesktop.org/notification/latest-single/
- Android clipboard restrictions: https://developer.android.com/about/versions/10/privacy/changes#clipboard-data
- Android OTP redaction: https://developer.android.com/about/versions/15/behavior-changes-all#otp-redaction
- Android cellular audio source: https://developer.android.com/reference/android/media/MediaRecorder.AudioSource#VOICE_CALL

Additional compatibility source: [AppImage updating](https://docs.appimage.org/packaging-guide/optional/updates.html).

## Preparation pack

- [Architecture and reuse approach](architecture.md)
- [Backlog and dependencies](backlog.md)
- [Test boundaries and acceptance scenarios](testing.md)
- [Release/signing/update contract](releases.md)
- [Observed readiness and outstanding gates](readiness.md)
- [Engineering skill configuration](agents/setup.md)

Preparation is documentation, skill installation and read-only checks. It does not authorize root changes, secret creation, publishing, repository visibility changes or implementation.
