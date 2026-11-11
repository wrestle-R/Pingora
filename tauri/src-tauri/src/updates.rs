use base64::{engine::general_purpose::STANDARD, Engine};
use ed25519_dalek::{Signature, VerifyingKey};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    collections::BTreeMap,
    fs::{self, File, OpenOptions},
    io::{Read, Write},
    os::unix::fs::{MetadataExt, OpenOptionsExt, PermissionsExt},
    path::{Path, PathBuf},
    time::Duration,
};
const REPOSITORY: &str = "https://api.github.com/repos/wrestle-R/Pingora";
#[derive(Clone, Deserialize, Serialize)]
pub struct Target {
    pub name: String,
    pub size: u64,
    pub sha256: String,
    #[serde(default)]
    pub package_name: String,
    #[serde(default)]
    pub signing_cert_sha256: String,
}
#[derive(Clone, Deserialize, Serialize)]
pub struct Manifest {
    pub schema: u32,
    pub version: String,
    pub version_code: u64,
    pub targets: BTreeMap<String, Target>,
}
#[derive(Clone, Deserialize)]
struct Asset {
    id: u64,
    name: String,
    size: u64,
}
#[derive(Deserialize)]
struct Release {
    tag_name: String,
    draft: bool,
    assets: Vec<Asset>,
}
#[derive(Clone)]
pub struct Candidate {
    pub manifest: Manifest,
    asset: Asset,
    bytes: Vec<u8>,
    signature: String,
    pub staged: Option<PathBuf>,
}
#[derive(Serialize)]
pub struct View {
    pub version: String,
    pub size: u64,
    pub downloaded: bool,
    pub message: String,
}
fn decode_hex<const N: usize>(value: &str) -> Result<[u8; N], String> {
    if value.len() != N * 2 {
        return Err("Invalid signing key".into());
    }
    let mut bytes = [0u8; N];
    for (i, b) in bytes.iter_mut().enumerate() {
        *b = u8::from_str_radix(&value[i * 2..i * 2 + 2], 16).map_err(|_| "Invalid signing key")?;
    }
    Ok(bytes)
}
pub fn verify(bytes: &[u8], signature: &str, public: &[u8; 32]) -> Result<Manifest, String> {
    if bytes.len() > 65536 {
        return Err("Update metadata exceeded its limit".into());
    }
    let raw = STANDARD
        .decode(signature.trim())
        .map_err(|_| "Invalid update signature")?;
    let signature = Signature::from_slice(&raw).map_err(|_| "Invalid update signature")?;
    VerifyingKey::from_bytes(public)
        .map_err(|_| "Invalid update signing key")?
        .verify_strict(bytes, &signature)
        .map_err(|_| "Update signature verification failed")?;
    let manifest: Manifest =
        serde_json::from_slice(bytes).map_err(|_| "Invalid signed update metadata")?;
    if manifest.schema != 1
        || manifest.version.len() > 64
        || semver::Version::parse(&manifest.version).is_err()
    {
        return Err("Unsupported update metadata".into());
    }
    for target in manifest.targets.values() {
        if target.name.is_empty()
            || target.name.len() > 160
            || !target
                .name
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || matches!(b, b'.' | b'-' | b'_'))
            || target.name.starts_with('.')
            || target.size == 0
            || target.size > 512 * 1024 * 1024
            || target.sha256.len() != 64
            || !target.sha256.bytes().all(|b| b.is_ascii_hexdigit())
        {
            return Err("Invalid signed update target".into());
        }
    }
    Ok(manifest)
}
fn trust() -> Result<[u8; 32], String> {
    decode_hex(include_str!("../../../docs/releases/trust/update-public-key.hex").trim())
}
fn client() -> Result<reqwest::blocking::Client, String> {
    reqwest::blocking::Client::builder()
        .user_agent("Pingora updater")
        .https_only(true)
        .timeout(Duration::from_secs(120))
        .connect_timeout(Duration::from_secs(10))
        .redirect(reqwest::redirect::Policy::custom(|attempt| {
            if attempt.previous().len() >= 5 {
                return attempt.error("Too many redirects");
            }
            if attempt.url().scheme() == "https"
                && matches!(
                    attempt.url().host_str(),
                    Some(
                        "api.github.com"
                            | "github.com"
                            | "release-assets.githubusercontent.com"
                            | "objects.githubusercontent.com"
                    )
                )
            {
                attempt.follow()
            } else {
                attempt.stop()
            }
        }))
        .build()
        .map_err(|_| "Could not initialize secure downloads".into())
}
fn token() -> Option<String> {
    std::process::Command::new("gh")
        .args(["auth", "token"])
        .output()
        .ok()
        .filter(|o| o.status.success() && o.stdout.len() < 1024)
        .and_then(|o| String::from_utf8(o.stdout).ok())
        .map(|s| s.trim().to_owned())
}
fn request(
    client: &reqwest::blocking::Client,
    url: &str,
    binary: bool,
) -> Result<reqwest::blocking::Response, String> {
    if !url.starts_with(&format!("{REPOSITORY}/")) {
        return Err("Update source is not this repository".into());
    }
    let mut request = client.get(url).header(
        "Accept",
        if binary {
            "application/octet-stream"
        } else {
            "application/vnd.github+json"
        },
    );
    if let Some(token) = token() {
        request = request.bearer_auth(token);
    }
    let response = request.send().map_err(|_| "Could not reach GitHub")?;
    if response.status() == reqwest::StatusCode::NOT_FOUND {
        return Err("No authorized published release is available. Private releases require your authenticated GitHub CLI session.".into());
    }
    if !response.status().is_success() {
        return Err(
            "GitHub refused the update request; check authentication and rate limits".into(),
        );
    }
    Ok(response)
}
fn asset_bytes(
    client: &reqwest::blocking::Client,
    asset: &Asset,
    limit: u64,
) -> Result<Vec<u8>, String> {
    if asset.size > limit {
        return Err("Release metadata asset exceeds its limit".into());
    }
    let mut bytes = Vec::new();
    request(
        client,
        &format!("{REPOSITORY}/releases/assets/{}", asset.id),
        true,
    )?
    .take(limit + 1)
    .read_to_end(&mut bytes)
    .map_err(|_| "Could not read release metadata")?;
    if bytes.len() as u64 > limit || bytes.len() as u64 != asset.size {
        return Err("Release metadata length mismatch".into());
    }
    Ok(bytes)
}
pub fn check() -> Result<Candidate, String> {
    let client = client()?;
    let mut bytes = Vec::new();
    request(&client, &format!("{REPOSITORY}/releases/latest"), false)?
        .take(1024 * 1024 + 1)
        .read_to_end(&mut bytes)
        .map_err(|_| "Could not read release")?;
    if bytes.len() > 1024 * 1024 {
        return Err("Release response exceeded its limit".into());
    }
    let release: Release =
        serde_json::from_slice(&bytes).map_err(|_| "Invalid release response")?;
    if release.draft {
        return Err("Draft releases cannot be installed".into());
    }
    let metadata = release
        .assets
        .iter()
        .find(|a| a.name == "pingora-update.json")
        .ok_or("Release has no signed update metadata")?;
    let signature = release
        .assets
        .iter()
        .find(|a| a.name == "pingora-update.sig")
        .ok_or("Release has no update signature")?;
    let bytes = asset_bytes(&client, metadata, 65536)?;
    let signature = String::from_utf8(asset_bytes(&client, signature, 256)?)
        .map_err(|_| "Invalid update signature")?;
    let manifest = verify(&bytes, &signature, &trust()?)?;
    if release.tag_name != format!("v{}", manifest.version) {
        return Err("Release tag and signed version do not match".into());
    }
    if semver::Version::parse(&manifest.version).map_err(|_| "Invalid version")?
        <= semver::Version::parse(env!("CARGO_PKG_VERSION"))
            .map_err(|_| "Invalid installed version")?
    {
        return Err("No newer signed stable version is available".into());
    }
    let target = manifest
        .targets
        .get("linux-x86_64")
        .ok_or("Release has no supported Linux artifact")?;
    if std::env::consts::ARCH != "x86_64"
        || !(target.name.ends_with(".AppImage") || target.name.ends_with(".tar.gz"))
    {
        return Err("This release does not support this installation".into());
    }
    let asset = release
        .assets
        .into_iter()
        .find(|a| a.name == target.name && a.size == target.size)
        .ok_or("Signed artifact does not match release inventory")?;
    Ok(Candidate {
        manifest,
        asset,
        bytes,
        signature,
        staged: None,
    })
}
pub fn view(candidate: &Candidate) -> View {
    View {
        version: candidate.manifest.version.clone(),
        size: candidate.asset.size,
        downloaded: candidate.staged.is_some(),
        message: "Signed metadata verified. Installation requires approval and an idle session."
            .into(),
    }
}
pub fn download(candidate: &mut Candidate, directory: &Path) -> Result<(), String> {
    verify(&candidate.bytes, &candidate.signature, &trust()?)?;
    let target = candidate
        .manifest
        .targets
        .get("linux-x86_64")
        .ok_or("Unsupported target")?;
    let directory = directory.join(&candidate.manifest.version);
    fs::create_dir_all(&directory).map_err(|_| "Could not create update staging directory")?;
    fs::set_permissions(&directory, fs::Permissions::from_mode(0o700))
        .map_err(|_| "Could not protect update staging directory")?;
    let part = directory.join(format!("pingora-update-{}.part", crate::runtime::now()));
    let mut file = OpenOptions::new()
        .create_new(true)
        .write(true)
        .mode(0o600)
        .open(&part)
        .map_err(|_| "Could not reserve update storage")?;
    let result = (|| {
        let mut response = request(
            &client()?,
            &format!("{REPOSITORY}/releases/assets/{}", candidate.asset.id),
            true,
        )?;
        let mut digest = Sha256::new();
        let mut count = 0;
        let mut buffer = [0; 65536];
        loop {
            let n = response
                .read(&mut buffer)
                .map_err(|_| "Update download interrupted")?;
            if n == 0 {
                break;
            }
            count += n as u64;
            if count > target.size {
                return Err("Update exceeded its signed size".into());
            }
            digest.update(&buffer[..n]);
            file.write_all(&buffer[..n])
                .map_err(|_| "Could not save update; check free space")?;
        }
        if count != target.size
            || format!("{:x}", digest.finalize()) != target.sha256.to_ascii_lowercase()
        {
            return Err("Downloaded update failed integrity verification".into());
        }
        file.sync_all()
            .map_err(|_| "Could not finish staging update")?;
        Ok(())
    })();
    drop(file);
    if let Err(error) = result {
        fs::remove_file(&part).ok();
        return Err(error);
    }
    let path = directory.join(&target.name);
    fs::rename(part, &path).map_err(|_| "Could not finish staging update")?;
    fs::write(directory.join("pingora-update.json"), &candidate.bytes)
        .map_err(|_| "Could not stage verified metadata")?;
    fs::write(directory.join("pingora-update.sig"), &candidate.signature)
        .map_err(|_| "Could not stage verified signature")?;
    candidate.staged = Some(path);
    Ok(())
}
pub fn install(candidate: &Candidate, approved: bool, busy: bool) -> Result<(), String> {
    if !approved {
        return Err("Installation requires explicit approval".into());
    }
    if busy {
        return Err("Finish active calls and transfers before installing".into());
    }
    verify(&candidate.bytes, &candidate.signature, &trust()?)?;
    let target = candidate
        .manifest
        .targets
        .get("linux-x86_64")
        .ok_or("Unsupported target")?;
    let source = candidate
        .staged
        .as_ref()
        .ok_or("Download and verify the update first")?;
    if target.name.ends_with(".tar.gz") {
        let executable = std::env::current_exe()
            .and_then(|p| p.canonicalize())
            .map_err(|_| "Could not identify the installed app")?;
        let home = std::env::var_os("HOME")
            .map(PathBuf::from)
            .and_then(|p| p.canonicalize().ok())
            .ok_or("Could not identify installation owner")?;
        if !executable.starts_with(home.join(".local/share/pingora/versions")) {
            return Err("Verified package downloaded. This app is not a managed portable installation; use docs/releases/install.py from your authenticated source checkout to install the signed release.".into());
        }
        let installer = executable
            .parent()
            .ok_or("Invalid installation path")?
            .join("updates/install.py");
        if !installer.is_file() {
            return Err(
                "Portable installer is missing; install manually from the signed source release"
                    .into(),
            );
        }
        let status = std::process::Command::new("python3")
            .arg(installer)
            .arg("--source")
            .arg(source.parent().ok_or("Update staging unavailable")?)
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status()
            .map_err(|_| "Could not start signed package installer")?;
        return if status.success() {
            Ok(())
        } else {
            Err("Signed installer refused the update; existing launcher and previous version retained".into())
        };
    }
    let installed=std::env::var_os("APPIMAGE").map(PathBuf::from).ok_or("This installation is managed outside the portable updater. Use its package manager or install the verified AppImage manually.")?.canonicalize().map_err(|_|"Portable installation is unavailable")?;
    let home = std::env::var_os("HOME")
        .map(PathBuf::from)
        .and_then(|p| p.canonicalize().ok())
        .ok_or("Could not establish portable installation ownership")?;
    if !installed.starts_with(home)
        || !installed.is_file()
        || installed
            .metadata()
            .map_err(|_| "Could not inspect installation")?
            .uid()
            != unsafe { libc::geteuid() }
    {
        return Err("Only user-owned portable AppImages can be replaced".into());
    }
    commit(source, &installed, target)
}
fn commit(source: &Path, installed: &Path, target: &Target) -> Result<(), String> {
    let staged = installed.with_extension("pingora-new");
    let mut output = OpenOptions::new()
        .write(true)
        .create_new(true)
        .mode(0o755)
        .open(&staged)
        .map_err(|_| "Could not stage replacement beside installation")?;
    let result = (|| {
        let mut input = File::open(source).map_err(|_| "Staged update unavailable")?;
        let mut digest = Sha256::new();
        let mut total = 0;
        let mut buffer = [0; 65536];
        loop {
            let n = input
                .read(&mut buffer)
                .map_err(|_| "Could not read staged update")?;
            if n == 0 {
                break;
            }
            total += n as u64;
            if total > target.size {
                return Err("Staged update changed".into());
            }
            digest.update(&buffer[..n]);
            output
                .write_all(&buffer[..n])
                .map_err(|_| "Could not stage replacement")?;
        }
        if total != target.size
            || format!("{:x}", digest.finalize()) != target.sha256.to_ascii_lowercase()
        {
            return Err("Staged update changed after download".into());
        }
        output
            .sync_all()
            .map_err(|_| "Could not flush replacement")?;
        Ok(())
    })();
    drop(output);
    if let Err(error) = result {
        fs::remove_file(&staged).ok();
        return Err(error);
    }
    let backup = installed.with_extension(format!("previous-{}", crate::runtime::now()));
    if fs::hard_link(installed, backup).is_err() {
        fs::remove_file(staged).ok();
        return Err("Could not preserve the previous installation".into());
    }
    fs::rename(&staged, installed)
        .map_err(|_| "Could not replace portable installation; previous version preserved".into())
}
#[cfg(test)]
mod tests {
    use super::*;
    use ed25519_dalek::{Signer, SigningKey};
    #[test]
    fn approval_and_busy_gates_leave_the_staged_update_untouched() {
        let directory = std::env::temp_dir().join(format!(
            "pingora-update-gates-{}-{}",
            std::process::id(),
            crate::runtime::now()
        ));
        fs::create_dir_all(&directory).unwrap();
        let staged = directory.join("staged");
        fs::write(&staged, b"candidate").unwrap();
        let key = SigningKey::from_bytes(&[42; 32]);
        let data = br#"{"schema":1,"version":"0.2.0","version_code":2000,"targets":{}}"#;
        let signature = STANDARD.encode(key.sign(data).to_bytes());
        let candidate = Candidate {
            manifest: verify(data, &signature, &key.verifying_key().to_bytes()).unwrap(),
            asset: Asset {
                id: 1,
                name: "candidate.tar.gz".into(),
                size: 9,
            },
            bytes: data.to_vec(),
            signature,
            staged: Some(staged.clone()),
        };
        assert_eq!(
            install(&candidate, false, false).unwrap_err(),
            "Installation requires explicit approval"
        );
        assert_eq!(fs::read(&staged).unwrap(), b"candidate");
        assert_eq!(
            install(&candidate, true, true).unwrap_err(),
            "Finish active calls and transfers before installing"
        );
        assert_eq!(fs::read(&staged).unwrap(), b"candidate");
        fs::remove_dir_all(directory).unwrap();
    }
    #[test]
    fn signatures_reject_tampering_and_untrusted_keys() {
        let key = SigningKey::from_bytes(&[42; 32]);
        let data = br#"{"schema":1,"version":"0.2.0","version_code":20099,"targets":{}}"#;
        let sig = STANDARD.encode(key.sign(data).to_bytes());
        assert!(verify(data, &sig, &key.verifying_key().to_bytes()).is_ok());
        assert!(verify(b"changed", &sig, &key.verifying_key().to_bytes()).is_err());
        assert!(verify(
            data,
            &sig,
            &SigningKey::from_bytes(&[43; 32]).verifying_key().to_bytes()
        )
        .is_err());
    }
    #[test]
    fn failed_staged_update_preserves_installed_bytes() {
        let directory = std::env::temp_dir().join(format!(
            "pingora-update-{}-{}",
            std::process::id(),
            crate::runtime::now()
        ));
        fs::create_dir_all(&directory).unwrap();
        let installed = directory.join("Pingora.AppImage");
        let staged = directory.join("download");
        fs::write(&installed, b"old").unwrap();
        fs::write(&staged, b"bad").unwrap();
        let target = Target {
            name: "Pingora.AppImage".into(),
            size: 3,
            sha256: format!("{:x}", Sha256::digest(b"new")),
            package_name: String::new(),
            signing_cert_sha256: String::new(),
        };
        assert!(commit(&staged, &installed, &target).is_err());
        assert_eq!(fs::read(&installed).unwrap(), b"old");
        fs::write(&staged, b"new").unwrap();
        commit(&staged, &installed, &target).unwrap();
        assert_eq!(fs::read(&installed).unwrap(), b"new");
        fs::remove_dir_all(directory).unwrap();
    }
}
