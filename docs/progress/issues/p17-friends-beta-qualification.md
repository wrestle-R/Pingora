# P17: Friends beta qualification

**What to build:** All required core behavior tested across three desktops; calls explicitly verified or unavailable; documented resource baseline; recovery/install/update runbook.

**Blocked by:** P06, P07, P09, P10, P11, P12, P13, P14, P16, P19

**Status:** blocked

**Spec:** [Product plan](../../plan.md)

## Acceptance criteria

- [ ] All required core behavior tested across three desktops.
- [ ] Calls explicitly verified or unavailable.
- [ ] Documented resource baseline.
- [ ] Recovery/install/update runbook.

## Verification

2026-11-11: fresh-profile startup regression reproduced/fixed and verified on the actual rebuilt tar. A short unpaired Wayland desktop idle sample is recorded in [profile data](../native-idle-profile.json) and [implementation evidence](../implementation-verification.md). Five-second warmup/twenty-second sample; no native daemon or sharing/calls. This is not long-run or paired-load qualification, so the resource criterion remains open.

## Comments

Full implementation is authorized; unfinished device/desktop/load acceptance remains explicit.
