# Pingora website

Paper Airwave, implemented with the Next.js App Router, TypeScript and local DM Sans / Manrope fonts. Home, docs, downloads, support, changelog and a custom 404 live in `src/app/`. The theme switch is the only browser-side application component.

## Run

Use a compatible Node/npm combination (Vercel builds use Node 24):

```sh
cd next
npm ci
npm run dev
```

Production checks and local server:

```sh
npm run build
npm run typecheck
npm start
```

This is now a Next.js server deployment, not a static export. Old ignored `out/` files are from the earlier static milestone and are not deployment input.

## Website backend

`src/lib/github.ts` is server-only. `/api/github/` and the downloads/changelog/support Server Components use the same cached public GitHub read model. Requests have a fixed repository, no Authorization header, a six-second timeout and a one-megabyte response cap. Data is narrowed to release names/dates/GitHub links/asset metadata and issue numbers/titles/states. No raw Markdown or HTML is rendered, PRs/drafts/prereleases are excluded, and private/empty/rate-limited/unavailable states remain explicit. Sanitized anonymous results are cached for five minutes; the public API can additionally be cached for one minute.

No API keys, database, accounts, diagnostic upload or notification relay are needed. Vercel’s GitHub integration owns its Git credentials; those credentials are not available to the public site. Even if a token were placed in the environment later, this backend does not use it. Private issues/releases and internal Markdown are never imported.

Actual release signing/asset validation remains P15/P19. The site does not call an asset verified merely because it exists on GitHub; it links to public release pages for review. Native Android/Tauri build probes are not app releases.

## Hosting

Vercel project `pingora` is connected to `wrestle-R/Pingora`, production branch `main`, Root Directory `next/`, framework Next.js, Node 24 and install command `npm ci`. Source deployment must come from GitHub pushes; do not use a CLI source upload or prebuilt deployment. No `.env` is required for the current site/backend, and no new secret has been provisioned. Use the owner’s Varte workflow for any future real environment files.

See [deployment record](../docs/progress/website-deployment.md) for verified deployment state and [original UI verification](../docs/progress/website-verification.md). The hero exploration in `design-exploration/` is not a route or public asset directory. Font licenses are beside their files. Phone control for native Slumber is separately tracked and not shipped by the website.
