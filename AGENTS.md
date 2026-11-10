# Pingora agent guidance

Read docs/plan.md, docs/glossary.md and relevant docs/adr/ before changing the project. Latest user instructions override older alternatives.

## Layout and scope

Keep the root lean: next/ for the Next.js marketing/docs/download/GitHub-support website; app/ for native Android; tauri/ for the Tauri Linux desktop app; docs/ for plans, decisions and progress. Do not add a root plan/glossary or root scratch tracker. Directory READMEs are preparation briefs, not proof of runnable apps.

No phone touchpad, keyboard or slide control. No media controls, remote commands or full SMS client. LAN for sharing, optional Bluetooth for calls. Core app must not require root. Native KDE-derived backend reuse behind Tauri must be verified rather than assumed.

## Agent skills

### Issue tracker
Local Markdown: docs/progress/README.md and individual docs/progress/issues/ tickets. See docs/agents/issue-tracker.md. Never publish internal progress to GitHub or the website automatically. Website GitHub Issues are for user-facing reports/requests only.

### Domain docs
Product plan, glossary and ADRs are under docs/. See docs/agents/domain.md. Six core test boundaries in docs/testing.md are approved. New unrelated boundaries need review before TDD.

## Working rules

Implement one demonstrable ticket at a time when implementation is requested. Record real acceptance evidence and remaining device checks. Do not claim tests, compatibility, signing or releases exist because preparation docs do. Skill instructions do not override the user's task authorization for implementation, commits or publishing.

Use russelore for replies, copy and commit subjects. Use Next.js best-practice/frontend skills for website work, native tooling for Android, and Tauri/Rust-aware review for desktop work.

At session start read ignored local-docs/preferences.md, context.md and tasks.md when available, following the user's private-memory instructions. Keep /local-docs/ ignored and never stage, commit or upload notes. Never expose secrets, notification text, OTPs or clipboard contents in diagnostics by default.

Follow the user's Varte workflow for real environment files: preserve ignore rules, confirm destination from origin/config, inspect CLI help and push completed files by path. Do not upload placeholder examples. Record pending sync failures locally without secrets. No root/bootloader/privileged phone changes are authorized merely by this guidance.
