import { useRef, useState } from "react";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  Field,
  FieldGroup,
  FieldLabel,
  FieldSet,
  FieldLegend,
} from "@/components/ui/field";
import {
  Collapsible,
  CollapsibleTrigger,
  CollapsibleContent,
} from "@/components/ui/collapsible";
import { Button } from "@/components/ui/button";
import { Action, CheckField, Panel, Message } from "./common";
import {
  defaultPolicy,
  errorText,
  invoke,
  type Peer,
  type Policy,
  type FeatureSnapshot,
  usePoll,
} from "@/lib/native";
function SharingPeer({
  peer,
  snapshot,
  refresh,
}: {
  peer: Peer;
  snapshot: FeatureSnapshot;
  refresh: () => void;
}) {
  const [policy, setPolicy] = useState(
    () => snapshot.policies[peer.id] || defaultPolicy(),
  );
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [search, setSearch] = useState(""),
    [text, setText] = useState("");
  const guard = useRef(false);
  const apps = usePoll<[string, string][]>("desktop_apps", {}, 60000);
  async function save(next: Policy) {
    if (guard.current) return;
    guard.current = true;
    setBusy(true);
    setError("");
    try {
      await invoke("feature_policy", { deviceId: peer.id, policy: next });
      setPolicy(next);
      refresh();
    } catch (e) {
      setError(errorText(e));
    } finally {
      guard.current = false;
      setBusy(false);
    }
  }
  const act = (kind: string, data = {}) =>
    invoke("feature_action", { deviceId: peer.id, kind, data });
  const remote = snapshot.remote_policies[peer.id],
    battery = snapshot.battery[peer.id],
    listing = snapshot.folders[peer.id];
  return (
    <Panel
      title={peer.name}
      description={
        remote
          ? `Phone sharing is ${remote.paused ? "paused" : "ready"}.`
          : "Waiting for the phone’s feature session."
      }
    >
      <Collapsible className="device-preferences">
        <CollapsibleTrigger asChild><Button variant="ghost">Device permissions & privacy</Button></CollapsibleTrigger>
        <CollapsibleContent className="pt-4">
      <FieldSet>
        <FieldLegend>Connection permissions</FieldLegend>
        <FieldGroup className="settings-grid">
          {[
            "paused",
            "show_locked",
            "notifications",
            "clipboard",
            "files",
            "folders",
            "battery",
            "ring",
            "slumber",
          ].map((feature) => (
            <CheckField
              key={feature}
              id={`${peer.id}-${feature}`}
              label={
                feature === "paused"
                  ? "Pause sharing"
                  : feature === "show_locked"
                    ? "Show private content while locked"
                    : `Allow ${feature}`
              }
              description={
                feature === "show_locked"
                  ? "Applies to this laptop only. Leave off for private notifications."
                  : undefined
              }
              checked={
                feature === "paused"
                  ? policy.paused
                  : feature === "show_locked"
                    ? policy.show_locked
                    : !!policy.enabled[feature]
              }
              disabled={busy}
              onChange={(checked) =>
                void save({
                  ...policy,
                  ...(feature === "paused" || feature === "show_locked"
                    ? { [feature]: checked }
                    : { enabled: { ...policy.enabled, [feature]: checked } }),
                })
              }
            />
          ))}
        </FieldGroup>
      </FieldSet>
        </CollapsibleContent>
      </Collapsible>
      <Message>{error}</Message>
      <Collapsible className="section-divider">
        <CollapsibleTrigger asChild>
          <Button variant="outline">
            Desktop notification apps · {policy.desktop_apps.includes("*") ? "All apps" : `${policy.desktop_apps.length} selected`}
          </Button>
        </CollapsibleTrigger>
        <CollapsibleContent className="mt-4 flex flex-col gap-3">
          <CheckField id={`${peer.id}-all-apps`} label="Share notifications from all desktop apps"
            checked={policy.desktop_apps.includes("*")} disabled={busy}
            onChange={(checked) => void save({ ...policy, desktop_apps: checked ? ["*"] : [] })} />
          <Input
            aria-label="Find a desktop app"
            placeholder="Find an app…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <div className="max-h-64 overflow-auto flex flex-col gap-3">
            {apps.error && <Message>{apps.error}</Message>}
            {apps.data
              ?.filter(([id, name]) =>
                `${id} ${name}`.toLowerCase().includes(search.toLowerCase()),
              )
              .map(([id, name]) => (
                <CheckField
                  key={id}
                  id={`${peer.id}-app-${id}`}
                  label={name}
                  checked={policy.desktop_apps.includes("*") || policy.desktop_apps.includes(id)}
                  disabled={busy || policy.desktop_apps.includes("*")}
                  onChange={(checked) =>
                    void save({
                      ...policy,
                      desktop_apps: checked
                        ? [...new Set([...policy.desktop_apps, id])]
                        : policy.desktop_apps.filter((value) => value !== id),
                    })
                  }
                />
              ))}
          </div>
        </CollapsibleContent>
      </Collapsible>
      <div className="section-divider">
        <p className="mb-3 text-sm text-muted-foreground">
          {battery
            ? `Phone battery ${battery.level}%${battery.charging ? " · charging" : ""}`
            : "Phone battery unavailable"}
        </p>
        <div className="action-row">
          <Action action={() => act("battery_request")}>Refresh battery</Action>
          <Action action={() => act("ring")}>Ring phone · 30 seconds</Action>
          <Action action={() => act("ring_stop")}>Stop ringing</Action>
          <Action action={() => invoke("offer_file", { deviceId: peer.id })}>
            Choose file to send
          </Action>
          <Action action={() => act("folder_list")}>
            Browse phone folders
          </Action>
        </div>
      </div>
      {snapshot.transfers
        .filter((t) => t.peer === peer.id)
        .map((t) => (
          <div className="section-divider" key={t.id}>
            <p className="mb-3">
              {t.name} · {t.received}/{t.size} bytes{" "}
              {t.direction === "send" ? "sent" : "received"} · {t.state}
            </p>
            <div className="action-row">
              {t.state === "approval_required" && (
                <Action action={() => act("file_accept", { id: t.id })}>
                  Accept transfer
                </Action>
              )}
              {t.state !== "saved" && t.state !== "sent" ? (
                <Action action={() => act("file_cancel", { id: t.id })}>
                  Reject or cancel
                </Action>
              ) : (
                <p className="text-sm text-muted-foreground">
                  {t.state === "sent"
                    ? "Sent to your phone."
                    : "Saved to Downloads/Pingora. Nothing was opened."}
                </p>
              )}
            </div>
          </div>
        ))}
      {listing && (
        <div className="section-divider flex flex-col gap-3">
          <Action action={() => act("folder_list")}>Shared folder roots</Action>
          {listing.error && (
            <Message>Folder access is unavailable or was revoked.</Message>
          )}
          {!listing.error && !listing.entries?.length && <Message>No accessible folders. On your phone, open Settings → Phone storage access, or choose a read-only folder.</Message>}
          {listing.entries?.map((entry) => (
            <div key={entry.id}>
              <Action
                action={() =>
                  act(entry.directory ? "folder_list" : "folder_read", {
                    node: entry.id,
                  })
                }
              >
                {entry.directory ? "Browse" : "Request download"}: {entry.name}
              </Action>
            </div>
          ))}
          {listing.next_offset != null && <Action action={() => act("folder_list", {node: listing.node || "", offset: listing.next_offset})}>Next page</Action>}
        </div>
      )}
      <FieldGroup className="section-divider">
        <Field>
          <FieldLabel htmlFor={`clipboard-${peer.id}`}>
            Text to send to the phone clipboard
          </FieldLabel>
          <Textarea
            id={`clipboard-${peer.id}`}
            maxLength={16384}
            value={text}
            onChange={(e) => setText(e.target.value)}
          />
        </Field>
        <Action
          disabled={!text}
          action={async () => {
            await act("clipboard", { text });
            setText("");
          }}
        >
          Send clipboard text
        </Action>
      </FieldGroup>
    </Panel>
  );
}
export function Sharing({
  peers,
  snapshot,
  refresh,
}: {
  peers: Peer[];
  snapshot: FeatureSnapshot | null;
  refresh: () => void;
}) {
  return (
    <div className="flex flex-col gap-6">
      {!snapshot ? (
        <Message>Sharing is unavailable. Check the connection service.</Message>
      ) : (
        <>
          {peers
            .filter((p) => p.paired && (p.reachable || !peers.some(other => other.paired && other.reachable && other.name === p.name)))
            .map((peer) => (
              <SharingPeer
                key={peer.id}
                peer={peer}
                snapshot={snapshot}
                refresh={refresh}
              />
            ))}
          {!peers.some((p) => p.paired) && (
            <Panel
              title="Choose what comes across"
              description="Pair your phone first, then enable the features you want."
            >
              <p className="text-muted-foreground">
                Every connection has its own permissions.
              </p>
            </Panel>
          )}
          {(snapshot.clipboard_available || snapshot.links.length > 0) && (
            <Panel title="Received from your phone">
              <div className="flex flex-col gap-4">
                {snapshot.clipboard_available && (
                  <Action
                    action={async () =>
                      navigator.clipboard.writeText(
                        await invoke<string>("received_clipboard"),
                      )
                    }
                  >
                    Copy received clipboard
                  </Action>
                )}
                {snapshot.links.map(([peer, url], i) => (
                  <div key={`${peer}-${i}`} className="flex flex-col gap-2">
                    <p className="break-all text-sm">{url}</p>
                    <Action
                      action={() => invoke("open_received_link", { url })}
                    >
                      Open received link
                    </Action>
                  </div>
                ))}
              </div>
            </Panel>
          )}
        </>
      )}
    </div>
  );
}
