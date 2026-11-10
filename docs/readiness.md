# Preparation readiness

Status: documentation and skill installation prepared; application implementation has not started. No runtime, phone-vendor or three-desktop compatibility pass is claimed.

## Completed this preparation pass

- Removed phone touchpad, keyboard and slide remote from active scope and backlog.
- Added approved health page, pause sharing, per-feature controls, sanitized diagnostics, inbox search and clear history.
- Consolidated scope/architecture, glossary, two decision records, dependency-ordered backlog, test boundaries and release/update contract.
- Installed all six selected Matt Pocock skills from pinned revision; reviewed their approval/commit behavior.
- Finalized root AGENTS.md and local Markdown tracker after user clarification; created 19 individual tickets (17 approved core slices plus 2 website extensions). No GitHub tracking issues created.
- Queried immutable candidate KDE/Foner revisions in upstream-candidates.json; did not import unreviewed source.
- Kept local project memory ignored; no real environment files or secrets created.

## Read-only environment evidence

- Repository origin: wrestle-R/Pingora, verified private. Initial main commit remains the only commit. Preparation changes are local and uncommitted.
- Owner laptop: Arch Linux, Hyprland/Wayland from earlier inspection.
- Tools present: GitHub CLI, ADB, C++ compiler, pkg-config, Java 17.0.21, CMake 4.4.5, Ninja 1.13.2; Qt6Core/Qt6DBus reported 6.12.0. Presence alone does not prove upstream dependency compatibility.
- ADB shows an authorized connected Nothing A001; prior property inspection identifies Android 16/API 36. No grants, apps or device settings were changed.
- Conventional /opt/android-sdk and ~/Android/Sdk directories were not found in the initial probe. An Android SDK elsewhere was not ruled out. SDK/build-tools/NDK requirements must be resolved from chosen upstream builds.
- Owner right-sidebar service has not been identified. Plasma/GNOME test environments have not been provisioned or tested.

## Setup complete; implementation frontier

The user approved the original backlog and six core test boundaries. [Skill setup](agents/setup.md) now points to the [local tracker](progress/README.md). Live agent guidance is root AGENTS.md. Website extension and requested next/app/tauri layout are reflected in scope; new website test boundary is proposed separately. No app test has been written or executed.

Plan/glossary now live under docs/ to keep root clean. next/, app/ and tauri/ contain briefs only. Tauri/Next/Rust/WebKit/Node toolchain compatibility has not been verified by the previous Qt/JVM-only tool probe.

First implementation frontier is P01: verify source/toolchain/license candidates. P02 (native adapter verification), P03 (pairing/health) and P14 (optional Bluetooth evaluation) then become available according to the backlog. Source-probe work must not claim to be a complete production app.

## Outstanding technical prerequisites

- Build-test candidate revisions; verify and pin Android/Tauri/native-service dependency toolchains and Next.js site toolchain. Native service/IPC packaging behind Tauri remains to be demonstrated.
- Audit reused files/dependency licenses and establish source/notice delivery. Repository-level license metadata is not sufficient.
- Identify owner notification sidebar and demonstrate notification/privacy behavior on Hyprland/Plasma/GNOME.
- Confirm minimum Android/desktop versions, Linux artifact architectures and package format through measured builds/tests.
- Test Android notifications and document-provider/clipboard behavior on real phones.
- Verify optional Bluetooth call answering and both audio directions; unsupported setups retain LAN features.
- Finalize active-transfer behavior during Pause sharing, body/queue budgets and history-at-rest protection before relevant implementation.
- Lock stable application/service IDs and signing/versioning identities before the first signed beta.
- Provide real signing material only when needed through secure input; choose trusted update metadata and an authenticated private-beta download path. No signing/upload request pending now.

## Verification performed

Preparation QA checks local Markdown links, source candidate SHA shapes, installed skill assets, 19 individual ticket IDs/dependency cycles, scope exclusions and whitespace. This is a document/setup check, not an application test or license clearance. Exact command outcomes are recorded in local task notes.
