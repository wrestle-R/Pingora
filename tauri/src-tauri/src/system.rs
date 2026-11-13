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

/// Read local status through bounded, fixed commands; never probe internet hosts.
fn status_command(program: &str, args: &[&str]) -> Option<String> {
    let result = Command::new("/usr/bin/timeout")
        .arg("2s")
        .arg(program)
        .args(args)
        .output()
        .ok()?;
    if !result.status.success() || result.stdout.len() > 8192 {
        return None;
    }
    String::from_utf8(result.stdout)
        .ok()
        .map(|v| v.trim().to_string())
}

pub fn valid_power_profile(value: &str) -> bool {
    matches!(value, "power-saver" | "balanced" | "performance")
}

pub fn laptop_status() -> Value {
    let profile =
        status_command("/usr/bin/powerprofilesctl", &["get"]).filter(|v| valid_power_profile(v));
    let available = status_command("/usr/bin/powerprofilesctl", &["list"]).unwrap_or_default();
    let profiles: Vec<&str> = ["power-saver", "balanced", "performance"]
        .into_iter()
        .filter(|value| {
            available
                .lines()
                .any(|line| line.trim().trim_start_matches('*').trim() == format!("{value}:"))
        })
        .collect();
    let internet = status_command("/usr/bin/nmcli", &["-t", "-f", "CONNECTIVITY", "general"])
        .filter(|v| {
            matches!(
                v.as_str(),
                "full" | "limited" | "portal" | "none" | "unknown"
            )
        });
    json!({"battery":battery(), "power_profile":profile, "power_profiles":profiles,
        "internet":internet.unwrap_or_else(|| "unknown".into())})
}

pub fn set_power_profile(value: &str) -> bool {
    valid_power_profile(value)
        && status_command("/usr/bin/powerprofilesctl", &["set", value]).is_some()
}

#[cfg(test)]
mod profile_tests {
    #[test]
    fn only_explicit_profiles_are_accepted() {
        for value in ["power-saver", "balanced", "performance"] {
            assert!(super::valid_power_profile(value));
        }
        for value in ["", "performance; reboot", "sleep", "--help", "BALANCED"] {
            assert!(!super::valid_power_profile(value));
        }
    }
}
