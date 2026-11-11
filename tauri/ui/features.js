(() => {
  const call = (command, args) => window.__TAURI__.core.invoke(command, args);
  const root = document.getElementById("sharing-peers");
  const inbox = document.getElementById("inbox");
  const status = document.getElementById("feature-status");
  const search = document.getElementById("inbox-search");
  let loading = false;
  let installedApps = [];
  call("desktop_apps")
    .then((apps) => {
      installedApps = apps;
      signature = "";
    })
    .catch(() => {});
  let signature = "";
  function node(tag, text, className) {
    const el = document.createElement(tag);
    if (text !== undefined) el.textContent = text;
    if (className) el.className = className;
    return el;
  }
  function button(label, action) {
    const el = node("button", label);
    el.addEventListener("click", async () => {
      el.disabled = true;
      try {
        await action();
      } catch (error) {
        status.textContent =
          typeof error === "string" ? error : "Could not complete the action.";
      } finally {
        el.disabled = false;
      }
    });
    return el;
  }
  async function perform(peer, kind, data = {}) {
    await call("feature_action", { deviceId: peer, kind, data });
    status.textContent = "Action sent for this connection.";
  }
  async function copy(text) {
    try {
      await navigator.clipboard.writeText(text);
      status.textContent = "Copied.";
    } catch {
      status.textContent =
        "Clipboard access is unavailable here. Select the text and copy it manually.";
    }
  }
  function render(snapshot, peers) {
    root.replaceChildren();
    for (const peer of peers.filter((p) => p.paired)) {
      const section = node("article", undefined, "peer");
      section.append(node("h3", peer.name));
      const remote = snapshot.remote_policies[peer.id];
      section.append(
        node(
          "p",
          remote
            ? `Phone sharing: ${remote.paused ? "paused" : "ready"}. Enabled: ${
                Object.entries(remote.enabled)
                  .filter(([, on]) => on)
                  .map(([name]) => name)
                  .join(", ") || "none"
              }.`
            : "Waiting for the phone’s feature session.",
        ),
      );
      const policy = snapshot.policies[peer.id] || {
        paused: false,
        show_locked: false,
        enabled: {},
        desktop_apps: [],
      };
      for (const feature of [
        "paused",
        "show_locked",
        "notifications",
        "clipboard",
        "files",
        "folders",
        "battery",
        "ring",
        "slumber",
      ]) {
        const label = node("label");
        const check = document.createElement("input");
        check.type = "checkbox";
        check.checked = ["paused", "show_locked"].includes(feature)
          ? policy[feature]
          : !!policy.enabled[feature];
        check.addEventListener("change", async () => {
          check.disabled = true;
          const next = structuredClone(policy);
          if (["paused", "show_locked"].includes(feature))
            next[feature] = check.checked;
          else next.enabled[feature] = check.checked;
          try {
            await call("feature_policy", { deviceId: peer.id, policy: next });
            Object.assign(policy, next);
            status.textContent = "Sharing settings saved.";
            signature = "";
            await refresh();
          } catch (error) {
            check.checked = !check.checked;
            status.textContent = String(error);
          } finally {
            check.disabled = false;
          }
        });
        label.append(
          check,
          document.createTextNode(
            feature === "paused"
              ? " Pause sharing"
              : feature === "show_locked"
                ? " Show private notification content while locked (this laptop only)"
                : ` Allow ${feature}`,
          ),
        );
        section.append(label);
      }
      const appLabel = node(
        "label",
        "Desktop apps allowed to share notifications",
      );
      const appSelect = document.createElement("select");
      appSelect.multiple = true;
      appSelect.size = 6;
      appSelect.setAttribute("aria-label", "Desktop notification apps");
      for (const [id, name] of installedApps) {
        const option = node("option", `${name} (${id})`);
        option.value = id;
        option.selected = policy.desktop_apps.includes(id);
        appSelect.append(option);
      }
      appSelect.addEventListener("change", async () => {
        const next = structuredClone(policy);
        next.desktop_apps = Array.from(appSelect.selectedOptions).map(
          (o) => o.value,
        );
        try {
          await call("feature_policy", { deviceId: peer.id, policy: next });
          Object.assign(policy, next);
          status.textContent = "Desktop app selection saved.";
        } catch (error) {
          status.textContent = String(error);
        }
      });
      appLabel.append(appSelect);
      section.append(appLabel);
      const battery = snapshot.battery[peer.id];
      section.append(
        node(
          "p",
          battery
            ? `Phone battery: ${battery.level}%${battery.charging ? " · charging" : ""}`
            : "Phone battery: unavailable",
        ),
      );
      const controls = node("div", undefined, "peer-actions");
      controls.append(
        button("Refresh battery", () => perform(peer.id, "battery_request")),
        button("Ring phone (30 seconds)", () => perform(peer.id, "ring")),
        button("Stop ringing", () => perform(peer.id, "ring_stop")),
      );
      controls.append(
        button("Choose file to send", async () => {
          await call("offer_file", { deviceId: peer.id });
          status.textContent =
            "File offered. Waiting for approval on the phone.";
        }),
      );
      controls.append(
        button("Browse shared phone folders", () =>
          perform(peer.id, "folder_list", {}),
        ),
      );
      section.append(controls);
      for (const offer of snapshot.transfers.filter(
        (item) => item.peer === peer.id,
      )) {
        const row = node("div");
        row.append(
          node(
            "p",
            `${offer.name} · ${offer.received}/${offer.size} bytes · ${offer.state}`,
          ),
        );
        if (offer.state === "approval_required")
          row.append(
            button("Accept transfer", () =>
              perform(peer.id, "file_accept", { id: offer.id }),
            ),
          );
        if (offer.state !== "saved")
          row.append(
            button("Reject or cancel transfer", () =>
              perform(peer.id, "file_cancel", { id: offer.id }),
            ),
          );
        else
          row.append(
            node("p", "Saved to Downloads/Pingora. Nothing was opened."),
          );
        section.append(row);
      }
      const listing = snapshot.folders[peer.id];
      if (listing) {
        section.append(
          button("Shared folder roots", () =>
            perform(peer.id, "folder_list", {}),
          ),
        );
        if (listing.error)
          section.append(
            node("p", "Folder access is unavailable or was revoked."),
          );
        for (const entry of listing.entries || []) {
          section.append(
            button(
              `${entry.directory ? "Browse" : "Request download"}: ${entry.name}`,
              () =>
                perform(
                  peer.id,
                  entry.directory ? "folder_list" : "folder_read",
                  { node: entry.id },
                ),
            ),
          );
        }
      }
      const linkLabel = node("label", "Link to send (HTTP or HTTPS)");
      const link = document.createElement("input");
      link.type = "url";
      link.maxLength = 4096;
      linkLabel.append(link);
      section.append(linkLabel);
      section.append(
        button("Send link", async () => {
          await perform(peer.id, "url", { url: link.value });
          link.value = "";
        }),
      );
      const label = node("label", "Text to send to the phone clipboard");
      const input = document.createElement("textarea");
      input.maxLength = 16384;
      input.rows = 3;
      label.append(input);
      section.append(label);
      section.append(
        button("Send clipboard text", async () => {
          await perform(peer.id, "clipboard", { text: input.value });
          input.value = "";
        }),
      );
      root.append(section);
    }
    if (!root.children.length)
      root.append(node("p", "Pair a device to choose sharing features."));
    for (const [peer, url] of snapshot.links) {
      const row = node("p", url);
      row.append(
        button("Open received link", () => call("open_received_link", { url })),
      );
      root.append(row);
    }
    if (snapshot.clipboard_available)
      root.append(
        button("Copy received phone clipboard", async () =>
          copy(await call("received_clipboard")),
        ),
      );
    document.getElementById("inbox-privacy").textContent =
      snapshot.private_hidden
        ? "Private inbox content is hidden while the desktop is locked or its lock state is unknown."
        : "";
    inbox.replaceChildren();
    for (const item of snapshot.history) {
      const article = node("article", undefined, "notice");
      article.append(
        node("h3", item.sender || item.title || item.app),
        node(
          "p",
          `${item.app} · ${item.title} · ${new Date(item.received).toLocaleString()}${item.active ? "" : " · saved history"}`,
        ),
      );
      const details = node("details");
      details.append(
        node("summary", item.body, "notice-preview"),
        node("p", item.body, "notice-body"),
      );
      article.append(details);
      const actions = node("div", undefined, "peer-actions");
      if (item.active) {
        actions.append(
          button("Dismiss source notification", () =>
            perform(item.peer, "notification_action", {
              source: item.source,
              version: item.version,
              action: "dismiss",
            }),
          ),
        );
        if (item.read)
          actions.append(
            button("Mark as read", () =>
              perform(item.peer, "notification_action", {
                source: item.source,
                version: item.version,
                action: "read",
              }),
            ),
          );
        if (item.reply) {
          const reply = document.createElement("textarea");
          reply.rows = 2;
          reply.maxLength = 4096;
          reply.setAttribute("aria-label", "Reply text");
          article.append(reply);
          actions.append(
            button("Send reply", async () => {
              await perform(item.peer, "notification_action", {
                source: item.source,
                version: item.version,
                action: "reply",
                text: reply.value,
              });
              reply.value = "";
            }),
          );
        }
      }
      const code = item.body.match(
        /(?:otp|verification|passcode|code)\D{0,30}(\d{4,8})\b/i,
      )?.[1];
      if (code) actions.append(button("Copy code", () => copy(code)));
      article.append(actions);
      inbox.append(article);
    }
    if (!snapshot.history.length)
      inbox.append(
        node(
          "p",
          "No saved notifications match. Enable notification sharing on both devices, select phone apps and allow Android notification access.",
        ),
      );
  }
  async function refresh() {
    if (loading || document.hidden) return;
    loading = true;
    try {
      const [snapshot, peers] = await Promise.all([
        call("feature_snapshot", { query: search.value }),
        call("device_list"),
      ]);
      const next = JSON.stringify([snapshot, peers]);
      const editing =
        (root.contains(document.activeElement) ||
          inbox.contains(document.activeElement)) &&
        document.activeElement.matches(
          "textarea,input[type=url],input[type=text],select",
        );
      if (next !== signature && (!editing || snapshot.private_hidden)) {
        render(snapshot, peers);
        signature = next;
      }
    } catch {
      status.textContent =
        "Feature connection unavailable. Start the LAN service and refresh.";
    } finally {
      loading = false;
    }
  }
  document
    .getElementById("preview-diagnostics")
    .addEventListener("click", async () => {
      try {
        document.getElementById("diagnostics-preview").textContent =
          JSON.stringify(await call("diagnostics"), null, 2);
      } catch (error) {
        status.textContent = String(error);
      }
    });
  document
    .getElementById("export-diagnostics")
    .addEventListener("click", async () => {
      try {
        await call("export_diagnostics");
        status.textContent = "Diagnostics exported.";
      } catch (error) {
        status.textContent = String(error);
      }
    });
  document
    .getElementById("clear-history")
    .addEventListener("click", async () => {
      try {
        await call("clear_history");
        signature = "";
        await refresh();
      } catch (error) {
        status.textContent = String(error);
      }
    });
  search.addEventListener("input", () => {
    signature = "";
    refresh();
  });
  document.addEventListener("visibilitychange", refresh);
  setInterval(refresh, 2000);
  refresh();
})();
