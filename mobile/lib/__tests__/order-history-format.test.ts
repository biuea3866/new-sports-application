/**
 * order-history-format — status/orderType 한글 라벨 매핑과 표시명 fallback 규칙을 검증한다.
 */
import {
  ORDER_TYPE_LABEL,
  formatOrderHistoryStatusLabel,
  isPaymentConfirmedStatus,
  formatPaymentLabel,
  formatOrderHistoryDisplayName,
  formatOrderHistoryAmount,
  formatOrderHistorySeatSummary,
} from '../order-history-format';

describe('formatOrderHistoryStatusLabel', () => {
  it('CONFIRMED를 결제완료로 변환한다', () => {
    expect(formatOrderHistoryStatusLabel('CONFIRMED')).toBe('결제완료');
  });

  it('PENDING을 대기로 변환한다', () => {
    expect(formatOrderHistoryStatusLabel('PENDING')).toBe('대기');
  });

  it('매핑에 없는 status는 원본 문자열을 그대로 반환한다', () => {
    expect(formatOrderHistoryStatusLabel('UNKNOWN_STATUS')).toBe('UNKNOWN_STATUS');
  });
});

describe('isPaymentConfirmedStatus', () => {
  it('CONFIRMED면 true를 반환한다', () => {
    expect(isPaymentConfirmedStatus('CONFIRMED')).toBe(true);
  });

  it('CONFIRMED가 아니면 false를 반환한다', () => {
    expect(isPaymentConfirmedStatus('PENDING')).toBe(false);
  });
});

describe('formatPaymentLabel', () => {
  it('paymentId가 있으면 내부 식별자를 노출하지 않는다(null 반환 — 화면이 줄 생략)', () => {
    expect(formatPaymentLabel(4821)).toBeNull();
  });

  it('paymentId가 없으면 미결제를 반환한다(사용자에게 의미 있는 정보라 유지)', () => {
    expect(formatPaymentLabel(null)).toBe('미결제');
  });
});

describe('formatOrderHistorySeatSummary', () => {
  it('좌석이 1건이면 해당 좌석 서술형 라벨만 반환한다', () => {
    expect(formatOrderHistorySeatSummary([{ section: 'A석', rowNo: '1', seatNo: 'A석-05' }])).toBe(
      'A석구역 1열 05번'
    );
  });

  it('좌석이 2건 이상이면 대표 좌석 + "외 N석"을 반환한다', () => {
    expect(
      formatOrderHistorySeatSummary([
        { section: 'A석', rowNo: '1', seatNo: 'A석-05' },
        { section: 'A석', rowNo: '1', seatNo: 'A석-06' },
      ])
    ).toBe('A석구역 1열 05번 외 1석');
  });

  it('좌석 정보가 없으면(null) null을 반환한다(화면에서 부가정보 줄 생략)', () => {
    expect(formatOrderHistorySeatSummary(null)).toBeNull();
  });

  it('좌석 정보가 빈 배열이면 null을 반환한다', () => {
    expect(formatOrderHistorySeatSummary([])).toBeNull();
  });
});

describe('formatOrderHistoryDisplayName', () => {
  it('title이 있으면 title을 그대로 반환한다', () => {
    expect(
      formatOrderHistoryDisplayName({
        title: '강남 풋살장 예약',
        orderType: 'BOOKING',
        sourceId: 42,
      })
    ).toBe('강남 풋살장 예약');
  });

  it('title이 빈 문자열이면 유형명 #sourceId fallback을 반환한다', () => {
    expect(formatOrderHistoryDisplayName({ title: '', orderType: 'GOODS', sourceId: 1203 })).toBe(
      `${ORDER_TYPE_LABEL.GOODS} #1203`
    );
  });

  it('title이 공백 문자열이면 fallback을 반환한다', () => {
    expect(
      formatOrderHistoryDisplayName({ title: '   ', orderType: 'RECRUITMENT', sourceId: 7 })
    ).toBe(`${ORDER_TYPE_LABEL.RECRUITMENT} #7`);
  });
});

describe('formatOrderHistoryAmount', () => {
  it('금액을 천 단위 구분자와 함께 원 단위로 반환한다', () => {
    expect(formatOrderHistoryAmount(50000)).toBe('50,000원');
  });

  it('금액이 0이면 무료를 반환한다', () => {
    expect(formatOrderHistoryAmount(0)).toBe('무료');
  });

  it('금액이 null이면 null을 반환한다(화면에서 금액 줄 생략)', () => {
    expect(formatOrderHistoryAmount(null)).toBeNull();
  });
});
