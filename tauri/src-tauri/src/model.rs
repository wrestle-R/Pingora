use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::{BTreeMap, VecDeque};

pub const DAY: u64 = 86_400_000;
#[derive(Clone, Copy, Debug, Deserialize, Serialize, PartialEq, Eq, PartialOrd, Ord)]
#[serde(rename_all = "snake_case")]
pub enum Feature {
    Notifications,
    Clipboard,
    Files,
    Folders,
    Battery,
    Ring,
    Slumber,
    Power,
}
#[derive(Clone, Deserialize, Serialize)]
#[serde(default)]
pub struct Policy {
    pub paused: bool,
    pub show_locked: bool,
    pub enabled: BTreeMap<Feature, bool>,
    pub desktop_apps: Vec<String>,
}
impl Default for Policy {
    fn default() -> Self {
        Self {
            paused: false,
            show_locked: false,
            enabled: [
                Feature::Notifications,
                Feature::Clipboard,
                Feature::Files,
                Feature::Folders,
                Feature::Battery,
                Feature::Ring,
                Feature::Slumber,
                Feature::Power,
            ]
            .into_iter()
            .map(|feature| (feature, true))
            .collect(),
            desktop_apps: vec!["*".into()],
        }
    }
}
impl Policy {
    pub fn allows(&self, feature: Feature) -> bool {
        !self.paused && self.enabled.get(&feature) == Some(&true)
    }
}
#[derive(Clone, Deserialize, Serialize)]
pub struct Notice {
    pub peer: String,
    pub source: String,
    pub app: String,
    pub conversation: String,
    pub version: String,
    pub sender: String,
    pub title: String,
    pub body: String,
    pub received: u64,
    #[serde(default)]
    pub active: bool,
    #[serde(default)]
    pub reply: bool,
    #[serde(default)]
    pub read: bool,
}
fn trim_bytes(s: &mut String, size: usize) {
    let mut end = size.min(s.len());
    while !s.is_char_boundary(end) {
        end -= 1;
    }
    s.truncate(end);
}
impl Notice {
    pub fn sanitize(&mut self) -> bool {
        if self.source.is_empty() || self.version.is_empty() || self.app.is_empty() {
            return false;
        }
        if self.source.len() > 512
            || self.version.len() > 128
            || self.app.len() > 256
            || self.conversation.len() > 512
        {
            return false;
        }
        trim_bytes(&mut self.sender, 512);
        trim_bytes(&mut self.title, 1024);
        trim_bytes(&mut self.body, 16384);
        if self.conversation.is_empty() {
            self.conversation = self.source.clone();
        }
        true
    }
}
#[derive(Clone, Deserialize, Serialize)]
pub struct PendingDismissal {
    pub peer: String,
    pub source: String,
    pub version: String,
    pub action_id: String,
    pub created: u64,
    #[serde(default)]
    pub last_sent: u64,
}
#[derive(Default, Deserialize, Serialize)]
pub struct Store {
    #[serde(default)]
    pub calls: crate::calls::Settings,
    #[serde(default)]
    pub pending_dismissals: VecDeque<PendingDismissal>,
    pub policies: BTreeMap<String, Policy>,
    pub history: VecDeque<Notice>,
}
impl Store {
    pub fn prune(&mut self, now: u64) {
        self.pending_dismissals
            .retain(|a| now.saturating_sub(a.created) < DAY);
        self.history.retain(|item| {
            now.saturating_sub(item.received) < DAY && item.received <= now.saturating_add(60_000)
        });
        while self.history.len() > 500 {
            self.history.pop_front();
        }
    }
    pub fn receive(&mut self, mut notice: Notice, now: u64) -> bool {
        self.prune(now);
        if !notice.sanitize()
            || self.history.iter().any(|n| {
                n.peer == notice.peer && n.source == notice.source && n.version == notice.version
            })
        {
            return false;
        }
        for n in &mut self.history {
            if n.peer == notice.peer && n.app == notice.app && n.conversation == notice.conversation
            {
                n.active = false;
                n.reply = false;
                n.read = false;
            }
        }
        notice.received = now;
        notice.active = true;
        self.history.push_back(notice);
        self.prune(now);
        true
    }
    pub fn remove(&mut self, peer: &str, source: &str, version: &str) {
        for n in &mut self.history {
            if n.peer == peer && n.source == source && n.version == version {
                n.active = false;
                n.reply = false;
                n.read = false;
            }
        }
    }
    pub fn clear(&mut self) {
        self.history.clear();
    }
    pub fn search(&mut self, query: &str, now: u64) -> Vec<Notice> {
        self.prune(now);
        let query = query.to_lowercase();
        self.history
            .iter()
            .rev()
            .filter(|n| {
                query.is_empty()
                    || [&n.sender, &n.title, &n.body, &n.app]
                        .iter()
                        .any(|s| s.to_lowercase().contains(&query))
            })
            .cloned()
            .collect()
    }
    pub fn current(&self, peer: &str, source: &str, version: &str) -> Option<&Notice> {
        self.history
            .iter()
            .rev()
            .find(|n| n.peer == peer && n.source == source && n.version == version && n.active)
    }
}
#[derive(Deserialize, Serialize)]
pub struct Envelope {
    #[serde(default)]
    pub seq: u64,
    pub id: String,
    pub kind: String,
    #[serde(default)]
    pub session: String,
    #[serde(default)]
    pub data: Value,
}
pub struct Session {
    pub local: String,
    pub remote: String,
    seen: VecDeque<String>,
    last_sequence: u64,
    pub last_hello: u64,
}
impl Session {
    pub fn new(local: String) -> Self {
        Self {
            local,
            remote: String::new(),
            seen: VecDeque::new(),
            last_sequence: 0,
            last_hello: 0,
        }
    }
    pub fn accept(&mut self, packet: &Envelope) -> bool {
        if packet.seq <= self.last_sequence
            || packet.id.is_empty()
            || packet.id.len() > 128
            || packet.session != self.local
            || self.seen.contains(&packet.id)
        {
            return false;
        }
        if self.seen.len() == 1024 {
            self.seen.pop_front();
        }
        self.last_sequence = packet.seq;
        self.seen.push_back(packet.id.clone());
        true
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn notice(source: &str, thread: &str, version: &str) -> Notice {
        Notice {
            peer: "phone".into(),
            source: source.into(),
            app: "chat".into(),
            conversation: thread.into(),
            version: version.into(),
            sender: "sender".into(),
            title: "group".into(),
            body: "OTP 123456 🌈".into(),
            received: 0,
            active: true,
            reply: true,
            read: true,
        }
    }
    #[test]
    fn conversations_replace_only_their_own_thread_and_stale_removals_protect_new_versions() {
        let mut s = Store::default();
        assert!(s.receive(notice("a", "one", "1"), 100));
        assert!(s.receive(notice("b", "two", "1"), 101));
        assert!(s.receive(notice("a", "one", "2"), 102));
        s.remove("phone", "a", "1");
        assert_eq!(s.history.iter().filter(|n| n.active).count(), 2);
        assert!(s.current("phone", "a", "2").is_some());
        assert!(!s.receive(notice("a", "one", "2"), 103));
    }
    #[test]
    fn history_bounds_and_otp_retention_are_identical() {
        let mut s = Store::default();
        for i in 0..510 {
            s.receive(notice(&i.to_string(), "thread", "v"), 100);
        }
        assert_eq!(s.search("123456", 100).len(), 500);
        s.prune(100 + DAY);
        assert!(s.history.is_empty());
    }
    #[test]
    fn unicode_body_limits_and_unknown_conversations_do_not_merge() {
        let mut s = Store::default();
        let mut n = notice("a", "", "1");
        n.body = "🌈".repeat(5000);
        s.receive(n, 100);
        s.receive(notice("b", "", "1"), 100);
        assert_eq!(s.history[0].body.len(), 16384);
        assert_eq!(s.history.iter().filter(|n| n.active).count(), 2);
        s.clear();
        assert!(s.history.is_empty());
    }
    #[test]
    fn session_rejects_replay_old_sessions_and_default_capabilities() {
        let mut session = Session::new("fresh".into());
        let mut packet = Envelope {
            seq: 1,
            id: "id".into(),
            kind: "ring".into(),
            session: "old".into(),
            data: Value::Null,
        };
        assert!(!session.accept(&packet));
        packet.session = "fresh".into();
        assert!(session.accept(&packet));
        assert!(!session.accept(&packet));
        let mut p = Policy::default();
        assert!(p.allows(Feature::Ring));
        p.enabled.insert(Feature::Ring, true);
        assert!(p.allows(Feature::Ring));
        p.paused = true;
        assert!(!p.allows(Feature::Ring));
    }
}
