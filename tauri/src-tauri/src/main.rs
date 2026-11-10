#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde::Serialize;
use std::process::Command;

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

fn paired_count() -> Option<usize> {
    let output = Command::new("/usr/bin/busctl")
        .args([
            "--user",
            "--json=short",
            "--timeout=3",
            "call",
            "io.github.wrestle_r.pingora",
            "/modules/kdeconnect",
            "org.kde.kdeconnect.daemon",
            "devices",
            "bb",
            "false",
            "true",
        ])
        .output()
        .ok()?;
    if !output.status.success() || output.stdout.len() > 65536 {
        return None;
    }
    let value: serde_json::Value = serde_json::from_slice(&output.stdout).ok()?;
    if value["type"].as_str()? != "as" {
        return None;
    }
    Some(value["data"][0].as_array()?.len())
}

fn read_health() -> Health {
    let count = paired_count();
    Health {
        paired: count.is_some_and(|count| count > 0),
        native_service: count.is_some(),
        transport: if count.is_some() {
            "LAN service available · pairing checks pending"
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
        .invoke_handler(tauri::generate_handler![health])
        .run(tauri::generate_context!())
        .expect("Pingora desktop failed to start");
}
