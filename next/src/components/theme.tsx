"use client";
import { useEffect, useState } from "react";
export default function Theme() {
  const [dark, setDark] = useState(false);
  useEffect(() => {
    setDark(document.documentElement.dataset.theme === "dark");
  }, []);
  function toggle() {
    const next = !dark;
    setDark(next);
    document.documentElement.dataset.theme = next ? "dark" : "light";
    try {
      localStorage.setItem("pingora-theme", next ? "dark" : "light");
    } catch {}
  }
  return (
    <button
      className="theme"
      onClick={toggle}
      aria-label="Dark theme"
      aria-pressed={dark}
    >
      <span aria-hidden="true">☀</span>
      <span aria-hidden="true">☾</span>
      <span className="theme-knob" />
    </button>
  );
}
