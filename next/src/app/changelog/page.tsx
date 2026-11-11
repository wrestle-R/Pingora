import { Button } from "@/components/ui/button";
import type { Metadata } from "next";
import Link from "next/link";
import { connection } from "next/server";
import { getPublicGithub, statusMessage } from "../../lib/github";
export const metadata: Metadata = { title: "Changelog" };
export default async function Changelog() {
  await connection();
  const github = await getPublicGithub();
  return (
    <section className="section document">
      <div className="page-head">
        <span className="eyebrow">THE FLIGHT LOG</span>
        <h1>Every new fold.</h1>
        <p>
          {github.releases.length > 0
            ? "Stable public releases from GitHub. Read the full notes and verification details at the source."
            : "No stable public release notes are available here right now. Website previews aren’t app releases."}
        </p>
        <p>{statusMessage(github.releasesState)}</p>
        {github.releases.length > 0 && (
          <ul>
            {github.releases.map((release) => (
              <li key={release.url}>
                <a href={release.url}>
                  {release.name || release.version} ·{" "}
                  {release.publishedAt.slice(0, 10)} ↗
                </a>
              </li>
            ))}
          </ul>
        )}
        <Button asChild variant="outline" size="lg"><Link href="/downloads">
          Back to release status ↗
        </Link></Button>
      </div>
    </section>
  );
}
