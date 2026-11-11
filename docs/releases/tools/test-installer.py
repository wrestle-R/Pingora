#!/usr/bin/env python3
"""Exercise the real installer with disposable signing keys and isolated homes."""
import base64
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import re
import runpy
import shutil
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]


class InstallerBoundary(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='pingora-installer-test-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.key = self.root / 'fixture-key.pem'
        subprocess.run(['openssl', 'genpkey', '-algorithm', 'ED25519', '-out', str(self.key)], check=True, capture_output=True)
        public = subprocess.check_output(['openssl', 'pkey', '-in', str(self.key), '-pubout', '-outform', 'DER'])[-32:].hex()
        source = (ROOT / 'docs/releases/install.py').read_text()
        self.source = re.sub(r"PUBLIC_KEY='[a-f0-9]{64}'", "PUBLIC_KEY='" + public + "'", source)
        self.installer = self.root / 'install.py'
        self.installer.write_text(self.source)
        self.home = self.root / 'home'

    def assets(self, version, unsafe=None):
        output = self.root / ('assets-' + version)
        output.mkdir(exist_ok=True)
        package = self.root / ('package-' + version) / 'Pingora'
        (package / 'native').mkdir(parents=True, exist_ok=True)
        for target in [package / 'pingora-desktop', package / 'native/kdeconnectd']:
            shutil.copy2('/usr/bin/true', target)
        (package / 'brand').mkdir(exist_ok=True)
        shutil.copy2(ROOT / 'tauri/frontend/public/brand/pingora.svg', package / 'brand/pingora.svg')
        name = f'pingora-{version}-linux-x86_64.tar.gz'
        with tarfile.open(output / name, 'w:gz') as archive:
            archive.add(package, arcname='Pingora')
            if unsafe:
                member = tarfile.TarInfo(unsafe)
                member.size = 3
                archive.addfile(member, io.BytesIO(b'bad'))
        artifact = output / name
        metadata = output / 'pingora-update.json'
        metadata.write_text(json.dumps({'schema': 1, 'version': version, 'targets': {'linux-x86_64': {'name': name, 'size': artifact.stat().st_size, 'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest()}}}))
        signature = subprocess.check_output(['openssl', 'pkeyutl', '-sign', '-rawin', '-inkey', str(self.key), '-in', str(metadata)])
        (output / 'pingora-update.sig').write_bytes(base64.b64encode(signature) + b'\n')
        return output

    def install(self, assets, home=None):
        return subprocess.run([sys.executable, str(self.installer), '--source', str(assets), '--home', str(home or self.home)], capture_output=True, text=True)

    def test_fresh_upgrade_preserves_data_old_binary_and_logo_and_rejects_downgrade(self):
        old, new = self.assets('0.0.1'), self.assets('0.0.2')
        result = self.install(old)
        self.assertEqual(result.returncode, 0, result.stderr)
        marker = self.home / '.config/pingora/synthetic-policy'
        marker.parent.mkdir(parents=True)
        marker.write_text('keep this policy')
        previous = self.home / '.local/bin/pingora'
        previous_path = previous.resolve()
        previous_bytes = previous.read_bytes()
        result = self.install(new)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(marker.read_text(), 'keep this policy')
        self.assertEqual(previous_path.read_bytes(), previous_bytes)
        current = previous.resolve()
        self.assertIn('/0.0.2/', str(current))
        entry = (self.home / '.local/share/applications/io.github.wrestle-r.pingora.desktop').read_text()
        self.assertIn('/0.0.2/Pingora/brand/pingora.svg', entry)
        self.assertNotEqual(self.install(old).returncode, 0)
        self.assertEqual(previous.resolve(), current)

    def test_corrupt_artifact_and_wrong_signature_never_activate(self):
        for kind in ['artifact', 'signature']:
            with self.subTest(kind=kind):
                assets = self.assets('0.0.3')
                target = assets / ('pingora-0.0.3-linux-x86_64.tar.gz' if kind == 'artifact' else 'pingora-update.sig')
                target.write_bytes(target.read_bytes() + b'corrupt' if kind == 'artifact' else base64.b64encode(bytes(64)))
                home = self.root / ('home-' + kind)
                self.assertNotEqual(self.install(assets, home).returncode, 0)
                self.assertFalse((home / '.local/bin/pingora').exists())

    def test_unrelated_launcher_or_desktop_entry_is_preserved_before_activation(self):
        assets = self.assets('0.0.1')
        for relative in ['.local/bin/pingora', '.local/share/applications/io.github.wrestle-r.pingora.desktop']:
            with self.subTest(relative=relative):
                home = self.root / ('unrelated-' + str(len(relative)))
                path = home / relative
                path.parent.mkdir(parents=True)
                path.write_text('unrelated file')
                self.assertNotEqual(self.install(assets, home).returncode, 0)
                self.assertEqual(path.read_text(), 'unrelated file')
                self.assertFalse((home / '.local/share/pingora/versions/0.0.1').exists())

    def test_signed_archive_traversal_is_rejected(self):
        assets = self.assets('0.0.1', '../outside')
        self.assertNotEqual(self.install(assets).returncode, 0)
        self.assertFalse((self.root / 'outside').exists())
        self.assertFalse((self.home / '.local/bin/pingora').exists())

    def test_latest_is_resolved_once_then_every_download_uses_the_same_tag(self):
        assets = self.assets('0.0.1')
        urls = []
        def response(request, **_):
            url = request.full_url
            urls.append(url)
            if url.endswith('/releases/latest'):
                data = json.dumps({'tag_name': 'v0.0.1'}).encode()
            else:
                self.assertIn('/download/v0.0.1/', url)
                data = (assets / url.rsplit('/', 1)[-1]).read_bytes()
            result = io.BytesIO(data)
            result.url = url
            return result
        with patch.object(sys, 'argv', ['install.py', '--home', str(self.home)]), patch('urllib.request.urlopen', response), contextlib.redirect_stdout(io.StringIO()):
            runpy.run_path(str(self.installer), run_name='__main__')
        self.assertEqual(sum(url.endswith('/releases/latest') for url in urls), 1)
        self.assertTrue((self.home / '.local/bin/pingora').is_symlink())

    @unittest.skipIf(os.geteuid() == 0, 'Bash bootstrap correctly refuses root')
    def test_generated_bash_bootstrap_runs_the_embedded_installer(self):
        assets = self.assets('0.0.1')
        bootstrap = self.root / 'install-app.sh'
        subprocess.run([sys.executable, str(ROOT / 'docs/releases/tools/build-installer.py'), '--output', str(bootstrap)], check=True)
        # Only the disposable copy trusts the disposable test key.
        original = (ROOT / 'docs/releases/install.py').read_text()
        bootstrap.write_text(bootstrap.read_text().replace(original, self.source))
        subprocess.run(['bash', '-n', str(bootstrap)], check=True)
        tools = self.root / 'bin'
        tools.mkdir()
        pacman = tools / 'pacman'
        pacman.write_text('#!/bin/sh\nexit 0\n')
        pacman.chmod(0o755)
        env = dict(os.environ, PATH=str(tools) + os.pathsep + os.environ['PATH'])
        result = subprocess.run(['bash', str(bootstrap), '--source', str(assets), '--home', str(self.home)], env=env, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue((self.home / '.local/bin/pingora').is_symlink())


if __name__ == '__main__':
    unittest.main(verbosity=2)
