#!/usr/bin/env python3
"""Inventory pinned source file notices; this is evidence, not license clearance.

Run after extracting the pinned tarballs from docs/upstream-candidates.json:
python3 docs/probes/audit-upstream.py /tmp/pingora-upstream > report.json
"""
import collections
import hashlib
import json
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[2]
candidates = json.loads((root / 'docs/upstream-candidates.json').read_text())['sources']
report = {'scope': 'Pinned code/build source headers only; no dependency resolution or distribution clearance.', 'sources': []}
for source in candidates:
    directory = Path(sys.argv[1]) / (source['repository'].split('/')[1] + '-' + source['revision'])
    counts = collections.Counter()
    files = []
    for path in sorted(directory.rglob('*')):
        if not path.is_file() or path.suffix not in {'.kt', '.java', '.cpp', '.h', '.qml', '.py', '.cmake', '.kts'} and path.name != 'CMakeLists.txt':
            continue
        data = path.read_bytes()
        text = data.decode('utf-8', errors='replace')
        match = re.search(r'SPDX-License-Identifier:\s*([^\r\n]+)', text)
        spdx = match[1].strip().rstrip('*/ ').strip() if match else None
        legacy = bool(re.search(r'GNU (?:General|Lesser)|GPL|LGPL|Apache License|Permission is hereby granted', text[:8000]))
        classification = spdx or ('legacy notice: manual review' if legacy else 'no detected header: manual review')
        counts[classification] += 1
        files.append({'path': str(path.relative_to(directory)), 'sha256': hashlib.sha256(data).hexdigest(), 'notice': classification})
    if not files:
        raise SystemExit(f'No code found in {directory}')
    report['sources'].append({**source, 'code_file_count': len(files), 'notice_counts': dict(counts), 'files': files})
print(json.dumps(report, indent=2))
