#!/usr/bin/env python3
"""Prepare a pinned, isolated KDE-derived LAN service build probe.

No install, daemon launch, keys, pairing or system configuration changes.
Generated source/build live under tauri/target, never in an installed KDE tree.
"""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile

root = Path(__file__).resolve().parents[2]
manifest = json.loads((Path(__file__).with_name('source.json')).read_text())
archive = Path(sys.argv[1])
if hashlib.sha256(archive.read_bytes()).hexdigest() != manifest['archive_sha256']:
    raise SystemExit('Pinned source archive checksum mismatch')
work = root / 'tauri/target/native'
source = work / ('kdeconnect-kde-' + manifest['revision'])
if source.exists():
    raise SystemExit('Prepared source already exists; reuse its build or choose a clean probe directory deliberately.')
work.mkdir(parents=True, exist_ok=True)
with tarfile.open(archive) as tar:
    tar.extractall(work, filter='data')

def replace(file, old, new):
    path = source / file
    text = path.read_text()
    if old not in text:
        raise SystemExit(f'Pinned patch context missing in {file}')
    path.write_text(text.replace(old, new))

replace('core/kdeconnectconfig.cpp',
        'absoluteFilePath(QStringLiteral("kdeconnect"))',
        'absoluteFilePath(QStringLiteral("pingora"))')
for path in source.rglob('*'):
    if path.is_file() and path.suffix in {'.cpp', '.h'}:
        text = path.read_text()
        if '"org.kde.kdeconnect"' in text:
            path.write_text(text.replace('"org.kde.kdeconnect"', '"io.github.wrestle_r.pingora"'))
replace('daemon/kdeconnectd.cpp', 'QStringLiteral("kdeconnect.daemon")', 'QStringLiteral("pingora.daemon")')
replace('daemon/kdeconnectd.cpp', 'i18n("KDE Connect Daemon")', 'i18n("Pingora service (development)")')
# Never enumerate or instantiate system-installed KDE Connect plugins.
replace('core/pluginloader.cpp',
    '''    const QVector<KPluginMetaData> data = KPluginMetaData::findPlugins(QStringLiteral("kdeconnect"));
    for (const KPluginMetaData &metadata : data) {
        plugins[metadata.pluginId()] = metadata;
    }''',
    '    // Pingora build probe: no device plugins are registered or advertised.')
p = source / 'CMakeLists.txt'
s = p.read_text()
start = s.index('add_subdirectory(core)')
end = s.index('ki18n_install(po)')
s = s[:start] + 'add_subdirectory(core)\nadd_subdirectory(dbusinterfaces)\nadd_subdirectory(daemon)\n\n' + s[end:]
p.write_text(s)
from bridge import apply as apply_bridge
apply_bridge(source)
build = work / 'build'
subprocess.run(['cmake', '-S', str(source), '-B', str(build), '-G', 'Ninja',
                '-DBLUETOOTH_ENABLED=OFF', '-DBUILD_TESTING=OFF',
                '-DWITH_X11=OFF', '-DWITH_PULSEAUDIO=OFF',
                '-DCMAKE_INSTALL_PREFIX=' + str(work / 'uninstalled')], check=True)
subprocess.run(['cmake', '--build', str(build), '--target', 'kdeconnectd', '--parallel', '4'], check=True)
print('Built an uninstalled probe:', build / 'bin/kdeconnectd')
print('No daemon launched; no system files or existing KDE Connect configuration changed.')
