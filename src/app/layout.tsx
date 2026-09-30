import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: "Webtoon Clone",
  description: "Read webtoons and manga from MangaDex",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <header className="header">
          <Link href="/" className="logo">Webtoon Clone</Link>
          <form action="/search">
            <input name="q" placeholder="Search series" />
          </form>
        </header>
        {children}
      </body>
    </html>
  );
}
