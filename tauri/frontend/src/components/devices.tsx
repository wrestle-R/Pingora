import {
  Empty,
  EmptyHeader,
  EmptyTitle,
  EmptyDescription,
  EmptyMedia,
} from "@/components/ui/empty";
import { useEffect, useState } from "react";
import { SmartphoneIcon, Link2Icon, ChevronDownIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Collapsible,
  CollapsibleTrigger,
  CollapsibleContent,
} from "@/components/ui/collapsible";
import { Action, CheckField, Confirm, Panel, Message } from "./common";
import { invoke, uniquePeers, type Peer, type Health } from "@/lib/native";
function Device({ peer, refresh }: { peer: Peer; refresh: () => void }) {
  const [checked, setChecked] = useState(false);
  useEffect(
    () => setChecked(false),
    [peer.verification, peer.requested_by_peer],
  );
  const pair = (action: string) =>
    invoke("pairing", {
      deviceId: peer.id,
      action,
      expectedVerification: action === "accept" ? peer.verification : null,
    });
  return (
    <article className="device-row">
      <div className="device-icon">
        <SmartphoneIcon aria-hidden="true" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-3">
          <h3>{peer.name}</h3><span className="text-xs text-muted-foreground">{peer.id.slice(-6)}</span>
          <Badge
            variant={peer.paired && peer.reachable ? "default" : "secondary"}
          >
            {peer.paired
              ? peer.reachable
                ? "Connected"
                : "Offline"
              : "Not paired"}
          </Badge>
        </div>
        <p className="mt-1 text-sm text-muted-foreground">
          {peer.requested_by_peer
            ? "Your phone is requesting to connect."
            : peer.requested
              ? "Waiting for approval on your phone."
              : peer.paired
                ? "Your saved, independently approved connection."
                : "A separate device identity. Pair only if you recognise it."}
        </p>
        {(peer.requested || peer.requested_by_peer) && (
          <>
            <code className="pair-code">
              {peer.verification || "Verification unavailable"}
            </code>
            {peer.requested_by_peer && (
              <CheckField
                id={`verify-${peer.id}`}
                label="I checked this value on my phone"
                checked={checked}
                disabled={!peer.verification}
                onChange={setChecked}
              />
            )}
          </>
        )}
        <div className="mt-4 flex flex-wrap gap-2">
          {peer.requested_by_peer && (
            <Action
              action={() => pair("accept")}
              disabled={!checked || !peer.verification}
              done={refresh}
              variant="default"
            >
              Accept pairing
            </Action>
          )}
          {peer.requested || peer.requested_by_peer ? (
            <Action action={() => pair("cancel")} done={refresh}>
              Cancel pairing
            </Action>
          ) : peer.paired ? (
            <Confirm
              label="Unpair"
              title={`Unpair ${peer.name}?`}
              description="Sharing will stop. Both devices will need approval to connect again."
              action={async () => {
                await pair("unpair");
                refresh();
              }}
            />
          ) : (
            <Action
              action={() => pair("request")}
              disabled={!peer.reachable}
              done={refresh}
              variant="default"
            >
              Request pairing
            </Action>
          )}
        </div>
      </div>
    </article>
  );
}
export function Devices({
  peers,
  health,
  owned,
  error,
  refresh,
}: {
  peers: Peer[] | null;
  health: Health | null;
  owned: boolean;
  error: string;
  refresh: () => void;
}) {
  const list = uniquePeers(peers || []),
    pairedNames = new Set(list.filter((p) => p.paired).map((p) => p.name));
  const activeNames = new Set(list.filter(p=>p.paired && p.reachable).map(p=>p.name));
  const archived = (p: Peer) => (!p.paired && pairedNames.has(p.name)) || (p.paired && !p.reachable && activeNames.has(p.name));
  const primary = list.filter(p=>!archived(p)), alternatives=list.filter(archived);
  return (
    <Panel
      title="Your devices"
      description="A trusted connection starts with approval on both screens."
    >
      <div className="mb-5 flex flex-wrap gap-2">
        <Action
          action={() => invoke("start_lan")}
          disabled={!!health?.native_service || owned}
          done={refresh}
          variant="default"
        >
          Start connection service
        </Action>
        <Action
          action={() => invoke("stop_lan")}
          disabled={!owned}
          done={refresh}
        >
          Stop service
        </Action>
        <Button variant="ghost" onClick={refresh}>
          Refresh
        </Button>
      </div>
      {error ? (
        <Message>{error}</Message>
      ) : !peers ? (
        <Skeleton className="h-28 w-full" />
      ) : !list.length ? (
        <Empty>
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <Link2Icon />
            </EmptyMedia>
            <EmptyTitle>Bring your phone a little closer.</EmptyTitle>
            <EmptyDescription>
              Open Pingora on both devices. A shared Wi-Fi or USB tethering
              network lets them find each other.
            </EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <>
          {primary.map((peer) => (
            <Device key={peer.id} peer={peer} refresh={refresh} />
          ))}
          {!!alternatives.length && (
            <Collapsible className="mt-4">
              <CollapsibleTrigger asChild>
                <Button variant="ghost">
                  Saved & alternative connections ({alternatives.length})
                  <ChevronDownIcon data-icon="inline-end" />
                </Button>
              </CollapsibleTrigger>
              <CollapsibleContent>
                <p className="my-3 text-sm text-muted-foreground">
                  These have separate identities, such as a development
                  installation. Your saved connection keeps its own permissions.
                </p>
                {alternatives.map((peer) => (
                  <Device key={peer.id} peer={peer} refresh={refresh} />
                ))}
              </CollapsibleContent>
            </Collapsible>
          )}
        </>
      )}
    </Panel>
  );
}
