#!/usr/bin/env python3
"""Build the self-contained Bash entrypoint from the verified Python installer."""
from pathlib import Path
import argparse
p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[3]
bootstrap='''#!/usr/bin/env bash
set -euo pipefail
if [[ $(uname -s) != Linux || $(uname -m) != x86_64 ]]; then
  echo 'Pingora currently supports Linux x86_64.' >&2; exit 1
fi
if [[ $EUID == 0 ]]; then
  echo 'Run this installer as your normal desktop user, not with sudo.' >&2; exit 1
fi
if command -v pacman >/dev/null 2>&1; then
  packages=(python openssl gtk3 webkit2gtk-4.1 qt6-base qt6-declarative qt6-wayland qt6-multimedia kstatusnotifieritem kcoreaddons kconfig kdbusaddons ki18n kiconthemes knotifications kio kcolorscheme kservice kirigami kpeople kwindowsystem kguiaddons kcrash kitemmodels kwidgetsaddons kpackage solid kirigami-addons qqc2-desktop-style dbus wl-clipboard)
  missing=()
  for package in "${packages[@]}"; do
    pacman -Q "$package" >/dev/null 2>&1 || missing+=("$package")
  done
  if ((${#missing[@]})); then
    echo 'Installing required runtime packages. Your package manager may ask for approval.'
    command -v sudo >/dev/null || { echo 'Install sudo or have an administrator install the listed runtime packages.' >&2; exit 1; }
    sudo pacman -S --needed "${missing[@]}" </dev/tty
  fi
else
  echo 'Automatic dependency setup is currently supported on Arch-based Linux.'
  echo 'Other hosts must already provide compatible GTK3, WebKitGTK4.1, Qt6 and KDE Frameworks6 libraries.'
fi
command -v python3 >/dev/null || { echo 'Python 3.12+ is required.' >&2; exit 1; }
command -v openssl >/dev/null || { echo 'OpenSSL is required.' >&2; exit 1; }
python3 - "$@" <<'PINGORA_PYTHON_INSTALLER'
'''
a.output.parent.mkdir(parents=True,exist_ok=True)
a.output.write_text(bootstrap+(root/'docs/releases/install.py').read_text()+"\nPINGORA_PYTHON_INSTALLER\n")
a.output.chmod(0o755)
