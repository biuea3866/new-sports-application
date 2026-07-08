/**
 * formatProgramPrice는 가격을 천단위 콤마 + "원"으로 표시한다
 * formatProgramDuration은 소요시간을 "N분"으로 표시한다
 * formatProgramCapacity는 정원을 "정원 N명"으로 표시한다
 */
import {
  formatProgramCapacity,
  formatProgramDuration,
  formatProgramPrice,
} from '../program-format';

describe('formatProgramPrice', () => {
  it('가격을 천단위 콤마 + "원"으로 표시한다', () => {
    expect(formatProgramPrice(50000)).toBe('50,000원');
  });

  it('0원도 "0원"으로 표시한다(시설상품은 무료 표기를 쓰지 않는다)', () => {
    expect(formatProgramPrice(0)).toBe('0원');
  });
});

describe('formatProgramDuration', () => {
  it('소요시간을 "N분"으로 표시한다', () => {
    expect(formatProgramDuration(60)).toBe('60분');
  });
});

describe('formatProgramCapacity', () => {
  it('정원을 "정원 N명"으로 표시한다', () => {
    expect(formatProgramCapacity(1)).toBe('정원 1명');
  });
});
