import Link from "next/link";
import Airwave from "../components/airwave";
const features = [
  [
    "01",
    "Notifications, with the words intact.",
    "The plan: readable previews for WhatsApp, Instagram, Gmail and SMS. One conversation, its newest message, and room to read what the source actually shares.",
  ],
  [
    "02",
    "Copy here. Pick it up there.",
    "Text clipboard sharing between your phone and desktop. Where Android limits background access, a shade action or widget gives you a way through.",
  ],
  [
    "03",
    "Send the thing. Keep going.",
    "Files, photos and links over your local network. Incoming transfers ask first, links wait for you to open them, and shared folders stay read-only.",
  ],
];
export default function Home() {
  return (
    <>
      <section className="hero">
        <span className="badge">
          <span /> In development
        </span>
        <h1>
          Your phone. Your
          <br className="wide" /> desktop. In sync.
        </h1>
        <p>
          Notifications, clipboard and sharing.
          <br className="mobile" /> Android meets Linux.
        </p>
        <div className="actions">
          <a className="button primary" href="#features">
            Explore Pingora <span>↗</span>
          </a>
          <Link className="button" href="/docs">
            Read the docs
          </Link>
        </div>
        <Airwave />
      </section>
      <section className="intro section">
        <div className="eyebrow">A SMALLER KIND OF CONNECTION</div>
        <h2>
          Two devices.
          <br />
          Less running between them.
        </h2>
        <p>
          That message on your phone, that link on your laptop, that one file
          you need over there. Pingora is being built for the little things that
          keep interrupting everything else.
        </p>
      </section>
      <section className="section" id="features">
        <div className="section-heading">
          <span className="eyebrow">WHAT WE’RE BUILDING</span>
          <span className="annotation">Just the useful bits ↙</span>
        </div>
        <div className="features">
          {features.map(([n, title, body]) => (
            <article key={n}>
              <span className="number">{n} /</span>
              <div className="feature-art" aria-hidden="true">
                {n === "01" ? (
                  <div className="paper-message">
                    <small>MESSAGES · PREVIEW</small>
                    <b>Weekend plans</b>
                    <p>Meera: Meet near the gate?</p>
                  </div>
                ) : n === "02" ? (
                  <>
                    <kbd>Copy</kbd>
                    <span className="dotted">······↗</span>
                    <kbd>Paste</kbd>
                  </>
                ) : (
                  <div className="paper-file">
                    ↗<span>that-one-file.pdf</span>
                  </div>
                )}
              </div>
              <h3>{title}</h3>
              <p>{body}</p>
            </article>
          ))}
        </div>
      </section>
      <section className="privacy section">
        <div>
          <span className="eyebrow">CLOSE BY, BY DESIGN</span>
          <h2>
            Your network.
            <br />
            Your say.
          </h2>
        </div>
        <div>
          <p>
            LAN for everyday sharing. Optional Bluetooth for SIM calls, on
            setups we can actually verify. No root required for the core app.
          </p>
          <p>
            Choose what gets shared, pause when you need to, and keep diagnostic
            exports free of message and clipboard content by default.
          </p>
          <Link className="text-link" href="/docs#privacy">
            Read about privacy ↗
          </Link>
        </div>
      </section>
      <section className="section more">
        <span className="eyebrow">A FEW MORE THINGS IN THE PLAN</span>
        <div>
          <p>
            Battery status. Ring a misplaced device. A bounded, searchable
            inbox. And optional calls, once the audio path is proven.
          </p>
          <Link href="/docs#scope" className="text-link">
            The scope, including the limits ↗
          </Link>
        </div>
      </section>
      <section className="closing section">
        <span className="badge">Still folding the first version.</span>
        <h2>
          Good things take
          <br />a few folds.
        </h2>
        <p>
          The website is here. The Android and Linux apps are still in
          development. No installers or signed releases yet.
        </p>
        <Link className="button primary" href="/downloads">
          Check release status ↗
        </Link>
      </section>
    </>
  );
}
