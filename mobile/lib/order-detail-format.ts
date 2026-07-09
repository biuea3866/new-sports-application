/**
 * order-detail-format — 주문 상세(Option A+, `app/orders/[orderType]/[id].tsx`) 화면이
 * 쓰는 순수 뷰모델 변환 유틸.
 *
 * BE 4종 주문상세 응답 보강(Option A+, `feat/booking-order-detail-enrich`·
 * `feat/goods-order-detail-enrich`·`feat/ticket-order-detail-enrich`·
 * `feat/recruitment-application-detail-endpoint`)이 origin/main에 머지되어, 4개 도메인
 * 모두 제목·주문 일시·원본 참조 PK를 제공한다(백엔드 소스 재대조 결과 — `api/types.ts`·
 * `api/recruitment.ts` 주석 참조). BOOKING의 facilityId·title, GOODS의 title·createdAt은
 * 여전히 nullable이라(구 데이터·특정 생성 경로) 그 경우에만 fallback을 쓴다.
 *
 * 화면이 공통 레이아웃(제목·상태 배지·결제 정보·주문 일시·유형별 요약·원본 보기)을
 * 분기 로직 없이 렌더링할 수 있도록 이 유틸이 단일 `OrderDetailViewModel`로 정규화한다
 * (`no-logic-in-component`).
 */
import type { ApplicationDetailResponse, ApplicationStatus } from '../api/recruitment';
import type {
  BookingResponse,
  BookingStatus,
  GoodsOrderDetailResponse,
  GoodsOrderStatus,
  TicketOrderDetailResponse,
  TicketOrderStatus,
} from '../api/types';
import { ROUTES } from './navigation';
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

/** title이 비어있거나 없으면 "유형명 #id" fallback을 쓴다(`formatOrderHistoryDisplayName`과 동일 원칙). */
function resolveTitle(
  orderType: keyof typeof ORDER_TYPE_LABEL,
  id: number,
  title: string | null
): string {
  if (title !== null && title.trim().length > 0) {
    return title;
  }
  return `${ORDER_TYPE_LABEL[orderType]} #${id}`;
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

/**
 * `GET /bookings/{id}` 응답 → 공통 뷰모델. facilityId·title은 Slot 조인 경로에서만 채워져
 * 여전히 nullable이다(cancel 경로 등) — null이면 title은 fallback, 원본 보기는 숨긴다.
 */
export function toBookingDetailViewModel(id: number, data: BookingResponse): OrderDetailViewModel {
  return {
    title: resolveTitle('BOOKING', id, data.title),
    statusLabel: formatOrderHistoryStatusLabel(data.status),
    isPaymentConfirmed: isBookingConfirmed(data.status),
    paymentLabel: formatPaymentLabel(data.paymentId),
    dateTimeLabel: formatOrderDetailDateTime(data.createdAt),
    summaryLines: [`슬롯 #${data.slotId}`],
    originRoute:
      data.facilityId !== null && data.facilityId.trim().length > 0
        ? ROUTES.facility.detail(data.facilityId)
        : null,
  };
}

/**
 * `GET /goods-orders/{orderId}` 응답 → 공통 뷰모델. title(대표 상품명)·createdAt은 여전히
 * nullable(백엔드 계약, `api/types.ts` 주석 참조) — null이면 각각 fallback/"정보 없음".
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
    title: resolveTitle('GOODS', id, data.title),
    statusLabel:
      data.status === null ? STATUS_UNKNOWN_LABEL : formatOrderHistoryStatusLabel(data.status),
    isPaymentConfirmed: isGoodsOrderConfirmed(data.status),
    paymentLabel: formatPaymentLabel(data.paymentId),
    dateTimeLabel: formatOrderDetailDateTime(data.createdAt),
    summaryLines: [...itemLines, totalLine],
    // 상품이 1건일 때만 그 상품 상세로 연결한다(2건 이상이면 단일 원본을 특정할 수 없음).
    originRoute: singleProductId === null ? null : ROUTES.product.detail(String(singleProductId)),
  };
}

/**
 * `GET /ticket-orders/{id}` 응답 → 공통 뷰모델. `TicketOrderDetailResponse`는
 * eventId·eventTitle·paymentId·createdAt을 모두 제공한다(Option A+, 구 최소 계약과 달리
 * fallback이 사실상 불필요 — eventTitle은 방어적으로만 fallback 처리).
 */
export function toTicketOrderDetailViewModel(
  data: TicketOrderDetailResponse
): OrderDetailViewModel {
  return {
    title: resolveTitle('TICKETING', data.ticketOrderId, data.eventTitle),
    statusLabel: formatOrderHistoryStatusLabel(data.status),
    isPaymentConfirmed: isTicketOrderConfirmed(data.status),
    paymentLabel: formatPaymentLabel(data.paymentId),
    dateTimeLabel: formatOrderDetailDateTime(data.createdAt),
    summaryLines: [],
    originRoute: ROUTES.event.detail(String(data.eventId)),
  };
}

/**
 * `GET /applications/{id}` 응답 → 공통 뷰모델. `recruitmentTitle`·`feeAmount`(non-null
 * BigDecimal)로 필드명이 확정됐다(Option A+, origin/main 머지 완료 — "API 미연동" 해소).
 */
export function toApplicationDetailViewModel(
  data: ApplicationDetailResponse
): OrderDetailViewModel {
  const feeLine = data.feeAmount === 0 ? '무료 참가' : `참가비 ${formatAmount(data.feeAmount)}원`;

  return {
    title: resolveTitle('RECRUITMENT', data.applicationId, data.recruitmentTitle),
    statusLabel: formatOrderHistoryStatusLabel(data.status),
    isPaymentConfirmed: isApplicationConfirmed(data.status),
    paymentLabel: formatPaymentLabel(data.paymentId),
    dateTimeLabel: formatOrderDetailDateTime(data.createdAt),
    summaryLines: [feeLine],
    originRoute: ROUTES.recruitment.detail(String(data.recruitmentId)),
  };
}
