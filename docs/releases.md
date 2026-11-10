# Release and update preparation

This is the release contract; workflows/installers/updaters are not implemented.

## Tag pipeline

1. Accept a validated version tag (proposed `vMAJOR.MINOR.PATCH`, with prerelease suffixes for betas). Validate tag/version mapping before touching release assets.
2. Check out that exact commit, install pinned toolchains and dependency versions, verify upstream provenance. Run appropriate Android and Tauri desktop/native-service checks/builds. Website checks are a separate required job for site changes.
3. Build candidate Android APK and Linux artifacts in separate jobs. Do not upload a release if required jobs fail. Stage artifacts as CI artifacts first.
4. Sign Android with the stable release identity; implement chosen Linux artifact/metadata authenticity mechanism. Never expose signing material in logs, PR jobs or artifacts.
5. Publish a complete draft GitHub Release, verify inventory/version/signatures, then publish. Include checksums, supported matrix, source/licensing notices and release notes. Make tag builds idempotent; never silently replace a different signed artifact under an existing version.

## Signing and trust

Before first beta lock Android application ID, versionCode mapping, signing key custody/backup/recovery and update signer identity. Use GitHub Actions secrets/environment protection or another approved secret store. Request secrets via the available secure input mechanism when implementation actually needs them; no dummy key presented as a release key.

Android and Linux signing methods differ. Choose the complete Linux metadata/artifact trust mechanism after packaging probes. Checksums alone are corruption checks, not independent authenticity if distributed from the same untrusted source.

## Downloads and installation

Keep GitHub repository private until owner changes visibility. Private beta access must be authenticated per authorized recipient, never through a bundled repository token. The README installer may use an existing authorized `gh` session for private assets; public release downloads can later use unauthenticated HTTPS. Clearly label private in-app access as unverified until an authentication path is implemented.

Proposed first Linux artifact: x86_64 AppImage, subject to Tauri/WebKit runtime, native-service/Bluetooth/notification integration validation. Do not imply every architecture/distro is supported. Package-manager-owned installs must use that manager for updates. Android artifact ABI coverage and minimum Android version must be derived from selected/pinned dependencies and tested before publishing compatibility claims.

Installer requirements: detect supported OS/architecture; select stable or explicitly requested prerelease; fetch exact matching assets; verify authenticity; show version/destination; avoid root for portable installs; preserve existing install on failure; support uninstall instructions. No executable download command is advertised until the script exists and assets are built.

## In-app updates

Check/download in app, show version/release notes and request installation approval. Android retains required system installer confirmation; Linux respects installation ownership and needed elevation. Do not install during a call or active transfer. Failed download/verification leaves old app usable. Preserve pairing/settings and migrate history safely; incompatible downgrade is not promised.

## Distribution review

Before importing KDE/Foner code, audit exact file/dependency licenses, record notices/modifications and package applicable corresponding-source delivery for recipients. Record which upstream patches were taken and establish a repeatable update procedure. Repository privacy does not substitute for the review.

## Website synchronization

Site downloads resolve only actual published version/asset metadata and disclose supported OS/architecture. No public bypass for private assets. Before first release show an explicit unavailable/not released state, not a fake download. Website deployment is separate from app tag publication; releasing apps does not require copying personal progress notes to the website. Keep internal docs/progress and local-docs out of public site content/imports. Hosting is not selected or deployed in this preparation.
