/**
 * 데이터 의존 화면(05 감사 로그 · 06 토큰 목록 · 07~09) 검증 캡쳐 (일회성 검증 도구).
 *
 * BE의 실제 직렬화(JsonInclude.NON_NULL)를 재현한다 — nullable 필드는 **키 자체를 생략**한다.
 * 이 payload로 05가 정상 렌더되고 06에 `Invalid Date`가 없으면 결함 #1·#2가 해소된 것이다.
 *
 * 사용: BASE_URL=http://localhost:3111 node scripts/verify-admin-data.mjs <출력디렉토리>
 */
import { chromium } from "@playwright/test";
import { mkdirSync } from "fs";
import { join } from "path";

const BASE_URL = process.env["BASE_URL"] ?? "http://localhost:3111";
const OUT_DIR = process.argv[2] ?? "/tmp/admin-data";
/**
 * CONTRACT=omitted : 현재 BE (전역 매퍼 NON_NULL) — null 필드의 키가 생략된다.
 * CONTRACT=null    : BE 매퍼 수정 후 — null 필드가 `null` 로 명시된다.
 * 두 계약 모두에서 화면이 부러지지 않아야 한다.
 */
const CONTRACT = process.env["CONTRACT"] ?? "omitted";

/** CONTRACT=null 이면 생략된 nullable 키를 명시적 null 로 채워 넣는다. */
function applyContract(value, nullableKeys) {
  if (CONTRACT !== "null") return value;
  const filled = { ...value };
  for (const key of nullableKeys) {
    if (filled[key] === undefined) filled[key] = null;
  }
  return filled;
}

const SNAPSHOT = {
  key: "virtualqueue.enabled",
  type: "RELEASE",
  status: "ACTIVE",
  description: "가상 대기열 킬스위치",
  strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
};

// CREATED 로그: before 키가 아예 없다 (NON_NULL 직렬화).
const AUDIT_LOGS = {
  content: [
    {
      changeType: "UPDATED",
      actorUserId: 12,
      before: { ...SNAPSHOT, strategy: { strategyType: "GLOBAL_TOGGLE", enabled: false } },
      after: SNAPSHOT,
      occurredAt: "2026-07-30T14:20:00Z",
    },
    {
      changeType: "CREATED",
      actorUserId: 12,
      after: { ...SNAPSHOT, strategy: { strategyType: "GLOBAL_TOGGLE", enabled: false } },
      occurredAt: "2026-07-29T09:50:00Z",
    },
  ],
  totalElements: 2,
  totalPages: 1,
  pageNumber: 0,
  pageSize: 10,
};

// 토큰 목록: expiresAt·lastUsedAt 키 생략 (무기한·미사용).
const TOKENS = {
  tokens: [
    { tokenId: 1, name: "Claude Desktop 운영봇", status: "ACTIVE", createdAt: "2026-07-31T02:00:00Z" },
    {
      tokenId: 2,
      name: "Cursor 개발 연동",
      status: "ACTIVE",
      expiresAt: "2026-12-31T00:00:00Z",
      lastUsedAt: "2026-07-31T08:12:00Z",
      createdAt: "2026-07-31T03:00:00Z",
    },
    { tokenId: 3, name: "폐기된 토큰", status: "REVOKED", createdAt: "2026-06-01T00:00:00Z" },
  ],
};

const MCP_AUDIT = {
  content: [
    { id: 1, toolName: "search_facilities", statusCode: 200, latencyMs: 132, calledAt: "2026-07-31T08:12:00Z" },
    { id: 2, toolName: "create_booking", statusCode: 500, latencyMs: 2140, calledAt: "2026-07-31T08:15:00Z" },
  ],
  totalElements: 2,
  totalPages: 1,
  pageNumber: 0,
  pageSize: 20,
};

const ANOMALIES = {
  content: [
    {
      id: 1,
      tokenId: 2,
      currentHourCount: 1820,
      baselineAverage: 42.5,
      status: "OPEN",
      detectedAt: "2026-07-31T07:00:00Z",
    },
    {
      id: 2,
      tokenId: 1,
      currentHourCount: 900,
      baselineAverage: 120.0,
      status: "FALSE_POSITIVE",
      detectedAt: "2026-07-30T22:00:00Z",
    },
  ],
  totalElements: 2,
  totalPages: 1,
  pageNumber: 0,
  pageSize: 20,
};

const USAGE = {
  errorRateStat: { totalCount: 15420, errorCount: 37, errorRatePercent: 0.24 },
  dailyStats: [
    { date: "2026-07-28", callCount: 3200 },
    { date: "2026-07-29", callCount: 4100 },
    { date: "2026-07-30", callCount: 3860 },
    { date: "2026-07-31", callCount: 4260 },
  ],
  toolCallStats: [
    { toolName: "search_facilities", callCount: 8200 },
    { toolName: "create_booking", callCount: 4300 },
    { toolName: "list_products", callCount: 2920 },
  ],
  toolLatencyStats: [
    { toolName: "search_facilities", p95LatencyMs: 180 },
    { toolName: "create_booking", p95LatencyMs: 940 },
    { toolName: "list_products", p95LatencyMs: 260 },
  ],
  // lastCalledAt 키 생략 케이스 포함
  tokenUsageStats: [
    { tokenId: 1, callCount: 9100, errorCount: 12, errorRatePercent: 0.13, lastCalledAt: "2026-07-31T08:12:00Z" },
    { tokenId: 3, callCount: 0, errorCount: 0, errorRatePercent: 0 },
  ],
};

const PAGES = [
  { name: "05-피처플래그-감사로그", path: "/admin/feature-flags/virtualqueue.enabled/audit-logs" },
  { name: "06-MCP-토큰-관리", path: "/admin/mcp/tokens" },
  { name: "07-MCP-감사로그", path: "/admin/mcp/audit-logs" },
  { name: "08-MCP-이상탐지", path: "/admin/mcp/anomalies" },
  { name: "09-MCP-사용분석", path: "/admin/mcp/usage-analytics" },
];

mkdirSync(OUT_DIR, { recursive: true });
const browser = await chromium.launch();

function json(body) {
  return { status: 200, contentType: "application/json", body: JSON.stringify(body) };
}

for (const theme of ["light", "dark"]) {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    deviceScaleFactor: 2,
  });
  await context.addInitScript((value) => {
    window.localStorage.setItem("theme", value);
  }, theme);

  await context.route("**/api/**", async (route) => {
    const url = route.request().url();
    if (url.includes("/audit-logs") && url.includes("/feature-flags/")) {
      return route.fulfill(json({
        ...AUDIT_LOGS,
        content: AUDIT_LOGS.content.map((log) => applyContract(log, ["before"])),
      }));
    }
    if (url.includes("/api/admin/mcp/tokens")) {
      return route.fulfill(json({
        tokens: TOKENS.tokens.map((token) => applyContract(token, ["expiresAt", "lastUsedAt"])),
      }));
    }
    if (url.includes("/api/admin/mcp/audit-logs")) return route.fulfill(json(MCP_AUDIT));
    if (url.includes("anomaly-events")) return route.fulfill(json(ANOMALIES));
    if (url.includes("usage-analytics")) return route.fulfill(json(USAGE));
    return route.continue();
  });

  const page = await context.newPage();
  for (const target of PAGES) {
    await page.goto(`${BASE_URL}${target.path}`, { waitUntil: "networkidle" });
    await page.waitForTimeout(1500);
    const bodyText = await page.locator("body").innerText();
    if (bodyText.includes("Invalid Date")) console.log(`  !! ${target.name}.${theme}: Invalid Date 발견`);
    if (bodyText.includes("invalid_type")) console.log(`  !! ${target.name}.${theme}: zod raw JSON 노출`);
    await page.screenshot({ path: join(OUT_DIR, `${target.name}.${theme}.png`), fullPage: true });
    console.log(`captured ${target.name}.${theme}`);
  }
  await context.close();
}

await browser.close();
console.log(`done (CONTRACT=${CONTRACT}) → ${OUT_DIR}`);
