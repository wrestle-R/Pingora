#!/usr/bin/env python3
"""Add a narrow IPC lid setter to the existing owner Slumber; dry-run by default.
Does not restart QuickShell, change preferences, lock/unlock or install root services.
"""
import argparse,hashlib,shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--file',type=Path,default=Path.home()/'.config/quickshell/ii/services/Slumber.qml');p.add_argument('--apply',action='store_true');a=p.parse_args()
s=a.file.read_text();anchor='        function activate(): void { root.enter(); }'
addition='''        function setLidAction(value: string): void {
            if (value === "sleep" || value === "slumber") root.setLidAction(value);
        }'''
if addition in s:print('Narrow Slumber IPC adapter already present');raise SystemExit(0)
if s.count(anchor)!=1 or 'function setLidAction(value)' not in s:raise SystemExit('Unrecognized Slumber source; no changes made')
if not a.apply:print('Compatible Slumber source. --apply adds only a validated sleep/slumber IPC setter; no restart or preference change.');raise SystemExit(0)
backup=a.file.with_name('Slumber.qml.pingora-'+hashlib.sha256(s.encode()).hexdigest()[:12]+'.backup')
if not backup.exists():shutil.copy2(a.file,backup)
a.file.write_text(s.replace(anchor,anchor+'\n'+addition));print('Narrow adapter written; backup retained. Activate it during a normal shell reload when safe.')
