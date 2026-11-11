#!/usr/bin/env python3
"""Sign only finished artifacts from the same checked-out tag; publish a draft."""
import hashlib,json,os,re,shutil,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[3];os.chdir(root)
def run(args,**kw):return subprocess.run(args,check=True,**kw)
tag=os.environ.get('GITHUB_REF_NAME','')
if os.environ.get('GITHUB_REF_TYPE')!='tag' or not re.fullmatch(r'v\d+\.\d+\.\d+',tag):raise SystemExit('Signing is restricted to stable version tags')
run(['git','merge-base','--is-ancestor','HEAD','origin/main'])
head=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip();source=root/'release-private/unsigned';info=json.loads((source/'build-info.json').read_text())
if subprocess.check_output(['git','rev-parse',tag+'^{commit}'],text=True).strip()!=head:raise SystemExit('Checkout does not match the signed tag')
if info['commit']!=head or tag!='v'+info['version']:raise SystemExit('Artifact commit/version does not match this tag')
version=info['version'];out=root/'release-private/signed';out.mkdir(parents=True,exist_ok=False);keys=root/'release-private/keys';tools=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
for path in keys.iterdir():
 if path.stat().st_mode&0o077:raise SystemExit('Signing material permissions must be private')
for name in [f'pingora-{version}-linux-x86_64.tar.gz',f'pingora-{version}-source.tar.gz','native-upstream.tar.gz','build-info.json','build-host-packages.txt']:
 shutil.copy2(source/name,out/name)
run([str(tools/'apksigner'),'sign','--ks',str(keys/'android-release.p12'),'--ks-pass','file:'+str(keys/'android-store-password'),'--ks-key-alias','pingora','--out',str(out/f'pingora-{version}.apk'),str(source/f'pingora-{version}-unsigned.apk')])
run(['python3','docs/releases/tools/verify-android.py',str(out/f'pingora-{version}.apk'),'--version',version,'--version-code',str(info['version_code'])])
cert=subprocess.check_output(['keytool','-exportcert','-keystore',str(keys/'android-release.p12'),'-alias','pingora','-storepass:file',str(keys/'android-store-password')],stderr=subprocess.DEVNULL)
run(['python3','docs/releases/tools/manifest.py','--version',version,'--version-code',str(info['version_code']),'--directory',str(out),'--signing-key',str(keys/'update-ed25519.pem'),'--android-cert',hashlib.sha256(cert).hexdigest()])
run(['python3','docs/releases/tools/build-installer.py','--output',str(out/'install-app.sh')])
# Reject any signer mismatch with the trust anchor embedded in both applications.
import base64,tempfile
with tempfile.TemporaryDirectory() as temporary:
 d=Path(temporary);public=bytes.fromhex((root/'docs/releases/trust/update-public-key.hex').read_text().strip());(d/'key').write_bytes(bytes.fromhex('302a300506032b6570032100')+public);(d/'sig').write_bytes(base64.b64decode((out/'pingora-update.sig').read_text()))
 run(['openssl','pkeyutl','-verify','-pubin','-keyform','DER','-inkey',str(d/'key'),'-rawin','-in',str(out/'pingora-update.json'),'-sigfile',str(d/'sig')],stdout=subprocess.DEVNULL)
# Publish a deterministic checksum inventory; optional incremental-install sidecars are not downloads.
for sidecar in out.glob('*.idsig'):sidecar.unlink()
checksums=[]
for artifact in sorted(out.iterdir()):
 if artifact.is_file() and artifact.name!='SHA256SUMS':
  with artifact.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
  checksums.append(digest+'  '+artifact.name)
(out/'SHA256SUMS').write_text('\n'.join(checksums)+'\n')
run(['python3','docs/releases/tools/verify-release.py','--directory',str(out),'--version',version,'--version-code',str(info['version_code'])])
run(['gh','release','create',tag,'--repo','wrestle-R/Pingora','--verify-tag','--draft','--title','Pingora '+version,'--notes','Signed development artifacts. Read the qualification limits before installing; publication follows acceptance review.',*[str(p) for p in out.iterdir() if p.is_file()]])
print('Complete signed draft created. No public release or compatibility qualification implied.')
