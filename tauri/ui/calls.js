(() => {
  const call = (command, args) => window.__TAURI__.core.invoke(command, args);
  const root = document.getElementById("calls-controls"),
    status = document.getElementById("calls-status");
  let loading = false,
    signature = "";
  function el(tag, text) {
    const value = document.createElement(tag);
    if (text !== undefined) value.textContent = text;
    return value;
  }
  function button(label, action) {
    const b = el("button", label);
    b.addEventListener("click", async () => {
      b.disabled = true;
      try {
        await action();
        signature = "";
        await refresh();
      } catch (error) {
        status.textContent = String(error);
      } finally {
        b.disabled = false;
      }
    });
    return b;
  }
  async function refresh() {
    if (loading || document.hidden) return;
    loading = true;
    try {
      const snapshot = await call("calls_snapshot"),
        next = JSON.stringify(snapshot);
      if (signature === next) return;
      if (
        root.contains(document.activeElement) &&
        document.activeElement.matches("select,input[type=range]")
      )
        return;
      signature = next;
      root.replaceChildren();
      if (!snapshot.available) {
        root.append(
          el(
            "p",
            "Compatible PipeWire telephony backend unavailable. LAN features remain usable. Connect your phone through the desktop’s Bluetooth settings and enable a compatible telephony backend.",
          ),
        );
        return;
      }
      const settings = snapshot.settings;
      const label = el("label"),
        enabled = document.createElement("input");
      enabled.type = "checkbox";
      enabled.checked = settings.enabled;
      label.append(
        enabled,
        document.createTextNode(" Enable optional incoming Bluetooth calls"),
      );
      root.append(label);
      const select = document.createElement("select");
      select.setAttribute("aria-label", "Bluetooth phone for calls");
      select.append(el("option", "Choose a Bluetooth phone"));
      select.firstChild.value = "";
      for (const gateway of snapshot.gateways) {
        const option = el("option", gateway.address);
        option.value = gateway.id;
        option.selected = settings.gateway === gateway.id;
        select.append(option);
      }
      root.append(select);
      async function save() {
        await call("calls_settings", {
          settings: {
            ...settings,
            enabled: enabled.checked,
            gateway: select.value,
          },
        });
        signature = "";
        status.textContent = "Call settings saved.";
      }
      enabled.addEventListener("change", () =>
        save().catch((error) => {
          enabled.checked = !enabled.checked;
          status.textContent = String(error);
        }),
      );
      select.addEventListener("change", () =>
        save().catch((error) => {
          status.textContent = String(error);
        }),
      );
      root.append(
        el(
          "p",
          `Bluetooth audio transport: ${snapshot.transport}. Microphone level: ${snapshot.microphone}/15. Speaker level: ${snapshot.speaker}/15.`,
        ),
      );
      const act = (kind, id = "", volume = 0) =>
        call("call_action", { kind, call: id, volume });
      if (snapshot.private_hidden)
        root.append(el("p", "Unlock the desktop to use call controls."));
      else if (settings.enabled) {
        root.append(
          button("Route call audio to laptop", () => act("route_here")),
          button("Mute microphone", () => act("mute")),
          button("Unmute microphone", () => act("unmute")),
        );
        const volume = document.createElement("input");
        volume.type = "range";
        volume.min = "0";
        volume.max = "15";
        volume.value = String(snapshot.speaker);
        volume.setAttribute("aria-label", "Call speaker volume");
        volume.addEventListener("change", () =>
          act("speaker", "", Number(volume.value)).catch((error) => {
            status.textContent = String(error);
          }),
        );
        root.append(volume);
        for (const current of snapshot.calls) {
          const row = el("article");
          row.append(
            el("p", `${current.number || "Phone call"} · ${current.state}`),
          );
          if (current.state === "incoming" || current.state === "waiting")
            row.append(
              button("Answer", () => act("answer", current.id)),
              button("Decline", () => act("decline", current.id)),
            );
          else row.append(button("Hang up", () => act("hangup", current.id)));
          root.append(row);
        }
        root.append(
          el(
            "p",
            "Use your phone’s call screen to move audio back to the phone. If Bluetooth disconnects, continue on the phone.",
          ),
        );
      }
    } catch (error) {
      status.textContent = String(error);
    } finally {
      loading = false;
    }
  }
  setInterval(refresh, 3000);
  document.addEventListener("visibilitychange", refresh);
  refresh();
})();
