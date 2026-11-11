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
export default function DownloadPlatforms() {
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
                  Signed development builds exist. Qualified public downloads
                  will appear here when available.
                </p>
              </CardContent>
              <CardFooter>
                <Badge variant="secondary">In development</Badge>
              </CardFooter>
            </Card>
          ))}
      </div>
    </>
  );
}
