import type { Metadata } from "next";
import Link from "next/link";
export const metadata: Metadata = { title: "Docs" };
const topics = [
  [
    "getting-started",
    "Start here",
    "Pingora is in development. There is no installable Android or Linux release yet. These notes describe the intended behaviour, not verified setup instructions. Don’t run third-party installers claiming to be Pingora.",
  ],
  [
    "pairing",
    "Pairing & permissions",
    "The plan is one Android phone and one Linux laptop first, with sharing over the same local network. Pairing and per-feature permissions will be documented against real builds. Notification access and selected apps will be explicit choices.",
  ],
  [
    "notifications",
    "Notifications & inbox",
    "Readable previews should preserve the newest message per conversation, with the full supplied notification text in the inbox. Reply and mark-as-read depend on the actions the source app exposes. Dismissal is separate from reading. History is bounded to 24 hours or 500 entries; clearing saved history does not dismiss source notifications. Android may withhold sensitive content, including OTPs.",
  ],
  [
    "clipboard",
    "Clipboard",
    "Automatic text sharing where the OS permits it, with a manual shade or foreground action and an Android widget as fallbacks. Android restricts background clipboard access. There is no permanent clipboard history planned.",
  ],
  [
    "sharing",
    "Files, links & folders",
    "Every incoming transfer asks for approval. Received links need an explicit Open action. Phone folder browsing is limited to folders you select and is read-only. Battery status and ring-device controls are also planned; ringing will have Stop and a timeout.",
  ],
  [
    "calls",
    "Optional calls",
    "Bluetooth is optional and reserved for incoming SIM calls. Answer, decline and both audio directions need verification on actual setups before they can be offered as supported. LAN sharing does not imply LAN cellular-call audio.",
  ],
  [
    "privacy",
    "Privacy & controls",
    "The core app must work without root. Per-feature controls, Pause sharing and safe diagnostic exports are planned. Message text, OTPs and clipboard contents must stay out of diagnostics by default. Friends’ locked-device content is hidden by default, with an explicit opt-in.",
  ],
  [
    "scope",
    "Scope & Slumber",
    "The initial targets are Android and Linux (Hyprland, Plasma and GNOME), with compatibility still to verify. Phone touchpad, keyboard, slide control, media controls, remote commands and a full SMS client are outside the core scope. Phone control for the owner’s native Slumber (Arch/Hyprland with QuickShell) is a separate requested integration. Its local status and activation interface has been identified; phone controls are not implemented or included in a release.",
  ],
  [
    "troubleshooting",
    "Troubleshooting & updates",
    "Once builds exist, check connection and permission health before exporting diagnostics. Reconnecting should produce a quiet summary rather than replaying every popup. Signed update instructions and package ownership rules will accompany actual releases; there is no update command to run today.",
  ],
];
export default function Docs() {
  return (
    <div className="document section">
      <div className="page-head">
        <span className="eyebrow">THE FIELD NOTES</span>
        <h1>
          A little context
          <br />
          before connecting.
        </h1>
        <p>
          Public product notes. We’ll add tested setup steps when the apps are
          ready.
        </p>
      </div>
      <div className="docs-layout">
        <aside>
          <nav aria-label="Documentation topics">
            {topics.map(([id, title]) => (
              <a key={id} href={"#" + id}>
                {title}
              </a>
            ))}
          </nav>
        </aside>
        <div>
          {topics.map(([id, title, text]) => (
            <section className="doc-topic" id={id} key={id}>
              <h2>{title}</h2>
              <p>{text}</p>
            </section>
          ))}
          <Link className="button" href="/support">
            Need a hand? ↗
          </Link>
        </div>
      </div>
    </div>
  );
}
