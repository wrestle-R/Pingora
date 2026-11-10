import type { Metadata } from "next";
import { connection } from "next/server";
import { getPublicGithub, statusMessage } from "../../lib/github";
export const metadata: Metadata = { title: "Support" };
const repo = "https://github.com/wrestle-R/Pingora";
export default async function Support() {
  await connection();
  const github = await getPublicGithub();
  return (
    <section className="section document">
      <div className="page-head">
        <span className="eyebrow">LET’S MAKE IT BETTER</span>
        <h1>
          Something off?
          <br />
          Something missing?
        </h1>
        <p>
          Bug reports and feature requests live on GitHub. You’ll need to sign
          in, and while the repository is private you’ll also need access.
        </p>
      </div>
      <div className="platforms">
        <article>
          <span className="number">01 /</span>
          <h2>Report a bug</h2>
          <p>
            Tell us what you expected, what happened, and the app version,
            Android version or Linux desktop involved. Add steps that reproduce
            it.
          </p>
          <a
            className="button primary"
            href={repo + "/issues/new?title=Bug%3A%20"}
          >
            Open a bug report ↗
          </a>
        </article>
        <article>
          <span className="number">02 /</span>
          <h2>Request a feature</h2>
          <p>
            Start with the thing you’re trying to do and where the current flow
            gets in the way. A small example helps.
          </p>
          <a className="button" href={repo + "/issues/new?title=Feature%3A%20"}>
            Suggest a feature ↗
          </a>
        </article>
      </div>
      <div className="notice">
        <h3>Public issue feed</h3>
        <p>{statusMessage(github.issuesState)}</p>
        {github.issues.length > 0 ? (
          <ul>
            {github.issues.map((issue) => (
              <li key={issue.number}>
                <a href={issue.url}>
                  #{issue.number} · {issue.title} ({issue.state}) ↗
                </a>
              </li>
            ))}
          </ul>
        ) : github.issuesState === "public" ? (
          <p>No public issues to show.</p>
        ) : null}
        <h3>Keep the private bits private.</h3>
        <p>
          Remove notification text, OTPs, clipboard contents, device identifiers
          and secrets from screenshots or logs before sharing. This website does
          not collect diagnostics or submit reports for you.
        </p>
        <a className="text-link" href={repo + "/issues"}>
          View issues on GitHub (access required) ↗
        </a>
      </div>
    </section>
  );
}
