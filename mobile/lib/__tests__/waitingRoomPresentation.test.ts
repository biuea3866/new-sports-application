/**
 * waitingRoomPresentation — 대기실 진행바 ratio·라벨 순수 함수 검증.
 * 근거: 티켓 FE-07 "테스트 케이스" · design-fe-app.md "진행바 비율"·"Open Questions"(clamp).
 */
import {
  computeProgressRatio,
  formatEtaLabel,
  formatPercentLabel,
} from '../waitingRoomPresentation';

describe('computeProgressRatio', () => {
  it('현재 aheadCount가 기준값의 절반이면 ratio는 0.5다', () => {
    expect(computeProgressRatio(620, 1240)).toBeCloseTo(0.5, 5);
  });

  it('현재 aheadCount가 기준값과 같으면(막 진입) ratio는 0이다', () => {
    expect(computeProgressRatio(1239, 1239)).toBe(0);
  });

  it('재유입 등으로 aheadCount가 기준값을 넘어서면 음수 대신 0으로 clamp한다', () => {
    expect(computeProgressRatio(1500, 1000)).toBe(0);
  });

  it('aheadCount가 0이면(맨 앞) ratio는 1이다', () => {
    expect(computeProgressRatio(0, 1239)).toBe(1);
  });

  it('기준값(initialAheadCount)이 없으면 ratio를 표시하지 않는다', () => {
    expect(computeProgressRatio(500, null)).toBeNull();
  });

  it('기준값이 0 이하이면 ratio를 표시하지 않는다', () => {
    expect(computeProgressRatio(500, 0)).toBeNull();
  });

  it('aheadCount가 없으면 ratio를 표시하지 않는다', () => {
    expect(computeProgressRatio(null, 1239)).toBeNull();
  });
});

describe('formatPercentLabel', () => {
  it('ratio 0.62를 "62%"로 반올림한다', () => {
    expect(formatPercentLabel(0.62)).toBe('62%');
  });

  it('ratio 0을 "0%"로 표시한다', () => {
    expect(formatPercentLabel(0)).toBe('0%');
  });

  it('ratio가 null이면 라벨도 null이다', () => {
    expect(formatPercentLabel(null)).toBeNull();
  });
});

describe('formatEtaLabel', () => {
  it('240초를 "약 4분"으로 변환한다', () => {
    expect(formatEtaLabel(240)).toBe('약 4분');
  });

  it('125초는 올림해서 "약 3분"으로 변환한다', () => {
    expect(formatEtaLabel(125)).toBe('약 3분');
  });

  it('1분 미만도 최소 "약 1분"으로 표시한다(0분 노출 방지)', () => {
    expect(formatEtaLabel(10)).toBe('약 1분');
  });

  it('etaSeconds가 없으면 라벨도 없다', () => {
    expect(formatEtaLabel(null)).toBeNull();
  });
});
