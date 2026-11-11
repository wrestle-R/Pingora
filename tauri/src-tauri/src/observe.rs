use crate::{model::Notice, runtime::Runtime};
use dbus::{
    arg::PropMap,
    blocking::Connection,
    channel::{BusType, Channel},
    Message, MessageType,
};
use sha2::{Digest, Sha256};
use std::{collections::BTreeMap, sync::Arc, time::Duration};

struct Candidate {
    app: String,
    title: String,
    body: String,
    version: String,
}
fn plain(text: &str) -> String {
    let mut output = String::new();
    let mut rest = text;
    while let Some(start) = rest.find('<') {
        output.push_str(&rest[..start]);
        let tail = &rest[start..];
        if let Some(end) = tail.find('>').filter(|end| *end < 1024) {
            let tag = &tail[1..end];
            if tag.starts_with(|c: char| c.is_ascii_alphabetic() || c == '/') {
                if tag.eq_ignore_ascii_case("br") || tag.eq_ignore_ascii_case("br/") {
                    output.push('\n');
                }
                rest = &tail[end + 1..];
                continue;
            }
        }
        output.push('<');
        rest = &tail[1..];
    }
    output.push_str(rest);
    output
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
}
pub fn start(runtime: Arc<Runtime>) {
    std::thread::spawn(move || {
        while !runtime.stop.load(std::sync::atomic::Ordering::Relaxed) {
            if !runtime.desktop_observation_enabled() {
                std::thread::sleep(Duration::from_millis(500));
                continue;
            }
            if monitor(&runtime).is_err() {
                runtime.observer_health(false);
                std::thread::sleep(Duration::from_secs(2));
            }
        }
    });
}
fn monitor(runtime: &Runtime) -> Result<(), ()> {
    let control = Connection::new_session().map_err(|_| ())?;
    let proxy = control.with_proxy(
        "org.freedesktop.DBus",
        "/org/freedesktop/DBus",
        Duration::from_secs(2),
    );
    let (owner,): (String,) = proxy
        .method_call(
            "org.freedesktop.DBus",
            "GetNameOwner",
            ("org.freedesktop.Notifications",),
        )
        .map_err(|_| ())?;
    let channel = Channel::get_private(BusType::Session).map_err(|_| ())?;
    let rules=vec!["type='method_call',interface='org.freedesktop.Notifications',member='Notify'".to_string(),format!("type='method_return',sender='{owner}'"),format!("type='signal',sender='{owner}',interface='org.freedesktop.Notifications',member='NotificationClosed'")];
    let request = Message::new_method_call(
        "org.freedesktop.DBus",
        "/org/freedesktop/DBus",
        "org.freedesktop.DBus.Monitoring",
        "BecomeMonitor",
    )
    .map_err(|_| ())?
    .append2(rules, 0u32);
    channel
        .send_with_reply_and_block(request, Duration::from_secs(2))
        .map_err(|_| ())?;
    runtime.observer_health(true);
    let mut pending: BTreeMap<(String, u32), Candidate> = BTreeMap::new();
    let mut maintenance = crate::runtime::now();
    while !runtime.stop.load(std::sync::atomic::Ordering::Relaxed)
        && runtime.desktop_observation_enabled()
    {
        channel
            .read_write(Some(Duration::from_millis(100)))
            .map_err(|_| ())?;
        while let Some(message) = channel.pop_message() {
            match message.msg_type() {
                MessageType::MethodCall => {
                    if message.member().as_ref().map(|m| m.as_ref()) != Some("Notify") {
                        continue;
                    }
                    type Args = (
                        String,
                        u32,
                        String,
                        String,
                        String,
                        Vec<String>,
                        PropMap,
                        i32,
                    );
                    let Ok((_, _, _, title, body, _, hints, _)) = message.read_all::<Args>() else {
                        continue;
                    };
                    let Some(app) = hints
                        .get("desktop-entry")
                        .and_then(|v| v.0.as_str())
                        .filter(|s| s.len() <= 256 && *s != "io.github.wrestle-r.pingora")
                    else {
                        continue;
                    };
                    if !runtime.desktop_app_selected(app)
                        || title.len() > 4096
                        || body.len() > 32768
                    {
                        continue;
                    }
                    let Some(sender) = message.sender().map(|s| s.to_string()) else {
                        continue;
                    };
                    let Some(serial) = message.get_serial() else {
                        continue;
                    };
                    let version = format!(
                        "{:x}",
                        Sha256::digest(format!("{sender}\0{serial}\0{title}\0{body}"))
                    );
                    if pending.len() >= 128 {
                        pending.clear();
                    }
                    pending.insert(
                        (sender, serial),
                        Candidate {
                            app: app.into(),
                            title: plain(&title),
                            body: plain(&body),
                            version,
                        },
                    );
                }
                MessageType::MethodReturn => {
                    let Some(destination) = message.destination().map(|s| s.to_string()) else {
                        continue;
                    };
                    let Some(serial) = message.get_reply_serial() else {
                        continue;
                    };
                    if let Some(item) = pending.remove(&(destination, serial)) {
                        if let Ok((id,)) = message.read_all::<(u32,)>() {
                            let source = format!("desktop:{owner}:{id}");
                            runtime.desktop_notice(
                                Notice {
                                    peer: String::new(),
                                    source: source.clone(),
                                    app: item.app,
                                    conversation: source,
                                    version: item.version,
                                    sender: String::new(),
                                    title: item.title,
                                    body: item.body,
                                    received: crate::runtime::now(),
                                    active: true,
                                    reply: false,
                                    read: false,
                                },
                                id,
                            );
                        }
                    }
                }
                MessageType::Signal
                    if message.member().as_ref().map(|m| m.as_ref())
                        == Some("NotificationClosed") =>
                {
                    if let Ok((id, _reason)) = message.read_all::<(u32, u32)>() {
                        runtime.desktop_removed(&format!("desktop:{owner}:{id}"));
                    }
                }
                _ => {}
            }
        }
        if crate::runtime::now().saturating_sub(maintenance) > 5000 {
            maintenance = crate::runtime::now();
            let result: Result<(String,), _> = proxy.method_call(
                "org.freedesktop.DBus",
                "GetNameOwner",
                ("org.freedesktop.Notifications",),
            );
            if !result.is_ok_and(|(current,)| current == owner) {
                return Err(());
            }
            pending.clear();
        }
    }
    runtime.observer_health(false);
    Ok(())
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn native_markup_is_text_and_unicode_survives() {
        assert_eq!(plain("<b>Hi 🌈</b><br/>A &amp; B &lt;3"), "Hi 🌈\nA & B <3");
        assert_eq!(plain("1 < 2"), "1 < 2");
    }
}
