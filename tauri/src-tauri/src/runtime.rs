use crate::{
    model::{Envelope, Feature, Notice, Policy, Session, Store},
    service,
};
use serde::Serialize;
use serde_json::{json, Value};
use std::{
    collections::BTreeMap,
    fs::{self, OpenOptions},
    io::{Read, Write},
    os::unix::fs::{OpenOptionsExt, PermissionsExt},
    path::PathBuf,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc, Mutex,
    },
    time::{Duration, SystemTime, UNIX_EPOCH},
};

pub fn now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}
fn random() -> Result<String, String> {
    let mut bytes = [0u8; 24];
    fs::File::open("/dev/urandom")
        .and_then(|mut f| f.read_exact(&mut bytes))
        .map_err(|_| "Secure randomness unavailable")?;
    Ok(bytes.iter().map(|b| format!("{b:02x}")).collect())
}
#[derive(Default)]
struct Live {
    sessions: BTreeMap<String, Session>,
    remote_policies: BTreeMap<String, Policy>,
    battery: BTreeMap<String, Value>,
    clipboard: Option<String>,
    errors: u64,
    observing: bool,
    desktop: BTreeMap<String, (Notice, u32)>,
}
pub struct Runtime {
    store: Mutex<Store>,
    live: Mutex<Live>,
    path: PathBuf,
    update: Mutex<Option<crate::updates::Candidate>>,
    sequence: std::sync::atomic::AtomicU64,
    pub stop: AtomicBool,
    transfers: Mutex<crate::transfers::Transfers>,
    folders: Mutex<BTreeMap<String, Value>>,
    links: Mutex<Vec<(String, String)>>,
    native: Mutex<Option<std::sync::mpsc::SyncSender<crate::native::Command>>>,
}
#[derive(Serialize)]
pub struct Snapshot {
    pub policies: BTreeMap<String, Policy>,
    pub remote_policies: BTreeMap<String, Policy>,
    pub history: Vec<Notice>,
    pub battery: BTreeMap<String, Value>,
    pub clipboard_available: bool,
    pub private_hidden: bool,
    pub transfers: Vec<crate::transfers::Offer>,
    pub folders: BTreeMap<String, Value>,
    pub links: Vec<(String, String)>,
}
impl Runtime {
    pub fn open(path: PathBuf, downloads: PathBuf) -> Result<Arc<Self>, String> {
        fs::create_dir_all(&path).map_err(|_| "Could not create private app storage")?;
        fs::set_permissions(&path, fs::Permissions::from_mode(0o700))
            .map_err(|_| "Could not protect app storage")?;
        let file = path.join("state.json");
        let mut store = if file.exists() {
            let bytes = fs::read(&file).map_err(|_| "Could not read app storage")?;
            if bytes.len() > 12 * 1024 * 1024 {
                return Err("App storage exceeded its limit".into());
            }
            serde_json::from_slice::<Store>(&bytes)
                .map_err(|_| "App storage is invalid; preserve it before recovery")?
        } else {
            Store::default()
        };
        store.prune(now());
        let runtime = Arc::new(Self {
            store: Mutex::new(store),
            live: Mutex::new(Live::default()),
            path: file,
            update: Mutex::new(None),
            sequence: std::sync::atomic::AtomicU64::new(1),
            stop: AtomicBool::new(false),
            native: Mutex::new(None),
            transfers: Mutex::new(crate::transfers::Transfers::new(downloads)),
            folders: Mutex::new(BTreeMap::new()),
            links: Mutex::new(vec![]),
        });
        {
            let store = runtime.store.lock().map_err(|_| "Storage busy")?;
            runtime.save(&store)?;
        }
        Ok(runtime)
    }
    pub fn set_native(&self, sender: std::sync::mpsc::SyncSender<crate::native::Command>) {
        if let Ok(mut n) = self.native.lock() {
            *n = Some(sender);
        }
    }
    pub fn update_check(&self) -> Result<crate::updates::View, String> {
        let candidate = crate::updates::check()?;
        let view = crate::updates::view(&candidate);
        *self.update.lock().map_err(|_| "Update state busy")? = Some(candidate);
        Ok(view)
    }
    pub fn update_download(&self) -> Result<crate::updates::View, String> {
        let mut update = self.update.lock().map_err(|_| "Update state busy")?;
        let candidate = update.as_mut().ok_or("Check for a signed update first")?;
        crate::updates::download(
            candidate,
            &self
                .path
                .parent()
                .ok_or("Update storage unavailable")?
                .join("updates"),
        )?;
        Ok(crate::updates::view(candidate))
    }
    pub fn update_install(&self, approved: bool) -> Result<(), String> {
        if self.busy() {
            return Err("Installation deferred while calls or transfers are active".into());
        }
        let update = self.update.lock().map_err(|_| "Update state busy")?;
        let candidate = update
            .as_ref()
            .ok_or("Check and download a signed update first")?;
        crate::updates::install(candidate, approved, self.busy())
    }
    pub fn calls_snapshot(&self) -> Result<crate::calls::Snapshot, String> {
        let settings = self.store.lock().map_err(|_| "Storage busy")?.calls.clone();
        Ok(crate::calls::snapshot(settings))
    }
    pub fn calls_settings(&self, settings: crate::calls::Settings) -> Result<(), String> {
        crate::calls::validate(&settings)?;
        let mut s = self.store.lock().map_err(|_| "Storage busy")?;
        s.calls = settings;
        self.save(&s)
    }
    pub fn call_action(&self, kind: &str, call: &str, volume: u8) -> Result<(), String> {
        let mut s = self.store.lock().map_err(|_| "Storage busy")?;
        crate::calls::action(&mut s.calls, kind, call, volume)?;
        self.save(&s)
    }
    pub fn busy(&self) -> bool {
        let settings = self
            .store
            .lock()
            .ok()
            .map(|s| s.calls.clone())
            .unwrap_or_default();
        crate::calls::busy(&settings) || self.transfers.lock().ok().is_some_and(|t| t.active())
    }
    pub fn diagnostics(&self) -> Result<Value, String> {
        let s = self.store.lock().map_err(|_| "Storage busy")?;
        let l = self.live.lock().map_err(|_| "Session busy")?;
        Ok(
            json!({"schema":1,"app_version":env!("CARGO_PKG_VERSION"),"platform":"linux","paired_feature_sessions":l.sessions.len(),"retained_entries":s.history.len(),"pending_dismissals":s.pending_dismissals.len(),"notification_observer_available":l.observing,"native_service_available":service::paired_count().is_some(),"transport_error_count":l.errors,"sharing_paused_count":s.policies.values().filter(|p|p.paused).count(),"private_payloads_included":false}),
        )
    }
    pub fn desktop_observation_enabled(&self) -> bool {
        self.store.lock().ok().is_some_and(|s| {
            s.policies
                .values()
                .any(|p| p.allows(Feature::Notifications) && !p.desktop_apps.is_empty())
        })
    }
    pub fn desktop_app_selected(&self, app: &str) -> bool {
        self.store.lock().ok().is_some_and(|s| {
            s.policies.values().any(|p| {
                p.allows(Feature::Notifications) && p.desktop_apps.iter().any(|a| a == app)
            })
        })
    }
    pub fn observer_health(&self, active: bool) {
        if let Ok(mut l) = self.live.lock() {
            l.observing = active;
        }
    }
    pub fn desktop_notice(&self, mut notice: Notice, native_id: u32) {
        if !notice.sanitize() {
            return;
        }
        if let Ok(mut l) = self.live.lock() {
            if l.desktop.len() >= 500 && !l.desktop.contains_key(&notice.source) {
                if let Some(key) = l.desktop.keys().next().cloned() {
                    l.desktop.remove(&key);
                }
            }
            l.desktop
                .insert(notice.source.clone(), (notice.clone(), native_id));
        }
        let peers: Vec<String> = self
            .store
            .lock()
            .ok()
            .map(|s| {
                s.policies
                    .iter()
                    .filter(|(_, p)| {
                        p.allows(Feature::Notifications) && p.desktop_apps.contains(&notice.app)
                    })
                    .map(|(id, _)| id.clone())
                    .collect()
            })
            .unwrap_or_default();
        for peer in peers {
            if self.permitted(&peer, Feature::Notifications) {
                self.send(
                    &peer,
                    "notice",
                    serde_json::to_value(&notice).unwrap_or_default(),
                )
                .ok();
            }
        }
    }
    pub fn desktop_removed(&self, source: &str) {
        let item = self
            .live
            .lock()
            .ok()
            .and_then(|mut l| l.desktop.remove(source));
        if let Some((notice, _)) = item {
            let peers: Vec<String> = self
                .store
                .lock()
                .ok()
                .map(|s| s.policies.keys().cloned().collect())
                .unwrap_or_default();
            for peer in peers {
                if self.permitted(&peer, Feature::Notifications) {
                    self.send(
                        &peer,
                        "notice_removed",
                        json!({"source":source,"version":notice.version}),
                    )
                    .ok();
                }
            }
        }
    }
    fn desktop_sync(&self, id: &str) {
        let apps = self
            .store
            .lock()
            .ok()
            .and_then(|s| s.policies.get(id).map(|p| p.desktop_apps.clone()))
            .unwrap_or_default();
        let notices: Vec<Notice> = self
            .live
            .lock()
            .ok()
            .map(|l| {
                l.desktop
                    .values()
                    .filter(|(n, _)| apps.contains(&n.app))
                    .map(|(n, _)| n.clone())
                    .collect()
            })
            .unwrap_or_default();
        for n in notices {
            let mut data = serde_json::to_value(n).unwrap_or_default();
            data["snapshot"] = json!(true);
            self.send(id, "notice", data).ok();
        }
    }
    fn desktop_dismiss(&self, data: &Value) -> &'static str {
        if !unlocked() {
            return "locked";
        }
        let item = self.live.lock().ok().and_then(|l| {
            l.desktop
                .get(data["source"].as_str().unwrap_or_default())
                .cloned()
        });
        let Some((notice, id)) = item else {
            return "unavailable";
        };
        if data["version"].as_str() != Some(notice.version.as_str()) {
            return "stale";
        }
        if data["action"].as_str() != Some("dismiss") {
            return "unavailable";
        }
        let result = dbus::blocking::Connection::new_session().and_then(|c| {
            c.with_proxy(
                "org.freedesktop.Notifications",
                "/org/freedesktop/Notifications",
                Duration::from_secs(2),
            )
            .method_call::<(), _, _, _>(
                "org.freedesktop.Notifications",
                "CloseNotification",
                (id,),
            )
        });
        if result.is_ok() {
            "requested"
        } else {
            "unavailable"
        }
    }
    pub fn ring_allowed(&self, id: &str) -> bool {
        self.permitted(id, Feature::Ring)
    }
    pub fn notifications_allowed(&self, id: &str) -> bool {
        self.allowed(id, Feature::Notifications)
    }
    fn native(&self, command: crate::native::Command) {
        if let Ok(n) = self.native.lock() {
            if let Some(tx) = n.as_ref() {
                let _ = tx.try_send(command);
            }
        }
    }
    fn save(&self, store: &Store) -> Result<(), String> {
        let bytes = serde_json::to_vec(store).map_err(|_| "Could not encode app storage")?;
        let temporary = self.path.with_extension("tmp");
        let mut file = OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .mode(0o600)
            .open(&temporary)
            .map_err(|_| "Could not save app storage")?;
        file.write_all(&bytes)
            .and_then(|_| file.sync_all())
            .map_err(|_| "Could not save app storage")?;
        fs::rename(temporary, &self.path).map_err(|_| "Could not finish saving app storage".into())
    }
    pub fn policy(&self, id: &str, policy: Policy) -> Result<(), String> {
        if !service::peers()?.iter().any(|p| p.id == id && p.paired) {
            return Err("Pair this device first".into());
        }
        if policy.desktop_apps.len() > 64 || policy.desktop_apps.iter().any(|s| s.len() > 256) {
            return Err("App selection exceeds its limit".into());
        }
        let mut s = self.store.lock().map_err(|_| "Storage busy")?;
        s.policies.insert(id.into(), policy.clone());
        self.save(&s)?;
        drop(s);
        if policy.paused || !policy.allows(Feature::Files) {
            if let Ok(mut t) = self.transfers.lock() {
                t.cancel_peer(id);
            }
        }
        if !policy.allows(Feature::Notifications) {
            self.native(crate::native::Command::ClearPeer(id.into()));
        }
        if policy.paused || !policy.allows(Feature::Clipboard) {
            self.live.lock().map_err(|_| "Session busy")?.clipboard = None;
        }
        self.send(
            id,
            "policy",
            json!({"enabled":policy.enabled,"paused":policy.paused}),
        )
        .ok();
        Ok(())
    }
    pub fn snapshot(&self, query: &str) -> Result<Snapshot, String> {
        if query.len() > 1024 {
            return Err("Search is too long".into());
        }
        let mut s = self.store.lock().map_err(|_| "Storage busy")?;
        let open = unlocked();
        let history = s
            .search(query, now())
            .into_iter()
            .filter(|n| open || s.policies.get(&n.peer).is_some_and(|p| p.show_locked))
            .collect();
        let hidden = !open;
        let live = self.live.lock().map_err(|_| "Session busy")?;
        Ok(Snapshot {
            policies: s.policies.clone(),
            remote_policies: live.remote_policies.clone(),
            history,
            battery: live.battery.clone(),
            clipboard_available: !hidden && live.clipboard.is_some(),
            private_hidden: hidden,
            transfers: self
                .transfers
                .lock()
                .map_err(|_| "Transfer state busy")?
                .snapshot(),
            folders: if hidden {
                BTreeMap::new()
            } else {
                self.folders
                    .lock()
                    .map_err(|_| "Folder state busy")?
                    .clone()
            },
            links: if hidden {
                vec![]
            } else {
                self.links.lock().map_err(|_| "Link state busy")?.clone()
            },
        })
    }
    pub fn clear(&self) -> Result<(), String> {
        let mut s = self.store.lock().map_err(|_| "Storage busy")?;
        s.clear();
        self.save(&s)
    }
    fn allowed(&self, id: &str, feature: Feature) -> bool {
        self.store
            .lock()
            .ok()
            .and_then(|s| s.policies.get(id).map(|p| p.allows(feature)))
            .unwrap_or(false)
    }
    fn permitted(&self, id: &str, feature: Feature) -> bool {
        self.allowed(id, feature)
            && self
                .live
                .lock()
                .ok()
                .and_then(|l| l.remote_policies.get(id).map(|p| p.allows(feature)))
                .unwrap_or(false)
    }
    pub fn send(&self, id: &str, kind: &str, data: Value) -> Result<(), String> {
        let token = self
            .live
            .lock()
            .map_err(|_| "Session busy")?
            .sessions
            .get(id)
            .map(|s| s.remote.clone())
            .filter(|s| !s.is_empty())
            .ok_or("Secure feature session is not ready")?;
        let packet = Envelope {
            seq: self.sequence.fetch_add(1, Ordering::Relaxed),
            id: random()?,
            kind: kind.into(),
            session: token,
            data,
        };
        service::send(
            id,
            &serde_json::to_string(&packet).map_err(|_| "Could not encode message")?,
        )
    }
    pub fn action(&self, id: &str, kind: &str, data: Value) -> Result<(), String> {
        let feature = match kind {
            "notification_action" => Feature::Notifications,
            "clipboard" => Feature::Clipboard,
            "battery_request" => Feature::Battery,
            "ring" | "ring_stop" => Feature::Ring,
            "slumber_status" | "slumber_activate" | "slumber_lid" => Feature::Slumber,
            "folder_list" | "folder_read" => Feature::Folders,
            "file_accept" | "file_cancel" | "url" => Feature::Files,
            _ => return Err("Unsupported action".into()),
        };
        if kind == "notification_action" && data["action"].as_str() == Some("dismiss") {
            if !self.allowed(id, Feature::Notifications) || !unlocked() {
                return Err("Enable notifications and unlock first".into());
            }
            let source = data["source"].as_str().ok_or("Missing source")?;
            let version = data["version"].as_str().ok_or("Missing version")?;
            let mut s = self.store.lock().map_err(|_| "Storage busy")?;
            if s.current(id, source, version).is_none() {
                return Err("Source action expired".into());
            }
            if !s
                .pending_dismissals
                .iter()
                .any(|a| a.peer == id && a.source == source && a.version == version)
            {
                if s.pending_dismissals.len() >= 128 {
                    return Err("Pending action queue is full".into());
                }
                s.pending_dismissals
                    .push_back(crate::model::PendingDismissal {
                        peer: id.into(),
                        source: source.into(),
                        version: version.into(),
                        action_id: random()?,
                        created: now(),
                        last_sent: 0,
                    });
            }
            s.remove(id, source, version);
            self.save(&s)?;
            return Ok(());
        }
        if !self.permitted(id, feature) {
            return Err("Enable this feature and resume sharing first".into());
        }
        if !unlocked() {
            return Err("Unlock the desktop before using device actions".into());
        }
        if kind == "notification_action" {
            let source = data["source"].as_str().ok_or("Missing source")?;
            let version = data["version"].as_str().ok_or("Missing version")?;
            let s = self.store.lock().map_err(|_| "Storage busy")?;
            let notice = s
                .current(id, source, version)
                .ok_or("Source action expired")?;
            match data["action"].as_str() {
                Some("dismiss") => {}
                Some("read") if notice.read => {}
                Some("reply")
                    if notice.reply
                        && data["text"]
                            .as_str()
                            .is_some_and(|s| !s.is_empty() && s.len() <= 4096) => {}
                _ => return Err("Source action is unavailable".into()),
            }
        }
        if kind == "clipboard" && !data["text"].as_str().is_some_and(|s| s.len() <= 16384) {
            return Err("Clipboard exceeds its limit".into());
        }
        if kind == "file_accept" {
            let transfer = data["id"].as_str().ok_or("Missing transfer identity")?;
            let next = self
                .transfers
                .lock()
                .map_err(|_| "Transfer state busy")?
                .accept(id, transfer, now())?;
            return self.send(id, "file_next", next);
        }
        if kind == "file_cancel" {
            let transfer = data["id"].as_str().ok_or("Missing transfer identity")?;
            self.transfers
                .lock()
                .map_err(|_| "Transfer state busy")?
                .cancel(id, transfer);
            return self.send(id, "file_cancel", data);
        }
        if kind == "url" {
            let url = data["url"].as_str().ok_or("Missing URL")?;
            validate_url(url)?;
        }
        self.send(id, kind, data)
    }
    pub fn offer_file(&self, id: &str, path: &std::path::Path) -> Result<(), String> {
        if !self.permitted(id, Feature::Files) || !unlocked() {
            return Err("Enable files, resume sharing and unlock first".into());
        }
        let data = self
            .transfers
            .lock()
            .map_err(|_| "Transfer state busy")?
            .prepare(id, &random()?, path, now())?;
        let transfer = data["id"].as_str().unwrap_or_default().to_owned();
        let result = self.send(id, "file_offer", data);
        if result.is_err() {
            if let Ok(mut transfers) = self.transfers.lock() {
                transfers.cancel(id, &transfer);
            }
        }
        result
    }
    pub fn forget(&self, id: &str) {
        if let Ok(mut l) = self.live.lock() {
            l.sessions.remove(id);
            l.remote_policies.remove(id);
            l.battery.remove(id);
            l.clipboard = None;
        }
        if let Ok(mut s) = self.store.lock() {
            s.policies.remove(id);
            s.pending_dismissals.retain(|a| a.peer != id);
            self.save(&s).ok();
        }
        if let Ok(mut t) = self.transfers.lock() {
            t.cancel_peer(id);
        }
        if let Ok(mut f) = self.folders.lock() {
            f.remove(id);
        }
        self.native(crate::native::Command::ClearPeer(id.into()));
    }
    pub fn clipboard(&self) -> Result<String, String> {
        if !unlocked() {
            return Err("Unlock the desktop before accessing clipboard content".into());
        }
        self.live
            .lock()
            .map_err(|_| "Session busy")?
            .clipboard
            .take()
            .ok_or_else(|| "No received clipboard text".into())
    }
    fn receive(&self, id: &str, packet: Envelope) {
        if packet.kind == "hello" {
            let Some(remote) = packet.data["token"].as_str().filter(|s| s.len() == 48) else {
                return;
            };
            let token = {
                let Ok(mut live) = self.live.lock() else {
                    return;
                };
                let Some(session) = live.sessions.get_mut(id) else {
                    return;
                };
                if !session.remote.is_empty() && session.remote != remote {
                    let Ok(token) = random() else {
                        return;
                    };
                    *session = Session::new(token);
                }
                session.remote = remote.into();
                session.local.clone()
            };
            let response = Envelope {
                seq: self.sequence.fetch_add(1, Ordering::Relaxed),
                id: random().unwrap_or_default(),
                kind: "hello_ack".into(),
                session: remote.into(),
                data: json!({"token":token}),
            };
            service::send(id, &serde_json::to_string(&response).unwrap_or_default()).ok();
            return;
        }
        let accepted = self
            .live
            .lock()
            .ok()
            .and_then(|mut l| l.sessions.get_mut(id).map(|s| s.accept(&packet)))
            .unwrap_or(false);
        if !accepted {
            return;
        }
        if packet.kind == "hello_ack" {
            if let Some(remote) = packet.data["token"].as_str().filter(|s| s.len() == 48) {
                if let Ok(mut l) = self.live.lock() {
                    if let Some(s) = l.sessions.get_mut(id) {
                        s.remote = remote.into();
                    }
                }
                let policy = self
                    .store
                    .lock()
                    .ok()
                    .and_then(|s| s.policies.get(id).cloned())
                    .unwrap_or_default();
                self.send(
                    id,
                    "policy",
                    json!({"enabled":policy.enabled,"paused":policy.paused}),
                )
                .ok();
                self.send(id, "sync", json!({})).ok();
            }
            return;
        }
        match packet.kind.as_str() {
            "notification_action" if self.permitted(id, Feature::Notifications) => {
                let result = self.desktop_dismiss(&packet.data);
                self.send(
                    id,
                    "action_result",
                    json!({"action_id":packet.data["action_id"],"result":result}),
                )
                .ok();
            }
            "sync" if self.permitted(id, Feature::Notifications) => {
                self.desktop_sync(id);
            }
            "action_result" => {
                if let Some(action) = packet.data["action_id"].as_str() {
                    if let Ok(mut s) = self.store.lock() {
                        s.pending_dismissals
                            .retain(|a| a.peer != id || a.action_id != action);
                        self.save(&s).ok();
                    }
                }
            }
            "policy" => {
                if let Ok(policy) = serde_json::from_value::<Policy>(packet.data) {
                    if policy.paused || !policy.allows(Feature::Files) {
                        if let Ok(mut t) = self.transfers.lock() {
                            t.cancel_peer(id);
                        }
                    }
                    if policy.paused || !policy.allows(Feature::Clipboard) {
                        if let Ok(mut l) = self.live.lock() {
                            l.clipboard = None;
                        }
                    }
                    if let Ok(mut l) = self.live.lock() {
                        l.remote_policies.insert(id.into(), policy);
                    }
                    if self.permitted(id, Feature::Notifications) {
                        self.desktop_sync(id);
                    }
                }
            }
            "notice" if self.permitted(id, Feature::Notifications) => {
                let snapshot = packet.data["snapshot"].as_bool().unwrap_or(false);
                if let Ok(mut notice) = serde_json::from_value::<Notice>(packet.data) {
                    notice.peer = id.into();
                    if let Ok(mut s) = self.store.lock() {
                        let show = s.policies.get(id).is_some_and(|p| p.show_locked);
                        if s.receive(notice.clone(), now()) {
                            self.save(&s).ok();
                            if !snapshot {
                                self.native(crate::native::Command::Notice(notice, show));
                            }
                        }
                    }
                }
            }
            "notice_removed" if self.permitted(id, Feature::Notifications) => {
                if let (Some(source), Some(version)) = (
                    packet.data["source"].as_str(),
                    packet.data["version"].as_str(),
                ) {
                    if let Ok(mut s) = self.store.lock() {
                        s.remove(id, source, version);
                        self.save(&s).ok();
                    }
                    self.native(crate::native::Command::Remove(
                        id.into(),
                        source.into(),
                        version.into(),
                    ));
                }
            }
            "battery_request" if self.permitted(id, Feature::Battery) => {
                if let Some(data) = crate::system::battery() {
                    self.send(id, "battery", data).ok();
                }
            }
            "ring" if self.permitted(id, Feature::Ring) => {
                self.native(crate::native::Command::Ring(id.into(), true));
            }
            "ring_stop" => {
                self.native(crate::native::Command::Ring(id.into(), false));
            }
            "battery" if self.permitted(id, Feature::Battery) => {
                if let (Some(level), Some(charging)) = (
                    packet.data["level"].as_u64().filter(|n| *n <= 100),
                    packet.data["charging"].as_bool(),
                ) {
                    if let Ok(mut l) = self.live.lock() {
                        l.battery
                            .insert(id.into(), json!({"level":level,"charging":charging}));
                    }
                }
            }
            "clipboard" if self.permitted(id, Feature::Clipboard) && unlocked() => {
                if let Some(text) = packet.data["text"].as_str().filter(|s| s.len() <= 16384) {
                    if let Ok(mut l) = self.live.lock() {
                        l.clipboard = Some(text.into());
                        crate::system::clipboard_write(text).ok();
                    }
                }
            }
            "file_offer" if self.permitted(id, Feature::Files) => {
                if let Ok(mut t) = self.transfers.lock() {
                    t.offer(id, &packet.data, now()).ok();
                }
            }
            "file_chunk" if self.permitted(id, Feature::Files) => {
                let result = self
                    .transfers
                    .lock()
                    .ok()
                    .map(|mut t| t.chunk(id, &packet.data));
                if let Some(Ok((data, done))) = result {
                    self.send(id, if done { "file_done" } else { "file_next" }, data)
                        .ok();
                } else if let Some(transfer) = packet.data["id"].as_str() {
                    if let Ok(mut t) = self.transfers.lock() {
                        t.cancel(id, transfer);
                    }
                    self.send(id, "file_cancel", json!({"id":transfer})).ok();
                }
            }
            "file_next" if self.permitted(id, Feature::Files) => {
                let result = self
                    .transfers
                    .lock()
                    .ok()
                    .map(|mut t| t.next(id, &packet.data));
                let sent = if let Some(Ok(data)) = result {
                    self.send(id, "file_chunk", data).is_ok()
                } else {
                    false
                };
                if !sent {
                    if let Some(transfer) = packet.data["id"].as_str() {
                        if let Ok(mut transfers) = self.transfers.lock() {
                            transfers.cancel(id, transfer);
                        }
                        self.send(id, "file_cancel", json!({"id":transfer})).ok();
                    }
                }
            }
            "file_done" => {
                if let Some(transfer) = packet.data["id"].as_str() {
                    if let Ok(mut transfers) = self.transfers.lock() {
                        transfers.complete(id, transfer).ok();
                    }
                }
            }
            "file_cancel" => {
                if let Some(transfer) = packet.data["id"].as_str() {
                    if let Ok(mut t) = self.transfers.lock() {
                        t.cancel(id, transfer);
                    }
                }
            }
            "folder_entries" if self.permitted(id, Feature::Folders) => {
                if packet.data["entries"]
                    .as_array()
                    .is_some_and(|a| a.len() <= 256)
                {
                    if let Ok(mut f) = self.folders.lock() {
                        f.insert(id.into(), packet.data);
                    }
                }
            }
            "url" if self.permitted(id, Feature::Files) => {
                if let Some(url) = packet.data["url"]
                    .as_str()
                    .filter(|s| validate_url(s).is_ok())
                {
                    if let Ok(mut links) = self.links.lock() {
                        if links.len() >= 20 {
                            links.remove(0);
                        }
                        links.push((id.into(), url.into()));
                    }
                }
            }
            "slumber_status" if self.permitted(id, Feature::Slumber) => {
                self.send(
                    id,
                    "slumber",
                    serde_json::to_value(crate::slumber_status()).unwrap_or_default(),
                )
                .ok();
            }
            "slumber_lid" if self.permitted(id, Feature::Slumber) => {
                if let Some(value) = packet.data["value"]
                    .as_str()
                    .filter(|v| matches!(*v, "sleep" | "slumber"))
                {
                    if crate::slumber_status().available {
                        if let Some(home) = std::env::var_os("HOME") {
                            std::process::Command::new("/usr/bin/timeout")
                                .args(["3s", "/usr/bin/qs", "-p"])
                                .arg(PathBuf::from(home).join(".config/quickshell/ii"))
                                .args(["ipc", "call", "slumber", "setLidAction", value])
                                .stdout(std::process::Stdio::null())
                                .stderr(std::process::Stdio::null())
                                .status()
                                .ok();
                        }
                    }
                    self.send(
                        id,
                        "slumber",
                        serde_json::to_value(crate::slumber_status()).unwrap_or_default(),
                    )
                    .ok();
                }
            }
            "slumber_activate" if self.permitted(id, Feature::Slumber) => {
                if crate::slumber_status().available {
                    let config = std::env::var_os("HOME")
                        .map(PathBuf::from)
                        .unwrap_or_default()
                        .join(".config/quickshell/ii");
                    std::process::Command::new("/usr/bin/timeout")
                        .args(["3s", "/usr/bin/qs", "-p"])
                        .arg(config)
                        .args(["ipc", "call", "slumber", "activate"])
                        .stdout(std::process::Stdio::null())
                        .stderr(std::process::Stdio::null())
                        .status()
                        .ok();
                }
                self.send(
                    id,
                    "slumber",
                    serde_json::to_value(crate::slumber_status()).unwrap_or_default(),
                )
                .ok();
            }
            _ => {}
        }
    }
    pub fn run(self: Arc<Self>) {
        let mut maintenance = 0u64;
        while !self.stop.load(Ordering::Relaxed) {
            if now().saturating_sub(maintenance) > 30_000 {
                maintenance = now();
                if let Ok(mut s) = self.store.lock() {
                    let count = s.history.len();
                    s.prune(now());
                    if s.history.len() != count {
                        self.save(&s).ok();
                    }
                }
            }
            if let Ok(peers) = service::peers() {
                let reachable: Vec<_> = peers
                    .iter()
                    .filter(|p| p.paired && p.reachable)
                    .map(|p| p.id.clone())
                    .collect();
                if let Ok(mut l) = self.live.lock() {
                    l.sessions.retain(|id, _| reachable.contains(id));
                    l.remote_policies.retain(|id, _| reachable.contains(id));
                    l.battery.retain(|id, _| reachable.contains(id));
                    if reachable.is_empty() {
                        l.clipboard = None;
                    }
                }
                if let Ok(mut t) = self.transfers.lock() {
                    t.retain_connected(&reachable);
                    t.prune(now());
                }
                if let Ok(mut folders) = self.folders.lock() {
                    folders.retain(|peer, _| reachable.contains(peer));
                }
                let revoked: Vec<String> = self
                    .store
                    .lock()
                    .ok()
                    .map(|s| {
                        s.policies
                            .keys()
                            .filter(|id| !peers.iter().any(|p| p.id == **id && p.paired))
                            .cloned()
                            .collect()
                    })
                    .unwrap_or_default();
                for id in revoked {
                    self.forget(&id);
                }
                for id in reachable {
                    if now().saturating_sub(maintenance) < 1000
                        && self.permitted(&id, Feature::Battery)
                    {
                        if let Some(data) = crate::system::battery() {
                            self.send(&id, "battery", data).ok();
                        }
                    }
                    let fresh = self
                        .live
                        .lock()
                        .ok()
                        .is_some_and(|l| !l.sessions.contains_key(&id));
                    if fresh {
                        if let Ok(token) = random() {
                            if let Ok(mut l) = self.live.lock() {
                                l.sessions.insert(id.clone(), Session::new(token.clone()));
                            }
                            let packet = Envelope {
                                seq: self.sequence.fetch_add(1, Ordering::Relaxed),
                                id: random().unwrap_or_default(),
                                kind: "hello".into(),
                                session: String::new(),
                                data: json!({"token":token}),
                            };
                            service::send(&id, &serde_json::to_string(&packet).unwrap_or_default())
                                .ok();
                        }
                    }
                    let retry = self.live.lock().ok().and_then(|mut l| {
                        l.sessions.get_mut(&id).and_then(|s| {
                            if s.remote.is_empty() && now().saturating_sub(s.last_hello) > 5000 {
                                s.last_hello = now();
                                Some(s.local.clone())
                            } else {
                                None
                            }
                        })
                    });
                    if let Some(token) = retry {
                        let packet = Envelope {
                            seq: self.sequence.fetch_add(1, Ordering::Relaxed),
                            id: random().unwrap_or_default(),
                            kind: "hello".into(),
                            session: String::new(),
                            data: json!({"token":token}),
                        };
                        service::send(&id, &serde_json::to_string(&packet).unwrap_or_default())
                            .ok();
                    }
                    if self.permitted(&id, Feature::Notifications) {
                        let pending = self
                            .store
                            .lock()
                            .ok()
                            .map(|mut s| {
                                let mut result = vec![];
                                for action in &mut s.pending_dismissals {
                                    if action.peer == id
                                        && now().saturating_sub(action.last_sent) > 10_000
                                    {
                                        action.last_sent = now();
                                        result.push(action.clone());
                                    }
                                }
                                result
                            })
                            .unwrap_or_default();
                        for action in pending {
                            self.send(&id,"notification_action",json!({"source":action.source,"version":action.version,"action":"dismiss","action_id":action.action_id})).ok();
                        }
                    }
                    if let Ok(batch) = service::take(&id) {
                        for raw in batch {
                            if let Ok(packet) = serde_json::from_str(&raw) {
                                self.receive(&id, packet);
                            }
                        }
                    }
                }
            } else {
                if let Ok(mut l) = self.live.lock() {
                    l.sessions.clear();
                    l.remote_policies.clear();
                    l.clipboard = None;
                    l.battery.clear();
                    l.errors = l.errors.saturating_add(1);
                }
                if let Ok(mut transfers) = self.transfers.lock() {
                    transfers.retain_connected(&[]);
                }
                if let Ok(mut folders) = self.folders.lock() {
                    folders.clear();
                }
            }
            std::thread::sleep(Duration::from_millis(500));
        }
    }
}
// Each supported desktop needs an observed lock adapter; an absent adapter hides content.
pub(crate) fn unlocked() -> bool {
    let desktop = std::env::var("XDG_CURRENT_DESKTOP")
        .unwrap_or_default()
        .to_ascii_lowercase();
    if desktop.contains("hyprland") {
        let state = crate::slumber_status();
        if !state.available || state.secure {
            return false;
        }
        if let Ok(entries) = fs::read_dir("/proc") {
            for entry in entries.flatten() {
                let name = entry.file_name();
                if name.to_string_lossy().bytes().all(|b| b.is_ascii_digit()) {
                    if let Ok(comm) = fs::read_to_string(entry.path().join("comm")) {
                        if matches!(comm.trim(), "hyprlock" | "swaylock") {
                            return false;
                        }
                    }
                }
            }
        }
    } else if desktop.contains("gnome") || desktop.contains("kde") || desktop.contains("plasma") {
        let Ok(connection) = dbus::blocking::Connection::new_session() else {
            return false;
        };
        let (service, path, interface) = if desktop.contains("gnome") {
            (
                "org.gnome.ScreenSaver",
                "/org/gnome/ScreenSaver",
                "org.gnome.ScreenSaver",
            )
        } else {
            (
                "org.freedesktop.ScreenSaver",
                "/org/freedesktop/ScreenSaver",
                "org.freedesktop.ScreenSaver",
            )
        };
        let value: Result<(bool,), _> = connection
            .with_proxy(service, path, Duration::from_secs(1))
            .method_call(interface, "GetActive", ());
        if !matches!(value, Ok((false,))) {
            return false;
        }
    } else {
        return false;
    }
    let Some(session) = std::env::var_os("XDG_SESSION_ID") else {
        return false;
    };
    std::process::Command::new("/usr/bin/timeout")
        .args(["2s", "/usr/bin/loginctl", "show-session"])
        .arg(session)
        .args(["--property=LockedHint", "--value"])
        .output()
        .ok()
        .is_some_and(|o| o.status.success() && o.stdout == b"no\n")
}

pub fn validate_url(value: &str) -> Result<(), String> {
    if value.len() > 4096 || value.chars().any(char::is_control) {
        return Err("Invalid URL".into());
    }
    let url = url::Url::parse(value).map_err(|_| "Invalid URL")?;
    if !matches!(url.scheme(), "https" | "http") || url.host_str().is_none() {
        return Err("Only HTTP and HTTPS links are supported".into());
    }
    Ok(())
}
