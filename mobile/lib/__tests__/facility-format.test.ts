/**
 * facility-format — 시설 선택기(picker)가 쓰는 표시·검색 순수 로직 검증.
 */
import { filterFacilitiesByKeyword, formatFacilityMetaLine } from '../facility-format';

const GANGNAM = { name: '강남 스포츠센터', gu: '강남구', type: '풋살장' };
const SONGPA = { name: '송파 배드민턴장', gu: '송파구', type: '배드민턴장' };

describe('formatFacilityMetaLine', () => {
  it('지역과 종류를 한 줄로 이어 표기한다', () => {
    expect(formatFacilityMetaLine(GANGNAM)).toBe('강남구 풋살장');
  });

  it('비어 있는 항목은 생략한다', () => {
    expect(formatFacilityMetaLine({ name: '이름', gu: '강남구', type: '' })).toBe('강남구');
  });
});

describe('filterFacilitiesByKeyword', () => {
  const facilities = [GANGNAM, SONGPA];

  it('검색어가 비면 전체를 그대로 반환한다', () => {
    expect(filterFacilitiesByKeyword(facilities, '')).toEqual(facilities);
    expect(filterFacilitiesByKeyword(facilities, '   ')).toEqual(facilities);
  });

  it('이름으로 검색한다', () => {
    expect(filterFacilitiesByKeyword(facilities, '배드민턴')).toEqual([SONGPA]);
  });

  it('지역으로 검색한다', () => {
    expect(filterFacilitiesByKeyword(facilities, '강남')).toEqual([GANGNAM]);
  });

  it('종류로 검색한다', () => {
    expect(filterFacilitiesByKeyword(facilities, '풋살장')).toEqual([GANGNAM]);
  });

  it('일치하는 시설이 없으면 빈 배열을 반환한다', () => {
    expect(filterFacilitiesByKeyword(facilities, '수영장')).toEqual([]);
  });

  it('대소문자와 앞뒤 공백을 무시한다', () => {
    const englishNamed = [{ name: 'Seoul Arena', gu: '중구', type: '체육관' }];
    expect(filterFacilitiesByKeyword(englishNamed, '  seoul ')).toEqual(englishNamed);
  });
});
