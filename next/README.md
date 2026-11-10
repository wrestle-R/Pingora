# Pingora website

The Paper Airwave website, built with the Next.js App Router, TypeScript and locally hosted DM Sans / Manrope fonts. Home, docs, downloads, support, changelog and a custom 404 are implemented in `src/app/`. The only client component is the persistent theme toggle.

## Run locally

Use Node.js 20.9 or newer and a compatible npm version.

```sh
cd next
npm ci
npm run dev
```

Open http://localhost:3000. To verify and produce static files:

```sh
npm run build
npm run typecheck
python3 -m http.server 3000 --directory out
```

Build output is in `out/`, with directory-style routes. A static host should serve `404.html` for unknown routes. No environment variables or credentials are needed. No hosting destination has been configured or deployed.

## Content and data boundaries

This is the marketing/docs website, not the Android app or Tauri desktop UI. All device illustrations are labelled concepts. Public content is curated directly in the route files; no internal Markdown, local notes, GitHub API data or tokens are imported. The repository remains private. Downloads and changelog deliberately show no-release states; support links open GitHub’s signed-in issue flow and explain the access requirement. Live public GitHub integration remains P19, dependent on real releases and public-data approval.

Slumber phone control is recorded as a requested, undefined integration; it is not presented as shipped. Real-device Android testing is outside this website ticket.

The selected reference is **05 — Paper Airwave**, in both themes. The original hero gallery remains in `design-exploration/` and is not copied into the production static output. The website uses an original responsive SVG/CSS illustration rather than a screenshot of the reference. Font licenses are beside the font files in `src/fonts/`.

See [website scope](../docs/website.md) and [verification](../docs/progress/website-verification.md).
