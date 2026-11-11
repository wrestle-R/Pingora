#!/usr/bin/env python3
"""Check the actual portable artifact on its build host, without a GUI/service launch."""
import argparse,json,os,subprocess,tarfile,tempfile
from pathlib import Path

parser=argparse.ArgumentParser();parser.add_argument('artifact',type=Path);args=parser.parse_args()
with tempfile.TemporaryDirectory(prefix='pingora-package-check-') as temporary:
    directory=Path(temporary)
    with tarfile.open(args.artifact,'r:gz') as archive:
        for entry in archive.getmembers():
            path=Path(entry.name)
            if path.is_absolute() or '..' in path.parts or not path.parts or path.parts[0]!='Pingora' or not(entry.isdir() or entry.isfile()):
                raise SystemExit('Unsafe package entry')
        archive.extractall(directory,filter='data')
    package=directory/'Pingora'
    required=['pingora-desktop','native/kdeconnectd','COPYING','native-source.json','updates/install.py','updates/trust/update-public-key.hex']
    if not all((package/name).is_file() for name in required):raise SystemExit('Incomplete portable package')
    trust=Path(__file__).resolve().parents[1]/'trust/update-public-key.hex'
    if (package/'updates/trust/update-public-key.hex').read_bytes()!=trust.read_bytes():raise SystemExit('Bundled installer trust anchor differs from this source')
    environment=os.environ.copy();environment.pop('LD_LIBRARY_PATH',None);environment.pop('LD_PRELOAD',None)
    for binary in ['pingora-desktop','native/kdeconnectd']:
        result=subprocess.run(['ldd',str(package/binary)],env=environment,capture_output=True,text=True,check=True)
        if 'not found' in result.stdout:raise SystemExit('Packaged executable has missing host libraries')
        if binary.startswith('native/'):
            own=[line for line in result.stdout.splitlines() if 'libkdeconnectcore.so' in line]
            if not own or not all(str(package/'native/lib')+'/' in line for line in own):raise SystemExit('Native service did not resolve its bundled core library')
    home=directory/'home';home.mkdir();environment.update(HOME=str(home),XDG_CONFIG_HOME=str(home/'config'),XDG_DATA_HOME=str(home/'data'),XDG_CACHE_HOME=str(home/'cache'))
    result=subprocess.run([str(package/'pingora-desktop'),'--probe-health'],env=environment,capture_output=True,text=True,check=True,timeout=15)
    health=json.loads(result.stdout)
    if not isinstance(health.get('paired'),bool) or not isinstance(health.get('native_service'),bool) or not isinstance(health.get('slumber'),dict):raise SystemExit('Installed health probe returned an invalid schema')
print('Portable artifact passed: required resources, matching public trust, host dependencies, bundled native-library resolution and headless health probe. No GUI or native service launched.')
