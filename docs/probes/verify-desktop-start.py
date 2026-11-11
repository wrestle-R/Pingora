#!/usr/bin/env python3
"""Exercise desktop setup with no XDG user-dirs configuration on an available display."""
import argparse,os,signal,subprocess,tempfile,time
from pathlib import Path

parser=argparse.ArgumentParser();parser.add_argument('binary',type=Path);parser.add_argument('--seconds',type=int,default=6);args=parser.parse_args()
if not 3<=args.seconds<=60:raise SystemExit('Choose a duration between 3 and 60 seconds')
binary=args.binary.resolve(strict=True)
with tempfile.TemporaryDirectory(prefix='pingora-fresh-start-') as temporary:
    home=Path(temporary)
    environment={**os.environ,'HOME':str(home),'XDG_CONFIG_HOME':str(home/'config'),'XDG_DATA_HOME':str(home/'data'),'XDG_CACHE_HOME':str(home/'cache')}
    # A separate bus cannot pair/use the existing service or observe source notifications.
    with (home/'launch.log').open('wb') as log:
        process=subprocess.Popen(['dbus-run-session','--',str(binary)],env=environment,stdout=log,stderr=log,start_new_session=True)
        try:
            for _ in range(args.seconds*10):
                time.sleep(0.1)
                if process.poll() is not None:raise SystemExit(f'Fresh-profile desktop exited during setup (status {process.returncode}); no startup pass')
        finally:
            try:os.killpg(process.pid,signal.SIGTERM)
            except ProcessLookupError:pass
            try:process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                try:os.killpg(process.pid,signal.SIGKILL)
                except ProcessLookupError:pass
                process.wait()
print('Fresh-profile desktop stayed running through setup without an XDG Downloads entry; isolated test processes stopped. This is a startup smoke check, not feature acceptance.')
