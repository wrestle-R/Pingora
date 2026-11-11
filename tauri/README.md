# Pingora desktop

Tauri 2 is the Linux application shell. Its frontend is React + TypeScript, built with Vite and Tailwind, with shadcn/ui Radix components. Rust retains native commands, pairing approval, per-device policy, privacy enforcement, file dialogs, diagnostics, calls and signed updates. The isolated native LAN service remains a separate process. The website is never loaded into the desktop webview.

## Build

Use Node 24, npm, Rust, GTK 3 and WebKitGTK 4.1 development libraries. The native notification integration test also requires `dbus-daemon`; it creates its own private bus and never claims the user session notification service. From the repository root:

```sh
npm --prefix tauri/frontend ci
npm --prefix tauri/frontend test
npm --prefix tauri/frontend run build
cargo test --locked --manifest-path tauri/src-tauri/Cargo.toml
cargo run --locked --features custom-protocol --manifest-path tauri/src-tauri/Cargo.toml
```

`tauri/frontend/` contains the React source and `components.json`. `tauri/ui/` is ignored generated output embedded by Tauri, so build the frontend before using Cargo directly. Tauri CLI hooks also run the Vite dev/build commands. Release tooling prepares the frontend before Rust checks and packaging.

For frontend development run `npm --prefix tauri/frontend run dev`; it binds only to `127.0.0.1:1420`. Native commands require the Tauri shell. An ordinary browser reports connection errors and does not simulate devices. Production assets, fonts and scripts are local. The production CSP permits inline styles for Radix positioning but keeps scripts local and connection origins restricted to Tauri IPC; Vite/WebSocket origins are development-only.

## Native integration and limits

See [isolated service build](native/README.md), [architecture](../docs/architecture.md), [Slumber contract](../docs/slumber.md), and [release tools](../docs/releases/README.md). Slumber depends on the matching QuickShell setup. Optional Bluetooth calls depend on a compatible PipeWire telephony backend; unavailable integrations report unavailable. No shell plugin or arbitrary remote commands are exposed.

Pingora has its own identity and original logo. KDE-derived native components retain their original licenses and credits. GPL-3.0-or-later applies to the desktop source. Development builds and tests are available; physical device, desktop compatibility and beta qualification remain tracked separately in the [local progress tracker](../docs/progress/README.md).
