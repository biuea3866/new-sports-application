// qa/load/k6/b2c-diurnal-write.js
// INFRA-05: B2C 일주기 쓰기 곡선(30%, 소진성) — ramping-arrival-rate executor.
//
// 근거: TDD "곡선 배율표"·"소진성 자원", 티켓 INFRA-05. B2C 목표 3000TPS의 30%(peak 900)를
// 쓰기 3종(booking 생성·goods 주문·ticket 좌석선택+구매)에 배분한다. reseed(INFRA-08)가
// 10분 주기로 소진성 자원(슬롯 capacity·재고 quantity·좌석 락)을 baseline으로 되돌려
// 이 곡선이 409 붕괴 없이 24시간 반복될 수 있게 한다.
//
// synthetic 시드 의존 (qa/load/seeds/simulator-baseline.sql, INFRA-03):
//   - 슬롯: id 9000001~9000030 (30건)
//   - 상품: id 9010001~9010010 (10건, stock baseline 1,000,000)
//   - 이벤트: id 9000001, 좌석 id 9000001~9005000 (5000건)
//
// 정찰 확정 사실(중요):
//   - Booking body는 paymentMethod: "CREDIT_CARD" 다(PaymentMethod enum, CreateBookingRequest).
//     구 스크립트(booking-create-throughput.js)의 "CARD"는 버그 값 — 절대 복사하지 않는다.
//   - X-User-Id write는 FK 체크가 없어 synthetic 유저(lib/pool.js, 900000+)에 대응하는
//     DB row가 없어도 된다.
//   - goods-orders는 Idempotency-Key 헤더가 필수(@RequestHeader required, 없으면 400).
//   - ticket-orders는 Idempotency-Key 헤더가 비어 있으면 MissingIdempotencyKeyException(400).
//   - bookings는 Idempotency-Key 헤더를 요구하지 않는다(BookingApiController 시그니처에 없음).
//   - 자원 소진 시 409(슬롯 충돌·재고 부족·좌석 락 경쟁)는 정상 비즈니스 응답이다 —
//     5xx만 실패로 집계한다(기존 스크립트 관례, booking-create-throughput.js 등).

import http from "k6/http";
import { check, group } from "k6";
import { assertSafeTarget, headerAuth, API_URL } from "./lib/auth.js";
import { scenarioMetrics, thresholdsFor } from "./lib/metrics.js";
import { b2cWriteStages } from "./lib/diurnal.js";
import { syntheticUserId } from "./lib/pool.js";
import { handleSummary as gapHandleSummary } from "./lib/gapreport.js";
import { pickWriteAction } from "./lib/writemix.js";

const SCENARIO_ID = "INFRA-05";
const METRIC_PREFIX = SCENARIO_ID.replace(/-/g, "_");
const { latency, failures } = scenarioMetrics(METRIC_PREFIX);

// 24h↔압축 스케일. 기본 0.02 = 24h를 약 28.8분(1728s)으로 압축(로컬 상시 구동 검증용).
// 실제 상시(24시간) 구동 시에는 QA_TIME_SCALE=1 로 오버라이드한다.
const TIME_SCALE = Number(__ENV.QA_TIME_SCALE) || 0.02;
// peak 900(=B2C 목표 3000 × 0.3, lib/diurnal.js#b2cWriteStages 고정값).
const PEAK_TPS = 900;

// ramping-arrival-rate 튜닝 — 목표 도착률을 서버 지연과 무관하게 유지하려면
// preAllocatedVUs·maxVUs가 목표 TPS × 예상 응답시간(s)보다 넉넉해야 한다.
const PRE_ALLOCATED_VUS = Number(__ENV.QA_WRITE_PRE_ALLOCATED_VUS) || 200;
const MAX_VUS = Number(__ENV.QA_WRITE_MAX_VUS) || 2000;

// synthetic 시드 범위 (qa/load/seeds/simulator-baseline.sql, INFRA-03)
const SLOT_ID_BASE = Number(__ENV.QA_DIURNAL_SLOT_ID_BASE) || 9000001;
const SLOT_POOL_SIZE = Number(__ENV.QA_DIURNAL_SLOT_POOL_SIZE) || 30;
const PRODUCT_ID_BASE = Number(__ENV.QA_DIURNAL_PRODUCT_ID_BASE) || 9010001;
const PRODUCT_POOL_SIZE = Number(__ENV.QA_DIURNAL_PRODUCT_POOL_SIZE) || 10;
const EVENT_ID = __ENV.QA_DIURNAL_EVENT_ID || "9000001";
const SEAT_ID_BASE = Number(__ENV.QA_DIURNAL_SEAT_ID_BASE) || 9000001;
const SEAT_POOL_SIZE = Number(__ENV.QA_DIURNAL_SEAT_POOL_SIZE) || 5000;

export const options = {
  scenarios: {
    b2c_diurnal_write: {
      executor: "ramping-arrival-rate",
      startRate: 0,
      timeUnit: "1s",
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
      stages: b2cWriteStages(TIME_SCALE),
      exec: "default",
    },
  },
  // 자원 소진 409는 정상 비즈니스 응답 — failures(5xx 전용 Rate)만 임계 판정에 사용한다.
  thresholds: thresholdsFor(METRIC_PREFIX, "complexPost", {
    p95: 1000,
    p99: 3000,
    errorRate: 0.01,
  }),
};

export function setup() {
  assertSafeTarget();
  return { targetPeak: PEAK_TPS };
}

function bookingWrite(headers) {
  const slotId = SLOT_ID_BASE + Math.floor(Math.random() * SLOT_POOL_SIZE);
  const payload = JSON.stringify({
    slotId,
    paymentMethod: "CREDIT_CARD",
    amount: 10000,
    currency: "KRW",
  });

  return http.post(`${API_URL}/bookings`, payload, {
    headers,
    tags: { scenario: SCENARIO_ID, step: "booking-create" },
  });
}

function goodsOrderWrite(headers, idempotencyKey) {
  const productId = PRODUCT_ID_BASE + Math.floor(Math.random() * PRODUCT_POOL_SIZE);
  const payload = JSON.stringify({
    method: "CREDIT_CARD",
    fromCart: false,
    items: [{ productId, quantity: 1 }],
  });

  return http.post(`${API_URL}/goods-orders`, payload, {
    headers: { ...headers, "Idempotency-Key": idempotencyKey },
    tags: { scenario: SCENARIO_ID, step: "goods-order-create" },
  });
}

function ticketOrderWrite(headers, idempotencyKey) {
  const seatId = SEAT_ID_BASE + Math.floor(Math.random() * SEAT_POOL_SIZE);

  const selectRes = http.post(
    `${API_URL}/events/${EVENT_ID}/seats/select`,
    JSON.stringify({ seatIds: [seatId] }),
    { headers, tags: { scenario: SCENARIO_ID, step: "seat-select" } },
  );

  // 좌석 선점(락) 경쟁으로 인한 실패는 정상 비즈니스 응답 — lockId가 없으면 구매를
  // 시도하지 않고 select 응답만으로 이번 iteration을 종료한다.
  if (selectRes.status !== 200) {
    return selectRes;
  }
  const lockId = selectRes.json("lockId");
  if (!lockId) {
    return selectRes;
  }

  const orderRes = http.post(
    `${API_URL}/ticket-orders`,
    JSON.stringify({ lockId, method: "CREDIT_CARD", currency: "KRW" }),
    {
      headers: { ...headers, "Idempotency-Key": idempotencyKey },
      tags: { scenario: SCENARIO_ID, step: "ticket-order-create" },
    },
  );

  // E2E(select+order) latency 관측을 위해 두 응답을 모두 계측하되, 실패 판정은 마지막
  // 응답(orderRes) 기준으로 반환한다.
  latency.add(selectRes.timings.duration);
  failures.add(selectRes.status >= 500);
  return orderRes;
}

export default function () {
  const userId = syntheticUserId(__VU);
  const headers = headerAuth(userId);
  const idempotencyKey = `${SCENARIO_ID}-${__VU}-${__ITER}-${Date.now()}`;
  const action = pickWriteAction(Math.random());

  group(`[${SCENARIO_ID}] B2C diurnal write mix (${action})`, () => {
    let res;
    if (action === "booking") {
      res = bookingWrite(headers);
    } else if (action === "goodsOrder") {
      res = goodsOrderWrite(headers, idempotencyKey);
    } else {
      res = ticketOrderWrite(headers, idempotencyKey);
    }

    latency.add(res.timings.duration);
    // 5xx만 실패로 집계 — 409(슬롯 충돌·재고 부족·좌석 락 경쟁)는 정상 비즈니스 응답.
    const isServerError = res.status >= 500;
    failures.add(isServerError);

    check(res, {
      "not 5xx (자원 소진 409는 정상 비즈니스 응답)": (r) => r.status < 500,
    });
  });
}

export function handleSummary(data) {
  return gapHandleSummary(data, { targetPeak: PEAK_TPS, scenarioId: "b2c-diurnal-write" });
}
