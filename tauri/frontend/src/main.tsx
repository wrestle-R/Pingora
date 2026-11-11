import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import "./index.css";
try {
  const theme = localStorage.getItem("pingora-theme") || "system";
  document.documentElement.classList.toggle(
    "dark",
    theme === "dark" ||
      (theme === "system" &&
        matchMedia("(prefers-color-scheme: dark)").matches),
  );
} catch {}
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
