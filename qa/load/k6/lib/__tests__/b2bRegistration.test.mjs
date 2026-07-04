// INFRA-06 검증 — lib/b2bRegistration.js
//
// b2b-diurnal.js 전용 순수 함수(등록 혼합 선택·요청 빌더·하루 환산 카운트)를 k6 런타임 없이
// Node.js 내장 테스트 러너(node:test)로 검증한다.
// 근거: 티켓 "테스트 케이스" 중 "무효/폐기 키면 401이 5xx로 집계되지 않는다"를 제외한
// 순수 로직 부분(등록 혼합 50:50, 하루 환산 1000건 이상 계산).
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  selectRegistrationAction,
  buildProductRegistrationRequest,
  buildEventRegistrationRequest,
  dailyEquivalentCount,
} from "../b2bRegistration.js";

test("selectRegistrationAction은 iteration 인덱스로 product·event를 50:50 교대 선택한다", () => {
  const actions = Array.from({ length: 10 }, (_, iterationIndex) => selectRegistrationAction(iterationIndex));

  assert.deepEqual(actions, [
    "product", "event", "product", "event", "product",
    "event", "product", "event", "product", "event",
  ]);
  const productCount = actions.filter((action) => action === "product").length;
  const eventCount = actions.filter((action) => action === "event").length;
  assert.equal(productCount, eventCount, "product·event 비율은 50:50이어야 한다");
});

test("buildProductRegistrationRequest는 CreateMyProductRequest 계약(필수 필드)을 채우고 VU·ITER로 유니크한 name을 만든다", () => {
  const first = buildProductRegistrationRequest(1, 0);
  const second = buildProductRegistrationRequest(1, 1);

  assert.notEqual(first.name, second.name, "반복마다 name이 달라야 중복 등록으로 오인되지 않는다");
  assert.ok(first.name.length > 0);
  assert.ok(["EQUIPMENT", "APPAREL", "FOOTWEAR", "ACCESSORY"].includes(first.category));
  assert.ok(Number(first.price) > 0);
  assert.ok(first.description.length > 0);
  assert.ok(first.imageUrl.startsWith("http"));
});

test("buildEventRegistrationRequest는 CreateMyEventRequest 계약을 채우고 좌석을 1석 이상 포함한다(빈 seats 방지)", () => {
  const request = buildEventRegistrationRequest(2, 5);

  assert.ok(request.title.length > 0);
  assert.ok(request.venue.length > 0);
  assert.ok(request.seats.length >= 1, "seats가 비어 있으면 이벤트 등록이 실패한다");
  assert.ok(new Date(request.startsAt).getTime() > Date.now(), "startsAt은 미래 시각이어야 한다");
  for (const seat of request.seats) {
    assert.ok(seat.sectionName.length > 0);
    assert.ok(seat.seatLabel.length > 0);
    assert.ok(Number(seat.price) > 0);
  }
});

test("dailyEquivalentCount는 압축 실행(timeScale<1)의 실측 건수를 24h 환산으로 extrapolate한다", () => {
  // b2bStages의 target TPS는 timeScale과 무관하고 duration만 축소되므로,
  // 압축 실행 실측 건수 ÷ timeScale = 24h(실시간) 환산 건수.
  assert.equal(dailyEquivalentCount(50, 0.05), 1000);
  assert.equal(dailyEquivalentCount(1000, 1), 1000);
});

test("dailyEquivalentCount는 timeScale=0이면(설정 오류 가드) 실측 건수를 그대로 반환한다", () => {
  assert.equal(dailyEquivalentCount(123, 0), 123);
});
