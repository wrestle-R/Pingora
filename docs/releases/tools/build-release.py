#!/usr/bin/env python3
"""Keyless hosted build: test/build an exact checkout; signing is a separate job."""
import hashlib,json,os,re,shutil,subprocess,tomllib,urllib.request
from pathlib import Path
root=Path(__file__).resolve().parents[3];os.chdir(root)
def run(args,**kw):return subprocess.run(args,check=True,**kw)
head=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
if subprocess.check_output(['git','status','--porcelain'],text=True).strip():raise SystemExit('Build requires a clean checkout')
version=tomllib.loads((root/'tauri/src-tauri/Cargo.toml').read_text())['package']['version'];m=re.fullmatch(r'(\d+)\.(\d+)\.(\d+)',version)
if not m:raise SystemExit('Unsupported source version')
code=int(m[1])*1000000+int(m[2])*1000+int(m[3])
if int(m[2])>999 or int(m[3])>999 or not 0<code<2100000000:raise SystemExit('Invalid Android version-code mapping')
if json.loads((root/'tauri/src-tauri/tauri.conf.json').read_text())['version']!=version:raise SystemExit('Tauri version mismatch')
text=(root/'app/build.gradle.kts').read_text()
if f'versionName = "{version}"' not in text or f'versionCode = {code}\n' not in text:raise SystemExit('Android version mismatch')
out=root/'release-private/unsigned';out.mkdir(parents=True,exist_ok=True)
run(['cargo','test','--locked','--manifest-path','tauri/src-tauri/Cargo.toml'])
run(['cargo','clippy','--locked','--manifest-path','tauri/src-tauri/Cargo.toml','--','-D','warnings'])
run(['app/gradlew','-p','app','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx3072m','testDebugUnitTest','assembleRelease'])
native=json.loads((root/'tauri/native/source.json').read_text());archive=out/'native-upstream.tar.gz'
urllib.request.urlretrieve(native['archive_url'],archive)
if hashlib.sha256(archive.read_bytes()).hexdigest()!=native['archive_sha256']:raise SystemExit('Native source digest mismatch')
run(['python3','tauri/native/prepare.py',str(archive)])
run(['python3','docs/releases/tools/build-linux.py','--version',version,'--output',str(out)])
shutil.copy2(root/'app/build/outputs/apk/release/pingora-android-release-unsigned.apk',out/f'pingora-{version}-unsigned.apk')
with (out/f'pingora-{version}-source.tar.gz').open('wb') as file:run(['git','archive','--format=tar.gz',head],stdout=file)
(out/'build-info.json').write_text(json.dumps({'commit':head,'version':version,'version_code':code,'native_archive_sha256':native['archive_sha256']},sort_keys=True)+'\n')
print('Keyless build complete. No signing material was present in this job.')
