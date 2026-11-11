#!/usr/bin/env python3
"""Install the pinned build SDK in a runner-temporary directory. No project .env."""
import hashlib,os,subprocess,urllib.request,zipfile
from pathlib import Path
root=Path(os.environ['RUNNER_TEMP'])/'pingora-sdk';root.mkdir(parents=True,exist_ok=True)
archive=root/'tools.zip';urllib.request.urlretrieve('https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip',archive)
if hashlib.sha256(archive.read_bytes()).hexdigest()!='4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583':raise SystemExit('SDK tool digest mismatch')
(root/'cmdline-tools').mkdir(exist_ok=True)
with zipfile.ZipFile(archive) as z:z.extractall(root/'cmdline-tools')
(root/'cmdline-tools/cmdline-tools').rename(root/'cmdline-tools/latest')
for file in (root/'cmdline-tools/latest/bin').iterdir():file.chmod(0o755)
archive.unlink()
subprocess.run([str(root/'cmdline-tools/latest/bin/sdkmanager'),'--sdk_root='+str(root),'platforms;android-37.0','build-tools;36.0.0','platform-tools'],input=b'y\n'*100,check=True)
with Path(os.environ['GITHUB_ENV']).open('a') as f:f.write('ANDROID_HOME='+str(root)+'\n')
print('Pinned Android SDK ready.')
