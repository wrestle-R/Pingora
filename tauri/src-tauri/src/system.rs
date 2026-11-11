use serde_json::{json, Value};
use std::{
    fs,
    io::Write,
    process::{Command, Stdio},
};
pub fn battery() -> Option<Value> {
    for entry in fs::read_dir("/sys/class/power_supply").ok()?.flatten() {
        let path = entry.path();
        if fs::read_to_string(path.join("type")).ok()?.trim() != "Battery" {
            continue;
        }
        let level = fs::read_to_string(path.join("capacity"))
            .ok()?
            .trim()
            .parse::<u8>()
            .ok()?;
        if level > 100 {
            return None;
        }
        let charging = fs::read_to_string(path.join("status")).ok()?.trim() == "Charging";
        return Some(json!({"level":level,"charging":charging}));
    }
    None
}
pub fn clipboard_write(text: &str) -> Result<(), String> {
    if text.len() > 16384 {
        return Err("Clipboard text exceeds its limit".into());
    }
    let program = if std::env::var_os("WAYLAND_DISPLAY").is_some() {
        "/usr/bin/wl-copy"
    } else {
        "/usr/bin/xclip"
    };
    if !std::path::Path::new(program).is_file() {
        return Err("Install wl-clipboard (Wayland) or xclip (X11), or copy text manually".into());
    }
    let mut command = Command::new("/usr/bin/timeout");
    command.arg("3s").arg(program);
    if program.ends_with("xclip") {
        command.args(["-selection", "clipboard"]);
    }
    let mut child = command
        .stdin(Stdio::piped())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
        .map_err(|_| "Could not access desktop clipboard")?;
    child
        .stdin
        .take()
        .ok_or("Clipboard unavailable")?
        .write_all(text.as_bytes())
        .map_err(|_| "Could not write clipboard")?;
    if !child
        .wait()
        .map_err(|_| "Could not write clipboard")?
        .success()
    {
        return Err("Clipboard service unavailable".into());
    }
    Ok(())
}
