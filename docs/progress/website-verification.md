# Paper Airwave website verification

Date: 2026-11-10. Ticket: P18. Selected concept: 05, both themes.

## Implemented flow

Visitors can explore the product, read curated docs, check release availability and follow GitHub-hosted report links. This site is a static export; it has no device API, GitHub API client, authentication backend or diagnostic upload endpoint. No real environment files were created.

## Evidence

- `npm run build` in `next/`: passed, all five routes, custom 404 and icon generated as static output.
- `npm run typecheck`: passed.
- `git diff --check`: passed.
- Dependency install / lock refresh: audit reported zero vulnerabilities. The machine’s npm 12 warns that its own preferred Node version is newer than the installed Node 20.20.2; installation, build and type checking nevertheless passed. Next’s minimum Node version is met.
- Headless Chromium 1194 with Playwright against both dev server and production `out/` files served locally. Production check: home/docs/downloads/support/changelog × light/dark × 390/768/1440 px (30 combinations). No horizontal overflow, page JavaScript errors or axe WCAG 2 A/AA violations detected. Automated checks do not prove complete accessibility conformance.
- Theme click and reload persistence: passed. Docs CTA navigation: passed. Custom missing-page content: passed (static host configured to use `404.html`).
- Visually inspected full-page home captures at 390 and 1440 px. Production screenshots and machine-readable matrix are in [website-evidence](website-evidence/).
- Exported HTML checked for private memory/progress path references: none. Fonts are local with OFL license files. No external data fetches or tokens are included.

## Limits and remaining work

No deployment was requested or performed; hosting destination is unconfigured. Static hosting must support directory index routes and map unknown URLs to `404.html`. GitHub links deliberately explain repository access requirements; anonymous private-repo issue creation is not claimed. P19 remains blocked by real release assets and public data prerequisites. No Android, Tauri, Slumber, Bluetooth, signing, installer or physical-device compatibility checks were performed for this website ticket.

The site is a responsive implementation of the chosen visual direction with an SVG/CSS paper illustration, not a pixel-identical photographic recreation. Public docs label planned behaviour and unverified platform support explicitly.
