# Issue tracker: local Markdown

Confirmed by the user: progress tracking is local Markdown.

- Index: docs/progress/README.md.
- Individual issues: docs/progress/issues/pNN-slug.md, one file per ticket.
- **Status:** ready-for-agent, in-progress, blocked, done.
- **Blocked by:** stable PNN references. Read their individual files before claiming a dependency is complete.
- Append discussion under Comments and evidence under Verification.
- PLAN source: docs/plan.md; backlog scope: docs/backlog.md. Individual tickets own status.

When skills say fetch a ticket, read the local file. When they say publish approved tickets, create/update one local file per approved work item. Do not call GitHub issue creation or modify remote issues to track implementation. No triage skill/labels are configured.

The website may use GitHub Issues for bug reports/feature requests and Releases for downloads. That is separate from this local implementation tracker. Public site builds must not automatically publish progress files or private memory.

User-requested clean root overrides the skill's default .scratch/ location. Tickets live under docs/progress/ deliberately.
