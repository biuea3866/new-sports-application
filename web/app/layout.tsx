import type { Metadata } from "next";
import localFont from "next/font/local";
import { ThemeToggle } from "@/components/ui/ThemeToggle";
import "./globals.css";

// 하이드레이션 전 FOUC(플래시) 방지 — localStorage 저장값 또는 시스템 기본을
// 최초 페인트 이전에 동기 반영한다. ThemeToggle의 초기 상태 로직과 대칭이다.
const THEME_INIT_SCRIPT = `
(function () {
  try {
    var stored = localStorage.getItem("theme");
    var isDark = stored ? stored === "dark" : window.matchMedia("(prefers-color-scheme: dark)").matches;
    document.documentElement.classList.toggle("dark", isDark);
  } catch (e) {}
})();
`;

const geistSans = localFont({
  src: "./fonts/GeistVF.woff",
  variable: "--font-geist-sans",
  weight: "100 900",
});
const geistMono = localFont({
  src: "./fonts/GeistMonoVF.woff",
  variable: "--font-geist-mono",
  weight: "100 900",
});

const appName = process.env["NEXT_PUBLIC_APP_NAME"] ?? "Sports Application";

export const metadata: Metadata = {
  title: appName,
  description: "생활 체육 플랫폼 — 시설 예약, 경기 티켓, 스포츠 물품 구매",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
      </head>
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased`}
      >
        <div className="flex justify-end p-2">
          <ThemeToggle />
        </div>
        {children}
      </body>
    </html>
  );
}
