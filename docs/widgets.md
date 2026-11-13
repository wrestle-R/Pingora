# Android widgets (0.1.8 candidate)

Add widgets from Home → Settings or your launcher's widget picker. Update both devices to a matching release.

## Clipboard

Tap **Send clipboard** to send the current text to the connected laptop. Android requires a briefly focused send surface to read the clipboard; Pingora gives that surface a separate task so it returns to the launcher instead of bringing the main app forward. No clipboard history is saved. Unlock the phone before sending. If multiple laptops are connected, choose the destination.

## Laptop status

The widget pins to one paired laptop and shows its reported battery, charging, system CPU/RAM and internet connectivity, plus the absolute last-sync time and elapsed age. The last successful snapshot remains available after disconnection, app process death or phone restart. Missing data says not synced, rather than claiming a zero-second-old reading. Status older than 90 seconds is treated as cached when refreshed; the elapsed indicator continues in the launcher even if Android stops Pingora. Smaller widget sizes omit secondary rows to keep core readings and the sync time visible. Controls recheck freshness and permissions when tapped. Offline or unavailable values are not presented as current readings.

- The widget shows only the current power-profile icon. In the app, open Power & lid controls to choose Battery saving, Efficiency or Powerful. These map to power-saver, balanced and performance; the displayed state comes from the laptop.
- Change Sleep/Slumber lid preference in the app. The widget only reports it when space permits. This changes what closing the lid does; it does not suspend or activate Slumber immediately.
- Tap refresh to request new status without opening the app.

Power-profile controls require `powerprofilesctl` and a working local power-profiles service. Internet status uses NetworkManager's reported connectivity through `nmcli`; other network managers show unknown. Slumber lid controls require the narrow IPC adapter documented under the release tools and the owner's matching QuickShell setup. Unsupported controls remain unavailable.

For notification delivery checks, Settings offers a harmless test notification, posted after five seconds so the app can be backgrounded. Connection health shows whether the listener is connected and provides a reconnect action. If your phone interrupts the connection, review Pingora's background battery setting; unrestricted mode allows background work and may use more battery. Android notification access and per-device sharing permissions are still required.

## Desktop background connection

The Linux installer creates a user-owned `pingora.service` when a user systemd manager is available. It starts at login and restarts after failure. Closing the desktop window hides it while sharing continues; opening Pingora again shows the existing instance. Use Settings → Quit Pingora or `systemctl --user stop pingora.service` to disconnect intentionally. To restart after a manual update, use `systemctl --user restart pingora.service`. On other setups, keep Pingora running in the desktop session.
