#!/usr/bin/env python3
"""Exercise real signed packages through install, GUI startup and preserved app state."""
from pathlib import Path
import argparse,json,os,signal,subprocess,tempfile,time

parser=argparse.ArgumentParser();parser.add_argument("old_assets",type=Path);parser.add_argument("new_assets",type=Path);args=parser.parse_args()
repo=Path(__file__).resolve().parents[2]

def launch(binary, home):
 env={**os.environ,'HOME':str(home),'XDG_CONFIG_HOME':str(home/'config'),'XDG_DATA_HOME':str(home/'data'),'XDG_CACHE_HOME':str(home/'cache')}
 with (home/'launch.log').open('wb') as log:
  process=subprocess.Popen(['dbus-run-session','--',str(binary)],env=env,stdout=log,stderr=log,start_new_session=True)
  try:
   time.sleep(4)
   assert process.poll() is None,'Installed desktop exited during upgrade check'
  finally:
   try:os.killpg(process.pid,signal.SIGTERM)
   except ProcessLookupError:pass
   process.wait(timeout=5)

with tempfile.TemporaryDirectory(prefix='pingora-real-upgrade-') as temporary:
 home=Path(temporary)
 cmd=['python3',str(repo/'docs/releases/install.py'),'--home',str(home),'--source']
 subprocess.run(cmd+[str(args.old_assets)],check=True,stdout=subprocess.DEVNULL)
 launcher=home/'.local/bin/pingora';old=launcher.resolve()
 state=home/'data/io.github.wrestle-r.pingora/state.json';state.parent.mkdir(parents=True)
 state.write_text(json.dumps({'policies':{'qualification-fixture':{'paused':True,'show_locked':False,'enabled':{'clipboard':False},'desktop_apps':[]}},'history':[]}))
 launch(launcher,home);before=json.loads(state.read_text())
 assert before['policies']['qualification-fixture']['paused'] is True
 assert before['policies']['qualification-fixture']['enabled']['clipboard'] is False
 subprocess.run(cmd+[str(args.new_assets)],check=True,stdout=subprocess.DEVNULL)
 new=launcher.resolve();assert old!=new and old.is_file() and '/'+json.loads((args.new_assets/'pingora-update.json').read_text())['version']+'/' in str(new)
 assert json.loads(state.read_text())==before
 launch(launcher,home);assert json.loads(state.read_text())==before
 refused=subprocess.run(cmd+[str(args.old_assets)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 assert refused.returncode!=0 and launcher.resolve()==new and json.loads(state.read_text())==before
 print('Actual signed Linux upgrade passed: both installed GUIs start, saved paused/disabled policy survives, previous executable retained, downgrade rejected. Isolated test home removed. No Android OS upgrade claim.')
