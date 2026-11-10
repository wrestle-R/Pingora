import type { Metadata } from "next";
import Link from "next/link";
import { connection } from "next/server";
import { getPublicGithub, statusMessage } from "../../lib/github";
export const metadata: Metadata = { title: "Downloads" };
export default async function Downloads() {
  await connection();
  const github = await getPublicGithub();
  return (
    <section className="section document">
      <div className="page-head">
        <span className="eyebrow">NOT QUITE READY TO FLY</span>
        <h1>
          The next version,
          <br />
          when it’s ready.
        </h1>
        <p>
          No verified installers are served by this website yet. Public
          releases, when available, link to GitHub for assets, signing and
          compatibility details.
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
        <p>{statusMessage(github.state)}</p>
        {github.releases.length > 0 && (
          <ul>
            {github.releases.map((release) => (
              <li key={release.url}>
                <a href={release.url}>
                  {release.version} · View assets and verification details on
                  GitHub ↗
                </a>
              </li>
            ))}
          </ul>
        )}
        <Link className="text-link" href="/changelog">
          Release notes ↗
        </Link>
      </div>
    </section>
  );
}
