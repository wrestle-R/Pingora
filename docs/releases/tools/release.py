#!/usr/bin/env python3
"""Qualified-host release entrypoint. No publication until all local checks pass."""
import argparse,hashlib,json,os,re,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[3];os.chdir(root)
p=argparse.ArgumentParser();p.add_argument('--tag',required=True);p.add_argument('--publish',action='store_true');a=p.parse_args()
m=re.fullmatch(r'v(\d+)\.(\d+)\.(\d+)',a.tag)
if not m:raise SystemExit('Run from a stable version tag vMAJOR.MINOR.PATCH')
def run(args,**kw):return subprocess.run(args,check=True,**kw)
head=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip();tag=subprocess.check_output(['git','rev-parse',a.tag+'^{commit}'],text=True).strip()
if head!=tag or subprocess.check_output(['git','status','--porcelain'],text=True).strip():raise SystemExit('Build an exact clean tagged checkout')
version=a.tag[1:];code=int(m[1])*1000000+int(m[2])*1000+int(m[3])
if int(m[2])>999 or int(m[3])>999 or not 0<code<2100000000:raise SystemExit('Version is outside Android version-code mapping')
private=root/'release-private';key=private/'android-release.p12';password=private/'android-store-password';update=private/'update-ed25519.pem'
if not all(p.is_file() for p in [key,password,update]):raise SystemExit('Qualified runner requires its private release signing files; none are embedded in the workflow')
for path in [key,password,update]:
 if path.stat().st_mode&0o077:raise SystemExit('Signing file permissions must be private')
# Require the exact same application version in each runtime before building.
import tomllib
if tomllib.loads((root/'tauri/src-tauri/Cargo.toml').read_text())['package']['version']!=version:raise SystemExit('Cargo version does not match tag')
if json.loads((root/'tauri/src-tauri/tauri.conf.json').read_text())['version']!=version:raise SystemExit('Tauri version does not match tag')
text=(root/'app/build.gradle.kts').read_text()
if f'versionName = "{version}"' not in text or f'versionCode = {code}\n' not in text:raise SystemExit('Android version/name must match tag and version-code formula')
run(['cargo','test','--locked','--manifest-path','tauri/src-tauri/Cargo.toml'])
run(['cargo','clippy','--locked','--manifest-path','tauri/src-tauri/Cargo.toml','--','-D','warnings'])
run(['app/gradlew','-p','app','--no-daemon','testDebugUnitTest','assembleRelease'])
out=root/'release-private/artifacts'/version;out.mkdir(parents=True,exist_ok=True)
# Prepare from the pinned archive in an isolated fresh generated directory.
import urllib.request,shutil
native_manifest=json.loads((root/'tauri/native/source.json').read_text())
archive=out/'native-upstream.tar.gz'
urllib.request.urlretrieve(native_manifest['archive_url'],archive)
if hashlib.sha256(archive.read_bytes()).hexdigest()!=native_manifest['archive_sha256']:raise SystemExit('Native archive checksum mismatch')
shutil.rmtree(root/'tauri/target/native',ignore_errors=True)
run(['python3','tauri/native/prepare.py',str(archive)])
run(['python3','docs/releases/tools/build-linux.py','--version',version,'--output',str(out)])
sdk=Path(os.environ.get('ANDROID_HOME',''));tools=sdk/'build-tools/36.0.0';apk=out/f'pingora-{version}.apk'
run([str(tools/'apksigner'),'sign','--ks',str(key),'--ks-pass','file:'+str(password),'--ks-key-alias','pingora','--out',str(apk),'app/build/outputs/apk/release/pingora-android-release-unsigned.apk'])
run([str(tools/'apksigner'),'verify',str(apk)])
cert=subprocess.check_output(['keytool','-exportcert','-keystore',str(key),'-alias','pingora','-storepass:file',str(password)],stderr=subprocess.DEVNULL)
run(['python3','docs/releases/tools/manifest.py','--version',version,'--version-code',str(code),'--directory',str(out),'--signing-key',str(update),'--android-cert',hashlib.sha256(cert).hexdigest()])
# Preserve the exact source and license/provenance records for recipients.
with (out/f'pingora-{version}-source.tar.gz').open('wb') as source:run(['git','archive','--format=tar.gz',head],stdout=source)
if a.publish:
 # Idempotent: gh refuses an already existing release; never clobber signed assets.
 run(['gh','release','create',a.tag,'--repo','wrestle-R/Pingora','--verify-tag','--draft','--title','Pingora '+version,'--notes','Development release. Read supported-host and remaining qualification limits before installation.',*[str(p) for p in out.iterdir()]])
 print('Complete signed draft uploaded. Publish only after artifact/qualification review.')
else:print('Signed artifacts built locally; no GitHub release published.')
