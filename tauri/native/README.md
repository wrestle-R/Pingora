# Isolated LAN service probe

This evaluates KDE Connect’s native Qt service behind the Tauri shell. It does not install a system daemon, register autostart, replace the notification server or touch existing KDE Connect configuration.

`source.json` pins both the upstream revision and the downloaded archive checksum. Obtain that exact archive, then run from the repository root:

```sh
python3 tauri/native/prepare.py /path/to/kdeconnect-kde.tar.gz
```

The script verifies the archive before extraction, applies checked source edits, configures CMake and builds only the daemon target. It refuses an existing prepared tree rather than silently overwriting work. Rebuild an existing tree with:

```sh
cmake --build tauri/target/native/build --target kdeconnectd --parallel 4
```

## Isolation changes

- Own D-Bus service: `io.github.wrestle_r.pingora`. The upstream internal object path/interface names remain for traceability; the bus owner is distinct.
- Own config directory: `$XDG_CONFIG_HOME/pingora` (normally `~/.config/pingora`). Do not print or upload its pairing keys/certificates.
- Only LAN backend enabled. Bluetooth transport is compiled out.
- Plugin registry is empty, with no scan of installed KDE Connect plugins. Only core/D-Bus-interface/daemon build subdirectories are configured. No forbidden upstream feature is loaded from the user’s system.
- A Pingora application/display identity, with upstream author/license notices retained.

No TLS/pairing cryptography was rewritten. The isolated build still uses upstream network packet names for interoperability. Exposing a daemon on D-Bus and reading it from Rust does not prove phone pairing, trusted-session rejection/revocation, notification mirroring or multi-desktop support.

## Observed result

2026-11-10: 192-step CMake/Ninja build passed. A short-lived service test with a separate temporary XDG_CONFIG_HOME exposed the own D-Bus service and only `LAN|lan|enabled`. Rust’s actual `--probe-health` observed `native_service:true`, `paired:false`, and the live native Slumber status. The process was stopped afterward; no service was installed or left running.

This is not a packaged release. KDE source and dependency notices must accompany any future distribution according to their actual licenses. The downloaded source includes LICENSES; the reviewed file inventory and remaining exceptions are under docs/progress.
