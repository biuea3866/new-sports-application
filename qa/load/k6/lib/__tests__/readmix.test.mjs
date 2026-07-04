// INFRA-04 검증 — lib/readmix.js (B2C 조회 곡선 조회 엔드포인트 가중 혼합)
//
// 근거 티켓: "조회 엔드포인트 3종(facility/product/event)이 가중 혼합으로 호출된다"
// (INFRA-04-b2c-diurnal-read-조회곡선.md 테스트 케이스). pickReadEndpoint는 k6 런타임에
// 의존하지 않는 순수 함수라 node:test로 직접 검증한다(lib/diurnal.js 선례와 동일 관례).
import { test } from "node:test";
import assert from "node:assert/strict";
import { READ_MIX, pickReadEndpoint } from "../readmix.js";

test("READ_MIX 가중치 합은 1이다", () => {
  const totalWeight = READ_MIX.reduce((sum, entry) => sum + entry.weight, 0);
  assert.ok(
    Math.abs(totalWeight - 1) < 1e-9,
    `가중치 합은 1이어야 한다: ${totalWeight}`,
  );
});

test("READ_MIX는 facility·product·event 3개 그룹을 모두 포함한다(3종 가중 혼합)", () => {
  const groups = new Set(READ_MIX.map((entry) => entry.group));
  assert.deepEqual([...groups].sort(), ["event", "facility", "product"]);
});

test("READ_MIX는 티켓이 명시한 7개 조회 엔드포인트를 모두 포함한다", () => {
  const names = READ_MIX.map((entry) => entry.name).sort();
  assert.deepEqual(names, [
    "event-detail",
    "events-list",
    "facilities-list",
    "facility-slots",
    "product-detail",
    "products-list",
    "products-popular",
  ]);
});

test("pickReadEndpoint(0)은 첫 엔드포인트를, 1 직전 값은 마지막 엔드포인트를 결정적으로 반환한다", () => {
  const first = pickReadEndpoint(0);
  assert.equal(first, READ_MIX[0]);

  const last = pickReadEndpoint(0.999999);
  assert.equal(last, READ_MIX[READ_MIX.length - 1]);
});

test("pickReadEndpoint는 누적 가중치 경계에서 다음 엔드포인트로 정확히 전환된다", () => {
  let cumulative = 0;
  for (let index = 0; index < READ_MIX.length; index++) {
    const entry = READ_MIX[index];
    // 구간 [cumulative, cumulative+weight) 내부의 값은 이 엔드포인트를 반환해야 한다.
    const midpoint = cumulative + entry.weight / 2;
    assert.equal(pickReadEndpoint(midpoint), entry, `randomValue=${midpoint}는 ${entry.name}이어야 한다`);
    cumulative += entry.weight;
  }
});

test("대량 샘플링 시 각 그룹의 출현 비율이 설계된 가중치에 근사한다(±1%)", () => {
  const SAMPLE_SIZE = 100000;
  const groupCounts = { facility: 0, product: 0, event: 0 };

  for (let i = 0; i < SAMPLE_SIZE; i++) {
    const randomValue = i / SAMPLE_SIZE; // 결정적 균등 분포 — Math.random 시드 불가 문제 회피
    const picked = pickReadEndpoint(randomValue);
    groupCounts[picked.group] += 1;
  }

  const groupWeights = READ_MIX.reduce((accumulator, entry) => {
    accumulator[entry.group] = (accumulator[entry.group] || 0) + entry.weight;
    return accumulator;
  }, {});

  for (const group of Object.keys(groupWeights)) {
    const actualRatio = groupCounts[group] / SAMPLE_SIZE;
    const expectedRatio = groupWeights[group];
    assert.ok(
      Math.abs(actualRatio - expectedRatio) < 0.01,
      `${group} 그룹 비율이 ±1% 이내여야 한다: actual=${actualRatio} expected=${expectedRatio}`,
    );
  }
});

test("pickReadEndpoint는 인자를 생략하면 Math.random() 기반으로 항상 유효한 엔드포인트를 반환한다", () => {
  const picked = pickReadEndpoint();
  assert.ok(READ_MIX.includes(picked), "READ_MIX에 포함된 엔드포인트여야 한다");
});
