# Pingora desktop

Native Tauri 2 development shell with a local, bundled health view. Website code is not loaded into the webview. The Rust `health` command reports pairing/transport as unimplemented and reads the isolated LAN service’s paired-state summary and the existing QuickShell Slumber status through fixed, timed IPC arguments. It does not start Slumber, suspend, unlock or change settings. No shell plugin or remote web origins are enabled.

## Build and inspect

On Linux with Rust, GTK 3 and WebKitGTK 4.1 development libraries:

```sh
cargo build --locked --manifest-path src-tauri/Cargo.toml
cargo run --locked --manifest-path src-tauri/Cargo.toml -- --probe-health
cargo run --locked --manifest-path src-tauri/Cargo.toml
```

Run these commands from `tauri/`. The second command exercises the same native health function without opening a window or changing the session. Slumber is specific to the owner’s matching Arch/Hyprland/QuickShell configuration; other systems report unavailable.

This is a P01 build/IPC probe, not a paired Android/Linux companion. No encrypted LAN session, native notification mirroring, phone control, release packaging or signing is implemented. KDE/Foner sources are separately evaluated at pinned revisions, not embedded into this binary. GPL-3.0-or-later licensing is declared for this new desktop source; upstream source-file notices and dependency obligations still require preserving if code is imported later.

See [Slumber contract](../docs/slumber.md), [architecture](../docs/architecture.md) and [tracker](../docs/progress/README.md).

See [isolated native service build](native/README.md) for the pinned source/checksum, isolation edits and build commands. This is a separately built native service probe, not a bundled release sidecar yet.
