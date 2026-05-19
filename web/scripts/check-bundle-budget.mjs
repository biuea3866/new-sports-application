/**
 * [S-02] 번들 예산 검증 스크립트
 * initial JS gzip ≤ 250KB를 초과하면 exit(1)
 *
 * 사용: node scripts/check-bundle-budget.mjs
 * 전제: next build 완료 후 .next 디렉토리 존재
 */
import { readFileSync, statSync } from "node:fs";
import { createReadStream } from "node:fs";
import { createGzip } from "node:zlib";
import { pipeline } from "node:stream/promises";
import { Writable } from "node:stream";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const webDir = path.resolve(__dirname, "..");
const BUDGET_BYTES = 250 * 1024; // 250KB gzip

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

async function main() {
  const buildManifestPath = path.join(webDir, ".next", "build-manifest.json");

  let buildManifest;
  try {
    buildManifest = JSON.parse(readFileSync(buildManifestPath, "utf-8"));
  } catch {
    console.error("build-manifest.json not found. Run `next build` first.");
    process.exit(1);
  }

  /** @type {string[]} */
  const initialChunks =
    buildManifest.pages?.["/_app"] ?? buildManifest.rootMainFiles ?? [];

  /** @type {string[]} */
  const allInitialFiles = [
    ...new Set([
      ...initialChunks,
      ...(buildManifest.pages?.["/_error"] ?? []),
    ]),
  ].filter((f) => f.endsWith(".js"));

  if (allInitialFiles.length === 0) {
    console.warn(
      "No initial JS chunks found in build-manifest.json. Budget check skipped."
    );
    process.exit(0);
  }

  let totalGzip = 0;
  const results = [];

  for (const chunk of allInitialFiles) {
    const fullPath = path.join(webDir, ".next", chunk.replace(/^\/?_next\//, ""));
    try {
      statSync(fullPath);
    } catch {
      // chunk path may already be absolute or have different prefix
      continue;
    }
    const gz = await gzipSize(fullPath);
    totalGzip += gz;
    results.push({ chunk, gz });
  }

  const kb = (n) => (n / 1024).toFixed(1);

  console.log("\n=== Bundle Budget Report ===");
  results.forEach(({ chunk, gz }) => {
    const flag = gz > 100 * 1024 ? "⚠" : " ";
    console.log(`  ${flag} ${kb(gz)}KB  ${chunk}`);
  });
  console.log(`\nTotal initial JS (gzip): ${kb(totalGzip)}KB`);
  console.log(`Budget:                  ${kb(BUDGET_BYTES)}KB`);

  if (totalGzip > BUDGET_BYTES) {
    console.error(
      `\nBUDGET EXCEEDED: ${kb(totalGzip)}KB > ${kb(BUDGET_BYTES)}KB`
    );
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
