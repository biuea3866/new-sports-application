/**
 * 대기질 등급 → 라벨/테마 토큰키 매핑, BAD 이상 판정 순수 함수 검증.
 * BE는 조회 실패 시에도 200 + representativeGrade="UNKNOWN"으로 응답하므로,
 * FE는 별도 에러 분기 없이 이 매핑으로 "정보없음" 표시를 얻는다.
 */
import { getAirQualityGradeDisplay, isBadOrWorse } from '../air-quality-format';

describe('getAirQualityGradeDisplay', () => {
  it.each([
    ['GOOD', '좋음', 'airGood'],
    ['MODERATE', '보통', 'airModerate'],
    ['BAD', '나쁨', 'airBad'],
    ['VERY_BAD', '매우나쁨', 'airVeryBad'],
    ['UNKNOWN', '정보없음', 'airUnknown'],
  ])('%s 등급은 라벨 "%s"과 토큰키 "%s"를 반환한다', (grade, expectedLabel, expectedTokenKey) => {
    const display = getAirQualityGradeDisplay(grade);

    expect(display.label).toBe(expectedLabel);
    expect(display.tokenKey).toBe(expectedTokenKey);
  });

  it('알 수 없는 grade 문자열이 들어오면 UNKNOWN(정보없음)으로 폴백한다', () => {
    const display = getAirQualityGradeDisplay('INVALID_GRADE');

    expect(display.label).toBe('정보없음');
    expect(display.tokenKey).toBe('airUnknown');
  });

  it('빈 문자열이 들어와도 UNKNOWN(정보없음)으로 폴백한다', () => {
    const display = getAirQualityGradeDisplay('');

    expect(display.label).toBe('정보없음');
    expect(display.tokenKey).toBe('airUnknown');
  });
});

describe('isBadOrWorse', () => {
  it.each(['BAD', 'VERY_BAD'])('%s 등급은 true를 반환한다', (grade) => {
    expect(isBadOrWorse(grade)).toBe(true);
  });

  it.each(['GOOD', 'MODERATE', 'UNKNOWN'])('%s 등급은 false를 반환한다', (grade) => {
    expect(isBadOrWorse(grade)).toBe(false);
  });

  it('알 수 없는 grade 문자열은 false를 반환한다', () => {
    expect(isBadOrWorse('INVALID_GRADE')).toBe(false);
  });
});
