# ADR-0001: Native KDE-derived companion scope

Status: accepted product direction; technical reuse subject to build/license verification.

Android + Linux, one phone/laptop pair, supporting Hyprland/Plasma/GNOME. Native KDE Android ecosystem; use a Tauri desktop shell and evaluate upstream desktop services/plugins behind native adapters and optional Foner Bluetooth SIM calling. LAN handles all non-call sharing. Core features do not require root. Reuse upstream security-sensitive plumbing where feasible; pin and audit sources before importing them.

User removed touchpad, keyboard and slide control in the latest correction. Do not re-add them as dependencies or launch requirements. Media controls, arbitrary remote commands, full SMS conversations and other operating systems remain excluded.

Approved: health page, pause sharing, feature toggles, sanitized diagnostics, inbox search and clear history. Exact unchosen implementation policies stay marked proposed.

Latest layout decision: next/ for the marketing/docs/download/issues website, app/ for Android, tauri/ for desktop. Local Markdown under docs/progress/ tracks work; GitHub integration on the website is not the progress tracker.
