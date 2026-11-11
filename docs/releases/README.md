# Native release tooling

No app release is published yet. See [current verification](../progress/implementation-verification.md).

- `tools/build-linux.py --version VERSION --output DIRECTORY`: optimized host-dependent Linux x86_64 tar with bundled own native service/libraries and installer. Host GTK3/WebKitGTK4.1/Qt6/KF6 dependencies remain required. Not an AppImage compatibility claim.
- `tools/manifest.py`: signs finished Android/Linux inventory using the protected Ed25519 file. Produces metadata/signature and Android update ZIP; never copies private keys.
- `install.py`: uses the recipient’s existing `gh` login for private assets, verifies the pinned public signature and artifact hash, extracts only safe regular files/directories, atomically updates a user-owned launcher and keeps previous versions. Requires Python3.12+, OpenSSL and gh. `--source` supports an already downloaded signed asset directory. `--tag` selects an exact version; no root is needed.
- `tools/release.py --tag vVERSION [--publish]`: exact clean-tag guard, matching Cargo/Tauri/Android versions, Android version-code mapping `major*1000000 + minor*1000 + patch`, tests/builds, pinned native source retrieval, signing and corresponding-source archives. `--publish` stages a complete draft only. Never clobbers an existing release.
- `tools/slumber-adapter.py`: dry-run compatible-source check; explicit `--apply` writes a backed-up narrow lid setter. Does not restart the shell, change a preference, lock or unlock it.

The GitHub workflow requires a dedicated qualified `pingora-release` self-hosted Linux x86_64 runner and a protected `native-release` environment. Runner custody and provisioning are still outstanding, so no successful CI run is claimed. The runner needs the toolchains/dependencies in the native build notes, ANDROID_HOME, Java21, Rust/Cargo, CMake/Ninja, Python3.12+, OpenSSL/keytool/apksigner, and protected ignored signing files. They are not uploaded by this source commit.

Private signing files: `release-private/android-release.p12`, `android-store-password`, and `update-ed25519.pem`. Keep secure backups outside Git; loss prevents ordinary signed updates. Public trust anchor: `trust/update-public-key.hex`. Never rotate identities silently. No signing passwords, keys, notification bodies or clipboard data belong in CI logs/artifacts.

After a qualified release exists, from a reviewed checkout authenticated for this repository:

```sh
python3 docs/releases/install.py --tag vMAJOR.MINOR.PATCH
```

This is the actual installer entrypoint, not a claim that any example version exists. Android recipients download the signed Android update ZIP through their own GitHub session, then use Connection health → Import signed Android update. Android installation keeps its system confirmation and signing protections. Current development/debug installations cannot be replaced by a different release package identity.
