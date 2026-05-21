/**
 * [S-02] 번들 예산 검증 스크립트
 * initial App Router JS gzip ≤ 250KB 를 초과하면 exit(1)
 *
 * 사용: node scripts/check-bundle-budget.mjs
 * 전제: next build 완료 후 .next 디렉토리 존재
 *
 * 측정 대상: app-build-manifest.json 의 `/page` (App Router 루트 진입) chunk 집합.
 * Pages Router 가 함께 빌드되는 경우의 stub (`/_app`, `/_error`)은 의도적으로 제외한다.
 */
import { readFileSync, statSync, createReadStream } from "node:fs";
import { createGzip } from "node:zlib";
import { pipeline } from "node:stream/promises";
import { Writable } from "node:stream";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const webDir = path.resolve(__dirname, "..");
const BUDGET_BYTES = 250 * 1024;

async function gzipSize(filePath) {
  let size = 0;
  const sink = new Writable({
    write(chunk, _enc, cb) {
      size += chunk.length;
      cb();
    },
  });
  await pipeline(createReadStream(filePath), createGzip(), sink);
  return size;
}

function readJson(filePath) {
  try {
    return JSON.parse(readFileSync(filePath, "utf-8"));
  } catch {
    return null;
  }
}

async function main() {
  const nextDir = path.join(webDir, ".next");
  const appManifest = readJson(path.join(nextDir, "app-build-manifest.json"));
  const pagesManifest = readJson(path.join(nextDir, "build-manifest.json"));

  // App Router 루트 진입 chunks — `/page` (App Router 사용 시 항상 존재).
  // App Router 미사용 프로젝트 fallback 으로 Pages Router 의 rootMainFiles 사용.
  const appRootChunks = appManifest?.pages?.["/page"] ?? null;
  const pagesRootChunks = pagesManifest?.rootMainFiles ?? null;

  const chunks = appRootChunks ?? pagesRootChunks ?? [];
  const initialJs = [...new Set(chunks)].filter((f) => f.endsWith(".js"));

  if (initialJs.length === 0) {
    console.error(
      "측정할 initial chunks 가 없습니다. app-build-manifest.json 의 `/page` 또는 build-manifest.json 의 `rootMainFiles` 를 확인하세요."
    );
    process.exit(1);
  }

  const source = appRootChunks ? "app-build-manifest.json#/page" : "build-manifest.json#rootMainFiles";

  let totalGzip = 0;
  const results = [];

  for (const chunk of initialJs) {
    const fullPath = path.join(nextDir, chunk);
    try {
      statSync(fullPath);
    } catch {
      console.error(`측정 실패 — 파일 없음: ${chunk}`);
      process.exit(1);
    }
    const gz = await gzipSize(fullPath);
    totalGzip += gz;
    results.push({ chunk, gz });
  }

  const kb = (n) => (n / 1024).toFixed(1);

  console.log("\n=== Bundle Budget Report ===");
  console.log(`측정 대상: ${source}`);
  results.forEach(({ chunk, gz }) => {
    const flag = gz > 100 * 1024 ? "⚠" : " ";
    console.log(`  ${flag} ${kb(gz)}KB  ${chunk}`);
  });
  console.log(`\nTotal initial JS (gzip): ${kb(totalGzip)}KB`);
  console.log(`Budget:                  ${kb(BUDGET_BYTES)}KB`);

  if (totalGzip > BUDGET_BYTES) {
    console.error(`\nBUDGET EXCEEDED: ${kb(totalGzip)}KB > ${kb(BUDGET_BYTES)}KB`);
    process.exit(1);
  } else {
    console.log(`\nBudget OK: ${kb(totalGzip)}KB ≤ ${kb(BUDGET_BYTES)}KB`);
    process.exit(0);
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
