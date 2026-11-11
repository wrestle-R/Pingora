# ADR-0003: React frontend inside the Tauri desktop

- Date: 2026-11-11
- Status: accepted by explicit user instruction

## Decision

Keep Tauri 2, Rust commands and the isolated KDE-derived native service. Replace the imperative HTML/JavaScript frontend with React + TypeScript, built by Vite. Initialize shadcn/ui with Radix components in both the desktop and independent Next.js website.

The desktop source is `tauri/frontend/`. Its build emits ignored `tauri/ui/`, embedded in the native binary. Cargo callers must prepare these assets explicitly; Tauri CLI hooks and release tooling do so. Native APIs are invoked through the official Tauri JavaScript package. No browser fallback simulates devices.

## Consequences

Node/npm become desktop build dependencies. Runtime assets and fonts remain local. Production CSP restricts scripts to local assets and connections to IPC; inline styles support Radix positioning. Vite's local HTTP/WebSocket and refresh script permissions are development-only. Pairing verification, per-device policy, lock privacy and signed update approval remain enforced in Rust.

Stable protocol IDs define a device. Equal display names cannot combine trust. Unpaired same-name installations are disclosed separately; debug phone builds advertise a development suffix.

The original Pingora logo and mineral/teal themes replace inherited product styling. Upstream licenses and credits remain. React tests cover UI behavior around existing approved protocol/inbox/update boundaries; physical phone and desktop qualification remain separate.
