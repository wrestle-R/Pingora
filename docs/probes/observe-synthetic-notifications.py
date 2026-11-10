#!/usr/bin/env python3
"""Observe only Notify calls from this process's own synthetic client.
No user notification source or history is subscribed to; no bodies are logged.
"""
from gi.repository import Gio, GLib
import json
import os
import time

client = Gio.bus_get_sync(Gio.BusType.SESSION, None)
monitor = Gio.DBusConnection.new_for_address_sync(
    os.environ['DBUS_SESSION_BUS_ADDRESS'],
    Gio.DBusConnectionFlags.AUTHENTICATION_CLIENT | Gio.DBusConnectionFlags.MESSAGE_BUS_CONNECTION,
    None, None)
seen = []
def receive(connection, message, incoming, data):
    if incoming and message.get_sender() == client.get_unique_name() and message.get_member() == 'Notify':
        seen.append(True)
    # Monitors must not reply to observed method calls.
    return None if incoming and message.get_message_type() == Gio.DBusMessageType.METHOD_CALL else message
monitor.add_filter(receive, None)
rule = f"type='method_call',sender='{client.get_unique_name()}',interface='org.freedesktop.Notifications',member='Notify'"
monitor.call_sync('org.freedesktop.DBus', '/org/freedesktop/DBus',
    'org.freedesktop.DBus.Monitoring', 'BecomeMonitor', GLib.Variant('(asu)', ([rule], 0)),
    GLib.VariantType.new('()'), Gio.DBusCallFlags.NONE, 3000, None)
result = client.call_sync('org.freedesktop.Notifications', '/org/freedesktop/Notifications',
    'org.freedesktop.Notifications', 'Notify',
    GLib.Variant('(susssasa{sv}i)', ('Pingora synthetic observation probe', 0, '', 'Pingora adapter check',
        'Synthetic fixture only. No existing content is read.', [], {'transient': GLib.Variant('b', True)}, 0)),
    GLib.VariantType.new('(u)'), Gio.DBusCallFlags.NONE, 3000, None).unpack()[0]
client.call_sync('org.freedesktop.Notifications', '/org/freedesktop/Notifications',
    'org.freedesktop.Notifications', 'CloseNotification', GLib.Variant('(u)', (result,)),
    GLib.VariantType.new('()'), Gio.DBusCallFlags.NONE, 3000, None)
context = GLib.MainContext.default()
deadline = time.monotonic() + 2
while time.monotonic() < deadline and not seen:
    context.iteration(False)
if not monitor.is_closed(): monitor.close_sync(None)
print(json.dumps({'own_synthetic_notify_observed': len(seen) == 1,
    'subscription_restricted_to_own_sender': True, 'notification_bodies_logged': False}))
if len(seen) != 1: raise SystemExit(1)
