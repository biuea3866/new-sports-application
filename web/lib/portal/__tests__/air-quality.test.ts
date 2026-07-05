/**
 * 대기질 등급 → 라벨/배지 토큰 매핑 검증.
 * BE 계약(GET /air-quality)의 grade 필드는 문자열로 내려오므로, 알 수 없는 값에 대한
 * 방어적 폴백(UNKNOWN)까지 함께 검증한다.
 */
import { describe, it, expect } from "vitest";
import { getAirQualityGradeDisplay } from "../air-quality";

describe("getAirQualityGradeDisplay", () => {
  it.each([
    ["GOOD", "좋음"],
    ["MODERATE", "보통"],
    ["BAD", "나쁨"],
    ["VERY_BAD", "매우나쁨"],
    ["UNKNOWN", "정보없음"],
  ])("%s 등급은 '%s' 한글 라벨을 반환한다", (grade, expectedLabel) => {
    expect(getAirQualityGradeDisplay(grade).label).toBe(expectedLabel);
  });

  it.each([
    ["GOOD", "bg-aq-good text-aq-good-foreground"],
    ["MODERATE", "bg-aq-moderate text-aq-moderate-foreground"],
    ["BAD", "bg-aq-bad text-aq-bad-foreground"],
    ["VERY_BAD", "bg-aq-verybad text-aq-verybad-foreground"],
    ["UNKNOWN", "bg-aq-unknown text-aq-unknown-foreground"],
  ])("%s 등급은 대응하는 aq 토큰 배지 클래스를 반환한다", (grade, expectedBadgeClass) => {
    expect(getAirQualityGradeDisplay(grade).badgeClass).toBe(expectedBadgeClass);
  });

  it("알 수 없는 grade 문자열이 들어와도 UNKNOWN(정보없음)으로 폴백한다", () => {
    const result = getAirQualityGradeDisplay("INVALID_GRADE");

    expect(result.label).toBe("정보없음");
    expect(result.badgeClass).toBe("bg-aq-unknown text-aq-unknown-foreground");
  });

  it("빈 문자열이 들어와도 UNKNOWN(정보없음)으로 폴백한다", () => {
    const result = getAirQualityGradeDisplay("");

    expect(result.label).toBe("정보없음");
  });
});
