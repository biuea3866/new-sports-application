/**
 * recruitment-format — 참가비·D-day·상태 라벨 순수 포맷 유틸 검증.
 * 근거: design-fe-app.md "텍스트 와이어프레임" A-R1/A-R2.
 */
import {
  APPLICATION_STATUS_LABEL,
  RECRUITMENT_STATUS_LABEL,
  formatDateTimeMinute,
  formatDeadlineDday,
  formatDeadlineSummary,
  formatFeeAmount,
  isApplicationCancellable,
  resolveRecruitmentDisplayStatus,
} from '../recruitment-format';

describe('formatFeeAmount', () => {
  it('0원이면 무료로 표기한다', () => {
    expect(formatFeeAmount(0)).toBe('무료');
  });

  it('참가비를 천 단위 구분자와 함께 원 단위로 표기한다', () => {
    expect(formatFeeAmount(5000)).toBe('5,000원');
  });
});

describe('formatDeadlineDday', () => {
  const now = new Date('2026-07-08T00:00:00+09:00');

  it('마감까지 이틀 남으면 D-2를 반환한다', () => {
    expect(formatDeadlineDday('2026-07-10T00:00:00+09:00', now)).toBe('D-2');
  });

  it('당일 마감이면 D-day를 반환한다', () => {
    expect(formatDeadlineDday('2026-07-08T12:00:00+09:00', now)).toBe('D-day');
  });

  it('마감이 지났으면 마감을 반환한다', () => {
    expect(formatDeadlineDday('2026-07-01T00:00:00+09:00', now)).toBe('마감');
  });
});

describe('formatDeadlineSummary', () => {
  const now = new Date('2026-07-08T00:00:00+09:00');

  it('마감 전이면 D-day와 "마감"을 함께 표기한다', () => {
    expect(formatDeadlineSummary('2026-07-10T00:00:00+09:00', now)).toBe('D-2 마감');
  });

  it('마감이 지났으면 "마감"을 한 번만 표기한다', () => {
    expect(formatDeadlineSummary('2026-07-01T00:00:00+09:00', now)).toBe('마감');
  });

  it('당일 마감이면 D-day와 "마감"을 함께 표기한다', () => {
    expect(formatDeadlineSummary('2026-07-08T12:00:00+09:00', now)).toBe('D-day 마감');
  });
});

describe('resolveRecruitmentDisplayStatus', () => {
  const now = new Date('2026-07-08T00:00:00+09:00');

  it('마감 전 OPEN 모집은 OPEN으로 표시한다', () => {
    expect(
      resolveRecruitmentDisplayStatus(
        { status: 'OPEN', applicationDeadline: '2026-07-10T00:00:00+09:00' },
        now
      )
    ).toBe('OPEN');
  });

  // BE 상태 전이(스케줄러)가 아직 안 돌아 status가 OPEN으로 남아 있어도,
  // 신청마감이 지난 모집에 "모집 중" 배지를 달면 안 된다.
  it('신청마감이 지난 OPEN 모집은 CLOSED로 표시한다', () => {
    expect(
      resolveRecruitmentDisplayStatus(
        { status: 'OPEN', applicationDeadline: '2026-07-01T00:00:00+09:00' },
        now
      )
    ).toBe('CLOSED');
  });

  it('취소된 모집은 마감 여부와 무관하게 CANCELLED를 유지한다', () => {
    expect(
      resolveRecruitmentDisplayStatus(
        { status: 'CANCELLED', applicationDeadline: '2026-07-01T00:00:00+09:00' },
        now
      )
    ).toBe('CANCELLED');
  });
});

describe('formatDateTimeMinute', () => {
  it('초 단위를 노출하지 않고 분까지만 표기한다', () => {
    const formatted = formatDateTimeMinute('2026-08-09T20:00:00+09:00');

    expect(formatted).toContain('2026');
    expect(formatted).not.toMatch(/:\d{2}:\d{2}/);
  });

  it('잘못된 값이면 대체 문구를 반환한다', () => {
    expect(formatDateTimeMinute('not-a-date')).toBe('-');
  });
});

describe('상태 라벨', () => {
  it('모집 상태를 한글 라벨로 매핑한다', () => {
    expect(RECRUITMENT_STATUS_LABEL.OPEN).toBe('모집 중');
    expect(RECRUITMENT_STATUS_LABEL.CLOSED).toBe('마감됨');
    expect(RECRUITMENT_STATUS_LABEL.CANCELLED).toBe('취소됨');
  });

  it('모집 상태 라벨에 영문 enum 원문을 노출하지 않는다', () => {
    Object.entries(RECRUITMENT_STATUS_LABEL).forEach(([status, label]) => {
      expect(label).not.toBe(status);
    });
  });

  it('신청 상태를 한글 라벨로 매핑한다', () => {
    expect(APPLICATION_STATUS_LABEL.PENDING).toBe('대기 중');
    expect(APPLICATION_STATUS_LABEL.CONFIRMED).toBe('확정');
    expect(APPLICATION_STATUS_LABEL.REFUNDED).toBe('환불됨');
  });
});

describe('isApplicationCancellable', () => {
  it('PENDING·CONFIRMED는 취소 가능하다', () => {
    expect(isApplicationCancellable('PENDING')).toBe(true);
    expect(isApplicationCancellable('CONFIRMED')).toBe(true);
  });

  it('CANCELLED·REFUNDED는 취소 대상이 아니다', () => {
    expect(isApplicationCancellable('CANCELLED')).toBe(false);
    expect(isApplicationCancellable('REFUNDED')).toBe(false);
  });
});
