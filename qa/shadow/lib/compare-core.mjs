// 섀도 응답 비교 규칙 (S2-06).
//
// HTTP·인증·리포트와 분리한 순수 로직이다 — 서버 없이 단위 테스트로 잠근다
// (`qa/shadow/test/compare-core.test.mjs`). 비교기 자신이 틀리면 "불일치 0건"이
// 아무것도 보장하지 않기 때문이다.

export const VERDICT = {
  MATCH: "MATCH",
  MISMATCH: "MISMATCH",
  /** 비결정 요소 때문에 판정할 수 없음 — 불일치와 구분해 재실행 대상으로 둔다. */
  INCONCLUSIVE: "INCONCLUSIVE",
};

/**
 * 서명·만료처럼 호출마다 반드시 달라지는 쿼리 파라미터.
 * 티켓은 "URL 구조(host·path·쿼리 키 집합)만 비교"라고 적었지만, 여기서는 한 단계 더 좁혀
 * **아래 키의 값만** 버리고 나머지 쿼리 값은 그대로 비교한다 — 키 집합이 같으면서 값이 바뀐
 * 실제 계약 변화(예: 버킷·리전 파라미터)를 놓치지 않기 위해서다.
 */
const VOLATILE_QUERY_KEYS = new Set([
  "X-Amz-Signature",
  "X-Amz-Date",
  "X-Amz-Credential",
  "X-Amz-Security-Token",
  "X-Amz-Expires",
  "Signature",
  "Expires",
]);

/** 목록 응답의 정렬 키. 파사드가 이 키 내림차순 하나로만 정렬한다. */
const SORT_KEY = "createdAt";

const isPlainObject = (value) => value !== null && typeof value === "object" && !Array.isArray(value);

/** URL 문자열을 비교 가능한 형태로 정규화한다. 파싱 불가면 원본을 그대로 둔다. */
function normalizeUrl(raw) {
  let url;
  try {
    url = new URL(raw);
  } catch {
    return raw;
  }
  const query = [...url.searchParams.entries()]
    .map(([key, value]) => [key, VOLATILE_QUERY_KEYS.has(key) ? "<volatile>" : value])
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0));
  return `${url.protocol}//${url.host}${url.pathname}?${query.map(([k, v]) => `${k}=${v}`).join("&")}`;
}

const looksLikeUrlField = (key, value) =>
  typeof value === "string" && /url$/i.test(key) && /^https?:\/\//.test(value);

/** 동률(정렬 키가 같은) 구간의 순서를 지우기 위한 안정 정렬용 표현. */
const canonical = (value) => JSON.stringify(normalize(value));

/**
 * 동률 구간 안에서만 순서를 지운다.
 * 정렬 키 시퀀스 자체는 건드리지 않으므로, 정렬이 실제로 달라지면 그대로 불일치로 남는다.
 */
function normalizeTieOrder(items) {
  if (!items.every((item) => isPlainObject(item) && SORT_KEY in item)) return items.map(normalize);
  const result = [];
  let group = [];
  let groupKey;
  const flush = () => {
    if (group.length === 0) return;
    result.push(...group.map(normalize).sort((a, b) => (canonical(a) < canonical(b) ? -1 : 1)));
    group = [];
  };
  for (const item of items) {
    if (item[SORT_KEY] !== groupKey) {
      flush();
      groupKey = item[SORT_KEY];
    }
    group.push(item);
  }
  flush();
  return result;
}

/** 비계약 요소(서명 값·동률 순서)를 제거한 비교용 표현으로 바꾼다. */
export function normalize(value) {
  if (Array.isArray(value)) return normalizeTieOrder(value);
  if (!isPlainObject(value)) return value;
  const normalized = {};
  for (const [key, child] of Object.entries(value)) {
    normalized[key] = looksLikeUrlField(key, child) ? normalizeUrl(child) : normalize(child);
  }
  return normalized;
}

/** 두 값의 차이를 JSON 경로 문자열 목록으로 모은다. */
export function diffPaths(baseline, candidate, path = "$") {
  if (Array.isArray(baseline) && Array.isArray(candidate)) {
    if (baseline.length !== candidate.length) {
      return [`${path}: 길이 다름 (baseline=${baseline.length} candidate=${candidate.length})`];
    }
    return baseline.flatMap((item, index) => diffPaths(item, candidate[index], `${path}[${index}]`));
  }
  if (isPlainObject(baseline) && isPlainObject(candidate)) {
    const keys = [...new Set([...Object.keys(baseline), ...Object.keys(candidate)])].sort();
    return keys.flatMap((key) => {
      const inBaseline = key in baseline;
      const inCandidate = key in candidate;
      if (!inBaseline) return [`${path}.${key}: candidate 에만 있음`];
      if (!inCandidate) return [`${path}.${key}: baseline 에만 있음`];
      return diffPaths(baseline[key], candidate[key], `${path}.${key}`);
    });
  }
  if (JSON.stringify(baseline) === JSON.stringify(candidate)) return [];
  return [`${path}: ${JSON.stringify(baseline)} != ${JSON.stringify(candidate)}`];
}

/** 부분 실패(타임아웃 의존)는 판정 대상에서 뺀다 — 부하에 따라 값이 달라진다. */
const partialFailure = (body) =>
  isPlainObject(body) && Array.isArray(body.failedDomains) && body.failedDomains.length > 0;

/**
 * 한 요청의 baseline·candidate 응답을 비교한다.
 *
 * @param request  코퍼스 항목 (경로·메서드 등, 리포트 식별용)
 * @param baseline `{ status, body }` 또는 전송 실패 시 `{ error }`
 * @param candidate 같은 형태
 */
export function compareResponses(request, baseline, candidate) {
  const base = { request };

  if (baseline?.error || candidate?.error) {
    const which = baseline?.error ? "baseline" : "candidate";
    return {
      ...base,
      verdict: VERDICT.INCONCLUSIVE,
      reason: `요청 전송 실패 (${which}: ${baseline?.error ?? candidate?.error})`,
      differences: [],
    };
  }

  if (partialFailure(baseline.body) || partialFailure(candidate.body)) {
    return {
      ...base,
      verdict: VERDICT.INCONCLUSIVE,
      reason:
        "failedDomains 가 비어 있지 않음 — 부분 실패는 타임아웃 의존이라 판정하지 않고 재실행 대상으로 둔다 " +
        `(baseline=${JSON.stringify(baseline.body?.failedDomains ?? [])} ` +
        `candidate=${JSON.stringify(candidate.body?.failedDomains ?? [])})`,
      differences: [],
    };
  }

  const differences = [];
  if (baseline.status !== candidate.status) {
    differences.push(`$.status: ${baseline.status} != ${candidate.status}`);
  }
  differences.push(...diffPaths(normalize(baseline.body), normalize(candidate.body)));

  return differences.length === 0
    ? { ...base, verdict: VERDICT.MATCH, differences: [] }
    : { ...base, verdict: VERDICT.MISMATCH, differences };
}

/** 비교 결과 목록을 리포트용 총계로 집계한다. */
export function summarize(results) {
  const counts = { total: results.length, match: 0, mismatch: 0, inconclusive: 0 };
  for (const result of results) {
    if (result.verdict === VERDICT.MATCH) counts.match += 1;
    else if (result.verdict === VERDICT.MISMATCH) counts.mismatch += 1;
    else counts.inconclusive += 1;
  }
  return counts;
}
