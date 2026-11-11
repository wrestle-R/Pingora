#!/usr/bin/env python3
"""Produce signed release metadata from finished artifacts. Private key stays local."""
import argparse,base64,hashlib,json,re,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--version',required=True);p.add_argument('--version-code',type=int,required=True);p.add_argument('--directory',type=Path,required=True);p.add_argument('--signing-key',type=Path,required=True);p.add_argument('--android-cert',required=True);a=p.parse_args()
if not re.fullmatch(r'\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?',a.version) or a.version_code<1:raise SystemExit('Invalid release version')
if not re.fullmatch('[0-9a-fA-F]{64}',a.android_cert):raise SystemExit('Invalid Android certificate fingerprint')
targets={}
for key,name in [('android',f'pingora-{a.version}.apk'),('linux-x86_64',f'pingora-{a.version}-linux-x86_64.tar.gz')]:
 f=a.directory/name
 if not f.is_file():raise SystemExit(f'Missing finished artifact: {name}')
 targets[key]={'name':name,'size':f.stat().st_size,'sha256':hashlib.sha256(f.read_bytes()).hexdigest()}
 if key=='android':targets[key].update(package_name='io.github.wrestle_r.pingora',signing_cert_sha256=a.android_cert.lower())
m=a.directory/'pingora-update.json';m.write_text(json.dumps({'schema':1,'version':a.version,'version_code':a.version_code,'targets':targets},sort_keys=True,separators=(',',':'))+'\n')
signature=subprocess.run(['openssl','pkeyutl','-sign','-rawin','-inkey',str(a.signing_key),'-in',str(m)],capture_output=True,check=True).stdout
(a.directory/'pingora-update.sig').write_text(base64.b64encode(signature).decode()+'\n')
import zipfile
with zipfile.ZipFile(a.directory/f'pingora-{a.version}-android-update.zip','w',zipfile.ZIP_DEFLATED) as z:
 for name in ['pingora-update.json','pingora-update.sig',targets['android']['name']]:z.write(a.directory/name,name)
print('Signed manifest and Android update bundle created; no private key copied.')
