// INFRA-05 검증 — lib/writemix.js (B2C 쓰기 곡선 쓰기 액션 가중 혼합)
//
// 근거 티켓: INFRA-05-b2c-diurnal-write-쓰기곡선.md "쓰기 혼합: POST /bookings ·
// POST /goods-orders · POST /events/{id}/seats/select + POST /ticket-orders" —
// pickWriteAction은 k6 런타임(http)에 의존하지 않는 순수 함수라 node:test로 직접
// 검증한다(INFRA-04 lib/readmix.js 선례와 동일 관례).
import { test } from "node:test";
import assert from "node:assert/strict";
import { WRITE_MIX, pickWriteAction } from "../writemix.js";

test("WRITE_MIX 가중치 합은 1이다", () => {
  const totalWeight = WRITE_MIX.reduce((sum, entry) => sum + entry.weight, 0);
  assert.ok(
    Math.abs(totalWeight - 1) < 1e-9,
    `가중치 합은 1이어야 한다: ${totalWeight}`,
  );
});

test("WRITE_MIX는 티켓이 명시한 쓰기 3종(booking·goodsOrder·ticketOrder)을 모두 포함한다", () => {
  const names = WRITE_MIX.map((entry) => entry.name).sort();
  assert.deepEqual(names, ["booking", "goodsOrder", "ticketOrder"]);
});

test("pickWriteAction(0)은 첫 액션을, 1 직전 값은 마지막 액션을 결정적으로 반환한다", () => {
  const first = pickWriteAction(0);
  assert.equal(first, WRITE_MIX[0].name);

  const last = pickWriteAction(0.999999);
  assert.equal(last, WRITE_MIX[WRITE_MIX.length - 1].name);
});

test("pickWriteAction은 누적 가중치 경계에서 다음 액션으로 정확히 전환된다", () => {
  let cumulative = 0;
  for (const entry of WRITE_MIX) {
    const midpoint = cumulative + entry.weight / 2;
    assert.equal(pickWriteAction(midpoint), entry.name, `randomValue=${midpoint}는 ${entry.name}이어야 한다`);
    cumulative += entry.weight;
  }
});

test("대량 샘플링 시 각 액션의 출현 비율이 설계된 가중치에 근사한다(±1%)", () => {
  const SAMPLE_SIZE = 100000;
  const counts = { booking: 0, goodsOrder: 0, ticketOrder: 0 };

  for (let i = 0; i < SAMPLE_SIZE; i++) {
    const randomValue = i / SAMPLE_SIZE; // 결정적 균등 분포 — Math.random 시드 불가 문제 회피
    counts[pickWriteAction(randomValue)] += 1;
  }

  const weightByName = WRITE_MIX.reduce((accumulator, entry) => {
    accumulator[entry.name] = entry.weight;
    return accumulator;
  }, {});

  for (const name of Object.keys(weightByName)) {
    const actualRatio = counts[name] / SAMPLE_SIZE;
    const expectedRatio = weightByName[name];
    assert.ok(
      Math.abs(actualRatio - expectedRatio) < 0.01,
      `${name} 비율이 ±1% 이내여야 한다: actual=${actualRatio} expected=${expectedRatio}`,
    );
  }
});

test("pickWriteAction은 인자를 생략하면 Math.random() 기반으로 항상 유효한 액션명을 반환한다", () => {
  const picked = pickWriteAction();
  assert.ok(WRITE_MIX.some((entry) => entry.name === picked), "WRITE_MIX에 포함된 액션명이어야 한다");
});
