import type { Metadata } from "next";
import Link from "next/link";
export const metadata: Metadata = { title: "Downloads" };
export default function Downloads() {
  return (
    <section className="section document">
      <div className="page-head">
        <span className="eyebrow">NOT QUITE READY TO FLY</span>
        <h1>
          The first release
          <br />
          is still taking shape.
        </h1>
        <p>
          No APK, Linux package or signed installer is available yet. We’ll put
          verified downloads here when there’s something ready to install.
        </p>
      </div>
      <div className="platforms">
        {["Android", "Linux"].map((name) => (
          <article key={name}>
            <span className="number">{name === "Android" ? "01" : "02"} /</span>
            <h2>{name}</h2>
            <p>
              {name === "Android"
                ? "Native phone companion."
                : "Tauri desktop companion. Targeting Hyprland, Plasma and GNOME; support is not yet verified."}
            </p>
            <span className="status">In development · no release</span>
          </article>
        ))}
      </div>
      <div className="notice">
        <h3>About GitHub downloads</h3>
        <p>
          The project repository is private. This site does not fetch or publish
          private release data. A GitHub link may require access and can show a
          404 if you don’t have it.
        </p>
        <Link className="text-link" href="/changelog">
          Release notes ↗
        </Link>
      </div>
    </section>
  );
}
