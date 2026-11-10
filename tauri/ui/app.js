const button = document.getElementById("refresh");
const set = (id, text) => {
  document.getElementById(id).textContent = text;
};
async function refresh() {
  button.disabled = true;
  try {
    const state = await window.__TAURI__.core.invoke("health");
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
    set("message", "Health refreshed. No settings changed.");
  } catch {
    for (const id of ["pair", "transport", "notifications", "slumber", "lid"])
      set(id, "Unavailable");
    set(
      "message",
      "Could not read native health. Run this window through the Pingora desktop binary.",
    );
  } finally {
    button.disabled = false;
  }
}
button.addEventListener("click", refresh);
refresh();
