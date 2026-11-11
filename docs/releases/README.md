# Native release tooling

## Current public and local versions

As verified on 2026-11-12, the latest published release is `v0.1.3` (nine expected assets). `v0.1.4` is still a draft. `0.1.5` is an installed local candidate and has not been published. README downloads must point only to published assets until publication is explicitly completed and anonymous downloads are verified.

The Bash entrypoint uses the Python installer bundled at release time. Editing `docs/releases/install.py` does not change existing immutable release scripts. Rebuild `install-app.sh` for the next release; never replace old signed artifacts to make them appear current.

Manual installs require Python 3.12+, OpenSSL, Linux x86_64 and compatible runtime libraries. Arch bootstrap installs missing runtime packages through normal package-manager approval. Close the app before manual updates; the in-app updater checks active work and asks for a restart after staging an installation. Existing versions and user data are retained. `--source` supports isolated signed-artifact checks without downloading or changing the real home.

Public releases through `v0.1.3` are available. Their built-host checks do not establish full device/desktop qualification. See [current verification](../progress/implementation-verification.md).

- `tools/build-linux.py --version VERSION --output DIRECTORY`: optimized host-dependent Linux x86_64 tar with bundled own native service/libraries and installer. Host GTK3/WebKitGTK4.1/Qt6/KF6 dependencies remain required. Not an AppImage compatibility claim.
- `tools/verify-linux.py ARTIFACT`: extracts the actual tar, checks required resources/trust, host dependencies and bundled native-library resolution, then runs only the headless health command. Every Linux package build invokes it before upload.
- `tools/manifest.py`: signs finished Android/Linux inventory using the protected Ed25519 file. Produces metadata/signature and Android update ZIP; never copies private keys.
- `install.py`: downloads public assets anonymously, or uses the recipient’s existing `gh` login with `--github-auth` for private assets, verifies the pinned public signature and artifact hash, extracts only safe regular files/directories, atomically updates a user-owned launcher and keeps previous versions. Requires Python3.12+, OpenSSL and gh. `--source` supports an already downloaded signed asset directory. `--tag` selects an exact version; no root is needed.
- `tools/release.py --tag vVERSION [--publish]`: exact clean-tag guard, matching Cargo/Tauri/Android versions, Android version-code mapping `major*1000000 + minor*1000 + patch`, tests/builds, pinned native source retrieval, signing and corresponding-source archives. `--publish` stages a complete draft only. Never clobbers an existing release.
- `../probes/verify-desktop-start.py BINARY`: on a host with an available display, runs the actual desktop with a fresh home/session bus and no XDG Downloads entry. Catches the initial-profile startup regression; does not claim feature acceptance.
- `tools/slumber-adapter.py`: dry-run compatible-source check; explicit `--apply` writes a backed-up narrow lid setter. Does not restart the shell, change a preference, lock or unlock it.

The GitHub workflow uses GitHub-hosted Ubuntu24.04 runners. The keyless build job runs inside a digest-pinned Arch Linux container, installs Java21/native dependencies and the checksum-pinned Android SDK, runs Rust/Android checks, then uploads unsigned Android/Linux artifacts and corresponding source. Its package inventory is retained with the artifacts. Arch dependencies use the fixed 2026-11-10 archive snapshot (both core/extra repository databases were reachable when configured). The image digest, package inventory, source pins and lockfiles record the build inputs; bit-for-bit output reproducibility has not been demonstrated.

A separate signing job runs only for stable `vMAJOR.MINOR.PATCH` tags whose commit belongs to `main`. It uses the `native-release` environment, restricted to `v*` tags, with environment secrets `PINGORA_ANDROID_KEYSTORE_B64`, `PINGORA_ANDROID_STORE_PASSWORD` and `PINGORA_UPDATE_SIGNING_KEY`. These secrets have been provisioned. The job materializes private files temporarily, signs/verifies the finished artifacts against both tracked public identities, creates a complete GitHub Release draft, then removes the key files. No production signing keys are available to the build job or included in artifacts. GitHub environment reviewer approval is not configured; tag restrictions are the current protection rule.

A manual run on `main` validates the unsigned hosted build without releasing anything:

```sh
gh workflow run native-release.yml --ref main
```

Hosted validation passed for `v0.1.0` at `7764207`: [build and protected signing run](https://github.com/wrestle-R/Pingora/actions/runs/42975982663). Downloaded signatures, hashes, APK identity, source/native pins and the private-download installer were independently checked; the installed Linux executable passed health and fresh-profile startup. Creating a tag does not prove device or desktop compatibility. Draft publication remains a separate acceptance decision; this workflow never publishes a draft automatically.

Release source exports exclude internal progress and agent instructions through the root `.gitattributes`; app, desktop, native build and release source remain included. The initial unpublished `v0.1.0` draft source archive was explicitly re-exported from the same tag with those filters after the build. APK/Linux artifacts and signed metadata retain the exact verified job bytes. See the recorded source-export evidence before publishing this draft.

Private signing files: `release-private/android-release.p12`, `android-store-password`, and `update-ed25519.pem`. Keep secure backups outside Git; loss prevents ordinary signed updates. Public identities: `trust/update-public-key.hex` and `trust/android-cert-sha256.hex`. Never rotate identities silently. No signing passwords, keys, notification bodies or clipboard data belong in CI logs/artifacts.

After a qualified release exists, from a reviewed checkout authenticated for this repository:

```sh
python3 docs/releases/install.py --tag vMAJOR.MINOR.PATCH
```

This is the actual installer entrypoint, not a claim that any example version exists. For a first Android install, recipients download the signed APK from an authorized GitHub release and use the Android system installer. For later versions, recipients download the signed Android update ZIP through their own GitHub session, then use Connection health → Import signed Android update. Android installation keeps its system confirmation and signing protections. Current development/debug installations cannot be replaced by a different release package identity.

## Installer verification

Run `python3 docs/releases/tools/test-installer.py`. It uses disposable Ed25519 keys and isolated homes, never the production key or the user's installation. It checks fresh install, upgrade/data preservation, downgrade rejection, corruption/signature failures, archive traversal, unrelated file protection and stable release selection. The Bash bootstrap execution test uses simulated package presence to avoid package-manager changes; it skips execution as root because the real bootstrap refuses root. CI also generates the Bash entrypoint and runs `bash -n`.

On 2026-11-12, all nine public `v0.1.3` asset URLs returned anonymous HTTP200. Both the published Bash installer and source Python installer installed the real signed release in isolated homes on the owner’s Arch host, with all bootstrap runtime packages already present. No application processes were launched by those installation checks. This does not replace other-distribution qualification or Android system-installer confirmation.

## APK install and publication gates

Android rejects an older version code even when its APK is valid and correctly signed; some system installers report this as an invalid package. A newer installed candidate must not be replaced with an older public APK. Keep its data and install the same or a newer version signed by the same release identity.

Hosted builds now verify APK ZIP integrity, application ID, exact version/name and pinned signer. Signed output includes a complete SHA256SUMS inventory, and release verification cross-checks the signed manifest, Android update ZIP, source exclusions and installer syntax before draft creation. A separate disposable Android 36 emulator job tests fresh install, upgrade from immutable 0.1.3, same-version reinstall and expected downgrade rejection. It never targets the owner's phone. Drafts are not published automatically; publication follows the successful hosted run and downloaded-artifact verification. Main and pull-request pushes run unsigned checks without production signing keys.
