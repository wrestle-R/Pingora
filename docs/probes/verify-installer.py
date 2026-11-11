from pathlib import Path
import tempfile,subprocess,shutil,json,hashlib,tarfile,base64
repo=Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory() as temporary:
 root=Path(temporary);assets=root/'assets';assets.mkdir();package=root/'package/Pingora';(package/'native').mkdir(parents=True)
 shutil.copy2('/usr/bin/true',package/'pingora-desktop');shutil.copy2('/usr/bin/true',package/'native/kdeconnectd')
 name='pingora-0.2.0-linux-x86_64.tar.gz';artifact=assets/name
 with tarfile.open(artifact,'w:gz') as tar:tar.add(package,arcname='Pingora')
 manifest={'schema':1,'version':'0.2.0','version_code':2000,'targets':{'linux-x86_64':{'name':name,'size':artifact.stat().st_size,'sha256':hashlib.sha256(artifact.read_bytes()).hexdigest()}}}
 (assets/'pingora-update.json').write_text(json.dumps(manifest))
 subprocess.run(['openssl','genpkey','-algorithm','Ed25519','-out',str(root/'key')],check=True,capture_output=True)
 public=subprocess.check_output(['openssl','pkey','-in',str(root/'key'),'-pubout','-outform','DER'])[-32:]
 signature=subprocess.check_output(['openssl','pkeyutl','-sign','-rawin','-inkey',str(root/'key'),'-in',str(assets/'pingora-update.json')])
 (assets/'pingora-update.sig').write_text(base64.b64encode(signature).decode())
 tools=root/'installer';(tools/'trust').mkdir(parents=True);(tools/'install.py').write_text((repo/'docs/releases/install.py').read_text().replace("PUBLIC_KEY='"+(repo/'docs/releases/trust/update-public-key.hex').read_text().strip()+"'","PUBLIC_KEY='"+public.hex()+"'"));(tools/'trust/update-public-key.hex').write_text(public.hex())
 home=root/'home';home.mkdir();cmd=['python3',str(tools/'install.py'),'--source',str(assets),'--home',str(home)]
 subprocess.run(cmd,check=True,capture_output=True)
 launcher=home/'.local/bin/pingora';assert launcher.is_symlink();previous=launcher.resolve().read_bytes()
 artifact.write_bytes(b'corrupt')
 r=subprocess.run(cmd,capture_output=True);assert r.returncode!=0 and launcher.resolve().read_bytes()==previous
 (assets/'pingora-update.sig').write_text(base64.b64encode(bytes(64)).decode())
 r=subprocess.run(cmd,capture_output=True);assert r.returncode!=0 and launcher.resolve().read_bytes()==previous
 print('Installer public boundary: valid signed install succeeds; corrupted artifact and invalid signature rejected; installed bytes preserved. Fixtures never executed.')
