// qa/load/k6/lib/b2bRegistration.js
// INFRA-06 전용 순수 함수 — b2b-diurnal.js가 소비한다(다른 시나리오는 참조하지 않음).
//
// 근거: 티켓 "등록 혼합: POST /api/goods-seller/products·POST /api/event-host/events"·
// "압축 실행에서 누적 등록 건수를 하루 환산 시 1000건 이상". http/k6 모듈에 의존하지 않는
// 순수 로직만 분리해 node:test로 k6 런타임 없이 검증한다(lib/diurnal.js 관례 계승).

const PRODUCT_CATEGORIES = ["EQUIPMENT", "APPAREL", "FOOTWEAR", "ACCESSORY"];
const EVENT_STARTS_AT_DAYS_FROM_NOW = 30;

/**
 * 반복(iteration) 인덱스로 "product"·"event" 등록을 교대 선택한다(50:50 혼합).
 * @param {number} iterationIndex k6 __ITER 값(0부터 시작)
 * @returns {"product"|"event"}
 */
export function selectRegistrationAction(iterationIndex) {
  return iterationIndex % 2 === 0 ? "product" : "event";
}

/**
 * POST /api/goods-seller/products 요청 바디(CreateMyProductRequest 계약).
 * VU·ITER로 name을 유니크하게 만들어 반복 호출이 동일 상품으로 충돌하지 않게 한다.
 */
export function buildProductRegistrationRequest(vu, iterationIndex) {
  const category = PRODUCT_CATEGORIES[iterationIndex % PRODUCT_CATEGORIES.length];
  return {
    name: `b2b-load-product-${vu}-${iterationIndex}`,
    category,
    price: 10000,
    description: "INFRA-06 B2B 일주기 부하 상품(synthetic)",
    imageUrl: "https://example.com/synthetic/product.png",
  };
}

/**
 * POST /api/event-host/events 요청 바디(CreateMyEventRequest 계약).
 * seats는 최소 1석을 포함해야 등록이 성립한다(빈 seats 방지).
 */
export function buildEventRegistrationRequest(vu, iterationIndex) {
  return {
    title: `b2b-load-event-${vu}-${iterationIndex}`,
    venue: "INFRA-06 synthetic venue",
    startsAt: futureIsoTimestamp(EVENT_STARTS_AT_DAYS_FROM_NOW),
    seats: [
      { sectionName: "A", seatLabel: `A-${iterationIndex}`, price: 50000 },
    ],
  };
}

function futureIsoTimestamp(daysFromNow) {
  return new Date(Date.now() + daysFromNow * 24 * 60 * 60 * 1000).toISOString();
}

/**
 * 압축 실행(timeScale<1)의 실측 누적 건수를 24h(실시간) 환산으로 extrapolate한다.
 * b2bStages(lib/diurnal.js)의 target TPS는 timeScale과 무관(동일)하고 stage duration만
 * 비례 축소되므로, 압축 실행 실측 건수 ÷ timeScale = 하루(24h) 환산 건수다.
 *
 * @param {number} registeredCount 압축 실행 동안 실제 등록 성공(2xx) 건수
 * @param {number} timeScale 1=실시간 24h, 0.2=압축(1/5) 등
 * @returns {number} 하루 환산 등록 건수(반올림)
 */
export function dailyEquivalentCount(registeredCount, timeScale = 1) {
  if (!timeScale) return registeredCount;
  return Math.round(registeredCount / timeScale);
}
