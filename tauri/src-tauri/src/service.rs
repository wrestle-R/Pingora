use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::process::{Child, Command, Stdio};
use std::sync::Mutex;

const SERVICE: &str = "io.github.wrestle_r.pingora";
const ROOT: &str = "/modules/kdeconnect";
const DEVICE: &str = "org.kde.kdeconnect.device";
const DAEMON: &str = "org.kde.kdeconnect.daemon";

#[derive(Default)]
pub struct NativeService(Mutex<Option<Child>>);

impl NativeService {
    pub fn start(&self) -> Result<(), String> {
        let mut child = self
            .0
            .lock()
            .map_err(|_| "Could not access service state")?;
        if paired_count().is_some() {
            return Ok(()); // Never replace a running service or adopt someone else's process.
        }
        if child
            .as_mut()
            .is_some_and(|process| matches!(process.try_wait(), Ok(None)))
        {
            return Ok(());
        }
        if let Some(mut old) = child.take() {
            let _ = old.kill();
            let _ = old.wait();
        }
        let binary = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../target/native/build/bin/kdeconnectd");
        if !binary.is_file() {
            return Err("Build the native LAN service first (see tauri/native/README.md).".into());
        }
        *child = Some(
            Command::new(binary)
                .stdin(Stdio::null())
                .stdout(Stdio::null())
                .stderr(Stdio::null())
                .spawn()
                .map_err(|_| "Could not start the local LAN service")?,
        );
        Ok(())
    }
    pub fn stop(&self) -> Result<(), String> {
        let mut child = self
            .0
            .lock()
            .map_err(|_| "Could not access service state")?;
        if let Some(mut process) = child.take() {
            process
                .kill()
                .map_err(|_| "Could not stop the owned LAN service")?;
            process
                .wait()
                .map_err(|_| "Could not wait for the owned LAN service")?;
        }
        Ok(())
    }
    pub fn owned(&self) -> bool {
        self.0.lock().ok().is_some_and(|mut child| {
            child
                .as_mut()
                .is_some_and(|process| matches!(process.try_wait(), Ok(None)))
        })
    }
}
impl Drop for NativeService {
    fn drop(&mut self) {
        let _ = self.stop();
    }
}

// Local bus only; no remote URL, shell, executable, interface or method accepted from the frontend.
fn bus(arguments: &[&str]) -> Result<Value, String> {
    let output = Command::new("/usr/bin/busctl")
        .args(["--user", "--json=short", "--timeout=2"])
        .args(arguments)
        .output()
        .map_err(|_| "Could not contact the local LAN service")?;
    if !output.status.success() {
        return Err(
            "The LAN service is unavailable or the device state changed. Refresh and try again."
                .into(),
        );
    }
    if output.stdout.len() > 65536 {
        return Err("The LAN service response exceeded its limit".into());
    }
    if output.stdout.is_empty() {
        return Ok(Value::Null);
    }
    serde_json::from_slice(&output.stdout)
        .map_err(|_| "The LAN service returned an invalid response".into())
}

fn ids(paired: bool) -> Result<Vec<String>, String> {
    let value = bus(&[
        "call",
        SERVICE,
        ROOT,
        DAEMON,
        "devices",
        "bb",
        "false",
        if paired { "true" } else { "false" },
    ])?;
    let array = value["data"][0].as_array().ok_or("Invalid device list")?;
    if array.len() > 32 {
        return Err("Too many devices discovered. Narrow the network before pairing.".into());
    }
    array
        .iter()
        .map(|value| {
            let id = value.as_str().ok_or("Invalid device identity")?;
            if id.is_empty()
                || id.len() > 128
                || !id
                    .bytes()
                    .all(|c| c.is_ascii_alphanumeric() || matches!(c, b'_' | b'-'))
            {
                return Err("Invalid device identity".into());
            }
            Ok(id.to_owned())
        })
        .collect()
}
pub fn paired_count() -> Option<usize> {
    ids(true).ok().map(|ids| ids.len())
}

#[derive(Serialize)]
pub struct Peer {
    pub id: String,
    pub name: String,
    pub reachable: bool,
    pub paired: bool,
    pub requested: bool,
    pub requested_by_peer: bool,
    pub verification: String,
}
fn peer(id: String) -> Result<Peer, String> {
    let path = format!("{ROOT}/devices/{id}");
    let value = bus(&[
        "call",
        SERVICE,
        &path,
        "org.freedesktop.DBus.Properties",
        "GetAll",
        "s",
        DEVICE,
    ])?;
    let props = value["data"][0]
        .as_object()
        .ok_or("Invalid device status")?;
    let flag = |key: &str| {
        props
            .get(key)
            .and_then(|v| v["data"].as_bool())
            .ok_or("Incomplete device status")
    };
    let requested = flag("isPairRequested")?;
    let requested_by_peer = flag("isPairRequestedByPeer")?;
    let name = props
        .get("name")
        .and_then(|v| v["data"].as_str())
        .ok_or("Missing device name")?
        .chars()
        .take(100)
        .collect();
    let verification = if requested || requested_by_peer {
        props
            .get("verificationKey")
            .and_then(|v| v["data"].as_str())
            .unwrap_or("")
            .chars()
            .take(200)
            .collect()
    } else {
        String::new()
    };
    Ok(Peer {
        id,
        name,
        reachable: flag("isReachable")?,
        paired: flag("isPaired")?,
        requested,
        requested_by_peer,
        verification,
    })
}
pub fn peers() -> Result<Vec<Peer>, String> {
    ids(false)?.into_iter().map(peer).collect()
}

#[derive(Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum PairAction {
    Request,
    Accept,
    Cancel,
    Unpair,
}
pub fn pairing(
    id: &str,
    action: PairAction,
    expected_verification: Option<&str>,
) -> Result<(), String> {
    // Resolve against a fresh service-owned list before constructing any object path.
    let known = ids(false)?
        .into_iter()
        .find(|known| known == id)
        .ok_or("Device is no longer available")?;
    let state = peer(known)?;
    let method = match action {
        PairAction::Request
            if state.reachable && !state.paired && !state.requested && !state.requested_by_peer =>
        {
            "requestPairing"
        }
        PairAction::Accept if state.reachable && state.requested_by_peer => {
            if state.verification.is_empty()
                || expected_verification != Some(state.verification.as_str())
            {
                return Err(
                    "Compare the current verification value again before accepting.".into(),
                );
            }
            "acceptPairing"
        }
        PairAction::Cancel if state.requested || state.requested_by_peer => "cancelPairing",
        PairAction::Unpair if state.paired => "unpair",
        _ => return Err("The pairing state changed. Refresh before continuing.".into()),
    };
    bus(&[
        "call",
        SERVICE,
        &format!("{ROOT}/devices/{id}"),
        DEVICE,
        method,
    ])?;
    Ok(())
}
