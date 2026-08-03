/**
 * 경기 상태·상품 카테고리 한글 라벨 계약.
 *
 * 회귀 방지: 스토어 탭(09-스토어-티켓)은 경기 상태를 `오픈` 배지로 한글화했는데, 같은 데이터를
 * 쓰는 홈 피드(03-홈-피드)만 `OPEN` 원문이었고 상품 카테고리도 `APPAREL`·`ACCESSORY`·
 * `EQUIPMENT` 로 노출됐다. 화면마다 라벨을 따로 두면 한쪽만 한글화된 채 남으므로 한 곳에 모은다.
 */
import {
  EVENT_STATUS_LABELS,
  PRODUCT_CATEGORY_LABELS,
  eventStatusLabel,
  productCategoryLabel,
} from '../catalog-labels';

describe('경기 상태 라벨', () => {
  it('모든 상태에 한글 라벨이 있다', () => {
    for (const label of Object.values(EVENT_STATUS_LABELS)) {
      expect(label.length).toBeGreaterThan(0);
      expect(label).not.toMatch(/[A-Za-z]/);
    }
  });

  it('OPEN 을 오픈으로 바꾼다', () => {
    expect(eventStatusLabel('OPEN')).toBe('오픈');
  });

  it('SCHEDULED·CLOSED 도 한글로 바꾼다', () => {
    expect(eventStatusLabel('SCHEDULED')).toBe('예정');
    expect(eventStatusLabel('CLOSED')).toBe('종료');
  });

  // 계약이 넓어져도 화면이 죽으면 안 된다 — 라벨은 표시 계층의 마지막 방어선이다.
  it('모르는 상태는 원문을 그대로 돌려주고 예외를 던지지 않는다', () => {
    expect(() => eventStatusLabel('CANCELLED')).not.toThrow();
    expect(eventStatusLabel('CANCELLED')).toBe('CANCELLED');
  });

  it('값이 없으면 빈 문자열을 돌려준다', () => {
    expect(eventStatusLabel('')).toBe('');
  });
});

describe('상품 카테고리 라벨', () => {
  it('BE ProductCategory 4종을 모두 안다', () => {
    expect(Object.keys(PRODUCT_CATEGORY_LABELS).sort()).toEqual([
      'ACCESSORY',
      'APPAREL',
      'EQUIPMENT',
      'FOOTWEAR',
    ]);
  });

  it('모든 카테고리에 한글 라벨이 있다', () => {
    for (const label of Object.values(PRODUCT_CATEGORY_LABELS)) {
      expect(label.length).toBeGreaterThan(0);
      expect(label).not.toMatch(/[A-Za-z]/);
    }
  });

  it('카테고리별로 서로 다른 라벨을 쓴다', () => {
    const labels = Object.values(PRODUCT_CATEGORY_LABELS);
    expect(new Set(labels).size).toBe(labels.length);
  });

  it('APPAREL 을 의류로 바꾼다', () => {
    expect(productCategoryLabel('APPAREL')).toBe('의류');
  });

  it('모르는 카테고리는 원문을 그대로 돌려준다', () => {
    expect(productCategoryLabel('SUPPLEMENT')).toBe('SUPPLEMENT');
  });

  it('값이 없으면 빈 문자열을 돌려준다', () => {
    expect(productCategoryLabel('')).toBe('');
  });
});
