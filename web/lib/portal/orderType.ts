/**
 * 주문 유형(OrderType) — 값 목록 · 한글 라벨의 단일 출처.
 *
 * `주문 유형` 열이 BOOKING/TICKETING/GOODS 영문 원문을 그대로 노출해 이미 한글화된 다른 화면과
 * 표기가 어긋났다(02-파트너포털 매출 화면 결함). mobile 앱(`mobile/lib/order-history-format.ts`
 * `ORDER_TYPE_LABEL`)과 값을 맞춰, 같은 도메인 개념이 화면마다 다른 한글로 갈리지 않게 한다.
 */
import type { OrderType } from "./payments";

export const ORDER_TYPE_LABELS: Record<OrderType, string> = {
  BOOKING: "예약",
  TICKETING: "티켓",
  GOODS: "상품",
};

/** 값이 비어 있을 때 표에 채우는 자리표시자. */
const EMPTY_PLACEHOLDER = "-";

function isKnownOrderType(value: string): value is OrderType {
  return Object.prototype.hasOwnProperty.call(ORDER_TYPE_LABELS, value);
}

/**
 * 주문 유형을 화면 문구로 바꾼다.
 *
 * 계약 밖 값이 와도 예외를 던지지 않고 원문을 그대로 돌려준다 — 표시 계층이 화면 전체를
 * 죽이는 일이 없어야 하고, 원문이 남아야 어떤 값이 새로 생겼는지 추적할 수 있다.
 */
export function orderTypeLabel(orderType: string | null | undefined): string {
  if (orderType === null || orderType === undefined || orderType.length === 0) {
    return EMPTY_PLACEHOLDER;
  }
  return isKnownOrderType(orderType) ? ORDER_TYPE_LABELS[orderType] : orderType;
}
