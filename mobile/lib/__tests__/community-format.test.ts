/**
 * community-format — 종목·공개여부 표시용 순수 매핑 검증.
 * 근거: FE-11 티켓 "커뮤니티 목록이 카드로 렌더되고 공개/비공개 표시가 정확하다".
 */
import {
  getSportCategoryDisplay,
  getVisibilityLabel,
  SPORT_CATEGORY_OPTIONS,
} from '../community-format';

describe('getSportCategoryDisplay', () => {
  it('SOCCER는 이모지·라벨 축구를 반환한다', () => {
    expect(getSportCategoryDisplay('SOCCER')).toEqual({ label: '축구', emoji: '⚽' });
  });

  it('ETC는 기타로 매핑된다', () => {
    expect(getSportCategoryDisplay('ETC')).toEqual({ label: '기타', emoji: '🏅' });
  });
});

describe('getVisibilityLabel', () => {
  it('PUBLIC은 공개로 표시된다', () => {
    expect(getVisibilityLabel('PUBLIC')).toBe('공개');
  });

  it('PRIVATE은 승인제임을 함께 표시한다', () => {
    expect(getVisibilityLabel('PRIVATE')).toBe('비공개 승인제');
  });
});

describe('SPORT_CATEGORY_OPTIONS', () => {
  it('BE SportCategory 12개 값과 1:1로 대응한다', () => {
    expect(SPORT_CATEGORY_OPTIONS).toHaveLength(12);
    expect(SPORT_CATEGORY_OPTIONS.find((option) => option.value === 'SOCCER')?.label).toBe(
      '⚽ 축구'
    );
  });
});
