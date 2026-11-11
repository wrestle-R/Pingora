# Pingora

Your Android phone and Linux laptop, a little closer. Share notifications, clipboard text and files over your local network, with approval and permissions for each device.

[Website](https://pingora-seven.vercel.app) · [Downloads](https://github.com/wrestle-R/Pingora/releases/latest) · [Report a problem](https://github.com/wrestle-R/Pingora/issues)

**Latest public release: 0.1.3.** [Download the Android APK](https://github.com/wrestle-R/Pingora/releases/download/v0.1.3/pingora-0.1.3.apk). Version 0.1.4 is still a draft, so its download links do not work publicly. Version 0.1.5 is a local candidate.

## Install on Linux

For **Arch-based Linux x86_64**, this command installs missing runtime dependencies, verifies the signed release, installs Pingora and adds it to your application menu:

Close existing Pingora windows before a manual update. Then run:

```sh
bash -o pipefail -c 'curl -fsSL --retry 3 https://github.com/wrestle-R/Pingora/releases/latest/download/install-app.sh | bash'
```

Open Pingora from your application menu, or run:

```sh
~/.local/bin/pingora
```

The installer verifies the pinned Ed25519 signature, size and SHA-256 before installing to `~/.local/share/pingora/versions/`. It creates `~/.local/bin/pingora`, preserves previous versions and refuses to overwrite an unrelated launcher. The app installs without root or GitHub login. Missing Arch runtime packages use your package manager’s normal sudo approval. Add `~/.local/bin` to your PATH to run `pingora` anywhere.

Prefer downloading first? [Download the published installer](https://github.com/wrestle-R/Pingora/releases/download/v0.1.3/install-app.sh), inspect it, then run `bash install-app.sh --tag v0.1.3`. [Download the Linux archive](https://github.com/wrestle-R/Pingora/releases/download/v0.1.3/pingora-0.1.3-linux-x86_64.tar.gz).

**Supported-host limits:** the current package is tested on the owner's Arch Linux/Hyprland setup. It requires host libraries and is not a universal AppImage. GNOME, Plasma, other distributions and extended reliability checks are not fully qualified. These early public releases should not be treated as a fully qualified production release.

## Install on Android

**[Download Pingora 0.1.3 APK](https://github.com/wrestle-R/Pingora/releases/download/v0.1.3/pingora-0.1.3.apk)**

Open the APK on your phone and approve Android's installation prompt. This is the signed release app; it installs separately from a development build. No root is required. Android16 on the Nothing A001 has been exercised; other devices remain subject to compatibility testing.

To upgrade an older Android release to 0.1.3, [download the signed Android update bundle](https://github.com/wrestle-R/Pingora/releases/download/v0.1.3/pingora-0.1.3-android-update.zip) and import it through Connection health (Home → Settings → Connection & Android permissions in the 0.1.5 candidate). Pingora checks the package identity, version, signer and signed artifact before handing installation to Android. Same-version and older bundles are rejected; if you already have 0.1.3 or a newer local candidate, wait for a newer published release.

After updating, restart Pingora on both devices. If a same-name device appears twice, compare their connection states and identities; approve pairing only after matching the verification values, and forget an obsolete identity explicitly.

## Connect your devices

Pingora uses its own discovery (UDP 1816, TCP 1816–1864, mDNS `_pingora._udp`). **Close older Pingora windows, then update both Android and Linux to 0.1.3 or newer**; older builds use a different discovery channel. KDE Connect is not required. If a firewall is active, allow those ports only on your trusted LAN.


1. Open Pingora on both devices. The laptop starts its connection service automatically.
2. Use the same trusted Wi-Fi network, or USB tethering from your phone. USB debugging alone does not provide a shared network.
3. Request pairing, compare the verification values on both screens and approve only if they match.
4. Enable the features you want in the sharing settings on both devices. Sharing features are enabled for new pairings; you can turn them off individually. Android system permissions and folder grants still require your approval.
5. For notification mirroring, grant Android notification access and choose the phone apps to share. Battery restrictions can interrupt background connections; Connection health shows recovery guidance.

## Local candidate: 0.1.5

The public downloads above are **0.1.3**. Version **0.1.4 is a draft**, and the newer **0.1.5 builds are local candidates, not published downloads**. The navigation, direct clipboard widget, clipboard receipt alerts, matching launcher logo and expanded read-only storage access described below belong to 0.1.5. The published installer installs the latest published release; it does not install drafts or local candidates.

## Find your way around Android (0.1.5 candidate)

- **Home:** your laptop and connection state, file and clipboard sharing, and expandable transfers and received links. Laptop details contains battery, ringing and optional Slumber controls.
- **Inbox:** retained mirrored notifications, search and supported source actions.
- **Settings:** sharing switches, phone notification sources and read-only folder access. Connection help leads to Android permissions and signed updates.

Unavailable actions show connection guidance. There is no separate Send link action; send text through the clipboard. Incoming files still need approval, and links open only when you choose them.

Android folder browsing supports chosen folders, plus read-only primary shared storage after Settings → Phone storage access on Android 11+. Android private app folders remain unavailable. The clipboard widget sends via a small focused surface and closes automatically; clipboard receipt alerts never include copied text.

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

[Release notes and checksums](https://github.com/wrestle-R/Pingora/releases/tag/v0.1.3) · [Build the desktop](tauri/README.md) · [Android source](app/) · [Security and update design](docs/releases.md)

## Credits and source

Pingora is an independent app with its own identity. Connectivity reuses KDE Connect components with their original notices preserved. See [desktop licensing](tauri/COPYING), [native source provenance](tauri/native/source.json) and the notices in Android source. Corresponding Pingora and pinned native source archives accompany the release.
