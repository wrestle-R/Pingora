#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde::Serialize;
use std::process::Command;
use std::sync::Arc;
use tauri::{Manager, State};
mod calls;
mod model;
mod native;
mod observe;
mod runtime;
mod service;
mod system;
mod transfers;
mod updates;

#[derive(Serialize)]
struct Health {
    bundled_ui: bool,
    paired: bool,
    native_service: bool,
    transport: &'static str,
    notifications: &'static str,
    private_hidden: bool,
    slumber: Slumber,
}

#[derive(Serialize)]
struct Slumber {
    available: bool,
    active: bool,
    secure: bool,
    lid_ready: bool,
    lid_control: bool,
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
        lid_control: false,
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
        lid_control: slumber_lid_control(),
        lid_action: lid_action.into(),
    }
}

fn slumber_lid_control() -> bool {
    let Some(home) = std::env::var_os("HOME") else {
        return false;
    };
    let output = Command::new("/usr/bin/timeout")
        .args(["2s", "/usr/bin/qs", "-p"])
        .arg(std::path::PathBuf::from(home).join(".config/quickshell/ii"))
        .args(["ipc", "show"])
        .output();
    let Ok(output) = output else {
        return false;
    };
    if !output.status.success() || output.stdout.len() > 32768 {
        return false;
    }
    let text = String::from_utf8_lossy(&output.stdout);
    text.split("target ")
        .find(|section| section.starts_with("slumber\n"))
        .is_some_and(|section| section.contains("function setLidAction(value: string)"))
}

fn read_health() -> Health {
    let count = service::paired_count();
    Health {
        bundled_ui: !tauri::is_dev(),
        paired: count.is_some_and(|count| count > 0),
        native_service: count.is_some(),
        transport: if count.is_some() {
            "LAN service available"
        } else {
            "Native LAN service not running"
        },
        notifications:
            "Enable notifications on both devices; Android notification access is required",
        private_hidden: !runtime::unlocked(),
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

#[tauri::command]
async fn feature_snapshot(
    state: State<'_, Arc<runtime::Runtime>>,
    query: String,
) -> Result<runtime::Snapshot, String> {
    let state = Arc::clone(&state);
    tauri::async_runtime::spawn_blocking(move || state.snapshot(&query))
        .await
        .map_err(|_| "Could not read inbox")?
}
#[tauri::command]
async fn feature_policy(
    state: State<'_, Arc<runtime::Runtime>>,
    device_id: String,
    policy: model::Policy,
) -> Result<(), String> {
    let state = Arc::clone(&state);
    tauri::async_runtime::spawn_blocking(move || state.policy(&device_id, policy))
        .await
        .map_err(|_| "Could not save feature settings")?
}
#[tauri::command]
async fn feature_action(
    state: State<'_, Arc<runtime::Runtime>>,
    device_id: String,
    kind: String,
    data: serde_json::Value,
) -> Result<(), String> {
    let state = Arc::clone(&state);
    tauri::async_runtime::spawn_blocking(move || state.action(&device_id, &kind, data))
        .await
        .map_err(|_| "Could not send action")?
}
#[tauri::command]
fn clear_history(state: State<'_, Arc<runtime::Runtime>>) -> Result<(), String> {
    state.clear()
}
#[tauri::command]
async fn received_clipboard(state: State<'_, Arc<runtime::Runtime>>) -> Result<String, String> {
    let state = Arc::clone(&state);
    tauri::async_runtime::spawn_blocking(move || state.clipboard())
        .await
        .map_err(|_| "Could not read clipboard")?
}

#[tauri::command]
async fn offer_file(
    state: State<'_, Arc<runtime::Runtime>>,
    device_id: String,
) -> Result<(), String> {
    let state = Arc::clone(&state);
    tauri::async_runtime::spawn_blocking(move || {
        let path = rfd::FileDialog::new()
            .set_title("Choose a file to send with Pingora")
            .pick_file()
            .ok_or("File selection cancelled")?;
        state.offer_file(&device_id, &path)
    })
    .await
    .map_err(|_| "Could not select file")?
}
#[tauri::command]
async fn open_received_link(url: String) -> Result<(), String> {
    runtime::validate_url(&url)?;
    tauri::async_runtime::spawn_blocking(move || {
        std::process::Command::new("/usr/bin/xdg-open")
            .arg(url)
            .stdin(std::process::Stdio::null())
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .spawn()
            .map(|_| ())
            .map_err(|_| "Could not open link".into())
    })
    .await
    .map_err(|_| "Could not open link")?
}

#[tauri::command]
async fn diagnostics(state: State<'_, Arc<runtime::Runtime>>) -> Result<serde_json::Value, String> {
    let state = Arc::clone(&state);
    tauri::async_runtime::spawn_blocking(move || state.diagnostics())
        .await
        .map_err(|_| "Could not prepare diagnostics")?
}
#[tauri::command]
async fn export_diagnostics(state: State<'_, Arc<runtime::Runtime>>) -> Result<(), String> {
    let state = Arc::clone(&state);
    tauri::async_runtime::spawn_blocking(move || {
        let data = state.diagnostics()?;
        let path = rfd::FileDialog::new()
            .set_file_name("pingora-diagnostics.json")
            .save_file()
            .ok_or("Export cancelled")?;
        std::fs::write(
            path,
            serde_json::to_vec_pretty(&data).map_err(|_| "Could not encode diagnostics")?,
        )
        .map_err(|_| "Could not save diagnostics".into())
    })
    .await
    .map_err(|_| "Could not export diagnostics")?
}
#[tauri::command]
async fn desktop_apps() -> Result<Vec<(String, String)>, String> {
    tauri::async_runtime::spawn_blocking(|| {
        let mut apps = std::collections::BTreeMap::new();
        let mut paths = vec![std::path::PathBuf::from("/usr/share/applications")];
        if let Some(home) = std::env::var_os("HOME") {
            paths.push(std::path::PathBuf::from(home).join(".local/share/applications"));
        }
        for path in paths {
            if let Ok(entries) = std::fs::read_dir(path) {
                for entry in entries.flatten().take(1024) {
                    let path = entry.path();
                    if path.extension().is_none_or(|e| e != "desktop") {
                        continue;
                    }
                    let Some(id) = path
                        .file_stem()
                        .and_then(|s| s.to_str())
                        .filter(|s| s.len() <= 256)
                    else {
                        continue;
                    };
                    if let Ok(text) = std::fs::read_to_string(&path) {
                        if text.len() > 65536
                            || text
                                .lines()
                                .any(|l| l == "NoDisplay=true" || l == "Hidden=true")
                        {
                            continue;
                        }
                        let name = text
                            .lines()
                            .find_map(|l| l.strip_prefix("Name="))
                            .unwrap_or(id);
                        apps.insert(id.to_owned(), name.chars().take(100).collect::<String>());
                    }
                }
            }
        }
        Ok(apps.into_iter().take(512).collect())
    })
    .await
    .map_err(|_| "Could not list desktop apps")?
}

#[tauri::command]
async fn calls_snapshot(
    state: State<'_, Arc<runtime::Runtime>>,
) -> Result<calls::Snapshot, String> {
    let state = Arc::clone(&state);
    tauri::async_runtime::spawn_blocking(move || state.calls_snapshot())
        .await
        .map_err(|_| "Could not read calls")?
}
#[tauri::command]
async fn calls_settings(
    state: State<'_, Arc<runtime::Runtime>>,
    settings: calls::Settings,
) -> Result<(), String> {
    let state = Arc::clone(&state);
    tauri::async_runtime::spawn_blocking(move || state.calls_settings(settings))
        .await
        .map_err(|_| "Could not save calls settings")?
}
#[tauri::command]
async fn call_action(
    state: State<'_, Arc<runtime::Runtime>>,
    kind: String,
    call: String,
    volume: u8,
) -> Result<(), String> {
    let state = Arc::clone(&state);
    tauri::async_runtime::spawn_blocking(move || state.call_action(&kind, &call, volume))
        .await
        .map_err(|_| "Could not complete call action")?
}

#[tauri::command]
async fn check_updates(state: State<'_, Arc<runtime::Runtime>>) -> Result<updates::View, String> {
    let state = Arc::clone(&state);
    tauri::async_runtime::spawn_blocking(move || state.update_check())
        .await
        .map_err(|_| "Could not check updates")?
}
#[tauri::command]
async fn download_update(state: State<'_, Arc<runtime::Runtime>>) -> Result<updates::View, String> {
    let state = Arc::clone(&state);
    tauri::async_runtime::spawn_blocking(move || state.update_download())
        .await
        .map_err(|_| "Could not download update")?
}
#[tauri::command]
async fn install_update(
    state: State<'_, Arc<runtime::Runtime>>,
    approved: bool,
) -> Result<(), String> {
    let state = Arc::clone(&state);
    tauri::async_runtime::spawn_blocking(move || state.update_install(approved))
        .await
        .map_err(|_| "Could not install update")?
}

#[tauri::command]
fn quit_app(app: tauri::AppHandle) {
    app.exit(0);
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
        .plugin(tauri_plugin_single_instance::init(|app, args, _| {
            if !args.iter().any(|arg| arg == "--background") {
                if let Some(window) = app.get_webview_window("main") {
                    window.show().ok();
                    window.set_focus().ok();
                }
            }
        }))
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                window.hide().ok();
            }
        })
        .manage(Arc::new(service::NativeService::default()))
        .setup(|app| {
            system::start_metrics();
            if std::env::args().any(|arg| arg == "--background") {
                if let Some(window) = app.get_webview_window("main") {
                    window.hide().ok();
                }
            }
            let lan = app.state::<Arc<service::NativeService>>().inner().clone();
            std::thread::spawn(move || {
                let _ = lan.start();
            });
            // Minimal Linux profiles may not define XDG_DOWNLOAD_DIR yet.
            let downloads = app
                .path()
                .download_dir()
                .or_else(|_| app.path().home_dir().map(|home| home.join("Downloads")))?;
            let runtime =
                runtime::Runtime::open(app.path().app_data_dir()?, downloads.join("Pingora"))
                    .map_err(std::io::Error::other)?;
            runtime.set_native(native::start(Arc::clone(&runtime), app.handle().clone()));
            observe::start(Arc::clone(&runtime));
            app.manage(Arc::clone(&runtime));
            std::thread::spawn(move || runtime.run());
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            health,
            quit_app,
            device_list,
            pairing,
            start_lan,
            stop_lan,
            owns_lan,
            feature_snapshot,
            feature_policy,
            feature_action,
            clear_history,
            received_clipboard,
            offer_file,
            open_received_link,
            diagnostics,
            export_diagnostics,
            desktop_apps,
            calls_snapshot,
            calls_settings,
            call_action,
            check_updates,
            download_update,
            install_update
        ])
        .build(tauri::generate_context!())
        .expect("Pingora desktop failed to start")
        .run(|app, event| {
            if matches!(event, tauri::RunEvent::Exit) {
                app.state::<Arc<runtime::Runtime>>()
                    .stop
                    .store(true, std::sync::atomic::Ordering::Relaxed);
                let _ = app.state::<Arc<service::NativeService>>().stop();
            }
        });
}
