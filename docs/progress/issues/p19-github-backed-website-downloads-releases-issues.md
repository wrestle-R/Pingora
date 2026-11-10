# P19: GitHub-backed website downloads/releases/issues

**What to build:** Only permitted metadata displayed; correct real release assets; loading/empty/rate-limit states; private data never exposed; public downloads agree with published versions.

**Blocked by:** P18, P15

**Status:** blocked

**Spec:** [Product plan](../../plan.md)

## Acceptance criteria

- [ ] Only permitted metadata displayed.
- [ ] Correct real release assets.
- [ ] Loading/empty/rate-limit states.
- [ ] Private data never exposed.
- [ ] Public downloads agree with published versions.

## Verification

P21 implements a server-only anonymous public GitHub read model/API, dynamic downloads/changelog/support pages, five-minute safe caching and private/rate-limit/unavailable states. Local private-repo response contains zero release/issue details; build/typecheck and 30page/theme/viewport checks pass. Actual release assets, signing validation and public-repo feed acceptance remain blocked by P15/public-data prerequisites. No authenticated private GitHub fetch is used.

## Comments

Preparation ticket only; status is not authorization to start implementation. No app/release artifact exists yet.
