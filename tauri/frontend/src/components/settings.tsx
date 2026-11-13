import { useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectGroup,
  SelectItem,
} from "@/components/ui/select";
import { Slider } from "@/components/ui/slider";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Action, CheckField, Panel, Message } from "./common";
import {
  invoke,
  errorText,
  usePoll,
  type Calls as CallsSnapshot,
  type Update,
} from "@/lib/native";
export function Calls() {
  const poll = usePoll<CallsSnapshot>("calls_snapshot");
  const data = poll.data;
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const guard = useRef(false);
  const [confirmed, setConfirmed] = useState<CallsSnapshot["settings"] | null>(
    null,
  );
  const settings = confirmed || data?.settings;
  async function save(change: Partial<CallsSnapshot["settings"]>) {
    if (!data || guard.current) return;
    guard.current = true;
    setBusy(true);
    setError("");
    try {
      await invoke("calls_settings", { settings: { ...settings, ...change } });
      setConfirmed({ ...data.settings, ...settings, ...change });
      poll.refresh();
    } catch (e) {
      setError(errorText(e));
    } finally {
      guard.current = false;
      setBusy(false);
    }
  }
  const act = (kind: string, call = "", volume = 0) =>
    invoke("call_action", { kind, call, volume });
  return (
    <Panel
      title="Calls, when your setup supports them"
      description="Optional incoming SIM calls through Bluetooth and a compatible PipeWire telephony backend."
    >
      {poll.error ? (
        <Message>{poll.error}</Message>
      ) : !data ? (
        <p>Checking call support…</p>
      ) : !data.available ? (
        <Message>
          Compatible telephony backend unavailable. Connect your phone through
          the desktop’s Bluetooth settings and enable a compatible backend. LAN
          sharing remains available.
        </Message>
      ) : (
        <FieldGroup>
          <CheckField
            id="calls-enabled"
            label="Enable incoming Bluetooth calls"
            checked={!!settings?.enabled}
            disabled={busy}
            onChange={(enabled) => void save({ enabled })}
          />
          <Field>
            <FieldLabel htmlFor="call-gateway">Bluetooth phone</FieldLabel>
            <Select
              value={settings?.gateway || "none"}
              disabled={busy}
              onValueChange={(gateway) =>
                void save({ gateway: gateway === "none" ? "" : gateway })
              }
            >
              <SelectTrigger id="call-gateway">
                <SelectValue placeholder="Choose a phone" />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectItem value="none">Choose a phone</SelectItem>
                  {data.gateways.map((g) => (
                    <SelectItem key={g.id} value={g.id}>
                      {g.address}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
          </Field>
          <p className="text-sm text-muted-foreground">
            Audio transport: {data.transport}. Microphone: {data.microphone}/15.
          </p>
          {data.private_hidden ? (
            <Message>Unlock your desktop to use call controls.</Message>
          ) : (
            settings?.enabled && (
              <>
                <div className="action-row">
                  <Action action={() => act("route_here")}>
                    Route audio to laptop
                  </Action>
                  <Action action={() => act("mute")}>Mute microphone</Action>
                  <Action action={() => act("unmute")}>
                    Unmute microphone
                  </Action>
                </div>
                <SpeakerVolume
                  initial={data.speaker}
                  save={(volume) => act("speaker", "", volume)}
                />
                {data.calls.map((call) => (
                  <div
                    className="section-divider flex flex-col gap-3"
                    key={call.id}
                  >
                    <p>
                      {call.number || "Phone call"} · {call.state}
                    </p>
                    <div className="action-row">
                      {call.state === "incoming" || call.state === "waiting" ? (
                        <>
                          <Action action={() => act("answer", call.id)}>
                            Answer
                          </Action>
                          <Action action={() => act("decline", call.id)}>
                            Decline
                          </Action>
                        </>
                      ) : (
                        <Action action={() => act("hangup", call.id)}>
                          Hang up
                        </Action>
                      )}
                    </div>
                  </div>
                ))}
                <p className="text-sm text-muted-foreground">
                  Use your phone’s call screen to move audio back to your phone.
                  If Bluetooth disconnects, continue on the phone.
                </p>
              </>
            )
          )}
          <Message>{error}</Message>
        </FieldGroup>
      )}
    </Panel>
  );
}
function SpeakerVolume({
  initial,
  save,
}: {
  initial: number;
  save: (volume: number) => Promise<unknown>;
}) {
  const [volume, setVolume] = useState(initial);
  return (
    <FieldGroup>
      <Field>
        <FieldLabel htmlFor="speaker-volume">
          Speaker volume · {volume}/15
        </FieldLabel>
        <Slider
          id="speaker-volume"
          aria-label="Call speaker volume"
          value={[volume]}
          min={0}
          max={15}
          step={1}
          onValueChange={(values) => setVolume(values[0])}
        />
      </Field>
      <Action action={() => save(volume)}>Apply volume</Action>
    </FieldGroup>
  );
}
function Updates() {
  const [candidate, setCandidate] = useState<Update | null>(null),
    [approved, setApproved] = useState(false),
    [busy, setBusy] = useState(false),
    [message, setMessage] = useState("");
  const guard = useRef(false);
  async function run(kind: "check" | "download" | "install") {
    if (guard.current) return;
    guard.current = true;
    setBusy(true);
    setMessage("");
    try {
      if (kind === "check") {
        setCandidate(null);
        setApproved(false);
        const update = await invoke<Update>("check_updates");
        setCandidate({ ...update, downloaded: false });
        setMessage(update.message);
      } else if (kind === "download") {
        setApproved(false);
        setCandidate((old) => (old ? { ...old, downloaded: false } : null));
        const update = await invoke<Update>("download_update");
        setCandidate(update);
        setMessage("Downloaded and verified. Approve installation when ready.");
      } else {
        await invoke("install_update", { approved });
        setCandidate(null);
        setApproved(false);
        setMessage(
          "Portable installation updated. Restart Pingora when ready. The previous installation was preserved.",
        );
      }
    } catch (e) {
      setCandidate(null);
      setApproved(false);
      setMessage(errorText(e));
    } finally {
      guard.current = false;
      setBusy(false);
    }
  }
  return (
    <Panel
      title="Updates"
      description="Signed packages are checked before installation. Active work can defer an update."
    >
      <div className="action-row">
        <Button disabled={busy} onClick={() => void run("check")}>
          Check for updates
        </Button>
        <Button
          variant="outline"
          disabled={busy || !candidate || candidate.downloaded}
          onClick={() => void run("download")}
        >
          Download and verify
        </Button>
      </div>
      {candidate && (
        <p className="mt-4 text-sm">
          Version {candidate.version} ·{" "}
          {(candidate.size / 1024 / 1024).toFixed(1)} MiB
        </p>
      )}
      <div className="my-5">
        <CheckField
          id="approve-update"
          label="I approve installing this verified update"
          checked={approved}
          disabled={busy || !candidate?.downloaded}
          onChange={setApproved}
        />
      </div>
      <Button
        variant="outline"
        disabled={busy || !approved || !candidate?.downloaded}
        onClick={() => void run("install")}
      >
        Install update
      </Button>
      <Message>{message}</Message>
    </Panel>
  );
}
export function Settings() {
  const [diagnostics, setDiagnostics] = useState("");
  return (
    <div className="flex flex-col gap-6">
      <Panel title="Background connection" description="Closing the window keeps sharing connected. Open Pingora again from your application menu. Quitting stops the connection until you reopen it.">
        <Action action={async () => { if(window.confirm("Quit Pingora and disconnect this laptop?")) await invoke("quit_app"); }}>Quit Pingora</Action>
      </Panel>
      <Updates />
      <Panel
        title="Connection diagnostics"
        description="A small, redacted report for troubleshooting. Private notification and clipboard content stay out."
      >
        <div className="action-row">
          <Action
            action={async () =>
              setDiagnostics(
                JSON.stringify(await invoke("diagnostics"), null, 2),
              )
            }
          >
            Preview diagnostics
          </Action>
          <Action action={() => invoke("export_diagnostics")}>
            Export diagnostics
          </Action>
        </div>
        {diagnostics && (
          <pre className="mt-5 max-h-96 overflow-auto rounded-lg bg-muted p-4 text-xs">
            {diagnostics}
          </pre>
        )}
      </Panel>
      <Panel
        title="This is Pingora"
        description="Your phone and laptop, a little closer."
      >
        <p className="text-sm leading-relaxed text-muted-foreground">
          Pingora is an independent app. Its native connectivity includes reused
          KDE Connect components, with their original licenses and credits
          preserved.
        </p>
      </Panel>
    </div>
  );
}
