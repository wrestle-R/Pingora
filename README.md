# Pingora

An Android and Linux companion focused on readable notifications, clipboard and local sharing, with a product website for docs, downloads and support. In preparation; no installable application or release exists yet.

## Planned features

- Readable notification mirroring, native sidebar integration and a searchable Pingora inbox.
- Text clipboard sharing, Android notification-shade action and status widget.
- Approved file/photo/link transfers and read-only browsing of shared phone folders.
- Battery/connection health, device ringing, pause sharing and per-feature controls.
- Optional Bluetooth SIM calling on verified setups; other sharing stays on LAN.

Android and Linux first, one phone/laptop pair per user. Linux launch targets: Hyprland, KDE Plasma and GNOME. Compatibility is not yet verified. No touchpad, keyboard or slide control.

## Repository layout

- `next/` — Next.js marketing, docs, downloads and GitHub issue/support website.
- `app/` — native Android app.
- `tauri/` — Tauri Linux desktop app.
- `docs/` — product plan, decisions, glossary and local progress tracker.

These directories currently contain preparation briefs, not runnable app scaffolds. Development progress is tracked in [local Markdown](docs/progress/README.md), not GitHub Issues. Website GitHub integration is for user-facing reports and releases.

## Preparation documents

- [Product plan](docs/plan.md)
- [Architecture and reuse](docs/architecture.md)
- [Proposed implementation backlog](docs/backlog.md)
- [Test boundaries and acceptance](docs/testing.md)
- [Release/update contract](docs/releases.md)
- [Readiness and outstanding prerequisites](docs/readiness.md)
- [Engineering skill setup](docs/agents/setup.md)

## Downloads and updates

Version-tag builds will publish Android/Linux artifacts to GitHub Releases. Repository remains private until the owner changes it. Private downloads require authorized GitHub access.

A verified download/install script and in-app update flow are planned. No installation command is advertised before actual artifacts and a tested script exist. See the release contract for signing, approval and package-manager requirements.
