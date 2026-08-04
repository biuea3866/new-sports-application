/**
 * 감사 로그 변경 유형 라벨 계약 테스트 — BE `FeatureFlagChangeType` enum과 FE 표현이 어긋나지
 * 않는지 고정한다.
 *
 * 회귀 방지: 감사 로그 화면의 변경 열 배지가 영문 원문(CREATED/UPDATED 등)으로 노출된 결함
 * (05-피처플래그-감사로그 캡쳐).
 */
import { describe, it, expect } from "vitest";
import {
  FEATURE_FLAG_CHANGE_TYPE_VALUES,
  FEATURE_FLAG_CHANGE_TYPE_LABELS,
  featureFlagChangeTypeLabel,
} from "../featureFlagChangeType";

/** BE `domain/featureflag/entity/FeatureFlagChangeType.kt`의 전량. 이 목록이 계약의 정답이다. */
const BACKEND_CHANGE_TYPES = ["CREATED", "UPDATED", "ARCHIVED", "ACTIVATED"] as const;

describe("감사 로그 변경 유형 계약", () => {
  it("BE FeatureFlagChangeType enum 4종을 빠짐없이 안다", () => {
    expect([...FEATURE_FLAG_CHANGE_TYPE_VALUES].sort()).toEqual([...BACKEND_CHANGE_TYPES].sort());
  });
});

describe("감사 로그 변경 유형 한글 라벨", () => {
  it("모든 변경 유형에 한글 라벨이 있다", () => {
    for (const changeType of FEATURE_FLAG_CHANGE_TYPE_VALUES) {
      const label = FEATURE_FLAG_CHANGE_TYPE_LABELS[changeType];
      expect(label.length).toBeGreaterThan(0);
      expect(label).not.toMatch(/[A-Za-z]/);
    }
  });

  it("변경 유형별로 서로 다른 라벨을 쓴다", () => {
    const labels = FEATURE_FLAG_CHANGE_TYPE_VALUES.map(
      (changeType) => FEATURE_FLAG_CHANGE_TYPE_LABELS[changeType]
    );
    expect(new Set(labels).size).toBe(labels.length);
  });

  it("알려진 변경 유형 문자열을 한글 라벨로 바꾼다", () => {
    expect(featureFlagChangeTypeLabel("CREATED")).toBe(FEATURE_FLAG_CHANGE_TYPE_LABELS.CREATED);
    expect(featureFlagChangeTypeLabel("ACTIVATED")).toBe(FEATURE_FLAG_CHANGE_TYPE_LABELS.ACTIVATED);
  });

  it("모르는 변경 유형은 원문을 그대로 돌려주고 예외를 던지지 않는다", () => {
    expect(() => featureFlagChangeTypeLabel("DELETED")).not.toThrow();
    expect(featureFlagChangeTypeLabel("DELETED")).toBe("DELETED");
  });

  it("값이 없으면 자리표시자를 돌려준다", () => {
    expect(featureFlagChangeTypeLabel(null)).toBe("-");
    expect(featureFlagChangeTypeLabel(undefined)).toBe("-");
  });
});
