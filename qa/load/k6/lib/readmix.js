// qa/load/k6/lib/readmix.js
// INFRA-04: B2C 조회(read) 엔드포인트 가중 혼합표.
//
// 근거: 티켓 "조회 엔드포인트 가중 혼합" — GET /facilities?gu&type(permitAll),
// GET /facilities/{fid}/slots, GET /products·/products/{id}·/products/popular,
// GET /events·/events/{id}. 테스트 케이스 "조회 엔드포인트 3종이 가중 혼합으로 호출된다"
// (facility/product/event 3개 도메인 그룹). 가중치는 실사용 브라우징 패턴을 근사한다:
// 목록 검색(시설 찾기·상품 목록)이 상세·부가 조회보다 빈도가 높다는 가정 하에
// facility·product 그룹을 동일 비중(0.40)으로, event 그룹은 상대적으로 낮은 비중(0.20)으로 뒀다.
export const READ_MIX = [
  { group: "facility", name: "facilities-list", weight: 0.3 },
  { group: "facility", name: "facility-slots", weight: 0.1 },
  { group: "product", name: "products-list", weight: 0.2 },
  { group: "product", name: "product-detail", weight: 0.1 },
  { group: "product", name: "products-popular", weight: 0.1 },
  { group: "event", name: "events-list", weight: 0.12 },
  { group: "event", name: "event-detail", weight: 0.08 },
];

/**
 * randomValue([0,1))를 누적 가중치 구간에 매핑해 READ_MIX 엔트리 하나를 결정적으로 고른다.
 * k6 스크립트의 default 함수에서 Math.random()과 함께 호출한다.
 *
 * @param {number} [randomValue] 0 이상 1 미만의 균등분포 값. 기본값 Math.random()
 * @returns {{group: string, name: string, weight: number}}
 */
export function pickReadEndpoint(randomValue = Math.random()) {
  let cumulative = 0;
  for (const entry of READ_MIX) {
    cumulative += entry.weight;
    if (randomValue < cumulative) return entry;
  }
  return READ_MIX[READ_MIX.length - 1];
}
