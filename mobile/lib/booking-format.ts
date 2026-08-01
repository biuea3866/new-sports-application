/**
 * booking-format — 시설 예약(MO-05) 화면이 공용으로 쓰는 순수 표시 유틸.
 *
 * 목록에 내부 식별자(`예약 #2`)나 영문 enum(`CONFIRMED`)이 그대로 나오지 않도록,
 * 표시 문자열 결정을 화면 밖 순수 함수로 분리한다(no-logic-in-component).
 * 라벨 집합은 모집 신청(`recruitment-format.ts#APPLICATION_STATUS_LABEL`)과 같은 규칙을 따른다.
 */
import type { BookingStatus } from '../api/types';

/** BE `BookingResponse.title`이 없을 때(슬롯 조인 없는 응답 경로) 쓰는 대체 문구. */
const BOOKING_TITLE_FALLBACK = '시설 예약';

/** `BookingStatus`에 대한 한글 라벨. */
export const BOOKING_STATUS_LABEL: Record<BookingStatus, string> = {
  PENDING: '결제 대기',
  CONFIRMED: '확정',
  CANCELLED: '취소됨',
  EXPIRED: '기간 만료',
  REFUNDED: '환불됨',
};

/**
 * 예약 카드 제목. BE가 시설·회차 제목(`title`)을 내려주므로 그대로 쓰고,
 * 비어 있을 때만 대체 문구로 폴백한다 — 사용자에게 예약 PK를 노출하지 않는다.
 */
export function resolveBookingTitle(booking: { id: number; title: string | null }): string {
  const title = booking.title ?? '';
  return title.trim().length > 0 ? title : BOOKING_TITLE_FALLBACK;
}

/** 취소 가능한 상태인지 — 이미 종료된 예약은 재취소 대상이 아니다. */
export function isBookingCancellable(status: BookingStatus): boolean {
  return status === 'PENDING' || status === 'CONFIRMED';
}
