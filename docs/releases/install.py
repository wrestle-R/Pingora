#!/usr/bin/env python3
"""Verify and install a user-owned Linux package using the recipient's gh session."""
import argparse,base64,hashlib,json,os,re,shutil,subprocess,tarfile,tempfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--tag');p.add_argument('--source',type=Path,help='Local directory of signed release assets');p.add_argument('--home',type=Path,default=Path.home());a=p.parse_args()
if os.uname().sysname!='Linux' or os.uname().machine!='x86_64':raise SystemExit('Only Linux x86_64 is supported by this installer')
repo='wrestle-R/Pingora'
with tempfile.TemporaryDirectory(prefix='pingora-install-') as temp:
 work=Path(temp)
 def obtain(name):
  if a.source:shutil.copy2(a.source/name,work/name)
  else:
   args=['gh','release','download']+([a.tag] if a.tag else [])+['--repo',repo,'--pattern',name,'--dir',str(work)]
   subprocess.run(args,check=True,stdout=subprocess.DEVNULL)
  return work/name
 metadata=obtain('pingora-update.json');signature=obtain('pingora-update.sig')
 if metadata.stat().st_size>65536 or signature.stat().st_size>256:raise SystemExit('Oversized update metadata')
 public=bytes.fromhex((Path(__file__).parent/'trust/update-public-key.hex').read_text().strip())
 if len(public)!=32:raise SystemExit('Invalid trusted public key')
 (work/'public.der').write_bytes(bytes.fromhex('302a300506032b6570032100')+public)
 (work/'signature').write_bytes(base64.b64decode(signature.read_bytes(),validate=False))
 result=subprocess.run(['openssl','pkeyutl','-verify','-pubin','-keyform','DER','-inkey',str(work/'public.der'),'-rawin','-in',str(metadata),'-sigfile',str(work/'signature')],capture_output=True)
 if result.returncode:raise SystemExit('Signature verification failed; installation unchanged')
 manifest=json.loads(metadata.read_text());version=manifest['version']
 if manifest['schema']!=1 or not re.fullmatch(r'\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?',version):raise SystemExit('Invalid signed release version')
 if a.tag and a.tag!='v'+version:raise SystemExit('Requested tag does not match signed metadata')
 target=manifest['targets']['linux-x86_64'];name=target['name']
 if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,159}\.tar\.gz',name):raise SystemExit('Invalid artifact name')
 artifact=obtain(name)
 if artifact.stat().st_size!=target['size'] or not 0<target['size']<=536870912 or hashlib.sha256(artifact.read_bytes()).hexdigest()!=target['sha256']:raise SystemExit('Artifact integrity verification failed; installation unchanged')
 home=a.home.resolve();versions=home/'.local/share/pingora/versions';versions.mkdir(parents=True,exist_ok=True)
 destination=versions/version;launcher=home/'.local/bin/pingora';launcher.parent.mkdir(parents=True,exist_ok=True)
 if launcher.is_symlink() and launcher.resolve().is_relative_to(versions.resolve()):
  old=launcher.resolve().parents[1].name
  if re.fullmatch(r'\d+\.\d+\.\d+',old) and tuple(map(int,version.split('-')[0].split('.')))<=tuple(map(int,old.split('.'))):raise SystemExit('Refusing same-version or older installation; current version retained')
 if destination.exists():raise SystemExit('This version is already installed; existing version retained')
 if launcher.exists() or launcher.is_symlink():
  if not launcher.is_symlink() or not launcher.resolve().is_relative_to(versions.resolve()):raise SystemExit('Refusing to replace an unrelated launcher')
 with tempfile.TemporaryDirectory(prefix='.staging-',dir=versions) as staging:
  staging=Path(staging)
  with tarfile.open(artifact,'r:gz') as archive:
   entries=archive.getmembers()
   if sum(e.size for e in entries)>1073741824:raise SystemExit('Archive exceeds unpacked size limit')
   for e in entries:
    path=Path(e.name)
    if path.is_absolute() or '..' in path.parts or not (e.isdir() or e.isfile()) or not path.parts or path.parts[0]!='Pingora':raise SystemExit('Unsafe archive entry')
   archive.extractall(staging,filter='data')
  binary=staging/'Pingora/pingora-desktop';native=staging/'Pingora/native/kdeconnectd'
  if not binary.is_file() or not native.is_file():raise SystemExit('Incomplete Linux package')
  binary.chmod(0o755);native.chmod(0o755)
  os.rename(staging,destination)
 temporary=launcher.with_name('.pingora-new')
 if temporary.exists() or temporary.is_symlink():raise SystemExit('An installation is already pending; existing launcher retained')
 try:temporary.symlink_to(destination/'Pingora/pingora-desktop');os.replace(temporary,launcher)
 finally:temporary.unlink(missing_ok=True)
 print(f'Installed verified Pingora {version} at {destination}. Launch with {launcher}. Previous versions retained.')
