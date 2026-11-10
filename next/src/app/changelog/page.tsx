import type { Metadata } from "next";
import Link from "next/link";
export const metadata: Metadata = { title: "Changelog" };
export default function Changelog() {
  return (
    <section className="section document">
      <div className="page-head">
        <span className="eyebrow">THE FLIGHT LOG</span>
        <h1>
          No releases.
          <br />
          Yet.
        </h1>
        <p>
          There are no published Pingora app releases to list here. Website
          previews aren’t app releases. Verified versions, dates and release
          notes will appear alongside real downloads.
        </p>
        <Link className="button" href="/downloads">
          Back to release status ↗
        </Link>
      </div>
    </section>
  );
}
