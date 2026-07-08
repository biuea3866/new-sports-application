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
