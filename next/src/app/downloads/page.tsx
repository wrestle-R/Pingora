import DownloadPlatforms from "@/components/download-platforms";
import type { Metadata } from "next";
import Link from "next/link";
import { connection } from "next/server";
import { getPublicGithub, statusMessage } from "../../lib/github";
export const metadata: Metadata = { title: "Downloads" };
export default async function Downloads() {
  await connection();
  const github = await getPublicGithub();
  const release = github.releases.find((item) => {
    if (!/^v\d+\.\d+\.\d+$/.test(item.version)) return false;
    const version = item.version.slice(1);
    const required = [`pingora-${version}.apk`, `pingora-${version}-linux-x86_64.tar.gz`, "install-app.sh", "pingora-update.json", "pingora-update.sig"];
    return required.every((name) => item.assets.some((asset) => asset.name === name && asset.bytes > 0));
  }) || null;
  return (
    <section className="section document">
      <div className="page-head">
        <span className="eyebrow">YOUR DEVICES, CONNECTED</span>
        <h1>
          Download Pingora.
        </h1>
        <p>
          {release ? `Published release ${release.version}. Download the signed Android APK or Linux installer from GitHub.` : "Published releases will appear here when GitHub is available. Draft and incomplete releases are not offered."}
          {" "}Check the version before replacing an existing installation. Compatibility qualification is still in progress.
        </p>
      </div>
      <DownloadPlatforms release={release} />
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
