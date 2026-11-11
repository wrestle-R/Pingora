const invoke = (command, args) => window.__TAURI__.core.invoke(command, args);
const button = document.getElementById("refresh");
const start = document.getElementById("start-lan");
const stop = document.getElementById("stop-lan");
const set = (id, text) => {
  document.getElementById(id).textContent = text;
};
const verified = new Map();
let busy = false;
let refreshing = false;
function actionButton(label, callback, disabled = false) {
  const button = document.createElement("button");
  button.textContent = label;
  button.disabled = disabled;
  button.addEventListener("click", callback);
  return button;
}
async function act(command, args, message) {
  if (busy) return;
  busy = true;
  for (const control of document.querySelectorAll("button"))
    control.disabled = true;
  let result = message;
  try {
    await invoke(command, args);
  } catch (error) {
    result =
      typeof error === "string"
        ? error
        : "The device state changed. Refresh and try again.";
  } finally {
    busy = false;
    await refresh();
    set("device-status", result);
  }
}
function renderDevices(peers) {
  const container = document.getElementById("devices");
  container.replaceChildren();
  const pending = new Set();
  for (const peer of peers) {
    const row = document.createElement("article");
    row.className = "peer";
    const title = document.createElement("h3");
    title.textContent = peer.name;
    const state = document.createElement("p");
    state.textContent = peer.paired
      ? peer.reachable
        ? "Paired · reachable"
        : "Paired · offline"
      : peer.requested_by_peer
        ? "Phone requests pairing"
        : peer.requested
          ? "Waiting for approval on the phone"
          : peer.reachable
            ? "Available · not paired"
            : "Offline · not paired";
    row.append(title, state);
    const controls = document.createElement("div");
    controls.className = "peer-actions";
    const pair = (action) =>
      act(
        "pairing",
        {
          deviceId: peer.id,
          action,
          expectedVerification: action === "accept" ? peer.verification : null,
        },
        "Pairing state updated.",
      );
    if (peer.requested || peer.requested_by_peer) {
      pending.add(peer.id);
      const code = document.createElement("code");
      code.textContent = peer.verification || "Verification unavailable";
      row.append(code);
      if (verified.get(peer.id) !== peer.verification) verified.delete(peer.id);
      if (peer.requested_by_peer) {
        const label = document.createElement("label");
        const check = document.createElement("input");
        check.type = "checkbox";
        check.checked =
          verified.get(peer.id) === peer.verification && !!peer.verification;
        label.append(
          check,
          document.createTextNode(" I checked this value on my phone"),
        );
        const accept = actionButton(
          "Accept pairing",
          () => pair("accept"),
          !check.checked || busy,
        );
        check.addEventListener("change", () => {
          check.checked
            ? verified.set(peer.id, peer.verification)
            : verified.delete(peer.id);
          accept.disabled = !check.checked || !peer.verification || busy;
        });
        row.append(label);
        controls.append(accept);
      }
      controls.append(
        actionButton("Cancel pairing", () => pair("cancel"), busy),
      );
    } else if (peer.paired) {
      controls.append(
        actionButton(
          "Unpair",
          () => {
            if (
              window.confirm(
                "Unpair this device? It will need approval again before reconnecting.",
              )
            )
              pair("unpair");
          },
          busy,
        ),
      );
    } else if (peer.reachable)
      controls.append(
        actionButton("Request pairing", () => pair("request"), busy),
      );
    row.append(controls);
    container.append(row);
  }
  for (const id of verified.keys()) if (!pending.has(id)) verified.delete(id);
  if (!peers.length) {
    const empty = document.createElement("p");
    empty.textContent =
      "No devices found. Open Pingora on your phone on the same local network, then refresh. An untrusted-network warning on Android means automatic discovery is restricted.";
    container.append(empty);
  }
}
async function refresh() {
  if (refreshing) return;
  refreshing = true;
  button.disabled = true;
  try {
    const [state, owned] = await Promise.all([
      invoke("health"),
      invoke("owns_lan"),
    ]);
    set("pair", state.paired ? "Paired" : "Not paired");
    set("transport", state.transport);
    set("notifications", state.notifications);
    set(
      "slumber",
      state.slumber.available
        ? state.slumber.active
          ? "Active"
          : "Available · inactive"
        : "Unavailable",
    );
    set("lid", state.slumber.available ? state.slumber.lid_action : "Unknown");
    set("message", "Health refreshed. Slumber settings are unchanged.");
    start.disabled = state.native_service || owned || busy;
    stop.disabled = !owned || busy;
    if (state.native_service) {
      const peers = await invoke("device_list");
      renderDevices(peers);
      set(
        "device-status",
        `${peers.length} device(s) known. ${peers.filter((peer) => peer.paired && peer.reachable).length} paired and reachable.`,
      );
    } else {
      renderDevices([]);
      set(
        "device-status",
        owned
          ? "Starting the LAN service…"
          : "LAN service stopped. Start it to discover and pair your phone.",
      );
    }
  } catch {
    for (const id of ["pair", "transport", "notifications", "slumber", "lid"])
      set(id, "Unavailable");
    set(
      "message",
      "Could not read native health. Run this window through the Pingora desktop binary.",
    );
    document.getElementById("devices").replaceChildren();
    verified.clear();
    start.disabled = true;
    stop.disabled = true;
    set(
      "device-status",
      "Could not read device status. Refresh before attempting pairing.",
    );
  } finally {
    button.disabled = false;
    refreshing = false;
  }
}
button.addEventListener("click", refresh);
start.addEventListener("click", () =>
  act("start_lan", {}, "Starting the local LAN service…"),
);
stop.addEventListener("click", () =>
  act("stop_lan", {}, "Stopped the owned LAN service."),
);
document.addEventListener("visibilitychange", () => {
  if (!document.hidden) refresh();
});
setInterval(() => {
  if (!document.hidden && !busy) refresh();
}, 5000);
refresh();
