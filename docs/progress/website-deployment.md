# GitHub-connected website deployment

2026-11-10. Work in progress; no live deployment verified yet.

## Target and configuration

- Team: `russeldanielpaul-gmailcoms-projects` (`team_O90VR0gf2fZbfMVv7bMXryQF`).
- Project: `pingora` (`prj_WnAM5x8hymrBu2BkH74m5QI7Ch2a`).
- GitHub: `wrestle-R/Pingora`, production branch `main`, Root Directory `next`.
- Framework: Next.js. Node24. Install: `npm ci`. Build: package.json `build` script.
- Vercel’s existing GitHub credential is used by the platform; no credential values were read or copied. Source deployments must use the GitHub connection. Project configuration through the CLI API is not a source upload.

The connected Vercel app needs reauthentication; local CLI login works and was used instead. No new API key, `.env`, Vercel token or database is necessary for the current backend, so there is no environment-file sync pending.

## Server boundary

The website now uses Next.js Server Components and a Node route handler at `/api/github/`. The shared server-only module fetches anonymous public metadata only from the fixed project repository, validates object shapes, omits bodies/HTML and restricts links to the expected GitHub repo. A private/missing repo returns a safe private-or-unavailable state without fetching its issues or releases. Failures are represented without forwarding raw error/response text. Cached sanitized results target five-minute freshness.

This is public read-only metadata, not native notification/clipboard/pairing infrastructure. P19’s actual release asset/signing guarantees still depend on P15. No private release data or credentials should ever be published by the site.

## Verification

Local final build/typecheck passed. Production server route check returned private-or-unavailable with zero releases/issues. Chromium/axe checked all five pages in both themes at390/768/1440px: zero detected WCAG2A/AA violations, no overflow or pageerrors; theme persistence/navigation/custom404 pass. Evidence: website-evidence/server-browser-results.json. Git-triggered deployment verification remains pending. See P21 for completion.
