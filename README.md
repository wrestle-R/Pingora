# Pingora

Your Android phone and Linux laptop, a little closer. Share notifications, clipboard text and files over your local network, with approval and permissions for each device.

[Website](https://pingora-seven.vercel.app) · [Downloads](https://github.com/wrestle-R/Pingora/releases/latest) · [Report a problem](https://github.com/wrestle-R/Pingora/issues)

**Already have a newer Pingora installed? Keep it.** Android rejects older APKs as downgrades and may show “package appears to be invalid.” Do not uninstall your working app to install an older download.

**Latest public release: 0.1.7.** [Download the Android APK](https://github.com/wrestle-R/Pingora/releases/download/v0.1.7/pingora-0.1.7.apk).

## Install on Linux

For **Arch-based Linux x86_64**, this command installs missing runtime dependencies, verifies the signed release, installs Pingora and adds it to your application menu. Run it as your normal desktop user.

Close Pingora before a manual update. Then run:

```sh
bash -o pipefail -c 'curl -fsSL --retry 3 https://github.com/wrestle-R/Pingora/releases/latest/download/install-app.sh | bash'
```

Open Pingora from your application menu, or run:

```sh
~/.local/bin/pingora
```

The installer verifies the pinned Ed25519 signature, size and SHA-256 before installing to `~/.local/share/pingora/versions/`. It creates `~/.local/bin/pingora`, preserves previous versions and refuses to overwrite an unrelated launcher. The app installs without root or GitHub login. Missing Arch runtime packages use your package manager’s normal sudo approval. Add `~/.local/bin` to your PATH to run `pingora` anywhere.

Prefer downloading first? [Download the published installer](https://github.com/wrestle-R/Pingora/releases/download/v0.1.7/install-app.sh), inspect it, then run `bash install-app.sh --tag v0.1.7`. [Download the Linux archive](https://github.com/wrestle-R/Pingora/releases/download/v0.1.7/pingora-0.1.7-linux-x86_64.tar.gz).

**Supported-host limits:** the current package is tested on the owner's Arch Linux/Hyprland setup. It requires host libraries and is not a universal AppImage. GNOME, Plasma, other distributions and extended reliability checks are not fully qualified. These early public releases should not be treated as a fully qualified production release.

## Install on Android

**[Download Pingora 0.1.7 APK](https://github.com/wrestle-R/Pingora/releases/download/v0.1.7/pingora-0.1.7.apk)**

Open the APK on your phone and approve Android's installation prompt. This is the signed release app; it installs separately from a development build. No root is required. Android 16 on the Nothing A001 has been exercised; other devices remain subject to compatibility testing.

To upgrade an older Android release to 0.1.7, [download the signed Android update bundle](https://github.com/wrestle-R/Pingora/releases/download/v0.1.7/pingora-0.1.7-android-update.zip) and import it through Connection health (Home → Settings → Connection & Android permissions). Pingora checks the package identity, version, signer and signed artifact before handing installation to Android. Same-version and older bundles are rejected; if you already have 0.1.7 or a newer build, wait for a newer published release.

After updating, restart Pingora on both devices. If a same-name device appears twice, compare their connection states and identities; approve pairing only after matching the verification values, and forget an obsolete identity explicitly.

## Connect your devices

Pingora uses its own discovery (UDP 1816, TCP 1816–1864, mDNS `_pingora._udp`). **Close older Pingora windows, then update both Android and Linux to the current release**; releases before 0.1.3 use a different discovery channel. KDE Connect is not required. If a firewall is active, allow those ports only on your trusted LAN.


1. Open Pingora on both devices. The laptop starts its connection service automatically.
2. Use the same trusted Wi-Fi network, or USB tethering from your phone. USB debugging alone does not provide a shared network.
3. Request pairing, compare the verification values on both screens and approve only if they match.
4. Enable the features you want in the sharing settings on both devices. Sharing features are enabled for new pairings; you can turn them off individually. Android system permissions and folder grants still require your approval.
5. For notification mirroring, grant Android notification access and choose the phone apps to share. Battery restrictions can interrupt background connections; Connection health shows recovery guidance.

## Find your way around Android

- **Home:** your laptop and connection state, file and clipboard sharing, and expandable transfers and received links. Laptop details contain battery, ringing and optional Slumber controls.
- **Inbox:** retained mirrored notifications, search and supported source actions.
- **Settings:** sharing switches, phone notification sources and read-only folder access. Connection help leads to Android permissions and signed updates.

Unavailable actions show connection guidance. There is no separate Send link action; send text through the clipboard. Incoming files still need approval, and links open only when you choose them.

Android folder browsing supports chosen folders, plus read-only primary shared storage after Settings → Phone storage access on Android 11+. Android private app folders remain unavailable. The clipboard widget sends through a brief separate task and returns to the launcher; clipboard receipt alerts never include copied text. Add the laptop-status widget in Settings for reported battery, internet status, power profiles and Sleep/Slumber lid-close preference. See [widget instructions](docs/widgets.md).

## What it does

- Notification mirroring, source-aware actions and a searchable inbox with bounded history.
- Explicit clipboard sharing, including an Android quick-settings tile and widget.
- Approved file transfers, links that open only when requested, and read-only phone folder browsing after an explicit storage grant.
- Battery status, bounded device ringing, pause and per-feature controls.
- Optional integration with the owner's native QuickShell Slumber setup; unsupported setups report unavailable.

Tauri and Rust power the desktop, with a React + shadcn interface. Android remains native. There is no remote touchpad, keyboard, media control, arbitrary command execution or full SMS client. Calling is deferred.

## Updates, removal and support

Signed updates preserve the existing installation on verification failures. Linux installs retain the previous executable and reject downgrades; active transfers defer app-driven installation. OS confirmations remain explicit.

To uninstall the user-owned Linux installation, remove `~/.local/bin/pingora`, `~/.local/share/pingora/versions/` and `~/.local/share/applications/io.github.wrestle-r.pingora.desktop`. Pairing/history are separate app data and are retained. Uninstall Android through system settings.

[Release notes and checksums](https://github.com/wrestle-R/Pingora/releases/tag/v0.1.7) · [Build the desktop](tauri/README.md) · [Android source](app/) · [Security and update design](docs/releases.md)

## Release checks

The [0.1.7 hosted pipeline](https://github.com/wrestle-R/Pingora/actions/runs/43375836021) passed Android and Rust tests, desktop frontend checks, optimized packaging, signature verification and Android API 36 fresh-install/upgrade/reinstall checks. The published APK also upgraded the owner’s Android 16 phone normally. The Linux installer was checked with anonymous downloads and an isolated upgrade that preserved settings and the previous executable.

Physical phone checks also passed clipboard widget delivery and launcher return, background/listener-recovery test notifications, battery reporting, power-profile changes and both lid-mode directions. Physical folder browsing and broader/overnight reliability qualification are still pending. See the [release notes](https://github.com/wrestle-R/Pingora/releases/tag/v0.1.7) for the tested limits.

## Credits and source

Pingora is an independent app with its own identity. Connectivity reuses KDE Connect components with their original notices preserved. See [desktop licensing](tauri/COPYING), [native source provenance](tauri/native/source.json) and the notices in Android source. Corresponding Pingora and pinned native source archives accompany the release.
