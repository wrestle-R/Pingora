#!/usr/bin/env python3
"""Verify APK bytes, release identity/version and (after signing) the pinned signer."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import zipfile

parser=argparse.ArgumentParser()
parser.add_argument('apk',type=Path)
parser.add_argument('--version',required=True)
parser.add_argument('--version-code',required=True,type=int)
parser.add_argument('--unsigned',action='store_true')
a=parser.parse_args()
tools=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
with zipfile.ZipFile(a.apk) as archive:
 if not {'AndroidManifest.xml','classes.dex'}.issubset(archive.namelist()) or archive.testzip():
  raise SystemExit('APK contents are incomplete or corrupt')
badging=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(a.apk)],text=True)
package=re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",badging)
if not package or package.groups()!=('io.github.wrestle_r.pingora',str(a.version_code),a.version):
 raise SystemExit('APK package/version does not match the release')
if not a.unsigned:
 result=subprocess.run([str(tools/'apksigner'),'verify','--print-certs',str(a.apk)],capture_output=True,text=True)
 if result.returncode:raise SystemExit('APK signature verification failed')
 fingerprints=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)',result.stdout)
 trusted=(Path(__file__).resolve().parents[1]/'trust/android-cert-sha256.hex').read_text().strip().lower()
 if [s.lower() for s in fingerprints]!=[trusted]:raise SystemExit('APK release signer mismatch')
print(f'Android artifact verified: {a.version} / {a.version_code}, correct package'+(' (unsigned build)' if a.unsigned else ', pinned signing certificate'))
