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
    Notice(Notice),
    Remove(String, String, String),
    ClearPeer(String),
    Ring(String, bool),
}
#[derive(Clone)]
struct Entry {
    id: u32,
    notice: Notice,
    revealed: bool,
    open: bool,
    retiring: bool,
}
fn escape(text: &str) -> String {
    text.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
}

// One projection builds initial and replacement payloads, so redaction applies to
// every field sent to the native daemon, including actions and markup.
fn presentation(notice: &Notice, reveal: bool, open: bool) -> (String, String, Vec<&'static str>) {
    let summary = if reveal {
        escape(if notice.sender.is_empty() {
            &notice.title
        } else {
            &notice.sender
        })
    } else {
        "New phone notification".into()
    };
    let body = if reveal {
        escape(&notice.body)
    } else {
        "Open Pingora to view private content.".into()
    };
    let mut actions = vec!["default", "Open Pingora"];
    if open {
        actions.extend(["dismiss", "Dismiss source"]);
        if notice.read {
            actions.extend(["read", "Mark as read"]);
        }
    }
    (summary, body, actions)
}
fn notify(
    bus: &Connection,
    old: u32,
    notice: &Notice,
    reveal: bool,
    open: bool,
    quiet: bool,
) -> Result<u32, dbus::Error> {
    let (summary, body, actions) = presentation(notice, reveal, open);
    let mut hints = PropMap::new();
    hints.insert(
        "desktop-entry".into(),
        Variant(Box::new("io.github.wrestle-r.pingora".to_string())),
    );
    if quiet {
        hints.insert("suppress-sound".into(), Variant(Box::new(true)));
    }
    let (id,): (u32,) = bus
        .with_proxy(
            "org.freedesktop.Notifications",
            "/org/freedesktop/Notifications",
            Duration::from_secs(2),
        )
        .method_call(
            "org.freedesktop.Notifications",
            "Notify",
            ("Pingora", old, "", summary, body, actions, hints, -1i32),
        )?;
    Ok(id)
}
fn close(bus: &Connection, id: u32) -> bool {
    let result: Result<(), _> = bus
        .with_proxy(
            "org.freedesktop.Notifications",
            "/org/freedesktop/Notifications",
            Duration::from_secs(1),
        )
        .method_call("org.freedesktop.Notifications", "CloseNotification", (id,));
    result.is_ok()
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
        let mut privacy_check = std::time::Instant::now();
        let mut ring_until = 0u64;
        let mut ring_peer: Option<String> = None;
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
                            ring_peer = if on { Some(peer.clone()) } else { None };
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
                    Command::Notice(notice) => {
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
                        let open = crate::runtime::unlocked();
                        let reveal = runtime
                            .notification_visibility(&notice.peer)
                            .is_some_and(|show_locked| show_locked || open);
                        let result = notify(&bus, old, &notice, reveal, open, false);
                        if let Ok(id) = result {
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
                                m.insert(
                                    key,
                                    Entry {
                                        id,
                                        notice,
                                        revealed: reveal,
                                        open,
                                        retiring: false,
                                    },
                                );
                            }
                        }
                    }
                    Command::Remove(peer, source, version) => {
                        if let Ok(mut m) = entries.lock() {
                            for entry in m.values_mut().filter(|e| {
                                e.notice.peer == peer
                                    && e.notice.source == source
                                    && e.notice.version == version
                            }) {
                                entry.retiring = true;
                            }
                        }
                    }
                    Command::ClearPeer(peer) => {
                        if let Ok(mut m) = entries.lock() {
                            for entry in m.values_mut().filter(|e| e.notice.peer == peer) {
                                entry.retiring = true;
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
            if ring_peer
                .as_ref()
                .is_some_and(|peer| !runtime.ring_allowed(peer))
            {
                ring_until = 0;
            }
            if crate::runtime::now() >= ring_until {
                ring_peer = None;
                let notification = ring_id.swap(0, std::sync::atomic::Ordering::Relaxed);
                if notification != 0 {
                    let _: Result<(), _> = bus
                        .with_proxy(
                            "org.freedesktop.Notifications",
                            "/org/freedesktop/Notifications",
                            Duration::from_secs(1),
                        )
                        .method_call(
                            "org.freedesktop.Notifications",
                            "CloseNotification",
                            (notification,),
                        );
                }
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
            if privacy_check.elapsed() >= Duration::from_millis(500) {
                privacy_check = std::time::Instant::now();
                let snapshot = entries.lock().map(|m| m.clone()).unwrap_or_default();
                let open = !snapshot.is_empty() && crate::runtime::unlocked();
                for (key, entry) in snapshot {
                    let visibility = runtime.notification_visibility(&entry.notice.peer);
                    let reveal = visibility.is_some_and(|show_locked| show_locked || open);
                    if entry.retiring || visibility.is_none() {
                        if close(&bus, entry.id) {
                            if let Ok(mut m) = entries.lock() {
                                m.remove(&key);
                            }
                        }
                    } else if reveal != entry.revealed || open != entry.open {
                        match notify(&bus, entry.id, &entry.notice, reveal, open, true) {
                            Ok(id) if id == entry.id => {
                                if let Ok(mut m) = entries.lock() {
                                    if let Some(current) = m.get_mut(&key) {
                                        current.revealed = reveal;
                                        current.open = open;
                                    }
                                }
                            }
                            // Do not resurrect expired notifications during privacy refresh.
                            Ok(id) => {
                                if close(&bus, id) {
                                    if let Ok(mut m) = entries.lock() {
                                        m.remove(&key);
                                    }
                                } else if let Ok(mut m) = entries.lock() {
                                    if let Some(current) = m.get_mut(&key) {
                                        current.id = id;
                                        current.retiring = true;
                                    }
                                }
                            }
                            Err(_) => {
                                // Retain failed-close entries so the next pass retries instead
                                // of forgetting a possibly visible preview on a sick daemon.
                                if close(&bus, entry.id) {
                                    if let Ok(mut m) = entries.lock() {
                                        m.remove(&key);
                                    }
                                } else if let Ok(mut m) = entries.lock() {
                                    if let Some(current) = m.get_mut(&key) {
                                        current.retiring = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
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

#[cfg(test)]
mod tests {
    use super::*;
    fn notice() -> Notice {
        serde_json::from_value(serde_json::json!({"peer":"synthetic","source":"one","version":"v1","app":"test","conversation":"thread","sender":"<sender>","title":"Private title","body":"Secret & <b>markup</b>","received":1,"read":true})).unwrap()
    }
    #[test]
    fn native_daemon_receives_private_bus_replacements_and_close() {
        use dbus::channel::{Channel, MatchingReceiver, Sender};
        use std::io::BufRead;
        use std::sync::atomic::{AtomicBool, Ordering};
        struct Fixture {
            child: std::process::Child,
            stop: Arc<AtomicBool>,
            worker: Option<std::thread::JoinHandle<()>>,
        }
        impl Drop for Fixture {
            fn drop(&mut self) {
                self.stop.store(true, Ordering::Relaxed);
                if let Some(worker) = self.worker.take() {
                    let _ = worker.join();
                }
                let _ = self.child.kill();
                let _ = self.child.wait();
            }
        }
        // Dedicated bus, never the user's notification daemon/session bus.
        let mut child = std::process::Command::new("dbus-daemon")
            .args(["--session", "--nofork", "--print-address=1"])
            .stdout(std::process::Stdio::piped())
            .stderr(std::process::Stdio::null())
            .spawn()
            .unwrap();
        let mut address = String::new();
        std::io::BufReader::new(child.stdout.take().unwrap())
            .read_line(&mut address)
            .unwrap();
        let stop = Arc::new(AtomicBool::new(false));
        let mut fixture = Fixture {
            child,
            stop: Arc::clone(&stop),
            worker: None,
        };
        let connect = || {
            let mut channel = Channel::open_private(address.trim()).unwrap();
            channel.register().unwrap();
            Connection::from(channel)
        };
        let server = connect();
        server
            .request_name("org.freedesktop.Notifications", false, true, false)
            .unwrap();
        let received = Arc::new(Mutex::new(Vec::new()));
        let captured = Arc::clone(&received);
        let closed = Arc::new(Mutex::new(Vec::new()));
        let captured_close = Arc::clone(&closed);
        server.start_receive(
            MatchRule::new_method_call(),
            Box::new(move |message, connection| {
                match message.member().as_ref().map(|member| member.as_ref()) {
                    Some("Notify") => {
                        let (_, old, _, summary, body, actions, _, _): (
                            String,
                            u32,
                            String,
                            String,
                            String,
                            Vec<String>,
                            PropMap,
                            i32,
                        ) = message.read_all().unwrap();
                        captured.lock().unwrap().push((old, summary, body, actions));
                        connection
                            .send(message.method_return().append1(if old == 0 {
                                41u32
                            } else {
                                old
                            }))
                            .unwrap();
                    }
                    Some("CloseNotification") => {
                        let (id,): (u32,) = message.read_all().unwrap();
                        captured_close.lock().unwrap().push(id);
                        connection.send(message.method_return()).unwrap();
                    }
                    _ => {}
                }
                true
            }),
        );
        fixture.worker = Some(std::thread::spawn(move || {
            while !stop.load(Ordering::Relaxed) {
                server.process(Duration::from_millis(20)).unwrap();
            }
        }));
        let client = connect();
        let notice = notice();
        let id = notify(&client, 0, &notice, true, true, false).unwrap();
        assert_eq!(
            notify(&client, id, &notice, false, false, true).unwrap(),
            id
        );
        assert_eq!(notify(&client, id, &notice, true, true, true).unwrap(), id);
        assert!(close(&client, id));
        let rows = received.lock().unwrap();
        assert_eq!(rows.len(), 3);
        assert!(rows[0].2.contains("Secret"));
        assert!(!rows[1].2.contains("Secret"));
        assert!(!rows[1].3.iter().any(|action| action == "dismiss"));
        assert!(rows[2].2.contains("Secret"));
        assert_eq!(*closed.lock().unwrap(), vec![id]);
    }
    #[test]
    fn native_payload_redacts_content_and_actions_when_locked_or_unknown() {
        let notice = notice();
        let (title, body, actions) = presentation(&notice, false, false);
        assert!(!title.contains("sender"));
        assert!(!body.contains("Secret"));
        assert_eq!(actions, vec!["default", "Open Pingora"]);
        let (title, body, actions) = presentation(&notice, true, true);
        assert_eq!(title, "&lt;sender&gt;");
        assert_eq!(body, "Secret &amp; &lt;b&gt;markup&lt;/b&gt;");
        assert!(actions.contains(&"read"));
        assert!(actions.contains(&"dismiss"));
        let (_, body, actions) = presentation(&notice, true, false);
        assert!(body.contains("Secret"));
        assert!(!actions.contains(&"read"));
    }
}
