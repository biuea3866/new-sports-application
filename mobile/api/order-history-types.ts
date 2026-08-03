/**
 * order-history-types.ts — 내 주문 통합 조회 응답 타입
 *
 * BE 계약: GET /api/orders (authenticated) → OrderHistoryResponse
 * 근거: design-fe-app.md "API 연동 표"·"응답 타입" — BE TDD 인터페이스 시그니처와 1:1 일치.
 */

export type OrderType = 'BOOKING' | 'TICKETING' | 'GOODS' | 'RECRUITMENT';

/**
 * TICKETING 주문이 잠근 좌석 1건의 원본 필드. BE가 조합한 문자열이 아니라 원본 필드로
 * 내려온다 — 표시 조합(`"A석구역 1열 05번"`)은 모바일이 이미 쓰는
 * `seat-format.ts#formatSeatDescription`으로 통일한다(`lib/order-history-format.ts#formatOrderHistorySeatSummary`).
 */
export interface OrderHistorySeat {
  section: string;
  rowNo: string;
  seatNo: string;
}

export interface OrderHistoryItem {
  orderType: OrderType;
  sourceId: number;
  title: string; // 항목 표시명 (senior-pm 승격 → BE 계약에 추가 확정)
  status: string;
  paymentId: number | null;
  detailPath: string;
  createdAt: string; // ISO-8601
  // 결제 금액 — 4개 주문 컨텍스트가 각자 자기 데이터로 채운다. 금액을 확정할 수 없으면(과거
  // BOOKING 예약 등) null — 무료(0, RECRUITMENT)와 구분해야 하므로 0으로 방어하지 않는다.
  amount: number | null;
  // 같은 title을 가진 서로 다른 TICKETING 주문을 좌석으로 구분하기 위한 원본 필드 목록.
  // TICKETING 외 유형은 구분에 쓸 자기 데이터가 없어 null이다(유형마다 다른 필드를 억지로
  // 한 칸에 밀어넣지 않는다).
  seats: OrderHistorySeat[] | null;
}

export interface OrderHistoryResponse {
  items: OrderHistoryItem[];
  page: number;
  size: number;
  failedDomains: OrderType[];
}

export interface OrderHistoryCriteria {
  orderType?: OrderType;
  status?: string;
  page: number;
  size: number;
}
