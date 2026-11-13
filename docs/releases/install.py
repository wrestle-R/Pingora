#!/usr/bin/env python3
"""Verify and install a user-owned Linux package from signed public release assets."""
import argparse,base64,hashlib,json,os,re,shutil,subprocess,tarfile,tempfile,urllib.request,urllib.parse
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--tag');p.add_argument('--github-auth',action='store_true',help='Use gh for an authorized private repository');p.add_argument('--source',type=Path,help='Local directory of signed release assets');p.add_argument('--home',type=Path,default=Path.home());a=p.parse_args()
if __import__('sys').version_info<(3,12):raise SystemExit('Python 3.12+ is required')
if os.uname().sysname!='Linux' or os.uname().machine!='x86_64':raise SystemExit('Only Linux x86_64 is supported by this installer')
repo='wrestle-R/Pingora'
PUBLIC_KEY='20131ae1e757a3fb4cb029a7ad54f969f5db77e269e83e0545d7e7c4ad65535f'
if a.tag and not re.fullmatch(r'v\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?',a.tag):raise SystemExit('Invalid release tag')
# Resolve latest once, so metadata, signature and artifacts cannot cross releases.
if not a.tag and not a.source:
 if a.github_auth:
  a.tag=subprocess.check_output(['gh','release','view','--repo',repo,'--json','tagName','--jq','.tagName'],text=True).strip()
 else:
  request=urllib.request.Request(f'https://api.github.com/repos/{repo}/releases/latest',headers={'User-Agent':'Pingora-installer','Accept':'application/vnd.github+json'})
  with urllib.request.urlopen(request,timeout=30) as response:
   release=response.read(262145)
  if len(release)>262144:raise SystemExit('Oversized release response')
  a.tag=json.loads(release)['tag_name']
 if not isinstance(a.tag,str) or not re.fullmatch(r'v\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?',a.tag):raise SystemExit('Invalid published release tag')

with tempfile.TemporaryDirectory(prefix='pingora-install-') as temp:
 work=Path(temp)
 def obtain(name):
  if a.source:shutil.copy2(a.source/name,work/name)
  elif a.github_auth:
   args=['gh','release','download']+([a.tag] if a.tag else [])+['--repo',repo,'--pattern',name,'--dir',str(work)]
   subprocess.run(args,check=True,stdout=subprocess.DEVNULL)
  else:
   base=f'https://github.com/{repo}/releases/' + (f'download/{a.tag}/' if a.tag else 'latest/download/')
   request=urllib.request.Request(base+urllib.parse.quote(name,safe=''),headers={'User-Agent':'Pingora-installer'})
   limit=65536 if name.endswith('.json') else 256 if name.endswith('.sig') else 536870912
   with urllib.request.urlopen(request,timeout=60) as response,(work/name).open('wb') as output:
    if urllib.parse.urlparse(response.url).scheme!='https':raise SystemExit('Refusing an insecure download redirect')
    size=0
    while chunk:=response.read(1024*1024):
     size+=len(chunk)
     if size>limit:raise SystemExit('Release download exceeds its size limit')
     output.write(chunk)
  return work/name
 metadata=obtain('pingora-update.json');signature=obtain('pingora-update.sig')
 if metadata.stat().st_size>65536 or signature.stat().st_size>256:raise SystemExit('Oversized update metadata')
 public=bytes.fromhex(PUBLIC_KEY)
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
 with artifact.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
 if artifact.stat().st_size!=target['size'] or not 0<target['size']<=536870912 or digest!=target['sha256']:raise SystemExit('Artifact integrity verification failed; installation unchanged')
 home=a.home.resolve();versions=home/'.local/share/pingora/versions';versions.mkdir(parents=True,exist_ok=True)
 destination=versions/version;launcher=home/'.local/bin/pingora';launcher.parent.mkdir(parents=True,exist_ok=True)
 if launcher.is_symlink() and launcher.resolve().is_relative_to(versions.resolve()):
  old=launcher.resolve().parents[1].name
  if re.fullmatch(r'\d+\.\d+\.\d+',old) and tuple(map(int,version.split('-')[0].split('.')))<=tuple(map(int,old.split('.'))):raise SystemExit('Refusing same-version or older installation; current version retained')
 if destination.exists():raise SystemExit('This version is already installed; existing version retained')
 if launcher.exists() or launcher.is_symlink():
  if not launcher.is_symlink() or not launcher.resolve().is_relative_to(versions.resolve()):raise SystemExit('Refusing to replace an unrelated launcher')
 applications=home/'.local/share/applications'
 entry=applications/'io.github.wrestle-r.pingora.desktop'
 if entry.exists() and 'X-Pingora-Installer=true' not in entry.read_text():raise SystemExit('Refusing to replace an unrelated desktop entry; installation unchanged')
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
  for executable in [binary,native]:
   result=subprocess.run(['ldd',str(executable)],capture_output=True,text=True)
   if result.returncode or 'not found' in result.stdout:raise SystemExit('Required host libraries are missing or incompatible; existing installation unchanged')
  os.rename(staging,destination)
 temporary=launcher.with_name('.pingora-new')
 if temporary.exists() or temporary.is_symlink():raise SystemExit('An installation is already pending; existing launcher retained')
 try:temporary.symlink_to(destination/'Pingora/pingora-desktop');os.replace(temporary,launcher)
 finally:temporary.unlink(missing_ok=True)
 print(f'Installed verified Pingora {version} at {destination}. Launch with {launcher}. Previous versions retained.')

applications=home/'.local/share/applications';applications.mkdir(parents=True,exist_ok=True)
entry=applications/'io.github.wrestle-r.pingora.desktop'
if entry.exists() and 'X-Pingora-Installer=true' not in entry.read_text():raise SystemExit('Installed app, but preserved an existing unrelated desktop entry')
quoted=str(launcher).replace('\\','\\\\').replace('"','\\"').replace('$','\\$').replace('`','\\`')
icon=destination/'Pingora/brand/pingora.svg'
icon_name=str(icon) if icon.is_file() else 'network-wireless'
entry.write_text('[Desktop Entry]\nType=Application\nName=Pingora\nComment=Connect your Android phone and Linux laptop\nExec="'+quoted+'"\nIcon='+icon_name+'\nTerminal=false\nCategories=Network;Utility;\nX-Pingora-Installer=true\n')
print('Desktop menu entry installed. Open Pingora from your application menu.')

# Keep connectivity independent of the installer terminal and the visible window.
# Isolated --home checks write configuration but never touch the owner's service manager.
unit_dir=home/'.config/systemd/user';unit_dir.mkdir(parents=True,exist_ok=True)
unit=unit_dir/'pingora.service'
unit_text='''# Pingora managed background service
[Unit]
Description=Pingora phone connection
After=graphical-session.target
PartOf=graphical-session.target

[Service]
Type=simple
ExecStart="%h/.local/bin/pingora" --background
Restart=on-failure
RestartSec=5

[Install]
WantedBy=default.target
'''
if unit.exists() and not unit.read_text().startswith('# Pingora managed background service'):
 print('Existing user service retained. Enable background startup manually if desired.')
else:
 unit.write_text(unit_text)
 if home==Path.home().resolve() and shutil.which('systemctl'):
  result=subprocess.run(['systemctl','--user','daemon-reload'],capture_output=True)
  if result.returncode==0:
   result=subprocess.run(['systemctl','--user','enable','--now','pingora.service'],capture_output=True)
  print('Background service enabled.' if result.returncode==0 else 'Background service could not start; open Pingora after logging into your desktop.')
