#!/usr/bin/env python3
"""Keyless hosted build: test/build an exact checkout; signing is a separate job."""
import argparse,hashlib,json,os,re,shutil,subprocess,tomllib,urllib.request
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--stage',choices=['all','rust','android','native','linux','inventory'],default='all');stage=parser.parse_args().stage
root=Path(__file__).resolve().parents[3];os.chdir(root)
def run(args,**kw):return subprocess.run(args,check=True,**kw)
head=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
if subprocess.check_output(['git','status','--porcelain'],text=True).strip():raise SystemExit('Build requires a clean checkout')
version=tomllib.loads((root/'tauri/src-tauri/Cargo.toml').read_text())['package']['version'];m=re.fullmatch(r'(\d+)\.(\d+)\.(\d+)',version)
if not m:raise SystemExit('Unsupported source version')
if os.environ.get('GITHUB_REF_TYPE')=='tag' and os.environ.get('GITHUB_REF_NAME')!='v'+version:raise SystemExit('Tag does not match the stable source version')
code=int(m[1])*1000000+int(m[2])*1000+int(m[3])
if int(m[2])>999 or int(m[3])>999 or not 0<code<2100000000:raise SystemExit('Invalid Android version-code mapping')
if json.loads((root/'tauri/src-tauri/tauri.conf.json').read_text())['version']!=version:raise SystemExit('Tauri version mismatch')
text=(root/'app/build.gradle.kts').read_text()
if f'versionName = "{version}"' not in text or f'versionCode = {code}\n' not in text:raise SystemExit('Android version mismatch')
out=root/'release-private/unsigned';out.mkdir(parents=True,exist_ok=True)
native=json.loads((root/'tauri/native/source.json').read_text());archive=out/'native-upstream.tar.gz'
if stage in ['all','rust']:
 run(['npm','--prefix','tauri/frontend','ci','--no-audit','--no-fund'])
 run(['npm','--prefix','tauri/frontend','test'])
 run(['npm','--prefix','tauri/frontend','run','build'])
 run(['cargo','test','--locked','--manifest-path','tauri/src-tauri/Cargo.toml'])
 run(['cargo','clippy','--locked','--manifest-path','tauri/src-tauri/Cargo.toml','--','-D','warnings'])
if stage in ['all','android']:
 run(['app/gradlew','-p','app','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx3072m','testDebugUnitTest','assembleRelease'])
 shutil.copy2(root/'app/build/outputs/apk/release/pingora-android-release-unsigned.apk',out/f'pingora-{version}-unsigned.apk')
 run(['python3','docs/releases/tools/verify-android.py',str(out/f'pingora-{version}-unsigned.apk'),'--version',version,'--version-code',str(code),'--unsigned'])
if stage in ['all','native']:
 urllib.request.urlretrieve(native['archive_url'],archive)
 if hashlib.sha256(archive.read_bytes()).hexdigest()!=native['archive_sha256']:raise SystemExit('Native source digest mismatch')
 run(['python3','tauri/native/prepare.py',str(archive)])
if stage in ['all','linux']:
 run(['python3','docs/releases/tools/build-linux.py','--version',version,'--output',str(out)])
if stage in ['all','inventory']:
 for artifact in [out/f'pingora-{version}-unsigned.apk',out/f'pingora-{version}-linux-x86_64.tar.gz',archive]:
  if not artifact.is_file():raise SystemExit('Cannot inventory an incomplete build')
 with (out/f'pingora-{version}-source.tar.gz').open('wb') as file:run(['git','archive','--format=tar.gz',head],stdout=file)
 (out/'build-info.json').write_text(json.dumps({'commit':head,'version':version,'version_code':code,'native_archive_sha256':native['archive_sha256']},sort_keys=True)+'\n')
print('Keyless stage complete: '+stage+'. No signing material was present in this job.')
