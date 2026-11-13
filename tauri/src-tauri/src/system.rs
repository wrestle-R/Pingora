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
    start_metrics();
    let cpu = SYSTEM_CPU.lock().ok().and_then(|value| *value);
    let memory = fs::read_to_string("/proc/meminfo")
        .ok()
        .and_then(|text| memory_usage(&text));
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
        "internet":internet.unwrap_or_else(|| "unknown".into()),"cpu_percent":cpu,
        "ram_used_bytes":memory.map(|(used,_)|used),"ram_total_bytes":memory.map(|(_,total)|total)})
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

#[derive(Clone, Copy)]
struct CpuCounters {
    total: u64,
    idle: u64,
}
fn cpu_counters(text: &str) -> Option<CpuCounters> {
    let line = text.lines().next()?;
    let mut fields = line.split_whitespace();
    if fields.next()? != "cpu" {
        return None;
    }
    let values: Vec<u64> = fields
        .take(8)
        .map(str::parse)
        .collect::<Result<_, _>>()
        .ok()?;
    if values.len() < 4 {
        return None;
    }
    Some(CpuCounters {
        total: values.iter().sum(),
        idle: values[3] + values.get(4).copied().unwrap_or(0),
    })
}
fn cpu_usage(previous: CpuCounters, current: CpuCounters) -> Option<f64> {
    let total = current.total.checked_sub(previous.total)?;
    let idle = current.idle.checked_sub(previous.idle)?;
    if total == 0 || idle > total {
        return None;
    }
    Some((total - idle) as f64 * 100.0 / total as f64)
}
static SYSTEM_CPU: std::sync::Mutex<Option<f64>> = std::sync::Mutex::new(None);
static METRICS_STARTED: std::sync::Once = std::sync::Once::new();
pub fn start_metrics() {
    METRICS_STARTED.call_once(|| {
        std::thread::spawn(|| {
            let mut previous = fs::read_to_string("/proc/stat")
                .ok()
                .and_then(|text| cpu_counters(&text));
            loop {
                std::thread::sleep(std::time::Duration::from_secs(2));
                let current = fs::read_to_string("/proc/stat")
                    .ok()
                    .and_then(|text| cpu_counters(&text));
                if let Ok(mut value) = SYSTEM_CPU.lock() {
                    *value = previous.zip(current).and_then(|(p, c)| cpu_usage(p, c));
                }
                previous = current;
            }
        });
    });
}
fn memory_usage(text: &str) -> Option<(u64, u64)> {
    let value = |name: &str| {
        text.lines().find_map(|line| {
            let mut fields = line.split_whitespace();
            if fields.next()? != name {
                return None;
            }
            fields.next()?.parse::<u64>().ok()?.checked_mul(1024)
        })
    };
    let total = value("MemTotal:")?;
    let available = value("MemAvailable:")?;
    if total == 0 || available > total {
        return None;
    }
    Some((total - available, total))
}
#[cfg(test)]
mod system_metrics_tests {
    use super::*;
    #[test]
    fn cpu_delta_ignores_guest_double_counting_and_rejects_resets() {
        let first = cpu_counters("cpu 10 0 10 80 0 0 0 0 999 999").unwrap();
        let second = cpu_counters("cpu 30 0 30 140 0 0 0 0 999 999").unwrap();
        assert_eq!(cpu_usage(first, second), Some(40.0));
        assert!(cpu_usage(second, first).is_none());
        assert!(cpu_usage(first, first).is_none());
    }
    #[test]
    fn ram_uses_available_instead_of_treating_reclaimable_cache_as_used() {
        assert_eq!(
            memory_usage("MemTotal: 1000 kB\nMemFree: 10 kB\nMemAvailable: 400 kB"),
            Some((600 * 1024, 1000 * 1024))
        );
        assert!(memory_usage("MemTotal: 1 kB\nMemAvailable: 2 kB").is_none());
    }
}
