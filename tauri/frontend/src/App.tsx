import { useEffect, useState } from "react";
import {
  LayoutDashboardIcon,
  InboxIcon,
  ArrowLeftRightIcon,
  PhoneIcon,
  Settings2Icon,
  SunMoonIcon,
  ChevronDownIcon,
  ShieldCheckIcon,
} from "lucide-react";
import {
  SidebarProvider,
  Sidebar,
  SidebarHeader,
  SidebarContent,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarGroupContent,
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuButton,
  SidebarFooter,
  SidebarInset,
  SidebarTrigger,
} from "@/components/ui/sidebar";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Devices } from "@/components/devices";
import { Sharing } from "@/components/sharing";
import { Inbox } from "@/components/inbox";
import { Calls, Settings } from "@/components/settings";
import { Panel, Message } from "@/components/common";
import {
  usePoll,
  uniquePeers,
  type Peer,
  type FeatureSnapshot,
  type Health,
} from "@/lib/native";
const pages = [
  {
    id: "overview",
    name: "Devices",
    icon: LayoutDashboardIcon,
    description: "One space for the devices you trust.",
  },
  {
    id: "inbox",
    name: "Inbox",
    icon: InboxIcon,
    description: "The conversations that came with you.",
  },
  {
    id: "sharing",
    name: "Files & clipboard",
    icon: ArrowLeftRightIcon,
    description: "Send a little. Share what you choose.",
  },
  {
    id: "calls",
    name: "Calls",
    icon: PhoneIcon,
    description: "Your phone calls, on a supported setup.",
  },
  {
    id: "settings",
    name: "Settings",
    icon: Settings2Icon,
    description: "Make this space yours.",
  },
];
function Theme() {
  const [theme, setTheme] = useState(() => {
    try {
      return localStorage.getItem("pingora-theme") || "system";
    } catch {
      return "system";
    }
  });
  useEffect(() => {
    const media = matchMedia("(prefers-color-scheme: dark)");
    const sync = () =>
      document.documentElement.classList.toggle(
        "dark",
        theme === "dark" || (theme === "system" && media.matches),
      );
    sync();
    media.addEventListener("change", sync);
    try {
      localStorage.setItem("pingora-theme", theme);
    } catch {}
    return () => media.removeEventListener("change", sync);
  }, [theme]);
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" className="w-full justify-between">
          <span className="flex items-center gap-2">
            <SunMoonIcon className="size-4" />
            Appearance
          </span>
          <ChevronDownIcon className="size-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="min-w-48">
        <DropdownMenuLabel>Choose your theme</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuRadioGroup value={theme} onValueChange={setTheme}>
          <DropdownMenuRadioItem value="light">Light</DropdownMenuRadioItem>
          <DropdownMenuRadioItem value="dark">Dark</DropdownMenuRadioItem>
          <DropdownMenuRadioItem value="system">System</DropdownMenuRadioItem>
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
export default function App() {
  const [page, setPage] = useState("overview"),
    [query, setQuery] = useState("");
  const peers = usePoll<Peer[]>("device_list"),
    health = usePoll<Health>("health"),
    owned = usePoll<boolean>("owns_lan"),
    features = usePoll<FeatureSnapshot>("feature_snapshot", { query }, 2000);
  const selected = pages.find((p) => p.id === page)!;
  const list = uniquePeers(peers.data || []),
    connected = list.filter((p) => p.paired && p.reachable).length;
  const refresh = () => {
    peers.refresh();
    health.refresh();
    owned.refresh();
    features.refresh();
  };
  return (
    <SidebarProvider>
      <Sidebar>
        <SidebarHeader className="p-6">
          <a
            href="#overview"
            onClick={(event) => {
              event.preventDefault();
              setPage("overview");
            }}
            className="brand"
          >
            <img src="./brand/pingora.svg" alt="" className="size-10" />
            <span>
              Pingora<small>Phone meets laptop.</small>
            </span>
          </a>
        </SidebarHeader>
        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupLabel>Your space</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {pages.map((item) => (
                  <SidebarMenuItem key={item.id}>
                    <SidebarMenuButton
                      isActive={page === item.id}
                      aria-current={page === item.id ? "page" : undefined}
                      onClick={() => setPage(item.id)}
                    >
                      <item.icon />
                      <span>{item.name}</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
          <SidebarGroup>
            <SidebarGroupLabel>Connected</SidebarGroupLabel>
            <SidebarGroupContent><SidebarMenu>
              {list.filter(peer=>peer.paired && peer.reachable).map(peer=><SidebarMenuItem key={peer.id}>
                <SidebarMenuButton onClick={()=>setPage("sharing")}><span className="status-dot online" /><span>{peer.name}</span></SidebarMenuButton>
              </SidebarMenuItem>)}
              {!connected && <p className="px-3 py-2 text-xs text-muted-foreground">No device connected</p>}
            </SidebarMenu></SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>
        <SidebarFooter className="gap-4 p-5">
          <div className="connection-status">
            <span className={connected ? "status-dot online" : "status-dot"} />
            <span>
              {connected
                ? `${connected} device${connected === 1 ? "" : "s"} connected`
                : "Waiting for a connection"}
            </span>
          </div>
          <Theme />
          <p className="text-xs text-muted-foreground">
            Yours to connect. Yours to control.
          </p>
        </SidebarFooter>
      </Sidebar>
      <SidebarInset>
        <header className="workspace-topbar">
          <SidebarTrigger />
          <span className="text-sm text-muted-foreground">Your workspace</span>
          <Badge variant="outline" className="ml-auto">
            Local connection
          </Badge>
        </header>
        <main className="workspace">
          <div className="page-heading">
            <div>

              <h1>{selected.name}</h1>
              <p>{selected.description}</p>
            </div>
            <div className="trust-note">
              <ShieldCheckIcon className="size-4" />
              <span>
                {connected ? "Paired by you" : "Approval on both devices"}
              </span>
            </div>
          </div>
          {page === "overview" && (
            <>
              <Devices
                peers={peers.data}
                health={health.data}
                owned={!!owned.data}
                error={peers.error || health.error}
                refresh={refresh}
              />
            </>
          )}
          {page === "inbox" && (
            <Inbox
              snapshot={features.data}
              query={query}
              setQuery={setQuery}
              refresh={features.refresh}
            />
          )}
          {page === "sharing" && (
            <Sharing
              peers={list}
              snapshot={features.data}
              refresh={features.refresh}
            />
          )}
          {page === "calls" && <Calls />}
          {page === "settings" && <Settings />}
          {features.error &&
            page !== "overview" &&
            page !== "calls" &&
            page !== "settings" && <Message>{features.error}</Message>}
        </main>
      </SidebarInset>
    </SidebarProvider>
  );
}
