import localFont from "next/font/local";
import type { Metadata } from "next";
import Link from "next/link";
import Theme from "../components/theme";
import "./globals.css";
const dmsans = localFont({
  src: [
    { path: "../fonts/dmsans-400.ttf", weight: "400", style: "normal" },
    { path: "../fonts/dmsans-500.ttf", weight: "500", style: "normal" },
    { path: "../fonts/dmsans-600.ttf", weight: "600", style: "normal" },
    { path: "../fonts/dmsans-700.ttf", weight: "700", style: "normal" },
  ],
  variable: "--font-dmsans",
  display: "swap",
});
const manrope = localFont({
  src: [
    { path: "../fonts/manrope-400.ttf", weight: "400", style: "normal" },
    { path: "../fonts/manrope-500.ttf", weight: "500", style: "normal" },
    { path: "../fonts/manrope-600.ttf", weight: "600", style: "normal" },
    { path: "../fonts/manrope-700.ttf", weight: "700", style: "normal" },
    { path: "../fonts/manrope-800.ttf", weight: "800", style: "normal" },
  ],
  variable: "--font-manrope",
  display: "swap",
});
export const metadata: Metadata = {
  title: {
    default: "Pingora · A little closer, across devices",
    template: "%s · Pingora",
  },
  description:
    "An Android and Linux companion in development. Notifications, clipboard and sharing, with a little less back and forth.",
};
export default function Layout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: `try{let t=localStorage.getItem('pingora-theme');document.documentElement.dataset.theme=t==='dark'||(!t&&matchMedia('(prefers-color-scheme: dark)').matches)?'dark':'light'}catch{}`,
          }}
        />
      </head>
      <body className={`${dmsans.variable} ${manrope.variable}`}>
        <a className="skip" href="#main">
          Skip to content
        </a>
        <header className="header">
          <Link className="brand" href="/" aria-label="Pingora home">
            <span className="mark" aria-hidden="true" />
            Pingora
          </Link>
          <nav aria-label="Main navigation">
            <Link href="/#features">Features</Link>
            <Link href="/docs">Docs</Link>
            <Link href="/support">Support</Link>
            <Link href="/downloads">Downloads</Link>
          </nav>
          <Theme />
        </header>
        <main id="main">{children}</main>
        <footer>
          <Link className="brand" href="/">
            ↗ Pingora
          </Link>
          <p>A little less back and forth.</p>
          <nav aria-label="Footer">
            <Link href="/changelog">Changelog</Link>
            <a href="https://github.com/wrestle-R/Pingora">GitHub ↗</a>
            <Link href="/support">Get in touch</Link>
          </nav>
          <small>Made for Android + Linux. Still taking shape.</small>
        </footer>
      </body>
    </html>
  );
}
