/**
 * order-history-format — 내 주문 통합 조회(`/orders`) 화면이 공용으로 쓰는 순수 포맷 유틸.
 *
 * 근거: `20260708-상품주문-공유상위컨텍스트-design-fe-app.md` "텍스트 와이어프레임 ②"·
 * "테마 토큰 정의 표"(status "결제완료"는 success 점 강조), 티켓 `FE-07`.
 * status/orderType → 한글 라벨 매핑과 표시명 fallback 규칙을 컴포넌트 밖으로 분리해
 * `OrderHistoryItemCard`가 분기 로직 없이 렌더링에만 집중하도록 한다.
 */
import type { OrderHistoryItem, OrderType } from '../api/order-history-types';

/** orderType → 한글 배지 라벨. */
export const ORDER_TYPE_LABEL: Record<OrderType, string> = {
  BOOKING: '예약',
  TICKETING: '티켓',
  GOODS: '상품',
  RECRUITMENT: '모집',
};

/**
 * 원본 도메인 status enum name → 한글 라벨. 4개 주문 도메인(BookingStatus·OrderStatus·
 * GoodsOrderStatus·ApplicationStatus)의 합집합을 다룬다. 매핑에 없는 값은 원본 문자열
 * 그대로 노출한다(안전한 fallback — 신규 status 추가 시 화면이 깨지지 않음).
 */
export const ORDER_HISTORY_STATUS_LABEL: Record<string, string> = {
  PENDING: '대기',
  CONFIRMED: '결제완료',
  CANCELLED: '취소',
  EXPIRED: '만료',
  REFUNDED: '환불',
  SHIPPED: '배송중',
  DELIVERED: '배송완료',
};

/** status name을 한글 라벨로 변환한다. 매핑에 없으면 원본 문자열을 그대로 반환한다. */
export function formatOrderHistoryStatusLabel(status: string): string {
  return ORDER_HISTORY_STATUS_LABEL[status] ?? status;
}

/** "결제완료"(CONFIRMED)인지 — success 점 강조 여부를 결정한다. */
export function isPaymentConfirmedStatus(status: string): boolean {
  return status === 'CONFIRMED';
}

/** paymentId 연계 표시. 있으면 "결제 #id", 없으면 "미결제". */
export function formatPaymentLabel(paymentId: number | null): string {
  return paymentId === null ? '미결제' : `결제 #${paymentId}`;
}

/**
 * 항목의 주 표시명. `title`이 있으면 그대로 사용하고, 비어 있거나 누락된 경우에만
 * "유형명 #sourceId" 형태의 fallback으로 대체한다(정상 흐름은 항상 title 사용).
 */
export function formatOrderHistoryDisplayName(
  item: Pick<OrderHistoryItem, 'title' | 'orderType' | 'sourceId'>
): string {
  if (item.title.trim().length > 0) {
    return item.title;
  }
  return `${ORDER_TYPE_LABEL[item.orderType]} #${item.sourceId}`;
}
