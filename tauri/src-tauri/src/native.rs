use crate::{model::Notice, runtime::Runtime};
use dbus::{
    arg::{PropMap, Variant},
    blocking::Connection,
    message::MatchRule,
};
use std::{
    collections::BTreeMap,
    sync::{
        mpsc::{sync_channel, SyncSender},
        Arc, Mutex,
    },
    time::Duration,
};
use tauri::Manager;

#[derive(Clone)]
pub enum Command {
    Notice(Notice, bool),
    Remove(String, String, String),
    ClearPeer(String),
    Ring(String, bool),
}
#[derive(Clone)]
struct Entry {
    id: u32,
    notice: Notice,
}
fn escape(text: &str) -> String {
    text.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
}

pub fn start(runtime: Arc<Runtime>, app: tauri::AppHandle) -> SyncSender<Command> {
    let (tx, rx) = sync_channel(128);
    let ring_sender = tx.clone();
    std::thread::spawn(move || {
        let Ok(bus) = Connection::new_session() else {
            return;
        };
        let entries: Arc<Mutex<BTreeMap<String, Entry>>> = Arc::new(Mutex::new(BTreeMap::new()));
        let observed = Arc::clone(&entries);
        let action_runtime = Arc::clone(&runtime);
        let action_app = app.clone();
        let ring_id = Arc::new(std::sync::atomic::AtomicU32::new(0));
        let action_ring = Arc::clone(&ring_id);
        let mut rule = MatchRule::new_signal("org.freedesktop.Notifications", "ActionInvoked");
        rule.sender = Some("org.freedesktop.Notifications".into());
        let _=bus.add_match(rule,move |(id,action):(u32,String),_,_| {
            if action=="stop_ring" && id==action_ring.load(std::sync::atomic::Ordering::Relaxed){let _=ring_sender.try_send(Command::Ring(String::new(),false));return true;}
            let entry=observed.lock().ok().and_then(|m|m.values().find(|e|e.id==id).cloned());
            if let Some(entry)=entry {
                if action=="default" {if let Some(window)=action_app.get_webview_window("main") {window.show().ok();window.set_focus().ok();}}
                else if action=="dismiss" || action=="read" {
                    action_runtime.action(&entry.notice.peer,"notification_action",serde_json::json!({"source":entry.notice.source,"version":entry.notice.version,"action":action})).ok();
                }
            }
            true
        });
        let observed = Arc::clone(&entries);
        let close_runtime = Arc::clone(&runtime);
        let mut rule = MatchRule::new_signal("org.freedesktop.Notifications", "NotificationClosed");
        rule.sender = Some("org.freedesktop.Notifications".into());
        let _=bus.add_match(rule,move |(id,reason):(u32,u32),_,_| {
            let entry=if let Ok(mut m)=observed.lock() {let key=m.iter().find(|(_,e)|e.id==id).map(|(k,_)|k.clone());key.and_then(|k|m.remove(&k))}else{None};
            if reason==2 {if let Some(e)=entry {close_runtime.action(&e.notice.peer,"notification_action",serde_json::json!({"source":e.notice.source,"version":e.notice.version,"action":"dismiss"})).ok();}}
            true
        });
        let mut ring_until = 0u64;
        let mut ringing: Option<std::process::Child> = None;
        while !runtime.stop.load(std::sync::atomic::Ordering::Relaxed) {
            while let Ok(command) = rx.try_recv() {
                let proxy = bus.with_proxy(
                    "org.freedesktop.Notifications",
                    "/org/freedesktop/Notifications",
                    Duration::from_secs(2),
                );
                match command {
                    Command::Ring(peer, on) => {
                        if !on || runtime.ring_allowed(&peer) {
                            ring_until = if on {
                                crate::runtime::now() + 30_000
                            } else {
                                0
                            };
                            if !on {
                                if let Some(mut child) = ringing.take() {
                                    child.kill().ok();
                                    child.wait().ok();
                                }
                            }
                            if on {
                                let hints = PropMap::new();
                                let result: Result<(u32,), _> = proxy.method_call(
                                    "org.freedesktop.Notifications",
                                    "Notify",
                                    (
                                        "Pingora",
                                        ring_id.load(std::sync::atomic::Ordering::Relaxed),
                                        "",
                                        "Ringing this laptop",
                                        "Stops automatically after 30 seconds.",
                                        vec!["stop_ring", "Stop ringing"],
                                        hints,
                                        30_000i32,
                                    ),
                                );
                                if let Ok((id,)) = result {
                                    ring_id.store(id, std::sync::atomic::Ordering::Relaxed);
                                }
                            }
                        }
                    }
                    Command::Notice(notice, private_content) => {
                        if !runtime.notifications_allowed(&notice.peer) {
                            continue;
                        }
                        let key = format!(
                            "{}\u{0}{}\u{0}{}",
                            notice.peer, notice.app, notice.conversation
                        );
                        let old = entries
                            .lock()
                            .ok()
                            .and_then(|m| m.get(&key).map(|e| e.id))
                            .unwrap_or(0);
                        let summary = if private_content {
                            escape(if notice.sender.is_empty() {
                                &notice.title
                            } else {
                                &notice.sender
                            })
                        } else {
                            "New phone notification".into()
                        };
                        let body = if private_content {
                            escape(&notice.body)
                        } else {
                            "Open Pingora to view private content.".into()
                        };
                        let mut actions =
                            vec!["default", "Open Pingora", "dismiss", "Dismiss source"];
                        if notice.read {
                            actions.extend(["read", "Mark as read"]);
                        }
                        let mut hints = PropMap::new();
                        hints.insert(
                            "desktop-entry".into(),
                            Variant(Box::new("io.github.wrestle-r.pingora".to_string())),
                        );
                        let result: Result<(u32,), _> = proxy.method_call(
                            "org.freedesktop.Notifications",
                            "Notify",
                            ("Pingora", old, "", summary, body, actions, hints, -1i32),
                        );
                        if let Ok((id,)) = result {
                            if let Ok(mut m) = entries.lock() {
                                if m.len() >= 500 && !m.contains_key(&key) {
                                    if let Some(first) = m.keys().next().cloned() {
                                        if let Some(e) = m.remove(&first) {
                                            let _: Result<(), _> = proxy.method_call(
                                                "org.freedesktop.Notifications",
                                                "CloseNotification",
                                                (e.id,),
                                            );
                                        }
                                    }
                                }
                                m.insert(key, Entry { id, notice });
                            }
                        }
                    }
                    Command::Remove(peer, source, version) => {
                        if let Ok(mut m) = entries.lock() {
                            let key = m
                                .iter()
                                .find(|(_, e)| {
                                    e.notice.peer == peer
                                        && e.notice.source == source
                                        && e.notice.version == version
                                })
                                .map(|(k, _)| k.clone());
                            if let Some(e) = key.and_then(|k| m.remove(&k)) {
                                let _: Result<(), _> = proxy.method_call(
                                    "org.freedesktop.Notifications",
                                    "CloseNotification",
                                    (e.id,),
                                );
                            }
                        }
                    }
                    Command::ClearPeer(peer) => {
                        if let Ok(mut m) = entries.lock() {
                            let keys: Vec<_> = m
                                .iter()
                                .filter(|(_, e)| e.notice.peer == peer)
                                .map(|(k, _)| k.clone())
                                .collect();
                            for key in keys {
                                if let Some(e) = m.remove(&key) {
                                    let _: Result<(), _> = proxy.method_call(
                                        "org.freedesktop.Notifications",
                                        "CloseNotification",
                                        (e.id,),
                                    );
                                }
                            }
                        }
                    }
                }
            }
            if ringing
                .as_mut()
                .is_some_and(|child| matches!(child.try_wait(), Ok(Some(_))))
            {
                ringing = None;
            }
            if crate::runtime::now() >= ring_until {
                if let Some(mut child) = ringing.take() {
                    child.kill().ok();
                    child.wait().ok();
                }
            } else if ringing.is_none() {
                ringing = std::process::Command::new("/usr/bin/paplay")
                    .arg("/usr/share/sounds/freedesktop/stereo/phone-incoming-call.oga")
                    .stdin(std::process::Stdio::null())
                    .stdout(std::process::Stdio::null())
                    .stderr(std::process::Stdio::null())
                    .spawn()
                    .ok();
            }
            let _ = bus.process(Duration::from_millis(100));
        }
        if let Some(mut child) = ringing.take() {
            child.kill().ok();
            child.wait().ok();
        }
        let proxy = bus.with_proxy(
            "org.freedesktop.Notifications",
            "/org/freedesktop/Notifications",
            Duration::from_secs(1),
        );
        if let Ok(m) = entries.lock() {
            for entry in m.values() {
                let _: Result<(), _> = proxy.method_call(
                    "org.freedesktop.Notifications",
                    "CloseNotification",
                    (entry.id,),
                );
            }
        };
    });
    tx
}
