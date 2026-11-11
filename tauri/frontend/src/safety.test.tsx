import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Devices } from "./components/devices";
import { Inbox } from "./components/inbox";
import { Sharing } from "./components/sharing";
import App from "./App";
import { Settings } from "./components/settings";
import {
  invoke,
  uniquePeers,
  type Peer,
  type FeatureSnapshot,
} from "./lib/native";
vi.mock("@tauri-apps/api/core", () => ({ invoke: vi.fn() }));
const native = vi.mocked(invoke),
  refresh = vi.fn();
const phone: Peer = {
  id: "one",
  name: "Test phone",
  paired: true,
  reachable: true,
  requested: false,
  requested_by_peer: false,
  verification: "",
};
const snapshot: FeatureSnapshot = {
  policies: {},
  remote_policies: {},
  history: [],
  battery: {},
  clipboard_available: false,
  private_hidden: false,
  transfers: [],
  folders: {},
  links: [],
};
beforeEach(() => {
  native.mockReset();
  refresh.mockReset();
  native.mockResolvedValue([]);
});
describe("native frontend safety boundaries", () => {
  it("renders the React workspace and supports keyboard theme selection", async () => {
    const user = userEvent.setup();
    native.mockImplementation(async (command) =>
      command === "device_list"
        ? []
        : command === "feature_snapshot"
          ? snapshot
          : command === "owns_lan"
            ? false
            : { native_service: false },
    );
    render(<App />);
    await user.click(screen.getByRole("button", { name: "Appearance" }));
    await user.keyboard("{ArrowDown}{ArrowDown}{Enter}");
    expect(screen.queryByRole("menu")).toBeNull();
    await user.click(screen.getByRole("button", { name: "Sharing" }));
    expect(
      screen.getByRole("heading", { level: 1, name: "Sharing" }),
    ).toBeTruthy();
  });
  it("deduplicates protocol identities without merging trust by display name", () => {
    const other = { ...phone, id: "two", paired: false };
    expect(uniquePeers([other, { ...phone, paired: false }, phone])).toEqual([
      phone,
      other,
    ]);
  });
  it("requires a fresh comparison when the pairing value changes", async () => {
    const user = userEvent.setup();
    const pending = {
      ...phone,
      paired: false,
      requested_by_peer: true,
      verification: "synthetic-one",
    };
    const view = render(
      <Devices
        peers={[pending]}
        health={null}
        owned={false}
        error=""
        refresh={refresh}
      />,
    );
    const accept = screen.getByRole("button", { name: "Accept pairing" });
    expect((accept as HTMLButtonElement).disabled).toBe(true);
    await user.click(screen.getByRole("checkbox"));
    await user.click(accept);
    expect(native).toHaveBeenCalledWith("pairing", {
      deviceId: "one",
      action: "accept",
      expectedVerification: "synthetic-one",
    });
    view.rerender(
      <Devices
        peers={[{ ...pending, verification: "synthetic-two" }]}
        health={null}
        owned={false}
        error=""
        refresh={refresh}
      />,
    );
    expect(
      (
        screen.getByRole("button", {
          name: "Accept pairing",
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(true);
  });
  it("removes visible notification text and actions when privacy becomes hidden", () => {
    const item = {
      peer: "one",
      source: "synthetic",
      version: "1",
      app: "Test app",
      sender: "Test sender",
      title: "Test",
      body: "Synthetic private message",
      received: 0,
      conversation: "",
      active: true,
      reply: true,
      read: true,
    };
    const view = render(
      <Inbox
        snapshot={{ ...snapshot, history: [item] }}
        query=""
        setQuery={() => {}}
        refresh={refresh}
      />,
    );
    expect(screen.getByText(item.body)).toBeTruthy();
    view.rerender(
      <Inbox
        snapshot={{ ...snapshot, history: [item], private_hidden: true }}
        query=""
        setQuery={() => {}}
        refresh={refresh}
      />,
    );
    expect(screen.queryByText(item.body)).toBeNull();
    expect(screen.queryByRole("button", { name: "Send reply" })).toBeNull();
  });
  it("keeps entered text on refresh and preserves the last confirmed permission during a second save", async () => {
    const user = userEvent.setup();
    render(<Sharing peers={[phone]} snapshot={snapshot} refresh={refresh} />);
    await user.type(
      screen.getByLabelText("Link to send"),
      "https://example.com",
    );
    await user.click(screen.getByLabelText("Allow clipboard"));
    await waitFor(() =>
      expect(native).toHaveBeenCalledWith(
        "feature_policy",
        expect.objectContaining({
          policy: expect.objectContaining({ enabled: { clipboard: true } }),
        }),
      ),
    );
    await user.click(screen.getByLabelText("Allow files"));
    await waitFor(() =>
      expect(native).toHaveBeenCalledWith(
        "feature_policy",
        expect.objectContaining({
          policy: expect.objectContaining({
            enabled: { clipboard: true, files: true },
          }),
        }),
      ),
    );
    expect(
      (screen.getByLabelText("Link to send") as HTMLInputElement).value,
    ).toBe("https://example.com");
  });
  it("clears downloaded approval when a new update check fails", async () => {
    const user = userEvent.setup();
    native.mockImplementation(async (command) => {
      if (command === "check_updates")
        return {
          version: "0.1.2",
          size: 1024,
          downloaded: false,
          message: "Test candidate",
        };
      if (command === "download_update")
        return { version: "0.1.2", size: 1024, downloaded: true, message: "" };
      return undefined;
    });
    render(<Settings />);
    await user.click(screen.getByRole("button", { name: "Check for updates" }));
    await user.click(
      screen.getByRole("button", { name: "Download and verify" }),
    );
    await user.click(
      screen.getByLabelText("I approve installing this verified update"),
    );
    expect(
      (
        screen.getByRole("button", {
          name: "Install update",
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(false);
    native.mockRejectedValueOnce("Synthetic offline");
    await user.click(screen.getByRole("button", { name: "Check for updates" }));
    expect(
      (
        screen.getByRole("button", {
          name: "Install update",
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(true);
    expect(
      (screen.getByRole("checkbox") as HTMLInputElement).getAttribute(
        "data-state",
      ),
    ).toBe("unchecked");
  });
});
