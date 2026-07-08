// qa/load/k6/lib/writemix.js
// INFRA-05: B2C 쓰기(write) 곡선의 쓰기 액션 가중 혼합표.
//
// 근거: 티켓 "쓰기 혼합" — POST /bookings(예약 생성), POST /goods-orders(상품 주문),
// POST /events/{id}/seats/select + POST /ticket-orders(좌석 선택+구매)를 균등 배분한다.
// 세 액션 모두 소진성 자원(슬롯 capacity·재고 quantity·좌석 락)을 소비하는 대표 쓰기
// 플로우이며, 우선순위 차등을 둘 근거(TDD·티켓)가 없어 1/3씩 균등 혼합한다(INFRA-04
// lib/readmix.js와 동일한 누적 가중치 선택 관례를 따른다).
export const WRITE_MIX = [
  { name: "booking", weight: 0.34 },
  { name: "goodsOrder", weight: 0.33 },
  { name: "ticketOrder", weight: 0.33 },
];

/**
 * randomValue([0,1))를 누적 가중치 구간에 매핑해 WRITE_MIX 액션명 하나를 결정적으로 고른다.
 * k6 스크립트의 default 함수에서 Math.random()과 함께 호출한다.
 *
 * @param {number} [randomValue] 0 이상 1 미만의 균등분포 값. 기본값 Math.random()
 * @returns {string} WRITE_MIX 엔트리의 name
 */
export function pickWriteAction(randomValue = Math.random()) {
  let cumulative = 0;
  for (const entry of WRITE_MIX) {
    cumulative += entry.weight;
    if (randomValue < cumulative) return entry.name;
  }
  return WRITE_MIX[WRITE_MIX.length - 1].name;
}
