import { invoke } from "@tauri-apps/api/core";
import { useEffect, useState } from "react";
export { invoke };
export const errorText = (error: unknown) =>
  typeof error === "string"
    ? error
    : "Could not complete that action. Check the connection and retry.";
export interface Peer {
  id: string;
  name: string;
  paired: boolean;
  reachable: boolean;
  requested: boolean;
  requested_by_peer: boolean;
  verification: string;
}
export interface Policy {
  paused: boolean;
  show_locked: boolean;
  enabled: Record<string, boolean>;
  desktop_apps: string[];
}
export interface Notice {
  peer: string;
  source: string;
  app: string;
  conversation: string;
  version: string;
  sender: string;
  title: string;
  body: string;
  received: number;
  active: boolean;
  reply: boolean;
  read: boolean;
}
export interface FeatureSnapshot {
  policies: Record<string, Policy>;
  remote_policies: Record<string, Policy>;
  history: Notice[];
  battery: Record<string, { level: number; charging: boolean }>;
  clipboard_available: boolean;
  private_hidden: boolean;
  transfers: {
    id: string;
    peer: string;
    name: string;
    size: number;
    received: number;
    state: string;
    direction: "send" | "receive";
  }[];
  folders: Record<
    string,
    {
      error?: string;
      node?: string;
      next_offset?: number;
      entries?: { name: string; id: string; directory: boolean }[];
    }
  >;
  links: [string, string][];
}
export interface Health {
  paired: boolean;
  native_service: boolean;
  transport: string;
  notifications: string;
  slumber: {
    available: boolean;
    active: boolean;
    secure: boolean;
    lid_ready: boolean;
    lid_action: string;
  };
}
export interface Calls {
  available: boolean;
  private_hidden: boolean;
  settings: {
    enabled: boolean;
    gateway: string;
    microphone_before_mute: number;
  };
  gateways: { id: string; address: string }[];
  calls: { id: string; number: string; state: string }[];
  transport: string;
  microphone: number;
  speaker: number;
}
export interface Update {
  version: string;
  size: number;
  downloaded: boolean;
  message: string;
}
export const defaultPolicy = (): Policy => ({
  paused: false,
  show_locked: false,
  enabled: { notifications: true, clipboard: true, files: true, folders: true, battery: true, ring: true, slumber: true },
  desktop_apps: ["*"],
});
// Stable protocol identities define a connection. Equal display names never merge trust.
export function uniquePeers(peers: Peer[]): Peer[] {
  const identities = new Map<string, Peer>();
  for (const peer of peers) {
    const old = identities.get(peer.id);
    if (!old || (!old.paired && peer.paired)) identities.set(peer.id, peer);
  }
  return [...identities.values()]
    .filter(
      (p) => p.paired || p.reachable || p.requested || p.requested_by_peer,
    )
    .sort(
      (a, b) =>
        Number(b.paired) - Number(a.paired) || a.name.localeCompare(b.name),
    );
}
export function usePoll<T>(
  command: string,
  args: Record<string, unknown> = {},
  interval = 3000,
) {
  const key = JSON.stringify(args);
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<{
    data: T | null;
    error: string;
    loading: boolean;
  }>({ data: null, error: "", loading: true });
  useEffect(() => {
    let alive = true,
      pending = false;
    const load = async () => {
      if (pending || document.hidden) return;
      pending = true;
      try {
        const data = await invoke<T>(command, JSON.parse(key));
        if (alive) setState({ data, error: "", loading: false });
      } catch (error) {
        if (alive)
          setState({ data: null, error: errorText(error), loading: false });
      } finally {
        pending = false;
      }
    };
    void load();
    const timer = setInterval(load, interval);
    document.addEventListener("visibilitychange", load);
    return () => {
      alive = false;
      clearInterval(timer);
      document.removeEventListener("visibilitychange", load);
    };
  }, [command, key, interval, revision]);
  return { ...state, refresh: () => setRevision((value) => value + 1) };
}
