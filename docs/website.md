# Website scope

User-requested Next.js application at root next/. The Paper Airwave implementation is in next/src/app/. Use Next.js conventions and a separate local frontend for Tauri. Do not add a website build to the Android root app/ directory.

## Pages

- Home: clear Android/Linux companion positioning, approved feature overview, privacy/network model and release status. No invented testimonials or download counts.
- Docs: installation, pairing, Android permissions, sidebar/inbox behavior, clipboard shade/widget, transfers/shared folders, optional Bluetooth calling, troubleshooting and updates. Public product documentation only.
- Downloads: Android APK and supported Linux builds from actual GitHub Releases, version/date/architecture/signature information and installer instructions when they exist. Honest unreleased/private/unavailable states.
- Issues/support: user-facing bug/feature information from permitted GitHub Issues, filters/status where useful, Report bug / Request feature links to GitHub's signed-in issue flow. Internal work stays in local Markdown. No anonymous write-through GitHub endpoint or custom account system by default.
- Changelog: actual published release notes.

## Data and privacy contract

GitHub is the source for public issue/release information once public. While the repository is private, a public site must show no private issue/release details; use an explicit private/unavailable state. If authenticated previews are later added, authentication/authorization and cache isolation are required first. Do not expose a private API token through browser bundles or public server responses.

Public site content is explicitly curated. Never auto-publish docs/progress, local-docs, skill setup or unreviewed internal notes merely because they are Markdown. Accountless marketing/docs can run independently of device communication.

Handle GitHub rate limits, network failures, empty results and missing release assets without breaking basic navigation. Cache only allowed data with a documented freshness policy. Sanitize untrusted issue/release Markdown/links; do not render arbitrary remote HTML.

## Delivery

Use Next.js App Router. The user selected GitHub-connected Vercel hosting with a Next.js server backend. Source deployments must come through GitHub pushes. Read-only public integration + GitHub-hosted report flow is the initial direction. Responsive, accessible pages, coherent typography, keyboard navigation and clear docs hierarchy. Version/dependency pins and design direction are selected during website implementation.

Root next/ is the site directory; its src/app/ is a Next.js route tree, distinct from root app/ Android. Root tauri/ contains the desktop web UI/Rust bridge; it does not load the public site with desktop privileges.

## Status

P18 implemented: Paper Airwave responsive home, public docs, downloads status, support navigation, changelog empty state and custom 404. Both themes work, with local fonts. P21 adds Vercel hosting and a server-only anonymous public GitHub read model/API. Real release artifacts and live-deployment verification are separate acceptance gates. P19 remains blocked by real releases and public-data prerequisites. See [verification](progress/website-verification.md) and [run instructions](../next/README.md).

The user’s selected direction is 05 in both themes. The later Slumber phone-control request is preserved as an undefined integration, not a shipped feature or permission to introduce arbitrary remote commands.
