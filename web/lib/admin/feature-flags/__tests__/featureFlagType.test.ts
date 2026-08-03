/**
 * 피처 플래그 종류 라벨 계약 테스트 — BE `FeatureFlagType` enum과 FE 표현이 어긋나지 않는지 고정한다.
 *
 * 회귀 방지: 목록·생성·상세 화면이 종류 배지·select를 영문 원문(RELEASE 등)으로 노출한 결함
 * (02-피처플래그-목록/03-생성 캡쳐). BE가 계약의 정답이므로 4종 전부가 한글 라벨을 가져야 하고,
 * 계약 밖 값이 섞여도 화면이 죽지 않아야 한다.
 */
import { describe, it, expect } from "vitest";
import {
  FEATURE_FLAG_TYPE_VALUES,
  FEATURE_FLAG_TYPE_LABELS,
  featureFlagTypeLabel,
} from "../featureFlagType";

/** BE `domain/featureflag/entity/FeatureFlagType.kt`의 전량. 이 목록이 계약의 정답이다. */
const BACKEND_FEATURE_FLAG_TYPES = ["RELEASE", "OPERATIONAL", "EXPERIMENT", "ENTITLEMENT"] as const;

describe("피처 플래그 종류 계약", () => {
  it("BE FeatureFlagType enum 4종을 빠짐없이 안다", () => {
    expect([...FEATURE_FLAG_TYPE_VALUES].sort()).toEqual([...BACKEND_FEATURE_FLAG_TYPES].sort());
  });
});

describe("피처 플래그 종류 한글 라벨", () => {
  it("모든 종류에 한글 라벨이 있다", () => {
    for (const type of FEATURE_FLAG_TYPE_VALUES) {
      const label = FEATURE_FLAG_TYPE_LABELS[type];
      expect(label.length).toBeGreaterThan(0);
      // 영문 원문(RELEASE 등)이 그대로 노출되면 안 된다.
      expect(label).not.toMatch(/[A-Za-z]/);
    }
  });

  it("종류별로 서로 다른 라벨을 쓴다", () => {
    const labels = FEATURE_FLAG_TYPE_VALUES.map((type) => FEATURE_FLAG_TYPE_LABELS[type]);
    expect(new Set(labels).size).toBe(labels.length);
  });

  it("알려진 종류 문자열을 한글 라벨로 바꾼다", () => {
    expect(featureFlagTypeLabel("RELEASE")).toBe(FEATURE_FLAG_TYPE_LABELS.RELEASE);
    expect(featureFlagTypeLabel("EXPERIMENT")).toBe(FEATURE_FLAG_TYPE_LABELS.EXPERIMENT);
  });

  // 계약이 다시 어긋나도 화면이 죽지 않아야 한다 — 라벨은 표시 계층의 마지막 방어선이다.
  it("모르는 종류는 원문을 그대로 돌려주고 예외를 던지지 않는다", () => {
    expect(() => featureFlagTypeLabel("SOMETHING_NEW")).not.toThrow();
    expect(featureFlagTypeLabel("SOMETHING_NEW")).toBe("SOMETHING_NEW");
  });

  it("값이 없으면 자리표시자를 돌려준다", () => {
    expect(featureFlagTypeLabel(null)).toBe("-");
    expect(featureFlagTypeLabel(undefined)).toBe("-");
  });
});
