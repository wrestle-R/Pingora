(() => {
  const call = (command, args) => window.__TAURI__.core.invoke(command, args);
  const check = document.getElementById("check-updates"),
    download = document.getElementById("download-update"),
    install = document.getElementById("install-update"),
    approval = document.getElementById("approve-update"),
    status = document.getElementById("update-status");
  let staged = false,
    busy = false;
  function controls() {
    check.disabled = busy;
    download.disabled = busy || !download.dataset.available;
    install.disabled = busy || !staged || !approval.checked;
  }
  async function run(action) {
    busy = true;
    controls();
    try {
      await action();
    } catch (error) {
      status.textContent = String(error);
    } finally {
      busy = false;
      controls();
    }
  }
  check.addEventListener("click", () =>
    run(async () => {
      staged = false;
      download.dataset.available = "";
      approval.checked = false;
      status.textContent = "Checking signed release metadata…";
      const update = await call("check_updates");
      download.dataset.available = "true";
      status.textContent = `Version ${update.version} · ${(update.size / 1024 / 1024).toFixed(1)} MiB. ${update.message}`;
    }),
  );
  download.addEventListener("click", () =>
    run(async () => {
      status.textContent = "Downloading and verifying the signed artifact…";
      const update = await call("download_update");
      staged = update.downloaded;
      status.textContent = `Version ${update.version} downloaded and verified. Approve installation when ready.`;
    }),
  );
  approval.addEventListener("change", controls);
  install.addEventListener("click", () =>
    run(async () => {
      await call("install_update", { approved: approval.checked });
      staged = false;
      approval.checked = false;
      status.textContent =
        "Portable installation updated. Restart Pingora when ready. The previous installation was preserved.";
    }),
  );
  controls();
})();
