# Implementation backlog

The user approved the original P01–P17 breakdown and six core test boundaries. Website work P18–P19 extends it for the latest request. Individual local tickets under [progress](progress/README.md) are the status authority; this document records scope/dependencies, not a second progress tracker. No work is claimed implemented. Each product slice includes Android/desktop/UI/protocol changes only as needed to demonstrate its named behavior. Research/build probes are explicitly labeled preparation.

| ID | Work | Blocked by | Demo / acceptance |
| --- | --- | --- | --- |
| P01 | Preparation: verify pinned upstream/toolchain/license candidates | None | Android and Tauri/native-service build paths and dependency requirements documented; actual file license audit; no unsupported compatibility promises |
| P02 | Preparation: validate native notification + privacy adapters | P01 | Existing sidebar identified; notification observation/replacement/actions/lock privacy demonstrated on all three desktops |
| P03 | Pair devices and see truthful connection/permission health | P01 | Phone/laptop approve pairing; unauthorized connection rejected; revoke works; missing permission has a useful fix |
| P04 | Read one correctly formatted WhatsApp conversation | P02, P03 | Phone source → desktop native notification + Pingora inbox; sender/body readable; next message replaces only same thread |
| P05 | Complete allowlists and conversation normalization | P04 | WhatsApp groups, Instagram, Gmail, SMS; desktop-selected apps → phone; summaries/Unicode/ambiguous IDs handled without loops |
| P06 | Dismiss/read/reply safely and reconnect quietly | P05 | Source-supported actions work; timeout does not dismiss; stale offline dismissal cannot clear newer message; one reconnect summary |
| P07 | Retain/search/clear bounded history and copy codes | P05 | 24h/500 cap; OTP parity; copy only supplied code; explicit history clear does not issue source dismissals |
| P08 | Pause sharing and control capabilities | P03 | Per-feature endpoint enforcement; pause preserves pairing/history; paused folder access/new sharing rejected; transfer interactions specified before merge |
| P09 | Share clipboard from shade/widget | P03, P08 | Explicit phone action sends current text; foreground fallback; paired laptop text delivered where permitted; no clipboard echo/history |
| P10 | Approve and receive a file or URL | P03, P08 | Approval every transfer; no auto-open; safe destination/collision/cancel; URL requires Open action |
| P11 | Browse explicitly shared phone folders read-only | P10 | Choose/revoke phone folder, laptop lists/downloads, write/delete denied, traversal rejected |
| P12 | See battery and ring either paired device | P03, P08 | Truthful battery/connection states; explicit ring, Stop action, timeout; mute/DND limits explained |
| P13 | Export safe diagnostics from health screen | P03 | Preview/export explicit diagnostics; no content/OTP/clipboard/path/credential leaks; errors useful for troubleshooting |
| P14 | Optional incoming Bluetooth SIM calls | P01 | Answer/decline and bidirectional laptop audio on verified setup; fallback/disconnect; LAN independent; no outgoing dialler |
| P15 | Tagged builds, signed beta assets and real installer | P01, P03 | Exact tag produces both app artifacts; stable signing identity; authenticated private download; README installer tested on actual assets |
| P16 | Download verified updates in-app and install with approval | P15 | Prior version updates; busy deferral; OS handoff; signature failures keep current version usable; private authentication proven |
| P17 | Friends beta qualification | P06, P07, P09, P10, P11, P12, P13, P14, P16, P19 | All required core behavior tested across three desktops; calls explicitly verified or unavailable; documented resource baseline; recovery/install/update runbook |
| P18 | Website marketing, docs and issue-report navigation | None | Responsive home/docs/support pages; clear unreleased/private states; GitHub-hosted bug/feature links; no internal progress exposed |
| P19 | GitHub-backed website downloads/releases/issues | P18, P15 | Only permitted metadata displayed; correct real release assets; loading/empty/rate-limit states; private data never exposed; public downloads agree with published versions |

P14 is optional in capability availability: P17 requires evaluation and an honest supported/unsupported result, not a claim that every device supports calls. P15 can start early so packaging feedback reaches feature work; do not defer all release checks until the end.

## Scope checks

No ticket adds touchpad, keyboard, slides, media control, remote commands or a full SMS client. File approval and folder read-only boundaries apply across all relevant slices. Progress is local Markdown; do not publish internal tickets to GitHub. Existing approvals do not start app implementation or authorize commits/pushes. P18 can be scoped independently of Android/native feasibility; its GitHub release integration waits for real assets.
