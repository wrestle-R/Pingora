# Native release tooling

The `v0.1.0` signed development draft has been built successfully; no qualified app release is published yet. See [current verification](../progress/implementation-verification.md).

- `tools/build-linux.py --version VERSION --output DIRECTORY`: optimized host-dependent Linux x86_64 tar with bundled own native service/libraries and installer. Host GTK3/WebKitGTK4.1/Qt6/KF6 dependencies remain required. Not an AppImage compatibility claim.
- `tools/verify-linux.py ARTIFACT`: extracts the actual tar, checks required resources/trust, host dependencies and bundled native-library resolution, then runs only the headless health command. Every Linux package build invokes it before upload.
- `tools/manifest.py`: signs finished Android/Linux inventory using the protected Ed25519 file. Produces metadata/signature and Android update ZIP; never copies private keys.
- `install.py`: uses the recipient’s existing `gh` login for private assets, verifies the pinned public signature and artifact hash, extracts only safe regular files/directories, atomically updates a user-owned launcher and keeps previous versions. Requires Python3.12+, OpenSSL and gh. `--source` supports an already downloaded signed asset directory. `--tag` selects an exact version; no root is needed.
- `tools/release.py --tag vVERSION [--publish]`: exact clean-tag guard, matching Cargo/Tauri/Android versions, Android version-code mapping `major*1000000 + minor*1000 + patch`, tests/builds, pinned native source retrieval, signing and corresponding-source archives. `--publish` stages a complete draft only. Never clobbers an existing release.
- `../probes/verify-desktop-start.py BINARY`: on a host with an available display, runs the actual desktop with a fresh home/session bus and no XDG Downloads entry. Catches the initial-profile startup regression; does not claim feature acceptance.
- `tools/slumber-adapter.py`: dry-run compatible-source check; explicit `--apply` writes a backed-up narrow lid setter. Does not restart the shell, change a preference, lock or unlock it.

The GitHub workflow uses GitHub-hosted Ubuntu24.04 runners. The keyless build job runs inside a digest-pinned Arch Linux container, installs Java21/native dependencies and the checksum-pinned Android SDK, runs Rust/Android checks, then uploads unsigned Android/Linux artifacts and corresponding source. Its package inventory is retained with the artifacts. Arch dependencies use the fixed 2026-11-10 archive snapshot (both core/extra repository databases were reachable when configured). The image digest, package inventory, source pins and lockfiles record the build inputs; bit-for-bit output reproducibility has not been demonstrated.

A separate signing job runs only for stable `vMAJOR.MINOR.PATCH` tags whose commit belongs to `main`. It uses the `native-release` environment, restricted to `v*` tags, with environment secrets `PINGORA_ANDROID_KEYSTORE_B64`, `PINGORA_ANDROID_STORE_PASSWORD` and `PINGORA_UPDATE_SIGNING_KEY`. These secrets have been provisioned. The job materializes private files temporarily, signs/verifies the finished artifacts against both tracked public identities, creates a complete GitHub Release draft, then removes the key files. No signing keys are available to the build job or included in artifacts. GitHub environment reviewer approval is not configured; tag restrictions are the current protection rule.

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
