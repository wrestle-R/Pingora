# P15: Tagged builds, signed beta assets and real installer

**What to build:** Exact tag produces both app artifacts; stable signing identity; authenticated private download; README installer tested on actual assets.

**Blocked by:** P03 (device acceptance before a qualified beta; release engineering below is verified)

**Status:** blocked

**Spec:** [Product plan](../../plan.md)

## Acceptance criteria

- [x] Exact tag produces both app artifacts.
- [x] Stable signing identity.
- [x] Authenticated private download.
- [x] README installer tested on actual assets.

## Verification

2026-11-11: tag `v0.1.0`, commit `7764207f472b5361384265284a1f20ad395011eb`, passed [GitHub-hosted build and protected signing](https://github.com/wrestle-R/Pingora/actions/runs/42975982663). Build35m19s; signing27s. Rust9tests/Clippy, Android testDebugUnitTest/optimized APK, isolated Qt service and optimized Linux tar/package verifier passed. The signing job used the tag-restricted native-release environment and created only a private draft.

Downloaded exact draft assets independently passed Ed25519, sizes/hashes, source commit/native archive pin, Android ZIP consistency and APK certificate/package/version checks. Actual README installer fetched the private draft through the authenticated owner's gh session into an isolated temporary home; installed executable health and six-second fresh-profile GUI startup passed. No system installation or phone changes were made.

Public identities are pinned in docs/releases/trust; three signing secrets are provisioned in native-release with v* tag policy, no reviewer gate. Wrong Android identity and wrong Ed25519 signer reject before bundle output. Real environment files were not changed.

The initial unpublished draft source archive contained internal progress/agent docs. It was explicitly filtered from the SAME tag and re-uploaded before publication; signed app/metadata bytes are unchanged. Root .gitattributes enforces these exclusions for future tag archives. All app/desktop/native/release source and the source commit header were retained. See [exact artifact inventory and checks](../native-release-verification.json).

## Remaining release gate

This completes the build/sign/download/install acceptance above, not a qualified beta. P03 device/UI acceptance remains open, as do broader P17 checks, recoverable off-machine signing-key backup verification and P16 prior-version OS upgrades. Keep the release as a private development draft. Never force-retag or silently replace signed assets.
