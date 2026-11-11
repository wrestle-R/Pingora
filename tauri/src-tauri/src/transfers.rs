use base64::{engine::general_purpose::STANDARD, Engine};
use serde::Serialize;
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{
    collections::BTreeMap,
    fs::{self, File, OpenOptions},
    io::{Read, Write},
    os::unix::fs::OpenOptionsExt,
    path::{Path, PathBuf},
};
const MAX: u64 = 100 * 1024 * 1024;
const CHUNK: usize = 12 * 1024;
#[derive(Clone, Serialize)]
pub struct Offer {
    pub peer: String,
    pub id: String,
    pub name: String,
    pub size: u64,
    pub received: u64,
    pub state: String,
    pub created: u64,
    pub direction: &'static str,
}
struct Incoming {
    offer: Offer,
    hash: String,
    file: Option<File>,
    path: Option<PathBuf>,
    digest: Sha256,
}
struct Outgoing {
    file: File,
    name: String,
    started: bool,
    offset: u64,
    size: u64,
    hash: String,
    created: u64,
}
pub struct Transfers {
    directory: PathBuf,
    incoming: BTreeMap<(String, String), Incoming>,
    outgoing: BTreeMap<(String, String), Outgoing>,
    completed: Vec<Offer>,
}
fn valid_id(id: &str) -> bool {
    !id.is_empty() && id.len() <= 64 && id.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'-')
}
fn safe_name(name: &str) -> bool {
    !name.is_empty()
        && name.len() <= 240
        && name != "."
        && name != ".."
        && !name.starts_with('.')
        && !name
            .chars()
            .any(|c| c.is_control() || matches!(c, '/' | '\\' | ':'))
}
impl Transfers {
    pub fn new(directory: PathBuf) -> Self {
        Self {
            directory,
            incoming: BTreeMap::new(),
            outgoing: BTreeMap::new(),
            completed: vec![],
        }
    }
    pub fn active(&self) -> bool {
        !self.outgoing.is_empty() || self.incoming.values().any(|i| i.offer.state == "receiving")
    }
    pub fn snapshot(&self) -> Vec<Offer> {
        self.incoming
            .values()
            .map(|x| x.offer.clone())
            .chain(self.outgoing.iter().map(|((peer, id), item)| {
                Offer {
                    peer: peer.clone(),
                    id: id.clone(),
                    name: item.name.clone(),
                    size: item.size,
                    received: item.offset,
                    state: if item.started {
                        "sending"
                    } else {
                        "waiting_for_approval"
                    }
                    .into(),
                    created: item.created,
                    direction: "send",
                }
            }))
            .chain(self.completed.iter().cloned())
            .collect()
    }
    pub fn offer(&mut self, peer: &str, data: &Value, now: u64) -> Result<(), String> {
        let id = data["id"]
            .as_str()
            .filter(|s| valid_id(s))
            .ok_or("Invalid transfer identity")?;
        let name = data["name"]
            .as_str()
            .filter(|s| safe_name(s))
            .ok_or("Unsafe transfer filename")?;
        let size = data["size"]
            .as_u64()
            .filter(|n| *n <= MAX)
            .ok_or("Transfer exceeds 100 MiB limit")?;
        let hash = data["sha256"]
            .as_str()
            .filter(|s| s.len() == 64 && s.bytes().all(|c| c.is_ascii_hexdigit()))
            .ok_or("Invalid transfer checksum")?;
        let key = (peer.into(), id.into());
        if self.incoming.contains_key(&key)
            || self.completed.iter().any(|o| o.peer == peer && o.id == id)
        {
            return Err("Transfer identity already used".into());
        }
        if self.incoming.len() >= 8 {
            return Err("Too many pending transfers".into());
        }
        self.incoming.insert(
            key,
            Incoming {
                offer: Offer {
                    peer: peer.into(),
                    id: id.into(),
                    name: name.into(),
                    size,
                    received: 0,
                    state: "approval_required".into(),
                    created: now,
                    direction: "receive",
                },
                hash: hash.to_ascii_lowercase(),
                file: None,
                path: None,
                digest: Sha256::new(),
            },
        );
        Ok(())
    }
    pub fn accept(&mut self, peer: &str, id: &str, now: u64) -> Result<Value, String> {
        let item = self
            .incoming
            .get_mut(&(peer.into(), id.into()))
            .ok_or("Transfer offer expired")?;
        if item.offer.state != "approval_required"
            || now.saturating_sub(item.offer.created) > 120_000
        {
            return Err("Transfer approval expired".into());
        }
        fs::create_dir_all(&self.directory).map_err(|_| "Could not create receiving folder")?;
        let path = self
            .directory
            .join(format!(".pingora-{}-{}.part", std::process::id(), id));
        let file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .mode(0o600)
            .open(&path)
            .map_err(|_| "Could not reserve transfer storage")?;
        item.file = Some(file);
        item.path = Some(path);
        item.offer.state = "receiving".into();
        Ok(json!({"id":id,"offset":0}))
    }
    pub fn chunk(&mut self, peer: &str, data: &Value) -> Result<(Value, bool), String> {
        let id = data["id"].as_str().ok_or("Invalid chunk")?;
        let item = self
            .incoming
            .get_mut(&(peer.into(), id.into()))
            .ok_or("Unapproved transfer")?;
        if item.offer.state != "receiving" {
            return Err("Transfer requires approval".into());
        }
        if data["offset"].as_u64() != Some(item.offer.received) {
            return Err("Unexpected transfer offset".into());
        }
        let encoded = data["bytes"]
            .as_str()
            .filter(|s| s.len() <= CHUNK * 4 / 3 + 4)
            .ok_or("Chunk exceeded its limit")?;
        let bytes = STANDARD
            .decode(encoded)
            .map_err(|_| "Invalid chunk encoding")?;
        if bytes.len() > CHUNK
            || item.offer.received + bytes.len() as u64 > item.offer.size
            || (bytes.is_empty() && item.offer.received != item.offer.size)
        {
            return Err("Invalid transfer length".into());
        }
        item.file
            .as_mut()
            .ok_or("Transfer storage unavailable")?
            .write_all(&bytes)
            .map_err(|_| "Could not save transfer (check free space)")?;
        item.digest.update(&bytes);
        item.offer.received += bytes.len() as u64;
        if item.offer.received == item.offer.size {
            if format!("{:x}", item.digest.clone().finalize()) != item.hash {
                return Err("Transfer checksum mismatch".into());
            }
            item.file
                .as_mut()
                .ok_or("Transfer storage unavailable")?
                .sync_all()
                .map_err(|_| "Could not finish saving transfer")?;
            item.file.take();
            let source = item.path.as_ref().ok_or("Transfer storage unavailable")?;
            let mut committed = false;
            for suffix in 0..1000 {
                let name = if suffix == 0 {
                    item.offer.name.clone()
                } else {
                    format!("{suffix}-{}", item.offer.name)
                };
                match fs::hard_link(source, self.directory.join(name)) {
                    Ok(()) => {
                        committed = true;
                        break;
                    }
                    Err(e) if e.kind() == std::io::ErrorKind::AlreadyExists => continue,
                    Err(_) => return Err("Could not commit received file".into()),
                }
            }
            if !committed {
                return Err("Too many filename collisions".into());
            }
            fs::remove_file(source).ok();
            item.path = None;
            item.offer.state = "saved".into();
            let completed = item.offer.clone();
            self.completed.push(completed);
            if self.completed.len() > 32 {
                self.completed.remove(0);
            }
            self.incoming.remove(&(peer.into(), id.into()));
            return Ok((json!({"id":id}), true));
        }
        Ok((json!({"id":id,"offset":item.offer.received}), false))
    }
    pub fn prepare(
        &mut self,
        peer: &str,
        id: &str,
        path: &Path,
        now: u64,
    ) -> Result<Value, String> {
        if self.outgoing.len() >= 4 || !valid_id(id) {
            return Err("Too many outgoing transfers".into());
        }
        let name = path
            .file_name()
            .and_then(|s| s.to_str())
            .filter(|s| safe_name(s))
            .ok_or("Unsupported filename")?;
        let mut file = File::open(path).map_err(|_| "Could not open selected file")?;
        let metadata = file
            .metadata()
            .map_err(|_| "Could not inspect selected file")?;
        if !metadata.is_file() || metadata.len() > MAX {
            return Err("Choose a regular file up to 100 MiB".into());
        }
        let mut digest = Sha256::new();
        let mut buffer = [0; CHUNK];
        let mut total = 0;
        loop {
            let n = file
                .read(&mut buffer)
                .map_err(|_| "Could not read selected file")?;
            if n == 0 {
                break;
            }
            total += n as u64;
            if total > MAX {
                return Err("File grew beyond transfer limit".into());
            }
            digest.update(&buffer[..n]);
        }
        if total != metadata.len() {
            return Err("Selected file changed; select it again".into());
        }
        let hash = format!("{:x}", digest.finalize());
        use std::io::{Seek, SeekFrom};
        file.seek(SeekFrom::Start(0))
            .map_err(|_| "Could not reopen selected file")?;
        self.outgoing.insert(
            (peer.into(), id.into()),
            Outgoing {
                file,
                name: name.into(),
                started: false,
                offset: 0,
                size: total,
                hash: hash.clone(),
                created: now,
            },
        );
        Ok(json!({"id":id,"name":name,"size":total,"sha256":hash}))
    }
    pub fn next(&mut self, peer: &str, data: &Value) -> Result<Value, String> {
        let id = data["id"].as_str().ok_or("Invalid transfer")?;
        let item = self
            .outgoing
            .get_mut(&(peer.into(), id.into()))
            .ok_or("Outgoing transfer expired")?;
        if data["offset"].as_u64() != Some(item.offset) {
            return Err("Unexpected requested offset".into());
        }
        let len = (item.size - item.offset).min(CHUNK as u64) as usize;
        let mut bytes = vec![0; len];
        item.file
            .read_exact(&mut bytes)
            .map_err(|_| "Selected file changed or became unavailable")?;
        let offset = item.offset;
        item.started = true;
        item.offset += len as u64;
        Ok(json!({"id":id,"offset":offset,"bytes":STANDARD.encode(bytes),"sha256":item.hash}))
    }
    pub fn complete(&mut self, peer: &str, id: &str) -> Result<(), String> {
        let key = (peer.into(), id.into());
        let item = self.outgoing.get(&key).ok_or("Outgoing transfer expired")?;
        if !item.started || item.offset != item.size {
            return Err("Transfer completion arrived before all bytes were sent".into());
        }
        let item = self
            .outgoing
            .remove(&key)
            .ok_or("Outgoing transfer expired")?;
        self.completed.push(Offer {
            peer: peer.into(),
            id: id.into(),
            name: item.name,
            size: item.size,
            received: item.offset,
            state: "sent".into(),
            created: item.created,
            direction: "send",
        });
        if self.completed.len() > 32 {
            self.completed.remove(0);
        }
        Ok(())
    }
    pub fn cancel(&mut self, peer: &str, id: &str) {
        if let Some(mut item) = self.incoming.remove(&(peer.into(), id.into())) {
            item.file.take();
            if let Some(path) = item.path {
                fs::remove_file(path).ok();
            }
        }
        self.outgoing.remove(&(peer.into(), id.into()));
    }
    pub fn cancel_peer(&mut self, peer: &str) {
        let ids: Vec<String> = self
            .incoming
            .keys()
            .chain(self.outgoing.keys())
            .filter(|(p, _)| p == peer)
            .map(|(_, id)| id.clone())
            .collect();
        for id in ids {
            self.cancel(peer, &id);
        }
    }
    pub fn retain_connected(&mut self, peers: &[String]) {
        let disconnected: Vec<_> = self
            .incoming
            .keys()
            .chain(self.outgoing.keys())
            .filter(|(peer, _)| !peers.contains(peer))
            .map(|(peer, _)| peer.clone())
            .collect();
        for peer in disconnected {
            self.cancel_peer(&peer);
        }
    }
    pub fn prune(&mut self, now: u64) {
        let ids: Vec<_> = self
            .incoming
            .iter()
            .filter(|(_, v)| {
                now.saturating_sub(v.offer.created)
                    > if v.offer.state == "approval_required" {
                        120_000
                    } else {
                        600_000
                    }
            })
            .map(|(k, _)| k.clone())
            .chain(
                self.outgoing
                    .iter()
                    .filter(|(_, v)| now.saturating_sub(v.created) > 600_000)
                    .map(|(k, _)| k.clone()),
            )
            .collect();
        for (peer, id) in ids {
            self.cancel(&peer, &id);
        }
    }
}
impl Drop for Transfers {
    fn drop(&mut self) {
        let peers: Vec<_> = self.incoming.keys().map(|(p, _)| p.clone()).collect();
        for peer in peers {
            self.cancel_peer(&peer);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    static TEST_DIRECTORY: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
    fn dir() -> PathBuf {
        std::env::temp_dir().join(format!(
            "pingora-transfer-{}-{}",
            std::process::id(),
            TEST_DIRECTORY.fetch_add(1, std::sync::atomic::Ordering::Relaxed)
        ))
    }
    #[test]
    fn outgoing_progress_cancel_and_completion_require_all_bytes() {
        let directory = dir();
        fs::create_dir_all(&directory).unwrap();
        let source = directory.join("selected.txt");
        fs::write(&source, vec![7u8; CHUNK + 3]).unwrap();
        let mut transfers = Transfers::new(directory.clone());
        transfers.prepare("phone", "send-one", &source, 1).unwrap();
        assert_eq!(transfers.snapshot()[0].state, "waiting_for_approval");
        assert_eq!(transfers.snapshot()[0].direction, "send");
        assert!(transfers.complete("phone", "send-one").is_err());
        transfers
            .next("phone", &json!({"id":"send-one","offset":0}))
            .unwrap();
        assert_eq!(transfers.snapshot()[0].received, CHUNK as u64);
        assert_eq!(transfers.snapshot()[0].state, "sending");
        assert!(transfers.complete("phone", "send-one").is_err());
        transfers
            .next("phone", &json!({"id":"send-one","offset":CHUNK}))
            .unwrap();
        transfers.complete("phone", "send-one").unwrap();
        assert_eq!(transfers.snapshot()[0].state, "sent");
        assert!(!transfers.active());
        transfers.prepare("phone", "send-two", &source, 2).unwrap();
        transfers.cancel("phone", "send-two");
        assert!(!transfers.active());
        assert_eq!(transfers.snapshot().len(), 1);
        assert_eq!(fs::read(source).unwrap(), vec![7u8; CHUNK + 3]);
        fs::remove_dir_all(directory).unwrap();
    }
    #[test]
    fn empty_file_still_needs_peer_approval_and_expired_offers_disappear() {
        let directory = dir();
        fs::create_dir_all(&directory).unwrap();
        let source = directory.join("empty.txt");
        fs::write(&source, []).unwrap();
        let mut transfers = Transfers::new(directory.clone());
        transfers.prepare("phone", "empty", &source, 1).unwrap();
        assert!(transfers.complete("phone", "empty").is_err());
        transfers
            .next("phone", &json!({"id":"empty","offset":0}))
            .unwrap();
        transfers.complete("phone", "empty").unwrap();
        transfers.offer("phone", &json!({"id":"pending","name":"incoming.txt","size":0,"sha256":format!("{:x}",Sha256::digest([]))}), 1).unwrap();
        transfers.prune(120_002);
        assert!(transfers.accept("phone", "pending", 120_002).is_err());
        assert!(transfers
            .snapshot()
            .iter()
            .all(|offer| offer.id != "pending"));
        fs::remove_dir_all(directory).unwrap();
    }
    #[test]
    fn disconnect_closes_transfers_and_removes_only_partial_files() {
        let directory = dir();
        fs::create_dir_all(&directory).unwrap();
        let selected = directory.join("selected.txt");
        fs::write(&selected, b"keep").unwrap();
        let mut transfers = Transfers::new(directory.clone());
        transfers
            .prepare("phone", "outgoing", &selected, 1)
            .unwrap();
        transfers.offer("phone",&json!({"id":"incoming","name":"received.txt","size":3,"sha256":format!("{:x}",Sha256::digest(b"yes"))}),1).unwrap();
        transfers.accept("phone", "incoming", 2).unwrap();
        assert!(transfers.active());
        assert_eq!(fs::read_dir(&directory).unwrap().count(), 2);
        transfers.retain_connected(&[]);
        assert!(!transfers.active());
        assert!(transfers.snapshot().is_empty());
        assert_eq!(fs::read_dir(&directory).unwrap().count(), 1);
        assert_eq!(fs::read(selected).unwrap(), b"keep");
        fs::remove_dir_all(directory).unwrap();
    }
    #[test]
    fn approval_traversal_integrity_and_collision_boundary() {
        let directory = dir();
        fs::create_dir_all(&directory).unwrap();
        fs::write(directory.join("photo.txt"), b"original").unwrap();
        let mut t = Transfers::new(directory.clone());
        let hash = format!("{:x}", Sha256::digest(b"new"));
        let mut offer = json!({"id":"one","name":"../photo.txt","size":3,"sha256":hash});
        assert!(t.offer("p", &offer, 1).is_err());
        offer["name"] = json!("photo.txt");
        t.offer("p", &offer, 1).unwrap();
        let chunk = json!({"id":"one","offset":0,"bytes":STANDARD.encode(b"new")});
        assert!(t.chunk("p", &chunk).is_err());
        t.accept("p", "one", 2).unwrap();
        assert!(t.chunk("p", &chunk).unwrap().1);
        assert_eq!(fs::read(directory.join("photo.txt")).unwrap(), b"original");
        assert_eq!(fs::read(directory.join("1-photo.txt")).unwrap(), b"new");
        drop(t);
        fs::remove_dir_all(directory).unwrap();
    }
    #[test]
    fn changed_transfer_identity_and_corruption_never_commit() {
        let directory = dir();
        let mut t = Transfers::new(directory.clone());
        let offer = json!({"id":"one","name":"file","size":3,"sha256":format!("{:x}",Sha256::digest(b"yes"))});
        t.offer("p", &offer, 1).unwrap();
        assert!(t.offer("p", &offer, 2).is_err());
        t.accept("p", "one", 2).unwrap();
        assert!(t
            .chunk("other", &json!({"id":"one","offset":0,"bytes":"bm8h"}))
            .is_err());
        assert!(t
            .chunk("p", &json!({"id":"one","offset":0,"bytes":"bm8h"}))
            .is_err());
        t.cancel_peer("p");
        assert!(!directory.join("file").exists());
        assert_eq!(fs::read_dir(&directory).unwrap().count(), 0);
        fs::remove_dir_all(directory).unwrap();
    }
}
