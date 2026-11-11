// SPDX-FileCopyrightText: 2026 AminBlg (PipeWire interface research in Foner)
// SPDX-License-Identifier: GPL-3.0-or-later
// Pingora Rust adapter for the pinned Foner-discovered interfaces; no outgoing dial API.
use dbus::{
    arg::{PropMap, Variant},
    blocking::Connection,
    Path,
};
use serde::{Deserialize, Serialize};
use std::{collections::HashMap, time::Duration};
const SERVICE: &str = "org.pipewire.Telephony";
const AG: &str = "org.pipewire.Telephony.AudioGateway1";
const TRANSPORT: &str = "org.pipewire.Telephony.AudioGatewayTransport1";
#[derive(Clone, Default, Deserialize, Serialize)]
#[serde(default)]
pub struct Settings {
    pub enabled: bool,
    pub gateway: String,
    pub microphone_before_mute: u8,
}
#[derive(Serialize)]
pub struct Gateway {
    pub id: String,
    pub address: String,
}
#[derive(Serialize)]
pub struct Call {
    pub id: String,
    pub state: String,
    pub number: String,
}
#[derive(Serialize)]
pub struct Snapshot {
    pub settings: Settings,
    pub available: bool,
    pub gateways: Vec<Gateway>,
    pub calls: Vec<Call>,
    pub transport: String,
    pub microphone: u8,
    pub speaker: u8,
    pub private_hidden: bool,
}
fn connect() -> Result<Connection, String> {
    Connection::new_session().map_err(|_| "Session bus unavailable".into())
}
fn gateways(connection: &Connection) -> Result<Vec<Gateway>, String> {
    type Objects = HashMap<Path<'static>, HashMap<String, PropMap>>;
    let (objects,): (Objects,) = connection
        .with_proxy(SERVICE, "/org/pipewire/Telephony", Duration::from_secs(2))
        .method_call(
            "org.freedesktop.DBus.ObjectManager",
            "GetManagedObjects",
            (),
        )
        .map_err(|_| "Compatible PipeWire telephony service unavailable")?;
    Ok(objects
        .into_iter()
        .filter_map(|(path, interfaces)| {
            interfaces.get(AG).map(|props| Gateway {
                id: path.to_string(),
                address: props
                    .get("Address")
                    .and_then(|v| v.0.as_str())
                    .unwrap_or("Bluetooth phone")
                    .chars()
                    .take(64)
                    .collect(),
            })
        })
        .take(32)
        .collect())
}
fn properties(connection: &Connection, path: &str, interface: &str) -> Result<PropMap, String> {
    let (props,): (PropMap,) = connection
        .with_proxy(SERVICE, path, Duration::from_secs(2))
        .method_call("org.freedesktop.DBus.Properties", "GetAll", (interface,))
        .map_err(|_| "Bluetooth phone disconnected")?;
    Ok(props)
}
fn current_calls(connection: &Connection, gateway: &str) -> Result<Vec<Call>, String> {
    let (calls,): (Vec<(Path<'static>, PropMap)>,) = connection
        .with_proxy(SERVICE, gateway, Duration::from_secs(2))
        .method_call("org.ofono.VoiceCallManager", "GetCalls", ())
        .map_err(|_| "Call state unavailable")?;
    Ok(calls
        .into_iter()
        .take(16)
        .map(|(path, props)| Call {
            id: path.to_string(),
            state: props
                .get("State")
                .and_then(|v| v.0.as_str())
                .unwrap_or("unknown")
                .chars()
                .take(24)
                .collect(),
            number: props
                .get("LineIdentification")
                .and_then(|v| v.0.as_str())
                .unwrap_or("")
                .chars()
                .take(64)
                .collect(),
        })
        .collect())
}
pub fn snapshot(settings: Settings) -> Snapshot {
    let hidden = !crate::runtime::unlocked();
    let mut result = Snapshot {
        settings,
        available: false,
        gateways: vec![],
        calls: vec![],
        transport: "unavailable".into(),
        microphone: 0,
        speaker: 0,
        private_hidden: hidden,
    };
    let Ok(connection) = connect() else {
        return result;
    };
    let Ok(available) = gateways(&connection) else {
        return result;
    };
    result.available = true;
    result.gateways = available;
    if !result.settings.enabled
        || !result
            .gateways
            .iter()
            .any(|g| g.id == result.settings.gateway)
    {
        return result;
    }
    let gateway = &result.settings.gateway;
    if let Ok(props) = properties(&connection, gateway, TRANSPORT) {
        result.transport = props
            .get("State")
            .and_then(|v| v.0.as_str())
            .unwrap_or("unknown")
            .chars()
            .take(24)
            .collect();
    }
    if let Ok(props) = properties(&connection, gateway, AG) {
        result.microphone = props
            .get("MicrophoneVolume")
            .and_then(|v| v.0.as_u64())
            .unwrap_or(0)
            .min(15) as u8;
        result.speaker = props
            .get("SpeakerVolume")
            .and_then(|v| v.0.as_u64())
            .unwrap_or(0)
            .min(15) as u8;
    }
    if let Ok(calls) = current_calls(&connection, gateway) {
        result.calls = calls;
    }
    if hidden {
        for call in &mut result.calls {
            call.number.clear();
        }
    }
    result
}
pub fn validate(settings: &Settings) -> Result<(), String> {
    if !settings.enabled {
        return Ok(());
    }
    let connection = connect()?;
    if !gateways(&connection)?
        .iter()
        .any(|g| g.id == settings.gateway)
    {
        return Err("Select a currently available Bluetooth phone".into());
    }
    Ok(())
}
pub fn action(settings: &mut Settings, kind: &str, call: &str, volume: u8) -> Result<(), String> {
    if !settings.enabled || !crate::runtime::unlocked() {
        return Err("Enable calls and unlock the desktop first".into());
    }
    let connection = connect()?;
    if !gateways(&connection)?
        .iter()
        .any(|g| g.id == settings.gateway)
    {
        return Err("Bluetooth phone disconnected; use the phone’s call screen".into());
    }
    let proxy = connection.with_proxy(SERVICE, settings.gateway.as_str(), Duration::from_secs(2));
    match kind {
        "answer" | "decline" | "hangup" => {
            let calls = current_calls(&connection, &settings.gateway)?;
            let current = calls
                .iter()
                .find(|c| c.id == call)
                .ok_or("Call no longer available")?;
            if kind == "answer" && current.state != "incoming" && current.state != "waiting" {
                return Err("Incoming call state changed".into());
            }
            let method = if kind == "answer" { "Answer" } else { "Hangup" };
            let result: Result<(), _> = connection
                .with_proxy(SERVICE, call, Duration::from_secs(2))
                .method_call("org.ofono.VoiceCall", method, ());
            result.map_err(|_| "Phone rejected the call action; use its call screen".into())
        }
        "route_here" => {
            let result: Result<(), _> = proxy.method_call(
                "org.freedesktop.DBus.Properties",
                "Set",
                (TRANSPORT, "RejectSCO", Variant(false)),
            );
            result.map_err(|_| "Audio route unavailable")?;
            let result: Result<(), _> = proxy.method_call(TRANSPORT, "Activate", ());
            result.map_err(|_| "Could not route call audio; use phone audio selection".into())
        }
        "mute" | "unmute" | "speaker" => {
            let (property, value) = if kind == "speaker" {
                ("SpeakerVolume", volume.min(15))
            } else if kind == "mute" {
                if let Ok(props) = properties(&connection, &settings.gateway, AG) {
                    let current = props
                        .get("MicrophoneVolume")
                        .and_then(|v| v.0.as_u64())
                        .unwrap_or(0)
                        .min(15) as u8;
                    if current > 0 {
                        settings.microphone_before_mute = current;
                    }
                }
                ("MicrophoneVolume", 0)
            } else {
                (
                    "MicrophoneVolume",
                    settings.microphone_before_mute.clamp(1, 15),
                )
            };
            let result: Result<(), _> = proxy.method_call(
                "org.freedesktop.DBus.Properties",
                "Set",
                (AG, property, Variant(value)),
            );
            result.map_err(|_| "Could not change call audio level".into())
        }
        _ => Err("Unsupported call action".into()),
    }
}
pub fn busy(settings: &Settings) -> bool {
    settings.enabled
        && connect()
            .ok()
            .and_then(|c| current_calls(&c, &settings.gateway).ok())
            .is_some_and(|c| !c.is_empty())
}
