# ADR-0002: Latest-conversation notifications with bounded history

Status: accepted product contract.

Existing native sidebar is primary; Pingora inbox also stores retained text. One latest-message presentation per known conversation; do not merge unrelated conversations when identity is uncertain. Three-line previews are a presentation goal; native service capabilities vary.

Ordinary messages and OTP notifications share retention: 24h or 500 entries. No special OTP expiry. Source redaction is accepted. Friends hide locked previews by default; explicit opt-in allows content. Owner laptop can opt in.

Explicit dismissals synchronize, popup timeouts do not. Offline actions apply only to unchanged source versions. Read is a separate supported source action. Reconnect is quiet with one summary. Clear history only deletes saved local history; dismissing source notifications requires its own explicit action.
