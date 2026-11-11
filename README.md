# Pingora

Your Android phone and Linux laptop, a little closer. Share notifications, clipboard text, files and links over your local network, with approval and permissions for each device.

[Website](https://pingora-seven.vercel.app) · [Downloads](https://github.com/wrestle-R/Pingora/releases/latest) · [Report a problem](https://github.com/wrestle-R/Pingora/issues)

## Install on Linux

For **Arch-based Linux x86_64**, this command installs missing runtime dependencies, verifies the signed release, installs Pingora and adds it to your application menu:

```sh
curl -fsSL https://github.com/wrestle-R/Pingora/releases/latest/download/install-app.sh | bash
```

Open Pingora from your application menu, or run:

```sh
~/.local/bin/pingora
```

The installer verifies the pinned Ed25519 signature, size and SHA-256 before installing to `~/.local/share/pingora/versions/`. It creates `~/.local/bin/pingora`, preserves previous versions and refuses to overwrite an unrelated launcher. The app installs without root or GitHub login. Missing Arch runtime packages use your package manager’s normal sudo approval. Add `~/.local/bin` to your PATH to run `pingora` anywhere.

Prefer downloading first? [Download the installer](https://raw.githubusercontent.com/wrestle-R/Pingora/main/docs/releases/install.py), inspect it, then run `python3 install.py --tag v0.1.1`. [Download the Linux archive](https://github.com/wrestle-R/Pingora/releases/download/v0.1.1/pingora-0.1.1-linux-x86_64.tar.gz).

**Supported-host limits:** the current package is tested on the owner's Arch Linux/Hyprland setup. It requires host libraries and is not a universal AppImage. GNOME, Plasma, other distributions and extended reliability checks are not fully qualified. This first public release should not be treated as a fully qualified production release.

## Install on Android

**[Download Pingora 0.1.1 APK](https://github.com/wrestle-R/Pingora/releases/download/v0.1.1/pingora-0.1.1.apk)**

Open the APK on your phone and approve Android's installation prompt. This is the signed release app; it installs separately from a development build. No root is required. Android16 on the Nothing A001 has been exercised; other devices remain subject to compatibility testing.

For later updates, [download the signed Android update bundle](https://github.com/wrestle-R/Pingora/releases/download/v0.1.1/pingora-0.1.1-android-update.zip) and import it through Connection health. Pingora checks the package identity, version, signer and signed artifact before handing installation to Android.

## Connect your devices

1. Open Pingora on both devices and start the laptop's connection service.
2. Use the same trusted Wi-Fi network, or USB tethering from your phone. USB debugging alone does not provide a shared network.
3. Request pairing, compare the verification values on both screens and approve only if they match.
4. Enable the features you want in Sharing controls on both devices. Sharing starts off.
5. For notification mirroring, grant Android notification access and choose the phone apps to share. Battery restrictions can interrupt background connections; Connection health shows recovery guidance.

## What it does

- Notification mirroring, source-aware actions and a searchable inbox with bounded history.
- Explicit clipboard sharing, including an Android quick-settings tile and widget.
- Approved file transfers, links that open only when requested, and read-only selected phone folders.
- Battery status, bounded device ringing, pause and per-feature controls.
- Optional integration with the owner's native QuickShell Slumber setup; unsupported setups report unavailable.

Tauri and Rust power the desktop, with a React + shadcn interface. Android remains native. There is no remote touchpad, keyboard, media control, arbitrary command execution or full SMS client. Calling is deferred.

## Updates, removal and support

Signed updates preserve the existing installation on verification failures. Linux installs retain the previous executable and reject downgrades; active transfers defer app-driven installation. OS confirmations remain explicit.

To uninstall the user-owned Linux installation, remove `~/.local/bin/pingora`, `~/.local/share/pingora/versions/` and `~/.local/share/applications/io.github.wrestle-r.pingora.desktop`. Pairing/history are separate app data and are retained. Uninstall Android through system settings.

[Release notes and checksums](https://github.com/wrestle-R/Pingora/releases/tag/v0.1.1) · [Build the desktop](tauri/README.md) · [Android source](app/) · [Security and update design](docs/releases.md)

## Credits and source

Pingora is an independent app with its own identity. Connectivity reuses KDE Connect components with their original notices preserved. See [desktop licensing](tauri/COPYING), [native source provenance](tauri/native/source.json) and the notices in Android source. Corresponding Pingora and pinned native source archives accompany the release.
