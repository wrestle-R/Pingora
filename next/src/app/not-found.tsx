import { Button } from "@/components/ui/button";
import Link from "next/link";
export default function NotFound() {
  return (
    <section className="section document page-head">
      <span className="eyebrow">404 / A WRONG TURN</span>
      <h1>
        This one
        <br />
        flew off.
      </h1>
      <p>There’s no page at this address.</p>
      <Button asChild variant="default" size="lg"><Link href="/">
        Back to Pingora ↗
      </Link></Button>
    </section>
  );
}
