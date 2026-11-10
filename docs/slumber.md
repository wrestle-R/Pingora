# Native Slumber integration

The user explicitly requested phone control for the Slumber installed on their Linux laptop, and identified `code/Hyprland-stuff` plus the right-sidebar control. This supersedes the earlier assumption that Slumber was an unidentified external service.

## Verified interface (2026-11-10)

Source snapshot: `/home/rdp/Desktop/code/Hyprland-stuff/slumber/`. Its `SOURCE.json` identifies wrestle-R/Linux revision `6a3738a76feb748416cbde07ff7e17b0cd919be6`, path `End-4/slumber/`. The local snapshot directory has no `.git`; a repository HEAD cannot be claimed. Native implementation is in `~/.config/quickshell/ii/services/Slumber.qml`, with sidebar UI `modules/ii/sidebarRight/LidActionControl.qml`.

Existing user IPC:

```sh
qs -p ~/.config/quickshell/ii ipc call slumber status
qs -p ~/.config/quickshell/ii ipc call slumber activate
```

Only `status` was executed during inspection. It exposes booleans for active/secure/lidReady/lidClosed/dark/intro, a lidAction enum and error text. Do not forward arbitrary error text into diagnostics. `activate` delegates to the existing native lock-secure/display path; Pingora must never implement a separate password bypass or return “locked” before secure state is observed.

The sidebar also has Sleep/Slumber lid preference controls. Those call an internal `setLidAction` function, which is not exported over IPC. Add a reviewed narrow IPC setter before offering that phone control. An unrelated `idleSuspend` method exists; do not expose it as Slumber activation. There is no IPC unlock, and Pingora must not introduce one.

## Capability contract

Use an optional explicit `slumber` capability, disabled until locally enabled, restricted to an authenticated paired phone. Accepted actions are a finite set, never executable paths or command strings supplied by a remote peer. Status reads can be exposed locally while pairing is unimplemented; side effects require the completed session authorization/revocation/replay protections. Do not silently activate Slumber during testing, restart the desktop, change lid settings or install root services.

Supported source platform is Arch/Hyprland with the matching end-4 QuickShell setup. Plasma/GNOME and other shells report unavailable until an actual adapter exists. Slumber support does not broaden the product into touchpad, keyboard, media or generic remote commands.

Tracked in [P20](progress/issues/p20-native-slumber-phone-controls.md). No phone control is implemented yet.
