#!/usr/bin/env node
// 섀도 응답 비교 실행기 (S2-06).
//
// 같은 요청을 baseline(모놀리스)·candidate(edge) 에 각각 보내고 상태코드와 정규화한 본문을
// 비교한다. 비교 규칙 자체는 lib/compare-core.mjs 에 있고 단위 테스트로 잠겨 있다.
//
// 실행 (자기 검증 — 양쪽을 같은 대상으로):
//   node qa/shadow/compare.mjs --baseline http://localhost:8080 --candidate http://localhost:8080
//
// 실행 (2단계 섀도 — S2-15):
//   node qa/shadow/compare.mjs --baseline http://localhost:8080 --candidate http://localhost:8081
//
// 옵션:
//   --corpus <이름,이름>  실행할 코퍼스 (기본: 전부)
//   --out <디렉토리>      리포트 출력 (기본: qa/shadow/results)
//   --user / --password / --empty-user / --empty-password  로그인 계정
//
// 종료 코드: 불일치 0건이면 0, 하나라도 있으면 1. 판정 불가만 있으면 2 (재실행 대상).
import { readFile, readdir, mkdir, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { compareResponses, summarize, VERDICT } from "./lib/compare-core.mjs";

const SHADOW_DIR = dirname(fileURLToPath(import.meta.url));
const EXIT = { MATCHED: 0, MISMATCH: 1, INCONCLUSIVE_ONLY: 2, USAGE: 64 };

function parseArgs(argv) {
  const args = {
    baseline: null,
    candidate: null,
    corpus: null,
    out: join(SHADOW_DIR, "results"),
    user: process.env.QA_SHADOW_USER ?? "qa@example.com",
    password: process.env.QA_SHADOW_PASSWORD ?? "qa-pass",
    emptyUser: process.env.QA_SHADOW_EMPTY_USER ?? null,
    emptyPassword: process.env.QA_SHADOW_EMPTY_PASSWORD ?? "qa-pass",
  };
  const alias = {
    "--baseline": "baseline", "--candidate": "candidate", "--corpus": "corpus", "--out": "out",
    "--user": "user", "--password": "password", "--empty-user": "emptyUser", "--empty-password": "emptyPassword",
  };
  for (let i = 0; i < argv.length; i += 2) {
    const key = alias[argv[i]];
    if (!key) throw new Error(`알 수 없는 인수: ${argv[i]}`);
    args[key] = argv[i + 1];
  }
  if (!args.baseline || !args.candidate) throw new Error("--baseline 과 --candidate 는 필수입니다.");
  return args;
}

/** 운영 대상 실행 차단 — qa/load/k6/lib/auth.js 의 assertSafeTarget 과 같은 원칙. */
function assertSafeTarget(url) {
  if (!/^https?:\/\/(localhost|127\.0\.0\.1|[^/]*\.local)(:|\/|$)/.test(url)) {
    throw new Error(`[SAFETY] ${url} 는 로컬 대상이 아닙니다. 운영·staging 대상 실행은 별도 승인이 필요합니다.`);
  }
}

/** BE 계약: POST /auth/login { email, password } → { accessToken } (qa/load/k6/lib/auth.js 와 동일). */
async function issueToken(baseUrl, email, password) {
  const response = await fetch(`${baseUrl}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!response.ok) throw new Error(`로그인 실패 (${baseUrl}, ${email}): status=${response.status}`);
  const token = (await response.json())?.accessToken;
  if (!token) throw new Error(`응답에 accessToken 이 없습니다 (${baseUrl}, ${email})`);
  return token;
}

/**
 * 토큰은 **대상별로** 발급한다. 서명 키가 같아도 baseline 토큰을 candidate 에 재사용하면
 * 인증 실패가 "응답 불일치"로 보여 원인을 오독하게 된다.
 */
async function issueTokens(baseUrl, args) {
  const tokens = { user: await issueToken(baseUrl, args.user, args.password) };
  if (args.emptyUser) tokens.emptyUser = await issueToken(baseUrl, args.emptyUser, args.emptyPassword);
  return tokens;
}

function buildUrl(baseUrl, request) {
  const url = new URL(request.path, baseUrl);
  for (const [key, value] of Object.entries(request.query ?? {})) url.searchParams.set(key, value);
  return url.toString();
}

/** 응답을 `{ status, body }` 로 수집한다. 전송 실패는 `{ error }` — 비교기가 판정 불가로 분류한다. */
async function send(baseUrl, request, tokens) {
  const headers = {};
  if (request.auth) {
    const token = tokens[request.auth];
    if (!token) return { error: `auth="${request.auth}" 토큰이 준비되지 않음 (--empty-user 미지정?)` };
    headers.Authorization = `Bearer ${token}`;
  }
  if (request.body !== undefined) headers["Content-Type"] = "application/json";

  try {
    const response = await fetch(buildUrl(baseUrl, request), {
      method: request.method,
      headers,
      body: request.body === undefined ? undefined : JSON.stringify(request.body),
    });
    const text = await response.text();
    let body;
    try {
      body = text === "" ? null : JSON.parse(text);
    } catch {
      body = text; // JSON 이 아니면 원문 그대로 비교한다.
    }
    return { status: response.status, body };
  } catch (error) {
    return { error: String(error?.cause?.code ?? error?.message ?? error) };
  }
}

async function loadCorpus(names) {
  const dir = join(SHADOW_DIR, "corpus");
  const files = (await readdir(dir)).filter((f) => f.endsWith(".json"));
  const selected = names ? files.filter((f) => names.split(",").includes(f.replace(/\.json$/, ""))) : files;
  if (selected.length === 0) throw new Error(`실행할 코퍼스가 없습니다 (요청: ${names ?? "전부"})`);
  const corpora = [];
  for (const file of selected.sort()) {
    const parsed = JSON.parse(await readFile(join(dir, file), "utf8"));
    corpora.push({ name: file.replace(/\.json$/, ""), ...parsed });
  }
  return corpora;
}

function renderMarkdown(report) {
  const { summary, startedAt, baseline, candidate, results } = report;
  const lines = [
    `# 섀도 응답 비교 리포트`,
    ``,
    `- 실행: ${startedAt}`,
    `- baseline: ${baseline}`,
    `- candidate: ${candidate}`,
    `- **판정: ${summary.mismatch === 0 ? (summary.inconclusive === 0 ? "불일치 0건" : "불일치 0건 (판정 불가 존재 — 재실행 필요)") : `불일치 ${summary.mismatch}건`}**`,
    ``,
    `| 총계 | 일치 | 불일치 | 판정 불가 |`,
    `|---|---|---|---|`,
    `| ${summary.total} | ${summary.match} | ${summary.mismatch} | ${summary.inconclusive} |`,
  ];

  const mismatches = results.filter((r) => r.verdict === VERDICT.MISMATCH);
  if (mismatches.length > 0) {
    lines.push(``, `## 불일치`, ``);
    for (const item of mismatches) {
      lines.push(`### ${item.request.id} — ${item.request.method ?? "GET"} ${item.request.path}`, ``);
      for (const difference of item.differences) lines.push(`- \`${difference}\``);
      lines.push(``);
    }
  }

  const inconclusive = results.filter((r) => r.verdict === VERDICT.INCONCLUSIVE);
  if (inconclusive.length > 0) {
    lines.push(``, `## 판정 불가 (재실행 대상 — 불일치가 아니다)`, ``);
    for (const item of inconclusive) lines.push(`- **${item.request.id}** — ${item.reason}`);
    lines.push(``);
  }
  return lines.join("\n");
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  assertSafeTarget(args.baseline);
  assertSafeTarget(args.candidate);

  const corpora = await loadCorpus(args.corpus);
  const startedAt = new Date().toISOString();
  console.log(`baseline=${args.baseline}\ncandidate=${args.candidate}`);

  const tokens = {
    baseline: await issueTokens(args.baseline, args),
    candidate: await issueTokens(args.candidate, args),
  };

  const results = [];
  for (const corpus of corpora) {
    console.log(`\n──── ${corpus.name} (${corpus.requests.length}건)`);
    for (const request of corpus.requests) {
      // 상태를 바꾸는 요청(입장·이탈)이 섞여 있어 코퍼스 순서를 실행 순서로 지킨다.
      // 두 대상에 같은 순서로 보내야 상태 의존 응답이 같은 전제에서 비교된다.
      const baselineResponse = await send(args.baseline, request, tokens.baseline);
      const candidateResponse = await send(args.candidate, request, tokens.candidate);
      const result = compareResponses(
        { ...request, corpus: corpus.name },
        baselineResponse,
        candidateResponse,
      );
      results.push(result);
      const mark = { [VERDICT.MATCH]: "✅", [VERDICT.MISMATCH]: "❌", [VERDICT.INCONCLUSIVE]: "⚠️" }[result.verdict];
      console.log(`  ${mark} ${request.id}${result.verdict === VERDICT.MATCH ? "" : ` — ${result.reason ?? result.differences[0]}`}`);
    }
  }

  const summary = summarize(results);
  const report = { startedAt, baseline: args.baseline, candidate: args.candidate, summary, results };

  const stamp = startedAt.replace(/[-:]/g, "").replace("T", "-").slice(0, 13);
  const outDir = resolve(args.out);
  await mkdir(outDir, { recursive: true });
  const jsonPath = join(outDir, `${stamp}.json`);
  const mdPath = join(outDir, `${stamp}.md`);
  await writeFile(jsonPath, JSON.stringify(report, null, 2));
  await writeFile(mdPath, renderMarkdown(report));

  console.log(
    `\n════ 총 ${summary.total} · 일치 ${summary.match} · 불일치 ${summary.mismatch} · 판정 불가 ${summary.inconclusive}`,
  );
  console.log(`리포트: ${jsonPath}\n        ${mdPath}`);

  if (summary.mismatch > 0) return EXIT.MISMATCH;
  if (summary.inconclusive > 0) return EXIT.INCONCLUSIVE_ONLY;
  return EXIT.MATCHED;
}

main()
  .then((code) => process.exit(code))
  .catch((error) => {
    console.error(`🛑 ${error.message}`);
    process.exit(EXIT.USAGE);
  });
