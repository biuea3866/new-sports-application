/**
 * 대기질(에어코리아 PM10/PM2.5) 등급 → 라벨/테마 토큰키 순수 매핑.
 *
 * BE 계약: GET /air-quality → { ..., representativeGrade: AirQualityGrade, ... }.
 * BE는 조회 실패 시에도 200 + representativeGrade="UNKNOWN"으로 응답한다
 * (BE TDD 실패 경로) — FE는 별도 에러 분기 없이 이 매핑으로 "정보없음" 표시를 얻는다.
 *
 * 색 값 자체는 포함하지 않는다 — 컴포넌트가 useTheme()로 tokenKey를 해석해 실제 색을 얻는다
 * (private-fe-convention no-hardcoded-color).
 */
import type { AirQualityGrade } from '../api/types';

export interface AirQualityGradeDisplay {
  label: string;
  tokenKey: string;
}

const AIR_QUALITY_GRADE_DISPLAY_MAP: Record<AirQualityGrade, AirQualityGradeDisplay> = {
  GOOD: { label: '좋음', tokenKey: 'airGood' },
  MODERATE: { label: '보통', tokenKey: 'airModerate' },
  BAD: { label: '나쁨', tokenKey: 'airBad' },
  VERY_BAD: { label: '매우나쁨', tokenKey: 'airVeryBad' },
  UNKNOWN: { label: '정보없음', tokenKey: 'airUnknown' },
};

function isAirQualityGrade(value: string): value is AirQualityGrade {
  return Object.prototype.hasOwnProperty.call(AIR_QUALITY_GRADE_DISPLAY_MAP, value);
}

/**
 * 등급 문자열을 라벨·테마 토큰키로 매핑한다.
 * BE가 알 수 없는 값을 보내더라도(방어적 처리) UNKNOWN(정보없음)으로 폴백한다.
 */
export function getAirQualityGradeDisplay(grade: string): AirQualityGradeDisplay {
  if (isAirQualityGrade(grade)) {
    return AIR_QUALITY_GRADE_DISPLAY_MAP[grade];
  }
  return AIR_QUALITY_GRADE_DISPLAY_MAP.UNKNOWN;
}

/**
 * 예약 화면의 대기질 경고 트리거 판정 — BAD·VERY_BAD일 때만 true.
 * 알 수 없는 값은 경고 대상이 아니므로 false로 방어한다.
 */
export function isBadOrWorse(grade: string): boolean {
  return grade === 'BAD' || grade === 'VERY_BAD';
}
