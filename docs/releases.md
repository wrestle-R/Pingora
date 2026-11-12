# Release and update preparation

This is the release contract. Hosted workflow, signed metadata, installers and updater source are implemented; see [tooling](releases/README.md) and [observed verification](progress/implementation-verification.md). Signed public releases are available. Release 0.1.6 passed the hosted build/signing/install gates and owner-host package checks; broader platform and physical feature qualification remain outstanding.

## Tag pipeline

1. Accept a validated stable version tag `vMAJOR.MINOR.PATCH` (prerelease tags are currently rejected). Validate tag/version mapping before touching release assets.
2. Check out that exact commit, install pinned toolchains and dependency versions, verify upstream provenance. Run appropriate Android and Tauri desktop/native-service checks/builds. Website checks are a separate required job for site changes.
3. Build candidate Android APK and Linux artifacts in a keyless GitHub-hosted job. Signing runs separately in the tag-restricted `native-release` environment. Do not upload a release if required jobs fail. Stage artifacts as CI artifacts first.
4. Sign Android with the stable release identity; implement chosen Linux artifact/metadata authenticity mechanism. Never expose signing material in logs, PR jobs or artifacts.
5. Publish a complete draft GitHub Release, verify inventory/version/signatures, then publish. Include checksums, supported matrix, source/licensing notices and release notes. Make tag builds idempotent; never silently replace a different signed artifact under an existing version.

## Signing and trust

Android application ID is `io.github.wrestle_r.pingora`; versionCode maps `major*1000000 + minor*1000 + patch`, with minor/patch bounded to999. The Android certificate and Ed25519 metadata public identities are tracked under `docs/releases/trust/`. Private signing material has been provisioned in GitHub Actions environment secrets as explicitly authorized. Off-machine backup/recovery still needs verification before beta. Request secrets via the available secure input mechanism when implementation actually needs them; no dummy key presented as a release key.

Android APK signing and Ed25519 metadata signing are both implemented. Linux artifacts are authenticated by the pinned Ed25519 metadata and its SHA256/size inventory. Checksums alone are corruption checks, not independent authenticity if distributed from the same untrusted source.

## Downloads and installation

Keep GitHub repository private until owner changes visibility. Private beta access must be authenticated per authorized recipient, never through a bundled repository token. The README installer may use an existing authorized `gh` session for private assets; public release downloads can later use unauthenticated HTTPS. Clearly label private in-app access as unverified until an authentication path is implemented.

Current first Linux artifact: x86_64 portable tar with the isolated native service and its own libraries; compatible host GTK3/WebKitGTK4.1/Qt6/KF6 libraries remain required. It is not a universal AppImage. Do not imply every architecture/distro is supported. Package-manager-owned installs must use that manager for updates. Android artifact ABI coverage and minimum Android version must be derived from selected/pinned dependencies and tested before publishing compatibility claims.

Installer requirements: detect supported OS/architecture; select stable or explicitly requested prerelease; fetch exact matching assets; verify authenticity; show version/destination; avoid root for portable installs; preserve existing install on failure; support uninstall instructions. No executable download command is advertised until the script exists and assets are built.

## In-app updates

Check/download in app, show version/release notes and request installation approval. Android retains required system installer confirmation; Linux respects installation ownership and needed elevation. Do not install during a call or active transfer. Failed download/verification leaves old app usable. Preserve pairing/settings and migrate history safely; incompatible downgrade is not promised.

## Distribution review

Before importing KDE/Foner code, audit exact file/dependency licenses, record notices/modifications and package applicable corresponding-source delivery for recipients. Record which upstream patches were taken and establish a repeatable update procedure. Repository privacy does not substitute for the review. Release source archives exclude internal progress and agent instructions via .gitattributes; retain all required application/build/license source.

## Website synchronization

Site downloads resolve only actual published version/asset metadata and disclose supported OS/architecture. No public bypass for private assets. Before first release show an explicit unavailable/not released state, not a fake download. Website deployment is separate from app tag publication; releasing apps does not require copying personal progress notes to the website. Keep internal docs/progress and local-docs out of public site content/imports. Website hosting uses the GitHub-connected Vercel project; the Next.js server supplies only public GitHub metadata, without a private-release bypass.
