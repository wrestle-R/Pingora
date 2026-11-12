"use client";
import { useState } from "react";
import { SmartphoneIcon, MonitorIcon } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { PublicRelease } from "@/lib/github";
export default function DownloadPlatforms({ release }: { release: PublicRelease | null }) {
  const asset = (name: string) => release?.assets.find((file) => file.name === name && file.bytes > 0);
  const version = release?.version.replace(/^v/, "");
  const [platform, setPlatform] = useState("all");
  return (
    <>
      <div className="platform-picker">
        <Label htmlFor="platform">Show platform</Label>
        <Select value={platform} onValueChange={setPlatform}>
          <SelectTrigger id="platform">
            <SelectValue placeholder="All platforms" />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value="all">All platforms</SelectItem>
              <SelectItem value="Android">Android</SelectItem>
              <SelectItem value="Linux">Linux</SelectItem>
            </SelectGroup>
          </SelectContent>
        </Select>
      </div>
      <div className="platforms">
        {["Android", "Linux"]
          .filter((name) => platform === "all" || platform === name)
          .map((name) => (
            <Card className="platform-card" key={name}>
              <CardHeader>
                {name === "Android" ? (
                  <SmartphoneIcon aria-hidden="true" />
                ) : (
                  <MonitorIcon aria-hidden="true" />
                )}
                <CardTitle>{name}</CardTitle>
                <CardDescription>
                  {name === "Android"
                    ? "Your phone companion for sharing, notifications and the things in between."
                    : "Your desktop companion for Hyprland, Plasma and GNOME. Compatibility qualification is in progress."}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <p>
                  {release
                    ? name === "Android"
                      ? "Signed APK. Open it on your phone and approve Android’s installer. Android rejects downgrades; keep your existing app if it is newer than this release."
                      : "Linux x86_64. Automatic dependency setup supports Arch-based systems; other distributions need compatible libraries."
                    : "No complete published release is available right now. Check GitHub releases or try again later."}
                </p>
              </CardContent>
              <CardFooter className="flex flex-wrap gap-3">
                <Badge variant="secondary">{release?.version || "Unavailable"}</Badge>
                {!release && <Button variant="outline" asChild><a href="https://github.com/wrestle-R/Pingora/releases/latest">Open GitHub releases</a></Button>}
                {release && name === "Android" && asset(`pingora-${version}.apk`) && (
                  <Button asChild><a href={asset(`pingora-${version}.apk`)!.url}>Download Android APK</a></Button>
                )}
                {release && name === "Linux" && asset("install-app.sh") && (
                  <Button asChild><a href={asset("install-app.sh")!.url}>Download Linux installer</a></Button>
                )}
                {release && name === "Linux" && asset(`pingora-${version}-linux-x86_64.tar.gz`) && (
                  <Button variant="outline" asChild><a href={asset(`pingora-${version}-linux-x86_64.tar.gz`)!.url}>Linux archive</a></Button>
                )}
              </CardFooter>
            </Card>
          ))}
      </div>
    </>
  );
}
