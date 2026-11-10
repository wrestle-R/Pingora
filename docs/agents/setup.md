# Engineering skill setup

Installed from mattpocock/skills revision 4ae2317ddb22b046e26455e4f9c9b3aaaf62212d: setup-matt-pocock-skills, to-tickets, tdd, implement, code-review, diagnosing-bugs. Available in the current session.

User clarified setup: local Markdown progress tracking, clean root with next/ + app/ + tauri/ + docs/. Original 17-item breakdown and six core test boundaries approved. Website extension is recorded as P18–P19.

- Live agent guidance: root AGENTS.md.
- Tracker configuration: issue-tracker.md; one local file per ticket under docs/progress/. No GitHub tracking issues or labels created.
- Domain guidance: domain.md; plan/glossary/ADRs under docs/ for a clean root.
- Test policy: docs/testing.md. Six core boundaries approved; do not ask again. The new website data boundary remains a proposal before TDD on that extension.

User's explicit layout overrides setup skill defaults for root glossary and .scratch/ tickets. No triage skill installed.

## Workflow side effects

Implement calls TDD/review and includes committing, so apply only within the user's actual task authorization. Local ticket readiness alone does not authorize app implementation or Git operations. Code review requires a meaningful comparison point. Diagnosing bugs requires a reproducible symptom before fixing. No app tests, build workflow or signing secrets were generated in preparation.
