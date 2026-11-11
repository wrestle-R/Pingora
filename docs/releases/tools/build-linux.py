#!/usr/bin/env python3
"""Build a host-dependent portable development package, not a universal AppImage."""
import argparse,json,os,shutil,subprocess,tarfile,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[3]
p=argparse.ArgumentParser();p.add_argument('--version',required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
import re
if not re.fullmatch(r'\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?',a.version):raise SystemExit('Invalid version')
if os.uname().machine!='x86_64':raise SystemExit('Only x86_64 build path is qualified for this package')
build=root/'tauri/target/native/build'
if not (build/'CMakeCache.txt').exists():raise SystemExit('Prepare native source first')
subprocess.run(['cmake','-S',str(build.parent/('kdeconnect-kde-'+json.loads((root/'tauri/native/source.json').read_text())['revision'])),'-B',str(build),'-DKDE_SKIP_RPATH_SETTINGS=ON','-DCMAKE_INSTALL_RPATH_USE_LINK_PATH=OFF','-DCMAKE_BUILD_WITH_INSTALL_RPATH=ON','-DCMAKE_INSTALL_RPATH=$ORIGIN/lib;$ORIGIN'],check=True)
subprocess.run(['cmake','--build',str(build),'--target','kdeconnectd','--parallel','3'],check=True)
subprocess.run(['cargo','build','--release','--locked','--manifest-path',str(root/'tauri/src-tauri/Cargo.toml')],check=True)
native=root/'tauri/target/native/build/bin/kdeconnectd'
if not native.is_file():raise SystemExit('Prepare the pinned native service first (tauri/native/README.md)')
a.output.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(prefix='pingora-package-') as temp:
 d=Path(temp)/'Pingora';(d/'native').mkdir(parents=True)
 shutil.copy2(root/'tauri/src-tauri/target/release/pingora-desktop',d/'pingora-desktop');shutil.copy2(native,d/'native/kdeconnectd')
 (d/'native/lib').mkdir()
 for folder in ['bin','lib']:
  for library in (build/folder).glob('*.so*'):shutil.copy2(library,d/'native/lib'/library.name)
 (d/'updates/trust').mkdir(parents=True)
 shutil.copy2(root/'docs/releases/install.py',d/'updates/install.py')
 shutil.copy2(root/'docs/releases/trust/update-public-key.hex',d/'updates/trust/update-public-key.hex')
 shutil.copy2(root/'tauri/COPYING',d/'COPYING');shutil.copy2(root/'tauri/native/source.json',d/'native-source.json')
 (d/'README.txt').write_text('Pingora Linux x86_64 development package. Requires compatible GTK3, WebKitGTK4.1, Qt6 and KDE Frameworks6 host libraries. Built-host compatibility only; not a universal AppImage. Native source provenance in native-source.json; corresponding source supplied separately.\n')
 with tarfile.open(a.output/f'pingora-{a.version}-linux-x86_64.tar.gz','w:gz') as archive:archive.add(d,arcname='Pingora')
subprocess.run(['python3',str(root/'docs/releases/tools/verify-linux.py'),str(a.output/f'pingora-{a.version}-linux-x86_64.tar.gz')],check=True)
print('Linux package built with native service. Verify host dependencies and runtime before release.')
