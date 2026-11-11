#!/usr/bin/env python3
"""CI-only install/upgrade/downgrade contract on a disposable Android emulator."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile
import urllib.request

p=argparse.ArgumentParser();p.add_argument('--directory',type=Path,required=True);a=p.parse_args()
serial=os.environ.get('ANDROID_SERIAL','emulator-5554')
if not serial.startswith('emulator-'):raise SystemExit('This destructive test is restricted to a disposable emulator')
adb=['adb','-s',serial]
if subprocess.check_output(adb+['shell','getprop','ro.kernel.qemu'],text=True).strip()!='1':raise SystemExit('Refusing a non-emulator device')
apks=list(a.directory.glob('pingora-*.apk'))
if len(apks)!=1:raise SystemExit('Expected exactly one signed release APK')
apk=apks[0]
tools=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
def badging(path):
 result=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(path)],text=True)
 return int(re.search(r"versionCode='(\d+)'",result).group(1)),re.search(r"versionName='([^']+)'",result).group(1)
code,version=badging(apk)
subprocess.run(['python3','docs/releases/tools/verify-android.py',str(apk),'--version',version,'--version-code',str(code)],check=True)
if code<=1003:raise SystemExit('Candidate must be newer than the published upgrade baseline')
def install(path):
 result=subprocess.run(adb+['install','-r',str(path)],capture_output=True,text=True)
 if result.returncode or 'Success' not in result.stdout:raise SystemExit('Android install failed: '+result.stdout+result.stderr)
subprocess.run(adb+['uninstall','io.github.wrestle_r.pingora'],capture_output=True)
try:
 install(apk)  # Actual package-manager fresh install, not just ZIP/signature parsing.
 subprocess.run(adb+['uninstall','io.github.wrestle_r.pingora'],check=True,capture_output=True)
 with tempfile.TemporaryDirectory(prefix='pingora-apk-upgrade-') as temp:
  baseline=Path(temp)/'baseline.apk'
  with urllib.request.urlopen('https://github.com/wrestle-R/Pingora/releases/download/v0.1.3/pingora-0.1.3.apk',timeout=60) as response:baseline.write_bytes(response.read())
  if hashlib.sha256(baseline.read_bytes()).hexdigest()!='6917d4f4adda293de1dcef9b4e4bfefef3e00d3fab675757f0ff8babf0da6144':raise SystemExit('Published baseline digest changed')
  install(baseline)
  install(apk)  # No uninstall and no downgrade bypass.
  installed=subprocess.check_output(adb+['shell','dumpsys','package','io.github.wrestle_r.pingora'],text=True)
  if not re.search(r'versionCode='+str(code)+r'\b',installed):raise SystemExit('Upgrade did not install the expected version code')
  install(apk)  # Reinstalling the same signed version must also work.
  rejected=subprocess.run(adb+['install','-r',str(baseline)],capture_output=True,text=True)
  if rejected.returncode==0 or 'INSTALL_FAILED_VERSION_DOWNGRADE' not in rejected.stdout+rejected.stderr:raise SystemExit('Expected Android downgrade protection was not observed')
 print('Android package-manager fresh install, signed upgrade, same-version reinstall and downgrade rejection passed')
finally:
 subprocess.run(adb+['uninstall','io.github.wrestle_r.pingora'],capture_output=True)
