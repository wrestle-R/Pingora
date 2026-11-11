# P15: Tagged builds, signed beta assets and real installer

**What to build:** Exact tag produces both app artifacts; stable signing identity; authenticated private download; README installer tested on actual assets.

**Blocked by:** P01, P03

**Status:** blocked

**Spec:** [Product plan](../../plan.md)

## Acceptance criteria

- [ ] Exact tag produces both app artifacts.
- [ ] Stable signing identity.
- [ ] Authenticated private download.
- [ ] README installer tested on actual assets.

## Verification

2026-11-11: Android145tests and optimized APK build pass; Rust9tests/Clippy and Linux tar build pass. Candidate APK signature and Ed25519 metadata verify against tracked identities. The actual signed development Linux tar installed into an isolated temporary home using `docs/releases/install.py --source`; the installed executable health probe passed. This is not a tagged release or prior-version OS-upgrade qualification.

User selected GitHub-hosted runners with protected signing secrets. Environment `native-release` is provisioned with the three named secrets documented in the release guide and restricted to `v*` tags; no reviewer gate is configured. Workflow separates a keyless digest-pinned Arch build from a hosted signing job and only creates complete drafts. Local actionlint/Python checks pass. First hosted unsigned run: https://github.com/wrestle-R/Pingora/actions/runs/42971092839 (in progress).

## Comments

Full implementation and milestone pushes are authorized. Keep exact-tag production, private-download and signing-backup/upgrade qualification distinct from candidate package checks.

## Implementation checkpoint (2026-11-11)

Source paths now exist for this ticket; see [combined implementation evidence and remaining acceptance](../implementation-verification.md). Build/test evidence does not complete the real-device/desktop/release criteria above. Retain unfinished checkboxes until those exact outcomes are demonstrated.
