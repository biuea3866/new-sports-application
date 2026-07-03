/**
 * Route Handler 런타임 회귀 가드 (FE-02).
 * design-fe-web.md "실패 경로·동시성·멱등" — `@vercel/otel`은 Node 런타임 fetch 계측을 전제한다.
 * Edge 런타임(`export const runtime = "edge"`)으로 전환되면 BFF 호출의 traceparent 자동 계측이
 * 깨질 수 있으므로, 모든 Route Handler가 기본(Node) 런타임을 유지하는지 정적으로 검증한다.
 */
import { describe, it, expect } from "vitest";
import { readFileSync, readdirSync, statSync } from "fs";
import path from "path";

const EDGE_RUNTIME_PATTERN = /export\s+const\s+runtime\s*=\s*["']edge["']/;

function collectRouteFiles(dir: string): string[] {
  const entries = readdirSync(dir);
  const files: string[] = [];

  for (const entry of entries) {
    const fullPath = path.join(dir, entry);
    const stats = statSync(fullPath);

    if (stats.isDirectory()) {
      files.push(...collectRouteFiles(fullPath));
      continue;
    }

    if (entry === "route.ts" || entry === "route.tsx") {
      files.push(fullPath);
    }
  }

  return files;
}

describe("Route Handler 런타임", () => {
  it("app/ 하위 모든 Route Handler가 Edge 런타임을 선언하지 않는다(Node 기본 유지)", () => {
    const appDir = path.resolve(__dirname, "..");
    const routeFiles = collectRouteFiles(appDir);

    expect(routeFiles.length).toBeGreaterThan(0);

    const edgeRuntimeFiles = routeFiles.filter((filePath) =>
      EDGE_RUNTIME_PATTERN.test(readFileSync(filePath, "utf-8"))
    );

    expect(edgeRuntimeFiles).toEqual([]);
  });
});
