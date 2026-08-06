// 섀도 응답 비교기 핵심 로직 단위 테스트 (S2-06).
//
// 비교기 자신이 틀리면 "불일치 0건"이 아무 의미도 없다. 그래서 비교 규칙을
// 실행기(HTTP·인증·리포트)와 분리해 여기서 잠근다 — 서버 없이 돌아간다.
//
//   node --test qa/shadow/test/
import { test } from "node:test";
import assert from "node:assert/strict";

import { compareResponses, VERDICT } from "../lib/compare-core.mjs";

const ok = (body, status = 200) => ({ status, body });

test("같은 응답이면 일치로 판정한다", () => {
  const response = ok({ items: [{ sourceId: 1, title: "a" }], failedDomains: [] });
  const result = compareResponses({ path: "/api/catalog" }, response, structuredClone(response));
  assert.equal(result.verdict, VERDICT.MATCH);
});

test("본문 한 필드가 다르면 불일치로 검출한다", () => {
  const baseline = ok({ items: [{ sourceId: 1, title: "a" }], failedDomains: [] });
  const candidate = ok({ items: [{ sourceId: 1, title: "b" }], failedDomains: [] });
  const result = compareResponses({ path: "/api/catalog" }, baseline, candidate);
  assert.equal(result.verdict, VERDICT.MISMATCH);
  assert.match(result.differences.join(" "), /title/);
});

test("상태코드만 다르고 본문이 같으면 불일치로 검출한다", () => {
  const body = { items: [], failedDomains: [] };
  const result = compareResponses({ path: "/api/catalog" }, ok(body, 200), ok(structuredClone(body), 500));
  assert.equal(result.verdict, VERDICT.MISMATCH);
  assert.match(result.differences.join(" "), /status/);
});

test("목록의 동률 항목 순서가 뒤바뀌어도 일치로 판정한다", () => {
  // 파사드는 sortedByDescending(createdAt) 하나만 쓴다 — 동률 항목의 상대 순서는
  // 원본 조회 순서에 따라 갈리므로 계약이 아니다.
  const at = "2026-08-01T10:00:00+09:00";
  const baseline = ok({
    items: [
      { sourceId: 1, title: "a", createdAt: at },
      { sourceId: 2, title: "b", createdAt: at },
    ],
    failedDomains: [],
  });
  const candidate = ok({
    items: [
      { sourceId: 2, title: "b", createdAt: at },
      { sourceId: 1, title: "a", createdAt: at },
    ],
    failedDomains: [],
  });
  const result = compareResponses({ path: "/api/catalog" }, baseline, candidate);
  assert.equal(result.verdict, VERDICT.MATCH);
});

test("정렬 키가 실제로 다르면(동률이 아니면) 불일치로 검출한다", () => {
  const baseline = ok({
    items: [
      { sourceId: 1, createdAt: "2026-08-02T10:00:00+09:00" },
      { sourceId: 2, createdAt: "2026-08-01T10:00:00+09:00" },
    ],
    failedDomains: [],
  });
  const candidate = ok({
    items: [
      { sourceId: 2, createdAt: "2026-08-01T10:00:00+09:00" },
      { sourceId: 1, createdAt: "2026-08-02T10:00:00+09:00" },
    ],
    failedDomains: [],
  });
  const result = compareResponses({ path: "/api/catalog" }, baseline, candidate);
  assert.equal(result.verdict, VERDICT.MISMATCH);
});

test("한쪽 failedDomains 가 비어 있지 않으면 판정 불가로 분류한다", () => {
  // 부분 실패는 타임아웃 의존이라 부하에 따라 달라진다 — 불일치와 구분해 재실행 대상으로 둔다.
  const baseline = ok({ items: [], failedDomains: [] });
  const candidate = ok({ items: [], failedDomains: ["TICKETING"] });
  const result = compareResponses({ path: "/api/orders" }, baseline, candidate);
  assert.equal(result.verdict, VERDICT.INCONCLUSIVE);
  assert.match(result.reason, /failedDomains/);
});

test("양쪽 failedDomains 가 모두 비어 있어야 일치 판정 대상이 된다", () => {
  const response = ok({ items: [], failedDomains: [] });
  const result = compareResponses({ path: "/api/orders" }, response, structuredClone(response));
  assert.equal(result.verdict, VERDICT.MATCH);
});

test("presigned URL 의 서명 값이 달라도 URL 구조가 같으면 일치로 판정한다", () => {
  const baseline = ok({
    uploadUrl: "http://minio:9000/bucket/a.png?X-Amz-Signature=aaa&X-Amz-Expires=600&X-Amz-Date=20260801T000000Z",
    objectKey: "images/a.png",
  });
  const candidate = ok({
    uploadUrl: "http://minio:9000/bucket/a.png?X-Amz-Signature=bbb&X-Amz-Expires=600&X-Amz-Date=20260806T000000Z",
    objectKey: "images/a.png",
  });
  const result = compareResponses({ path: "/images/presigned-upload" }, baseline, candidate);
  assert.equal(result.verdict, VERDICT.MATCH);
});

test("presigned URL 의 host·path 가 다르면 불일치로 검출한다", () => {
  const baseline = ok({ uploadUrl: "http://minio:9000/bucket/a.png?X-Amz-Signature=aaa", objectKey: "images/a.png" });
  const candidate = ok({ uploadUrl: "http://other:9000/bucket/a.png?X-Amz-Signature=aaa", objectKey: "images/a.png" });
  const result = compareResponses({ path: "/images/presigned-upload" }, baseline, candidate);
  assert.equal(result.verdict, VERDICT.MISMATCH);
});

test("presigned URL 의 쿼리 키 집합이 다르면 불일치로 검출한다", () => {
  const baseline = ok({ uploadUrl: "http://minio:9000/b/a.png?X-Amz-Signature=aaa&X-Amz-Expires=600" });
  const candidate = ok({ uploadUrl: "http://minio:9000/b/a.png?X-Amz-Signature=aaa" });
  const result = compareResponses({ path: "/images/presigned-upload" }, baseline, candidate);
  assert.equal(result.verdict, VERDICT.MISMATCH);
});

test("4xx 오류 응답 본문이 같으면 일치로 판정한다 (오류 계약 커버)", () => {
  const body = { type: "about:blank", title: "Unauthorized", status: 401, code: "UNAUTHORIZED" };
  const result = compareResponses({ path: "/api/orders" }, ok(body, 401), ok(structuredClone(body), 401));
  assert.equal(result.verdict, VERDICT.MATCH);
});

test("4xx 오류 코드가 다르면 불일치로 검출한다", () => {
  const baseline = ok({ status: 400, code: "INVALID_CONTENT_TYPE" }, 400);
  const candidate = ok({ status: 400, code: "BAD_REQUEST" }, 400);
  const result = compareResponses({ path: "/images/presigned-upload" }, baseline, candidate);
  assert.equal(result.verdict, VERDICT.MISMATCH);
});

test("비교 대상 경로가 목록이 아니어도(객체 응답) 필드 차이를 검출한다", () => {
  const baseline = ok({ position: 3, waitSeconds: 60 });
  const candidate = ok({ position: 4, waitSeconds: 60 });
  const result = compareResponses({ path: "/virtual-queues/EVENT/1/entries/me" }, baseline, candidate);
  assert.equal(result.verdict, VERDICT.MISMATCH);
  assert.match(result.differences.join(" "), /position/);
});

test("한쪽만 필드가 있으면 불일치로 검출한다 (계약 누락 탐지)", () => {
  const baseline = ok({ items: [{ sourceId: 1, amount: "1000.00" }], failedDomains: [] });
  const candidate = ok({ items: [{ sourceId: 1 }], failedDomains: [] });
  const result = compareResponses({ path: "/api/orders" }, baseline, candidate);
  assert.equal(result.verdict, VERDICT.MISMATCH);
  assert.match(result.differences.join(" "), /amount/);
});

test("요청 전송 자체가 실패하면 판정 불가로 분류한다", () => {
  const result = compareResponses(
    { path: "/api/catalog" },
    { error: "ECONNREFUSED" },
    ok({ items: [], failedDomains: [] }),
  );
  assert.equal(result.verdict, VERDICT.INCONCLUSIVE);
  assert.match(result.reason, /ECONNREFUSED|전송/);
});
