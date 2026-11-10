#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde::Serialize;
use std::process::Command;
use std::sync::Arc;
use tauri::{Manager, State};
mod service;

#[derive(Serialize)]
struct Health {
    paired: bool,
    native_service: bool,
    transport: &'static str,
    notifications: &'static str,
    slumber: Slumber,
}

#[derive(Serialize)]
struct Slumber {
    available: bool,
    active: bool,
    secure: bool,
    lid_ready: bool,
    lid_action: String,
}

// A fixed read-only native probe. Neither executable nor arguments come from the UI.
// Do not return stderr, error text or arbitrary JSON from another process.
fn slumber_status() -> Slumber {
    let unavailable = || Slumber {
        available: false,
        active: false,
        secure: false,
        lid_ready: false,
        lid_action: "unknown".into(),
    };
    let Some(config) = std::env::var_os("HOME").map(std::path::PathBuf::from) else {
        return unavailable();
    };
    let config = config.join(".config/quickshell/ii");
    let output = Command::new("/usr/bin/timeout")
        .args(["3s", "/usr/bin/qs", "-p"])
        .arg(config)
        .args(["ipc", "call", "slumber", "status"])
        .output();
    let Ok(output) = output else {
        return unavailable();
    };
    if !output.status.success() || output.stdout.len() > 4096 {
        return unavailable();
    }
    let Ok(value) = serde_json::from_slice::<serde_json::Value>(&output.stdout) else {
        return unavailable();
    };
    let (Some(active), Some(secure), Some(lid_ready), Some(lid_action)) = (
        value["active"].as_bool(),
        value["secure"].as_bool(),
        value["lidReady"].as_bool(),
        value["lidAction"].as_str(),
    ) else {
        return unavailable();
    };
    if !["sleep", "slumber"].contains(&lid_action) {
        return unavailable();
    }
    Slumber {
        available: true,
        active,
        secure,
        lid_ready,
        lid_action: lid_action.into(),
    }
}

fn read_health() -> Health {
    let count = service::paired_count();
    Health {
        paired: count.is_some_and(|count| count > 0),
        native_service: count.is_some(),
        transport: if count.is_some() {
            "LAN service available"
        } else {
            "Native LAN service not running"
        },
        notifications: "Adapter verification pending",
        slumber: slumber_status(),
    }
}

#[tauri::command]
async fn health() -> Result<Health, String> {
    tauri::async_runtime::spawn_blocking(read_health)
        .await
        .map_err(|_| "Could not read connection health".into())
}

#[tauri::command]
async fn device_list() -> Result<Vec<service::Peer>, String> {
    tauri::async_runtime::spawn_blocking(service::peers)
        .await
        .map_err(|_| "Could not read devices")?
}
#[tauri::command]
async fn pairing(
    device_id: String,
    action: service::PairAction,
    expected_verification: Option<String>,
) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        service::pairing(&device_id, action, expected_verification.as_deref())
    })
    .await
    .map_err(|_| "Could not complete pairing action")?
}
#[tauri::command]
async fn start_lan(state: State<'_, Arc<service::NativeService>>) -> Result<(), String> {
    let state = Arc::clone(&state);
    tauri::async_runtime::spawn_blocking(move || state.start())
        .await
        .map_err(|_| "Could not start LAN service")?
}
#[tauri::command]
async fn stop_lan(state: State<'_, Arc<service::NativeService>>) -> Result<(), String> {
    let state = Arc::clone(&state);
    tauri::async_runtime::spawn_blocking(move || state.stop())
        .await
        .map_err(|_| "Could not stop LAN service")?
}
#[tauri::command]
fn owns_lan(state: State<'_, Arc<service::NativeService>>) -> bool {
    state.owned()
}

fn main() {
    // A CLI probe exercises exactly the native boundary used by the webview without
    // launching a second notification daemon, changing settings or locking the session.
    if std::env::args().nth(1).as_deref() == Some("--probe-health") {
        println!(
            "{}",
            serde_json::to_string(&read_health()).expect("health is serializable")
        );
        return;
    }
    tauri::Builder::default()
        .manage(Arc::new(service::NativeService::default()))
        .invoke_handler(tauri::generate_handler![
            health,
            device_list,
            pairing,
            start_lan,
            stop_lan,
            owns_lan
        ])
        .build(tauri::generate_context!())
        .expect("Pingora desktop failed to start")
        .run(|app, event| {
            if matches!(event, tauri::RunEvent::Exit) {
                let _ = app.state::<Arc<service::NativeService>>().stop();
            }
        });
}
