/**
 * 원화 금액 표기 유틸 테스트.
 *
 * 회귀 배경: 이벤트 상세(17-이벤트-좌석-선택)의 "구역별 좌석"이 좌석 수만 보여주고
 * 등급별 가격이 없었다. BE가 구역별 최저가/최고가(minPrice/maxPrice)를 내려주므로
 * 단일가면 한 값, 등급 내 가격이 갈리면 "최저가~" 범위로 표기한다.
 */
import { formatCurrency, formatPriceRange } from '../currency-format';

describe('formatCurrency', () => {
  it('문자열 금액을 천단위 콤마와 원 단위로 표기한다', () => {
    expect(formatCurrency('50000')).toBe('50,000원');
  });

  it('숫자로 해석할 수 없으면 0원으로 표기한다', () => {
    expect(formatCurrency('not-a-number')).toBe('0원');
  });
});

describe('formatPriceRange', () => {
  it('구역 내 좌석 가격이 모두 같으면 단일가를 표기한다', () => {
    expect(formatPriceRange('80000', '80000')).toBe('80,000원');
  });

  it('구역 내 좌석 가격이 다르면 최저가 기준 범위로 표기한다', () => {
    expect(formatPriceRange('30000', '50000')).toBe('30,000원~');
  });

  it('숫자·문자열 어느 쪽이 와도 동일하게 동작한다', () => {
    expect(formatPriceRange(30000, 50000)).toBe('30,000원~');
  });
});
