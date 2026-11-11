"use client";
import { useEffect, useState } from "react";
import { SunIcon, MoonIcon, MonitorIcon, ChevronDownIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
type Mode = "light" | "dark" | "system";
export default function Theme() {
  const [mode, setMode] = useState<Mode>("system");
  function apply(value: Mode) {
    const dark =
      value === "dark" ||
      (value === "system" &&
        matchMedia("(prefers-color-scheme: dark)").matches);
    document.documentElement.dataset.theme = dark ? "dark" : "light";
    document.documentElement.classList.toggle("dark", dark);
  }
  useEffect(() => {
    let saved: Mode = "system";
    try {
      const value = localStorage.getItem("pingora-theme");
      if (value === "light" || value === "dark" || value === "system")
        saved = value;
    } catch {}
    setMode(saved);
    apply(saved);
  }, []);
  useEffect(() => {
    const media = matchMedia("(prefers-color-scheme: dark)");
    const changed = () => {
      if (mode === "system") apply(mode);
    };
    media.addEventListener("change", changed);
    return () => media.removeEventListener("change", changed);
  }, [mode]);
  const Icon =
    mode === "dark" ? MoonIcon : mode === "light" ? SunIcon : MonitorIcon;
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" aria-label="Choose appearance">
          <Icon data-icon="inline-start" />
          <span className="hidden sm:inline">Appearance</span>
          <ChevronDownIcon data-icon="inline-end" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuLabel>Appearance</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuRadioGroup
          value={mode}
          onValueChange={(value) => {
            const next = value as Mode;
            setMode(next);
            apply(next);
            try {
              localStorage.setItem("pingora-theme", next);
            } catch {}
          }}
        >
          <DropdownMenuRadioItem value="light">Light</DropdownMenuRadioItem>
          <DropdownMenuRadioItem value="dark">Dark</DropdownMenuRadioItem>
          <DropdownMenuRadioItem value="system">System</DropdownMenuRadioItem>
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
