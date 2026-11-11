#!/usr/bin/env python3
"""Fail closed on an incomplete signed release before upload or publication."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tarfile
import tempfile
import zipfile

p=argparse.ArgumentParser();p.add_argument('--directory',type=Path,required=True);p.add_argument('--version',required=True);p.add_argument('--version-code',type=int,required=True);a=p.parse_args()
d=a.directory
required={'install-app.sh','native-upstream.tar.gz','build-info.json','build-host-packages.txt','pingora-update.json','pingora-update.sig','SHA256SUMS',f'pingora-{a.version}.apk',f'pingora-{a.version}-linux-x86_64.tar.gz',f'pingora-{a.version}-source.tar.gz',f'pingora-{a.version}-android-update.zip'}
if not required.issubset({f.name for f in d.iterdir() if f.is_file()}):raise SystemExit('Release is missing required artifacts')
checksums={}
for line in (d/'SHA256SUMS').read_text().splitlines():
 match=re.fullmatch(r'([a-f0-9]{64})  ([A-Za-z0-9][A-Za-z0-9._-]*)',line)
 if not match or match[2] in checksums:raise SystemExit('Invalid checksum inventory')
 checksums[match[2]]=match[1]
if set(checksums)!={f.name for f in d.iterdir() if f.is_file() and f.name!='SHA256SUMS'}:raise SystemExit('Checksum inventory is incomplete')
for name,expected in checksums.items():
 with (d/name).open('rb') as stream:actual=hashlib.file_digest(stream,'sha256').hexdigest()
 if actual!=expected:raise SystemExit('Release artifact digest mismatch: '+name)
metadata=(d/'pingora-update.json').read_bytes();signature=base64.b64decode((d/'pingora-update.sig').read_bytes(),validate=False)
trust=Path(__file__).resolve().parents[1]/'trust'
with tempfile.TemporaryDirectory(prefix='pingora-release-verify-') as temporary:
 root=Path(temporary);(root/'key').write_bytes(bytes.fromhex('302a300506032b6570032100')+bytes.fromhex((trust/'update-public-key.hex').read_text().strip()));(root/'sig').write_bytes(signature)
 subprocess.run(['openssl','pkeyutl','-verify','-pubin','-keyform','DER','-inkey',str(root/'key'),'-rawin','-in',str(d/'pingora-update.json'),'-sigfile',str(root/'sig')],check=True,capture_output=True)
manifest=json.loads(metadata)
if manifest['schema']!=1 or manifest['version']!=a.version or manifest['version_code']!=a.version_code:raise SystemExit('Signed release version mismatch')
for key,suffix in [('android','.apk'),('linux-x86_64','-linux-x86_64.tar.gz')]:
 target=manifest['targets'][key];name='pingora-'+a.version+suffix
 if target['name']!=name or target['sha256']!=checksums[name] or target['size']!=(d/name).stat().st_size:raise SystemExit('Signed target inventory mismatch')
subprocess.run(['python3',str(Path(__file__).with_name('verify-android.py')),str(d/f'pingora-{a.version}.apk'),'--version',a.version,'--version-code',str(a.version_code)],check=True)
with zipfile.ZipFile(d/f'pingora-{a.version}-android-update.zip') as bundle:
 apk=manifest['targets']['android']['name']
 if set(bundle.namelist())!={'pingora-update.json','pingora-update.sig',apk}:raise SystemExit('Android update bundle inventory mismatch')
 for name in ['pingora-update.json','pingora-update.sig']:
  if bundle.read(name)!=(d/name).read_bytes():raise SystemExit('Android update bundle metadata mismatch')
 with bundle.open(apk) as stream:
  if hashlib.file_digest(stream,'sha256').hexdigest()!=checksums[apk]:raise SystemExit('Android update bundle APK mismatch')
with tarfile.open(d/f'pingora-{a.version}-source.tar.gz') as source:
 for item in source:
  name=item.name.lstrip('./')
  if name=='AGENTS.md' or name.startswith(('local-docs/','release-private/','docs/progress/','docs/agents/')):raise SystemExit('Internal files leaked into release source')
subprocess.run(['bash','-n',str(d/'install-app.sh')],check=True)
print('Complete release verified: signed targets, pinned APK identity, Android update ZIP, checksums, installer syntax and source exclusions')
