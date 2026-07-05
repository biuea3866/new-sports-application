import * as React from "react";
import { getAirQualityGradeDisplay, type AirQualityGrade } from "@/lib/portal/air-quality";

export interface AirQualityBadgeProps {
  grade: AirQualityGrade;
}

/**
 * 대기질 등급 배지. FE-02 등급 매핑(`getAirQualityGradeDisplay`)의 토큰 클래스만 사용한다(색 하드코딩 0건).
 * UNKNOWN(정보없음)은 배지를 렌더하지 않는다 — 상위 컴포넌트가 폴백 문구를 대신 표시한다.
 */
export function AirQualityBadge({ grade }: AirQualityBadgeProps) {
  if (grade === "UNKNOWN") {
    return null;
  }

  const display = getAirQualityGradeDisplay(grade);

  return (
    <span
      role="status"
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${display.badgeClass}`}
    >
      {display.label}
    </span>
  );
}
