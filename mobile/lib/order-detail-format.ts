/**
 * order-detail-format — 주문 상세(Option A, `app/orders/[orderType]/[id].tsx`) 화면이
 * 쓰는 순수 뷰모델 변환 유틸.
 *
 * 4개 주문 도메인(Booking/GoodsOrder/TicketOrder/Application)의 실제 GET 상세 응답은
 * 서로 필드 구성이 다르고(백엔드 소스 대조 결과: Booking·GoodsOrder는 title 없음,
 * GoodsOrder는 createdAt 없음, TicketOrder는 paymentId·createdAt·eventId 전혀 없음),
 * 화면이 공통 레이아웃(제목·상태 배지·결제 정보·주문 일시·유형별 요약·원본 보기)을
 * 분기 로직 없이 렌더링할 수 있도록 이 유틸이 단일 `OrderDetailViewModel`로 정규화한다
 * (`no-logic-in-component`).
 *
 * 응답에 없는 필드는 값을 지어내지 않고 "정보 없음"류 라벨 또는 `null`(보조 액션 숨김)로
 * 정직하게 표시한다 — 특히 원본 보기(참조 아이템 상세) 링크는 응답에 참조 PK가 있을 때만
 * 제공한다: BOOKING(facilityId 없음)·TICKETING(eventId 없음)은 항상 null,
 * GOODS는 상품이 1건일 때만 그 상품으로, RECRUITMENT는 항상 recruitmentId로 연결된다.
 */
import type { ApplicationDetailResponse, ApplicationStatus } from '../api/recruitment';
import type {
  BookingResponse,
  BookingStatus,
  GoodsOrderDetailResponse,
  GoodsOrderStatus,
  TicketOrderResponse,
  TicketOrderStatus,
} from '../api/types';
import { resolveCatalogRoute } from './catalog-navigation';
import {
  ORDER_TYPE_LABEL,
  formatOrderHistoryStatusLabel,
  formatPaymentLabel,
} from './order-history-format';

export interface OrderDetailViewModel {
  title: string;
  statusLabel: string;
  isPaymentConfirmed: boolean;
  paymentLabel: string;
  dateTimeLabel: string;
  summaryLines: string[];
  originRoute: string | null;
}

const DATETIME_UNAVAILABLE_LABEL = '정보 없음';
const STATUS_UNKNOWN_LABEL = '상태 미상';
const PAYMENT_INFO_UNAVAILABLE_LABEL = '결제 정보 없음';

/** ISO-8601 문자열을 로컬(ko-KR) 날짜·시각 문자열로 변환한다. 비어있거나 유효하지 않으면 "정보 없음". */
export function formatOrderDetailDateTime(iso: string | null | undefined): string {
  if (iso === null || iso === undefined || iso.trim().length === 0) {
    return DATETIME_UNAVAILABLE_LABEL;
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return DATETIME_UNAVAILABLE_LABEL;
  }
  return date.toLocaleString('ko-KR');
}

function formatAmount(amount: number | string): string {
  return Number(amount).toLocaleString('ko-KR');
}

function isBookingConfirmed(status: BookingStatus): boolean {
  return status === 'CONFIRMED';
}

function isGoodsOrderConfirmed(status: GoodsOrderStatus | null): boolean {
  return status !== null && status !== 'PENDING' && status !== 'CANCELLED';
}

function isTicketOrderConfirmed(status: TicketOrderStatus): boolean {
  return status === 'CONFIRMED';
}

function isApplicationConfirmed(status: ApplicationStatus): boolean {
  return status === 'CONFIRMED';
}

/** `GET /bookings/{id}` 응답 → 공통 뷰모델. BookingResponse에 title이 없어 fallback을 쓴다. */
export function toBookingDetailViewModel(id: number, data: BookingResponse): OrderDetailViewModel {
  return {
    title: `${ORDER_TYPE_LABEL.BOOKING} #${id}`,
    statusLabel: formatOrderHistoryStatusLabel(data.status),
    isPaymentConfirmed: isBookingConfirmed(data.status),
    paymentLabel: formatPaymentLabel(data.paymentId),
    dateTimeLabel: formatOrderDetailDateTime(data.createdAt),
    summaryLines: [`슬롯 #${data.slotId}`],
    // BookingResponse에는 facilityId가 없다(slotId만 보유) — 신뢰성 있는 원본 매핑 불가.
    originRoute: null,
  };
}

/**
 * `GET /goods-orders/{orderId}` 응답 → 공통 뷰모델. GoodsOrderDetailResponse에는
 * title·productName·createdAt이 없다(백엔드 실제 계약, `api/types.ts` 주석 참조).
 */
export function toGoodsOrderDetailViewModel(
  id: number,
  data: GoodsOrderDetailResponse
): OrderDetailViewModel {
  const itemLines = data.items.map(
    (item) => `상품 #${item.productId} × ${item.quantity} = ${formatAmount(item.subtotal)}원`
  );
  const totalLine = `합계 ${formatAmount(data.totalAmount)}원`;

  const singleProductId = data.items.length === 1 ? data.items[0].productId : null;

  return {
    title: `${ORDER_TYPE_LABEL.GOODS} #${id}`,
    statusLabel:
      data.status === null ? STATUS_UNKNOWN_LABEL : formatOrderHistoryStatusLabel(data.status),
    isPaymentConfirmed: isGoodsOrderConfirmed(data.status),
    paymentLabel: formatPaymentLabel(data.paymentId),
    // GoodsOrderDetailResponse에는 createdAt이 없다 — 항상 "정보 없음".
    dateTimeLabel: DATETIME_UNAVAILABLE_LABEL,
    summaryLines: [...itemLines, totalLine],
    // 상품이 1건일 때만 그 상품 상세로 연결한다(2건 이상이면 단일 원본을 특정할 수 없음).
    originRoute: singleProductId === null ? null : resolveCatalogRoute('PRODUCT', singleProductId),
  };
}

/**
 * `GET /ticket-orders/{id}` 응답 → 공통 뷰모델. TicketOrderResponse는
 * `{ticketOrderId, status}`뿐이라(백엔드 실제 계약) paymentId·createdAt·eventId가 없다.
 */
export function toTicketOrderDetailViewModel(data: TicketOrderResponse): OrderDetailViewModel {
  return {
    title: `${ORDER_TYPE_LABEL.TICKETING} #${data.ticketOrderId}`,
    statusLabel: formatOrderHistoryStatusLabel(data.status),
    isPaymentConfirmed: isTicketOrderConfirmed(data.status),
    // paymentId가 응답에 없다 — formatPaymentLabel(null)="미결제"는 오인을 유발하므로 쓰지 않는다.
    paymentLabel: PAYMENT_INFO_UNAVAILABLE_LABEL,
    dateTimeLabel: DATETIME_UNAVAILABLE_LABEL,
    summaryLines: [],
    // eventId가 응답에 없다 — 원본(이벤트) 상세로 신뢰성 있게 연결할 수 없다.
    originRoute: null,
  };
}

/**
 * `GET /applications/{id}` 응답 → 공통 뷰모델. BE에서 동시 신설 중인 계약(applicationId·
 * recruitmentId·title·status·feeAmount?·paymentId?·createdAt)을 그대로 반영한다 — 4개
 * 도메인 중 유일하게 title·createdAt·참조 PK(recruitmentId)를 모두 제공한다.
 */
export function toApplicationDetailViewModel(
  data: ApplicationDetailResponse
): OrderDetailViewModel {
  const feeLine =
    data.feeAmount === null || data.feeAmount === 0
      ? '무료 참가'
      : `참가비 ${formatAmount(data.feeAmount)}원`;

  return {
    title: data.title,
    statusLabel: formatOrderHistoryStatusLabel(data.status),
    isPaymentConfirmed: isApplicationConfirmed(data.status),
    paymentLabel: formatPaymentLabel(data.paymentId),
    dateTimeLabel: formatOrderDetailDateTime(data.createdAt),
    summaryLines: [feeLine],
    originRoute: resolveCatalogRoute('RECRUITMENT', data.recruitmentId),
  };
}
