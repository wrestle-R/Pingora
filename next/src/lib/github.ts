import "server-only";
import { unstable_cache } from "next/cache";

const repository = "wrestle-R/Pingora";
const github = `https://github.com/${repository}`;
const api = `https://api.github.com/repos/${repository}`;
type State =
  "public" | "private-or-unavailable" | "rate-limited" | "unavailable";
export type PublicRelease = {
  version: string;
  name: string;
  publishedAt: string;
  url: string;
  assets: { name: string; url: string; bytes: number }[];
};
export type PublicIssue = {
  number: number;
  title: string;
  state: "open" | "closed";
  url: string;
};
export type GithubStatus = {
  state: State;
  releasesState: State;
  issuesState: State;
  checkedAt: string;
  releases: PublicRelease[];
  issues: PublicIssue[];
};
const object = (value: unknown): Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
const string = (value: unknown, maximum = 200) =>
  typeof value === "string" ? value.slice(0, maximum) : "";
function allowedLink(value: unknown, prefix: string): string | null {
  if (typeof value !== "string" || value.length > 2000) return null;
  try {
    const url = new URL(value);
    return url.origin === "https://github.com" &&
      !url.username &&
      !url.password &&
      !url.search &&
      !url.hash &&
      url.pathname.startsWith(`/${repository}/${prefix}`)
      ? url.href
      : null;
  } catch {
    return null;
  }
}
async function read(path: string): Promise<{ state: State; data?: unknown }> {
  try {
    // No Authorization header, token, caller URL or remote HTML. Never reads private GitHub data.
    const response = await fetch(api + path, {
      headers: {
        Accept: "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
      },
      redirect: "error",
      signal: AbortSignal.timeout(6000),
      cache: "no-store",
    });
    if (response.status === 404) return { state: "private-or-unavailable" };
    if (response.status === 403 || response.status === 429)
      return { state: "rate-limited" };
    if (!response.ok || !response.body) return { state: "unavailable" };
    const reader = response.body.getReader();
    const chunks: Uint8Array[] = [];
    let size = 0;
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.length;
      if (size > 1_000_000) {
        await reader.cancel();
        return { state: "unavailable" };
      }
      chunks.push(value);
    }
    const bytes = new Uint8Array(size);
    let offset = 0;
    for (const chunk of chunks) {
      bytes.set(chunk, offset);
      offset += chunk.length;
    }
    return {
      state: "public",
      data: JSON.parse(new TextDecoder().decode(bytes)),
    };
  } catch {
    return { state: "unavailable" };
  }
}
async function load(): Promise<GithubStatus> {
  const metadata = await read("");
  const result: GithubStatus = {
    state: metadata.state,
    releasesState: metadata.state,
    issuesState: metadata.state,
    checkedAt: new Date().toISOString(),
    releases: [],
    issues: [],
  };
  if (metadata.state !== "public") return result;
  const repo = object(metadata.data);
  if (
    repo.private !== false ||
    string(repo.full_name).toLowerCase() !== repository.toLowerCase()
  )
    return {
      ...result,
      state: "private-or-unavailable",
      releasesState: "private-or-unavailable",
      issuesState: "private-or-unavailable",
    };
  const [releases, issues] = await Promise.all([
    read("/releases?per_page=10"),
    read("/issues?state=all&per_page=30"),
  ]);
  result.releasesState = releases.state;
  result.issuesState = issues.state;
  if (releases.state === "public" && Array.isArray(releases.data)) {
    for (const item of releases.data.slice(0, 10)) {
      const row = object(item);
      const url = allowedLink(row.html_url, "releases/tag/");
      const date = string(row.published_at, 40);
      if (
        row.draft !== false ||
        row.prerelease !== false ||
        !url ||
        !date ||
        !Number.isFinite(Date.parse(date))
      )
        continue;
      const assets: PublicRelease["assets"] = [];
      if (Array.isArray(row.assets))
        for (const asset of row.assets.slice(0, 50)) {
          const file = object(asset);
          const assetUrl = allowedLink(
            file.browser_download_url,
            "releases/download/",
          );
          if (
            assetUrl &&
            typeof file.size === "number" &&
            Number.isSafeInteger(file.size) &&
            file.size >= 0
          )
            assets.push({
              name: string(file.name),
              url: assetUrl,
              bytes: file.size,
            });
        }
      result.releases.push({
        version: string(row.tag_name, 80),
        name: string(row.name),
        publishedAt: date,
        url,
        assets,
      });
    }
  } else if (releases.state === "public") result.releasesState = "unavailable";
  if (issues.state === "public" && Array.isArray(issues.data)) {
    for (const item of issues.data.slice(0, 30)) {
      const row = object(item);
      if (
        row.pull_request ||
        typeof row.number !== "number" ||
        !Number.isSafeInteger(row.number) ||
        row.number <= 0 ||
        !["open", "closed"].includes(string(row.state))
      )
        continue;
      result.issues.push({
        number: row.number,
        title: string(row.title, 160),
        state: row.state as "open" | "closed",
        url: `${github}/issues/${row.number}`,
      });
    }
  } else if (issues.state === "public") result.issuesState = "unavailable";
  return result;
}
// Cache only this sanitized anonymous public read model, including safe failure states.
// No credentials or user-specific responses are captured. Freshness target: five minutes.
export const getPublicGithub = unstable_cache(
  load,
  ["pingora-anonymous-github-v1"],
  { revalidate: 300 },
);
export function statusMessage(state: State): string {
  switch (state) {
    case "private-or-unavailable":
      return "The repository is private or unavailable to anonymous visitors. Private issues and releases are not published here.";
    case "rate-limited":
      return "GitHub is limiting requests right now. Try again later; navigation and report links still work.";
    case "unavailable":
      return "GitHub could not be reached. Try again later; no release or issue details are being guessed.";
    case "public":
      return "Public GitHub metadata, refreshed about every five minutes. Release assets have not been verified by this website.";
  }
}
