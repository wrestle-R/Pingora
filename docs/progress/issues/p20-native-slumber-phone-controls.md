# P20: Control the existing Linux Slumber integration from the phone

**What to build:** An explicit optional capability for the owner’s native Slumber, using a narrow adapter to the existing QuickShell IPC. No arbitrary remote shell or password unlock.

**Blocked by:** P03, P08

**Status:** blocked

**Spec:** [Slumber integration](../../slumber.md)

## Acceptance criteria

- [ ] Phone displays real Slumber availability, active state and lid preference.
- [ ] Explicit phone action starts Slumber through the existing lock-safe implementation.
- [ ] Lid Sleep/Slumber preference is configurable through a reviewed supported IPC extension (not currently exposed).
- [ ] Paired-session authorization, local opt-in, disabled-feature rejection and unpair/replay protection apply.
- [ ] Unsupported desktops show unavailable; no fallback to arbitrary commands, suspend or unlocking.
- [ ] Verify with owner’s actual desktop without bypassing lock authentication.

## Verification

2026-11-10: located source at `/home/rdp/Desktop/code/Hyprland-stuff/slumber/` (lowercase “stuff”), and live implementation under `~/.config/quickshell/ii/`. Read-only `qs -p ~/.config/quickshell/ii ipc call slumber status` returned valid state. Live IPC exposes `status`, `activate`, `idleSuspend`; only status was invoked. No lock, lid preference, power state or dotfiles changed.

## Comments

User identified the existing right-sidebar Slumber explicitly. This is a specific exception to the former blanket remote-command exclusion, not authorization for generic shell execution. Source snapshot provenance points to wrestle-R/Linux commit 6a3738a76feb748416cbde07ff7e17b0cd919be6; local Hyprland-stuff directory is not a Git checkout. Pairing and capability enforcement must precede phone-triggered side effects.
