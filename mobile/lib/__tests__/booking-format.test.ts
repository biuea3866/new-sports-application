/**
 * booking-format — 예약 목록(MO-05) 표시 유틸의 순수 로직 검증.
 */
import { BOOKING_STATUS_LABEL, resolveBookingTitle } from '../booking-format';

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
