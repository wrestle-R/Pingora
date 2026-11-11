# P22: Pingora identity and React/shadcn interfaces

**Status:** in-progress
**Blocked by:** none for implementation; physical compatibility checks remain separate.

## Requested outcome

Keep Tauri and its native backend. Replace the desktop frontend with React, initialize real shadcn on desktop and website, introduce an original Pingora logo and a near-black dark theme. Improve dropdowns and checkboxes. Remove duplicate rows for identical protocol IDs without merging different installations or their trust.

## Implementation

- React/Vite/TypeScript desktop source in tauri/frontend, generated assets embedded by the existing Tauri shell. Native commands remain Rust-owned.
- Proper shadcn CLI initialization in both apps, Radix controls, keyboard-accessible theme dropdown and phone selection, confirmation dialogs, labelled checkboxes and responsive sidebar.
- Semantic mineral/teal palette with #080b0e dark canvas. Original P/ping-point vector and Android launcher/notification assets, preserved upstream credits.
- Duplicate stable IDs fold into one row. Unpaired same-name identities are grouped under a disclosure. Debug phone advertises a development suffix.
- Release tooling builds the frontend before Cargo. No external web content or scripts in production webview.

## Verification

- Next.js type-check and production build pass. Website/Android identity milestone 2018d48 pushed to main through GitHub only.
- Android debug build and all 148 JVM tests pass (0 failures/errors/skips).
- Six React interaction checks pass: rendered workspace/keyboard theme menu, identity dedupe, fresh pairing verification, lock privacy, cumulative policy saves without losing entered text, invalidation of stale update approval. Fixtures are synthetic; there is no mock native backend in production.
- Vite/TypeScript production build passes. Rust 10 tests, strict Clippy and optimized Tauri build pass.
- Clean npm ci, six frontend checks and production Vite build pass. Final optimized Tauri build embeds these assets; isolated fresh-profile startup stayed running for six seconds and its processes were stopped. Runtime npm audit reports zero vulnerabilities. Release Python scripts compile. Physical device actions, all supported desktop environments and visual acceptance are not claimed by these tests.

## Remaining acceptance

Run the rebuilt app on the user’s actual session and check dropdown focus, responsive layout and appearance. Complete the broader phone/desktop qualification under P03/P17 separately. The device tool remains blocked on its parent-owned touch-point guard; do not bypass it with ADB UI input.
