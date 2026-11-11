"use client";
import Link from "next/link";
import { MenuIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
export default function MobileNav() {
  return (
    <div className="mobile-navigation">
      <Sheet>
        <SheetTrigger asChild>
          <Button variant="outline" size="icon" aria-label="Open navigation">
            <MenuIcon />
          </Button>
        </SheetTrigger>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Explore Pingora</SheetTitle>
            <SheetDescription>
              Your phone and laptop, a little closer.
            </SheetDescription>
          </SheetHeader>
          <nav
            aria-label="Mobile navigation"
            className="flex flex-col gap-3 px-4"
          >
            {[
              ["/#features", "Features"],
              ["/docs", "Docs"],
              ["/support", "Support"],
              ["/downloads", "Downloads"],
              ["/changelog", "Changelog"],
            ].map(([href, label]) => (
              <SheetClose asChild key={href}>
                <Button variant="ghost" asChild>
                  <Link href={href}>{label}</Link>
                </Button>
              </SheetClose>
            ))}
          </nav>
        </SheetContent>
      </Sheet>
    </div>
  );
}
