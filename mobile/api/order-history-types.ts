/**
 * order-history-types.ts — 내 주문 통합 조회 응답 타입
 *
 * BE 계약: GET /api/orders (authenticated) → OrderHistoryResponse
 * 근거: design-fe-app.md "API 연동 표"·"응답 타입" — BE TDD 인터페이스 시그니처와 1:1 일치.
 */

export type OrderType = 'BOOKING' | 'TICKETING' | 'GOODS' | 'RECRUITMENT';

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
  // 같은 title을 가진 서로 다른 주문을 구분하는 부가 정보(현재는 TICKETING 좌석 라벨만 채움).
  subtitle: string | null;
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
