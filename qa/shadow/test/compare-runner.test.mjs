// 섀도 비교 실행기 종단 테스트 (S2-06).
//
// 코퍼스 로드 → 로그인 → 두 대상 호출 → 비교 → 리포트 → 종료 코드까지 실제로 돌린다.
// 대상은 스텁 서버다 — 이 테스트가 검증하는 것은 **실행기 배선**이지 실제 앱 응답이 아니다.
// 실제 모놀리스를 baseline·candidate 로 지정하는 자기 검증은 dev 스택이 필요하며 S2-15 소관이다.
import { test } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { execFile } from "node:child_process";
import { mkdtemp, readdir, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const SHADOW_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");
const TIE_AT = "2026-08-01T10:00:00+09:00";

/**
 * 대상 앱을 흉내내는 스텁.
 * @param overrides.title  카탈로그 항목 제목 (드리프트 주입용)
 */
function startStub({ title = "동일한 제목" } = {}) {
  let presignedCallCount = 0;
  const server = createServer((request, response) => {
    const url = new URL(request.url, "http://localhost");
    const send = (status, body) => {
      response.writeHead(status, { "Content-Type": "application/json" });
      response.end(JSON.stringify(body));
    };

    if (url.pathname === "/auth/login") return send(200, { accessToken: "stub-token" });

    if (url.pathname === "/api/catalog") {
      // createdAt 동률 2건 — 실행기가 동률 순서를 계약으로 보지 않는지 함께 확인한다.
      return send(200, {
        items: [
          { itemType: "PRODUCT", sourceId: 1, title, createdAt: TIE_AT },
          { itemType: "TICKET", sourceId: 2, title: "다른 항목", createdAt: TIE_AT },
        ],
        failedDomains: [],
      });
    }
    if (url.pathname === "/api/orders") {
      return send(200, { items: [], failedDomains: [] });
    }
    if (url.pathname === "/images/presigned-upload") {
      // 서명·시각은 호출마다 달라진다 — 정규화가 없으면 항상 불일치가 나야 정상이다.
      presignedCallCount += 1;
      return send(201, {
        uploadUrl: `http://minio:9000/bucket/a.png?X-Amz-Signature=sig-${presignedCallCount}-${Math.random()}&X-Amz-Expires=600`,
        objectKey: "images/a.png",
      });
    }
    return send(200, { ok: true, path: url.pathname });
  });
  return new Promise((resolve) => {
    server.listen(0, "127.0.0.1", () => {
      const { port } = server.address();
      resolve({ url: `http://127.0.0.1:${port}`, close: () => new Promise((r) => server.close(r)) });
    });
  });
}

async function runCompare(baseline, candidate, extra = ["--empty-user", "qa-empty@example.com"]) {
  const out = await mkdtemp(join(tmpdir(), "shadow-"));
  const args = [
    join(SHADOW_DIR, "compare.mjs"), "--baseline", baseline, "--candidate", candidate, "--out", out, ...extra,
  ];
  try {
    const { stdout } = await execFileAsync(process.execPath, args);
    return { code: 0, stdout, out };
  } catch (error) {
    return { code: error.code, stdout: error.stdout ?? "", out };
  }
}

async function readReport(out) {
  const files = await readdir(out);
  const jsonFile = files.find((f) => f.endsWith(".json"));
  assert.ok(jsonFile, "리포트 JSON 이 생성돼야 한다");
  assert.ok(files.some((f) => f.endsWith(".md")), "요약 md 가 생성돼야 한다");
  return JSON.parse(await readFile(join(out, jsonFile), "utf8"));
}

test("baseline·candidate 를 같은 대상으로 지정하면 불일치 0건이 나온다 (도구 자기 검증)", async () => {
  const stub = await startStub();
  try {
    const { code, out } = await runCompare(stub.url, stub.url);
    const report = await readReport(out);
    assert.equal(report.summary.mismatch, 0, `불일치 상세: ${JSON.stringify(report.results.filter((r) => r.verdict === "MISMATCH"), null, 2)}`);
    assert.equal(report.summary.total, report.summary.match + report.summary.inconclusive);
    assert.equal(code, 0);
  } finally {
    await stub.close();
  }
});

test("한쪽 응답 필드가 다르면 불일치로 검출하고 종료 코드 1 을 낸다 (거짓 통과 방지)", async () => {
  const baseline = await startStub();
  const candidate = await startStub({ title: "드리프트된 제목" });
  try {
    const { code, out } = await runCompare(baseline.url, candidate.url);
    const report = await readReport(out);
    assert.ok(report.summary.mismatch > 0, "드리프트가 불일치로 잡혀야 한다");
    assert.match(JSON.stringify(report.results), /title/);
    assert.equal(code, 1);
  } finally {
    await baseline.close();
    await candidate.close();
  }
});

test("리포트는 총계와 불일치 목록을 함께 남긴다", async () => {
  const baseline = await startStub();
  const candidate = await startStub({ title: "드리프트된 제목" });
  try {
    const { out } = await runCompare(baseline.url, candidate.url);
    const report = await readReport(out);
    assert.deepEqual(Object.keys(report.summary).sort(), ["inconclusive", "match", "mismatch", "total"]);
    const files = await readdir(out);
    const markdown = await readFile(join(out, files.find((f) => f.endsWith(".md"))), "utf8");
    assert.match(markdown, /## 불일치/);
    assert.match(markdown, /catalog-no-filter/);
  } finally {
    await baseline.close();
    await candidate.close();
  }
});

test("판정 불가만 있고 불일치가 없으면 종료 코드 2 로 구분한다 (재실행 대상)", async () => {
  // --empty-user 미지정 → auth="emptyUser" 요청의 토큰이 없어 전송 실패 → 판정 불가.
  // 불일치(1)와 섞이지 않게 별도 코드로 구분하는 것이 이 도구의 계약이다.
  const stub = await startStub();
  try {
    const { code, out } = await runCompare(stub.url, stub.url, []);
    const report = await readReport(out);
    assert.equal(report.summary.mismatch, 0);
    assert.ok(report.summary.inconclusive > 0);
    assert.equal(code, 2);
  } finally {
    await stub.close();
  }
});

test("운영으로 보이는 대상은 실행 전에 차단한다", async () => {
  const stub = await startStub();
  try {
    const { code, stdout } = await runCompare("https://api.example.com", stub.url);
    assert.equal(code, 64, `stdout=${stdout}`);
  } finally {
    await stub.close();
  }
});
