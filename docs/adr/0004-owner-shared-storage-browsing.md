# ADR-0004: Owner-requested read-only shared storage browsing

Date: 2026-11-12
Status: accepted by explicit owner request to browse phone folders

The owner expanded the earlier selected-folder-only scope. On Android 11+, offer the system All files access screen and expose primary shared storage only after Android grants access. Keep the existing selected-folder SAF alternative. Private app storage, Android/data and Android/obb remain unavailable; no root, privileged helper or bypass is introduced.

Browsing and downloads remain read-only. Remote callers receive opaque bounded node tokens, never local paths. Canonical containment and restricted-directory checks apply on each list/read. Revocation invalidates cached nodes and stops related outgoing transfers. Listings page at 256 entries and retain bounded node state. Files retain the existing approval, integrity and 100 MiB transfer limit.

Clipboard widget taps use a small focused send activity because Android restricts background clipboard reads. It sends automatically and closes without navigating the main app. It does not claim background clipboard access.
