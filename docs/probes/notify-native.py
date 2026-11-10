#!/usr/bin/env python3
"""Explicit synthetic notification probe; creates/replaces/closes its own items only.
Run deliberately on an unlocked test desktop with /usr/bin/python.
Never reads existing notifications or their contents. No input arguments accepted.
"""
from gi.repository import Gio, GLib
import json
connection=Gio.bus_get_sync(Gio.BusType.SESSION,None)
service='org.freedesktop.Notifications'; path='/org/freedesktop/Notifications'
def call(method, args, result):
 return connection.call_sync(service,path,service,method,args,GLib.VariantType.new(result),Gio.DBusCallFlags.NONE,3000,None).unpack()
info=call('GetServerInformation',None,'(ssss)')
caps=call('GetCapabilities',None,'(as)')[0]
# Only fixed synthetic content. No history reads or real notification bodies.
def notify(replaces, body):
 return call('Notify',GLib.Variant('(susssasa{sv}i)',('Pingora adapter probe',replaces,'','Pingora synthetic adapter check',body,['default','Test action'],{'transient':GLib.Variant('b',True)},0)),'(u)')[0]
first=notify(0,'Synthetic version one. Safe to dismiss.')
second=notify(first,'Synthetic version two. Replacement probe.')
call('CloseNotification',GLib.Variant('(u)',(second,)),'()')
if first!=second:call('CloseNotification',GLib.Variant('(u)',(first,)),'()')
print(json.dumps({'server':info,'capabilities':caps,'replacement_preserved_id':first==second,'created_then_closed_only_synthetic_notifications':True}))
