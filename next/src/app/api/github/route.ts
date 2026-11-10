import { getPublicGithub } from "../../../lib/github";
export const runtime = "nodejs";
export const maxDuration = 20;
export async function GET() {
  const data = await getPublicGithub();
  return Response.json(data, {
    headers: {
      "Cache-Control": "public, s-maxage=60, stale-while-revalidate=120",
      "X-Content-Type-Options": "nosniff",
    },
  });
}
