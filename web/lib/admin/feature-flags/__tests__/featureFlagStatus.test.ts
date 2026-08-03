/**
 * 피처 플래그 상태 라벨 계약 테스트 — BE `FeatureFlagStatus` enum과 FE 표현이 어긋나지 않는지 고정한다.
 *
 * 회귀 방지: 목록·상세 화면이 상태 배지를 영문 원문(ACTIVE 등)으로 노출한 결함
 * (02-피처플래그-목록/04-상세 캡쳐).
 */
import { describe, it, expect } from "vitest";
import {
  FEATURE_FLAG_STATUS_VALUES,
  FEATURE_FLAG_STATUS_LABELS,
  featureFlagStatusLabel,
} from "../featureFlagStatus";

/** BE `domain/featureflag/entity/FeatureFlagStatus.kt`의 전량. 이 목록이 계약의 정답이다. */
const BACKEND_FEATURE_FLAG_STATUSES = ["ACTIVE", "ARCHIVED"] as const;

describe("피처 플래그 상태 계약", () => {
  it("BE FeatureFlagStatus enum 2종을 빠짐없이 안다", () => {
    expect([...FEATURE_FLAG_STATUS_VALUES].sort()).toEqual(
      [...BACKEND_FEATURE_FLAG_STATUSES].sort()
    );
  });
});

describe("피처 플래그 상태 한글 라벨", () => {
  it("모든 상태에 한글 라벨이 있다", () => {
    for (const status of FEATURE_FLAG_STATUS_VALUES) {
      const label = FEATURE_FLAG_STATUS_LABELS[status];
      expect(label.length).toBeGreaterThan(0);
      expect(label).not.toMatch(/[A-Za-z]/);
    }
  });

  it("상태별로 서로 다른 라벨을 쓴다", () => {
    const labels = FEATURE_FLAG_STATUS_VALUES.map((status) => FEATURE_FLAG_STATUS_LABELS[status]);
    expect(new Set(labels).size).toBe(labels.length);
  });

  it("알려진 상태 문자열을 한글 라벨로 바꾼다", () => {
    expect(featureFlagStatusLabel("ACTIVE")).toBe(FEATURE_FLAG_STATUS_LABELS.ACTIVE);
    expect(featureFlagStatusLabel("ARCHIVED")).toBe(FEATURE_FLAG_STATUS_LABELS.ARCHIVED);
  });

  it("모르는 상태는 원문을 그대로 돌려주고 예외를 던지지 않는다", () => {
    expect(() => featureFlagStatusLabel("DELETED")).not.toThrow();
    expect(featureFlagStatusLabel("DELETED")).toBe("DELETED");
  });

  it("값이 없으면 자리표시자를 돌려준다", () => {
    expect(featureFlagStatusLabel(null)).toBe("-");
    expect(featureFlagStatusLabel(undefined)).toBe("-");
  });
});
