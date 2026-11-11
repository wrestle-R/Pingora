# Pingora

An Android and Linux companion focused on readable notifications, clipboard and local sharing, with a product website for docs, downloads and support. The website is live and Android/Linux development builds exist. No qualified app release is published yet.

## Product scope

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

The website, Android app and Tauri desktop/native-service source build locally. Build success does not establish device or desktop compatibility. Development progress is tracked in [local Markdown](docs/progress/README.md), not GitHub Issues. Website GitHub integration is for user-facing reports and releases.

## Project documents

- [Product plan](docs/plan.md)
- [Architecture and reuse](docs/architecture.md)
- [Implementation backlog](docs/backlog.md)
- [Test boundaries and acceptance](docs/testing.md)
- [Release/update contract](docs/releases.md)
- [Readiness and outstanding prerequisites](docs/readiness.md)
- [Engineering skill setup](docs/agents/setup.md)

## Downloads and updates

Version tags trigger GitHub-hosted Android/Linux builds and a separate protected signing job that creates a complete Release draft. The `v0.1.0` build/signing run passed and produced a private development draft. Publication follows qualification. Repository remains private until the owner changes it. Private downloads require authorized GitHub access.

The Linux installer exists and has been checked with a real signed development package, plus rejection fixtures for corruption and invalid signatures. After a qualified version is published, use an authenticated source checkout:

```sh
python3 docs/releases/install.py --tag vMAJOR.MINOR.PATCH
```

Replace the placeholder with an actual published version; none is advertised yet. The installer requires Linux x86_64, Python3.12+, OpenSSL and an authorized `gh` login for this private repository. It verifies the pinned Ed25519 signature and artifact hash, installs under `~/.local/share/pingora/versions/`, updates `~/.local/bin/pingora` and retains previous versions. Compatible host GTK3/WebKitGTK4.1/Qt6/KF6 libraries are required; the tar is not a universal AppImage. It refuses unrelated launchers and same-version/downgrade installs. Remove the user-owned launcher and version directory to uninstall; pairing/history are separate app data and are retained.

For a first Android install, download the signed APK through your authorized GitHub session and open it with the Android system installer. Later Android updates use the signed Android ZIP from the recipient’s own GitHub/browser session, imported through Connection health. Package/version/signature checks precede approval and the Android system installer. Active calls/transfers defer installation. Development/debug and release package identities are distinct.

See [release tooling](docs/releases/README.md) for the signing workflow and [verification](docs/progress/implementation-verification.md) for remaining phone, desktop, call-audio and upgrade checks.
