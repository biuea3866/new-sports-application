/**
 * booking-format — 예약 목록(MO-05) 표시 유틸의 순수 로직 검증.
 */
import {
  BOOKING_STATUS_LABEL,
  formatBookingCreatedDate,
  resolveBookingTitle,
} from '../booking-format';

describe('resolveBookingTitle', () => {
  it('BE가 내려준 시설·회차 제목을 그대로 사용한다', () => {
    expect(resolveBookingTitle({ id: 2, title: '강남 스포츠센터 07:00-08:00' })).toBe(
      '강남 스포츠센터 07:00-08:00'
    );
  });

  it('제목이 없으면 내부 식별자 대신 사람이 읽는 대체 문구를 쓴다', () => {
    expect(resolveBookingTitle({ id: 2, title: null })).toBe('시설 예약');
  });

  it('제목이 공백만이면 대체 문구를 쓴다', () => {
    expect(resolveBookingTitle({ id: 2, title: '   ' })).toBe('시설 예약');
  });
});

describe('BOOKING_STATUS_LABEL', () => {
  it('모든 예약 상태에 한글 라벨이 있다', () => {
    expect(BOOKING_STATUS_LABEL).toEqual({
      PENDING: '결제 대기',
      CONFIRMED: '확정',
      CANCELLED: '취소됨',
      EXPIRED: '기간 만료',
      REFUNDED: '환불됨',
    });
  });

  it('영문 enum 원문을 그대로 노출하지 않는다', () => {
    Object.entries(BOOKING_STATUS_LABEL).forEach(([status, label]) => {
      expect(label).not.toBe(status);
    });
  });
});

describe('formatBookingCreatedDate', () => {
  it('월·일이 한 자리인 날짜도 0패딩된 두 자리로 표시한다', () => {
    expect(formatBookingCreatedDate('2026-01-05T02:00:00Z')).toBe('2026. 01. 05.');
  });

  it('월·일이 두 자리인 날짜도 동일한 자릿수로 표시한다', () => {
    expect(formatBookingCreatedDate('2026-12-25T02:00:00Z')).toBe('2026. 12. 25.');
  });

  it('BE `BookingTitleLabel`(yyyy. MM. dd.)과 같은 자릿수 형식을 만든다', () => {
    const formatted = formatBookingCreatedDate('2026-01-05T02:00:00Z');

    expect(formatted).toMatch(/^\d{4}\. \d{2}\. \d{2}\.$/);
  });
});
