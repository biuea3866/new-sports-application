/**
 * 어드민 콘솔 다크/라이트 모드 육안 검증용 캡쳐 스크립트 (일회성 검증 도구).
 * 사용: BASE_URL=http://localhost:3111 node scripts/verify-admin-dark.mjs <출력디렉토리>
 */
import { chromium } from "@playwright/test";
import { mkdirSync } from "fs";
import { join } from "path";

const BASE_URL = process.env["BASE_URL"] ?? "http://localhost:3111";
const OUT_DIR = process.argv[2] ?? "/tmp/admin-verify";

const PAGES = [
  { name: "01-관리자-홈", path: "/admin" },
  { name: "06-MCP-토큰-관리", path: "/admin/mcp/tokens" },
  { name: "07-MCP-감사로그", path: "/admin/mcp/audit-logs" },
  { name: "08-MCP-이상탐지", path: "/admin/mcp/anomalies" },
  { name: "09-MCP-사용분석", path: "/admin/mcp/usage-analytics" },
  { name: "10-MCP-연동문서", path: "/admin/mcp/docs" },
];

mkdirSync(OUT_DIR, { recursive: true });

const browser = await chromium.launch();

for (const theme of ["light", "dark"]) {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    deviceScaleFactor: 2,
  });
  // ThemeToggle 은 localStorage("theme") 를 우선 사용한다.
  await context.addInitScript((value) => {
    window.localStorage.setItem("theme", value);
  }, theme);

  const page = await context.newPage();
  for (const target of PAGES) {
    await page.goto(`${BASE_URL}${target.path}`, { waitUntil: "networkidle" });
    await page.waitForTimeout(1200);
    await page.screenshot({
      path: join(OUT_DIR, `${target.name}.${theme}.png`),
      fullPage: true,
    });
    console.log(`captured ${target.name}.${theme}`);
  }
  await context.close();
}

await browser.close();
console.log(`done → ${OUT_DIR}`);
