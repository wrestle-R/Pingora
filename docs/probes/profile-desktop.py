#!/usr/bin/env python3
"""Short, isolated desktop baseline. Never observes the user's notification bus."""
import argparse,json,os,signal,subprocess,tempfile,time
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('binary',type=Path);p.add_argument('--seconds',type=int,default=60);args=p.parse_args()
if not 10<=args.seconds<=600:raise SystemExit('Choose 10 to 600 seconds')
binary=args.binary.resolve(strict=True)
clock=os.sysconf('SC_CLK_TCK')
def processes(root):
    rows={}
    for path in Path('/proc').glob('[0-9]*/stat'):
        try:
            fields=path.read_text().rsplit(')',1)[1].split()
            rows[int(path.parent.name)]=(int(fields[1]),int(fields[11])+int(fields[12]),int(fields[21])*os.sysconf('SC_PAGE_SIZE'))
        except (OSError,ValueError,IndexError):pass
    selected={root}
    while True:
        found={pid for pid,(parent,_,_) in rows.items() if parent in selected}
        if found<=selected:break
        selected|=found
    return {pid:rows[pid] for pid in selected if pid in rows}
with tempfile.TemporaryDirectory(prefix='pingora-idle-check-') as temporary:
    home=Path(temporary);environment={**os.environ,'HOME':str(home),'XDG_CONFIG_HOME':str(home/'config'),'XDG_DATA_HOME':str(home/'data'),'XDG_CACHE_HOME':str(home/'cache')}
    with (home/'startup.log').open('wb') as log:
        child=subprocess.Popen(['dbus-run-session','--',str(binary)],env=environment,stdout=log,stderr=log,start_new_session=True)
        try:
            time.sleep(5)
            if child.poll() is not None:raise SystemExit('Desktop exited during warmup')
            initial=processes(child.pid);seen={pid:row[1] for pid,row in initial.items()};start=time.monotonic();peak=0;count=0
            while time.monotonic()-start<args.seconds:
                if child.poll() is not None:raise SystemExit('Desktop exited during sampling')
                current=processes(child.pid);peak=max(peak,sum(r[2] for r in current.values()));count=max(count,len(current))
                for pid,row in current.items():seen[pid]=max(seen.get(pid,0),row[1])
                time.sleep(.5)
            elapsed=time.monotonic()-start
            ticks=sum(value-initial.get(pid,(0,0,0))[1] for pid,value in seen.items())
        finally:
            try:os.killpg(child.pid,signal.SIGTERM)
            except ProcessLookupError:pass
            try:child.wait(timeout=5)
            except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait()
print(json.dumps({'sample_seconds':round(elapsed,1),'warmup_seconds':5,'peak_summed_rss_mib':round(peak/1024/1024,1),'cpu_percent_one_core':round(ticks/clock/elapsed*100,2),'maximum_process_count':count,'paired_load':False,'shared_rss_pages_counted_more_than_once':True,'scope':'isolated unpaired startup/idle baseline; not long-run qualification'},indent=2))
