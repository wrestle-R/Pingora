# Android widgets (0.1.7)

Add widgets from Home → Settings or your launcher's widget picker. Update both devices to a matching release.

## Clipboard

Tap **Send clipboard** to send the current text to the connected laptop. Android requires a briefly focused send surface to read the clipboard; Pingora gives that surface a separate task so it returns to the launcher instead of bringing the main app forward. No clipboard history is saved. Unlock the phone before sending. If multiple laptops are connected, choose the destination.

## Laptop status

The widget pins to one paired laptop and shows its reported battery, charging and internet connectivity, plus an elapsed update indicator. Status older than 90 seconds is treated as stale when refreshed; the elapsed indicator continues in the launcher even if Android stops Pingora. Controls recheck freshness and permissions when tapped. Offline or unavailable values are not presented as current readings.

- Tap the power-profile button to cycle through the laptop's available power saver, balanced and performance profiles. The label changes only after the laptop reports its actual state.
- Tap the lid-mode button to switch the lid-close preference between Sleep and Slumber. This changes what closing the lid does; it does not suspend or activate Slumber immediately.
- Tap refresh to request new status without opening the app.

Power-profile controls require `powerprofilesctl` and a working local power-profiles service. Internet status uses NetworkManager's reported connectivity through `nmcli`; other network managers show unknown. Slumber lid controls require the narrow IPC adapter documented under the release tools and the owner's matching QuickShell setup. Unsupported controls remain unavailable.

For notification delivery checks, Settings offers a harmless test notification, posted after five seconds so the app can be backgrounded. Connection health shows whether the listener is connected and provides a reconnect action. If your phone interrupts the connection, review Pingora's background battery setting; unrestricted mode allows background work and may use more battery. Android notification access and per-device sharing permissions are still required.
