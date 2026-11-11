import { useState } from "react";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import {
  Empty,
  EmptyHeader,
  EmptyTitle,
  EmptyDescription,
} from "@/components/ui/empty";
import { Badge } from "@/components/ui/badge";
import { Action, Confirm, Panel, Message } from "./common";
import { invoke, type Notice, type FeatureSnapshot } from "@/lib/native";
function Notification({
  item,
  locked = false,
}: {
  item: Notice;
  locked?: boolean;
}) {
  const [reply, setReply] = useState("");
  const act = (action: string) =>
    invoke("feature_action", {
      deviceId: item.peer,
      kind: "notification_action",
      data: {
        source: item.source,
        version: item.version,
        action,
        ...(action === "reply" ? { text: reply } : {}),
      },
    });
  const otp = item.body.match(/\b\d{4,8}\b/);
  return (
    <article className="notice">
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs text-muted-foreground">
          {item.app} · {new Date(item.received).toLocaleString()}
        </p>
        <Badge variant="secondary">{item.active ? "Live" : "History"}</Badge>
      </div>
      <h3 className="mt-3 font-medium">
        {item.sender || item.title || item.app}
      </h3>
      <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-relaxed">
        {item.body}
      </p>
      <div className="action-row mt-4">
        {item.active && !locked && (
          <>
            <Action action={() => act("dismiss")}>Dismiss on source</Action>
            {item.read && (
              <Action action={() => act("read")}>Mark as read</Action>
            )}
          </>
        )}
        {otp && !locked && (
          <Action action={() => navigator.clipboard.writeText(otp[0])}>
            Copy code
          </Action>
        )}
      </div>
      {item.active && !locked && item.reply && (
        <FieldGroup className="mt-4">
          <Field>
            <FieldLabel htmlFor={`reply-${item.source}`}>Reply</FieldLabel>
            <Textarea
              id={`reply-${item.source}`}
              value={reply}
              maxLength={4096}
              onChange={(e) => setReply(e.target.value)}
            />
          </Field>
          <Action
            disabled={!reply.trim()}
            action={async () => {
              await act("reply");
              setReply("");
            }}
          >
            Send reply
          </Action>
        </FieldGroup>
      )}
    </article>
  );
}
export function Inbox({
  snapshot,
  query,
  setQuery,
  refresh,
}: {
  snapshot: FeatureSnapshot | null;
  query: string;
  setQuery: (query: string) => void;
  refresh: () => void;
}) {
  const visible =
    snapshot?.history.filter(
      (item) =>
        !snapshot.private_hidden || snapshot.policies[item.peer]?.show_locked,
    ) || [];
  return (
    <Panel
      title="Your inbox"
      description="Notifications from the apps you chose. Actions apply to their original source."
    >
      <div className="flex flex-wrap gap-3 mb-5">
        <Input
          className="flex-1 min-w-40"
          type="search"
          aria-label="Search inbox"
          placeholder="Find a notification…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <Confirm
          label="Clear history"
          title="Clear saved notification history?"
          description="This removes saved inbox content from this laptop."
          action={async () => {
            await invoke("clear_history");
            refresh();
          }}
        />
      </div>
      {!snapshot ? (
        <Message>Inbox unavailable. Check your connection.</Message>
      ) : visible.length ? (
        <>
          {snapshot.private_hidden && (
            <Message>
              Only content explicitly allowed on this locked laptop is shown.
              Unlock to use notification actions.
            </Message>
          )}
          {visible.map((item) => (
            <Notification
              key={`${item.peer}-${item.source}-${item.version}`}
              item={item}
              locked={snapshot.private_hidden}
            />
          ))}
        </>
      ) : snapshot.private_hidden ? (
        <Message>
          Private content is hidden while the desktop is locked or its lock
          state is unknown.
        </Message>
      ) : (
        <Empty>
          <EmptyHeader>
            <EmptyTitle>A little quieter in here.</EmptyTitle>
            <EmptyDescription>
              New notifications will appear when your phone is connected and
              sharing is enabled.
            </EmptyDescription>
          </EmptyHeader>
        </Empty>
      )}
    </Panel>
  );
}
