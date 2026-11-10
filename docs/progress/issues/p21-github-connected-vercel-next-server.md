# P21: GitHub-connected Vercel hosting with a Next.js server backend

**What to build:** Vercel GitHub integration for the existing `next/` site, with backend code in Next.js and server-only credentials if required. No source-upload deployment.

**Blocked by:** None

**Status:** in-progress

## Acceptance criteria

- [x] Correct account/project, GitHub repo/production branch and next/ root selected.
- [x] Next.js server backend; no browser credentials or private GitHub metadata exposed.
- [x] Only required keys provisioned; no unnecessary token creation. Current anonymous backend requires none.
- [x] Local production/API checks pass.
- [ ] Commit pushed to GitHub and Git-triggered production deployment reaches READY.
- [ ] Live routes, API and deployment commit verified.

## Verification

Vercel app connector requires reauthentication; authenticated local CLI works as cep1 in the owner’s existing team. Created project pingora with GitHub link wrestle-R/Pingora, main, next/ root and Next.js framework; Node24 selected. No CLI source upload performed. Local server-side public GitHub read model is implemented; deployment pending next milestone push.

## Comments

Requested during full Android/Linux implementation. This website deployment work does not replace the native product objective or make P19’s release/signing dependencies complete.
