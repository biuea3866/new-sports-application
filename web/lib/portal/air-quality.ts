/**
 * 대기질(에어코리아 PM10/PM2.5) 타입·등급 표시 매핑.
 * BE 계약: GET /air-quality → { pm10, pm25, pm10Grade, pm25Grade, representativeGrade, stationName, measuredAt }.
 * BE는 조회 실패 시에도 200 + representativeGrade="UNKNOWN" + pm10/pm25 null로 응답한다
 * (BE TDD 실패 경로) — FE는 이를 별도 에러 분기 없이 UNKNOWN 표시로 처리한다.
 */
import { z } from "zod";

export type AirQualityGrade = "GOOD" | "MODERATE" | "BAD" | "VERY_BAD" | "UNKNOWN";

export interface AirQualityResponse {
  pm10: number | null;
  pm25: number | null;
  pm10Grade: AirQualityGrade;
  pm25Grade: AirQualityGrade;
  representativeGrade: AirQualityGrade;
  stationName: string | null;
  measuredAt: string | null;
}

/**
 * BE 응답(외부 신뢰 경계) 검증용 zod 스키마.
 * `useAirQuality` 훅이 fetch 직후 `.parse`로 좁혀 `no-loose-assertion`을 준수한다.
 */
export const AirQualityGradeSchema = z.enum(["GOOD", "MODERATE", "BAD", "VERY_BAD", "UNKNOWN"]);

export const AirQualityResponseSchema = z.object({
  pm10: z.number().nullable(),
  pm25: z.number().nullable(),
  pm10Grade: AirQualityGradeSchema,
  pm25Grade: AirQualityGradeSchema,
  representativeGrade: AirQualityGradeSchema,
  stationName: z.string().nullable(),
  measuredAt: z.string().nullable(),
});

export interface AirQualityGradeDisplay {
  label: string;
  badgeClass: string;
}

const AIR_QUALITY_GRADE_DISPLAY_MAP: Record<AirQualityGrade, AirQualityGradeDisplay> = {
  GOOD: { label: "좋음", badgeClass: "bg-aq-good text-aq-good-foreground" },
  MODERATE: { label: "보통", badgeClass: "bg-aq-moderate text-aq-moderate-foreground" },
  BAD: { label: "나쁨", badgeClass: "bg-aq-bad text-aq-bad-foreground" },
  VERY_BAD: { label: "매우나쁨", badgeClass: "bg-aq-verybad text-aq-verybad-foreground" },
  UNKNOWN: { label: "정보없음", badgeClass: "bg-aq-unknown text-aq-unknown-foreground" },
};

function isAirQualityGrade(value: string): value is AirQualityGrade {
  return Object.prototype.hasOwnProperty.call(AIR_QUALITY_GRADE_DISPLAY_MAP, value);
}

/**
 * 등급 문자열을 라벨·배지 토큰 클래스로 매핑한다.
 * BE가 알 수 없는 값을 보내더라도(방어적 처리) UNKNOWN(정보없음)으로 폴백한다.
 */
export function getAirQualityGradeDisplay(grade: string): AirQualityGradeDisplay {
  if (isAirQualityGrade(grade)) {
    return AIR_QUALITY_GRADE_DISPLAY_MAP[grade];
  }
  return AIR_QUALITY_GRADE_DISPLAY_MAP.UNKNOWN;
}
