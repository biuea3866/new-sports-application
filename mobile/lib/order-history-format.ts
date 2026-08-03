/**
 * order-history-format — 내 주문 통합 조회(`/orders`) 화면이 공용으로 쓰는 순수 포맷 유틸.
 *
 * 근거: `20260708-상품주문-공유상위컨텍스트-design-fe-app.md` "텍스트 와이어프레임 ②"·
 * "테마 토큰 정의 표"(status "결제완료"는 success 점 강조), 티켓 `FE-07`.
 * status/orderType → 한글 라벨 매핑과 표시명 fallback 규칙을 컴포넌트 밖으로 분리해
 * `OrderHistoryItemCard`가 분기 로직 없이 렌더링에만 집중하도록 한다.
 */
import type { OrderHistoryItem, OrderHistorySeat, OrderType } from '../api/order-history-types';
import { formatFeeAmount } from './recruitment-format';
import { formatSeatDescription } from './seat-format';

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
 *
 * PAID/PREPARING/COMPLETED — 주문상세(Option A, `app/orders/[orderType]/[id].tsx`)가
 * 원본 도메인의 raw status(BookingStatus.COMPLETED, GoodsOrderStatus.PAID/PREPARING)를
 * 그대로 표시할 때 쓰는 라벨. `/api/orders` 통합 조회는 정규화된 값(CONFIRMED 등)만
 * 내려주므로 목록 화면(OrderHistoryItemCard)에는 영향 없는 additive 확장이다.
 */
export const ORDER_HISTORY_STATUS_LABEL: Record<string, string> = {
  PENDING: '대기',
  CONFIRMED: '결제완료',
  CANCELLED: '취소',
  EXPIRED: '만료',
  REFUNDED: '환불',
  SHIPPED: '배송중',
  DELIVERED: '배송완료',
  PAID: '결제완료',
  PREPARING: '준비중',
  COMPLETED: '완료',
};

/** status name을 한글 라벨로 변환한다. 매핑에 없으면 원본 문자열을 그대로 반환한다. */
export function formatOrderHistoryStatusLabel(status: string): string {
  return ORDER_HISTORY_STATUS_LABEL[status] ?? status;
}

/** "결제완료"(CONFIRMED)인지 — success 점 강조 여부를 결정한다. */
export function isPaymentConfirmedStatus(status: string): boolean {
  return status === 'CONFIRMED';
}

/**
 * paymentId 연계 표시. paymentId는 내부 결제 PK라 그대로 노출하지 않는다 — 있으면 사용자에게
 * 알려줄 정보가 없어(있다는 사실 자체는 상태 배지가 이미 전달) `null`을 반환해 화면이 줄
 * 자체를 생략한다. 없으면 "결제를 아직 진행하지 않았다"는 의미 있는 사실이라 "미결제"를 유지한다.
 */
export function formatPaymentLabel(paymentId: number | null): string | null {
  return paymentId === null ? '미결제' : null;
}

/**
 * TICKETING 좌석 부가정보 표시 — 같은 이벤트에 여러 좌석 주문이 있을 때 목록에서 구분할 수 있게
 * 돕는다. 좌석 서술형 라벨은 `seat-format.ts#formatSeatDescription`(주문 확인 화면이 이미 쓰는
 * "A석구역 1열 05번" 서식)을 재사용한다 — 새 포맷터를 만들지 않는다. 2건 이상이면 대표
 * 좌석(첫 번째) + "외 N석"으로 축약한다. 좌석 정보가 없으면(TICKETING 외 유형) `null`을
 * 반환해 화면이 부가정보 줄을 생략하게 한다.
 */
export function formatOrderHistorySeatSummary(seats: OrderHistorySeat[] | null): string | null {
  if (seats === null || seats.length === 0) return null;
  const representative = seats[0];
  const representativeLabel = formatSeatDescription(
    representative.section,
    representative.rowNo,
    representative.seatNo
  );
  return seats.length > 1 ? `${representativeLabel} 외 ${seats.length - 1}석` : representativeLabel;
}

/**
 * 결제 금액 표시. `formatFeeAmount`(recruitment-format)를 재사용해 천 단위 구분자·"무료"(0원)
 * 규칙을 중복 구현하지 않는다. `amount`가 `null`이면(금액 확정 불가 — 과거 BOOKING 예약 등)
 * `null`을 반환해 화면이 금액 줄 자체를 생략하게 한다 — `0`(무료 확정값)과 구분해야 하므로
 * 0으로 방어하지 않는다.
 */
export function formatOrderHistoryAmount(amount: number | null): string | null {
  if (amount === null) return null;
  return formatFeeAmount(amount);
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
