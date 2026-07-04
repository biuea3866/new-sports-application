import { describe, it, expect } from "vitest";
import { getStrategyLabel } from "../strategyLabel";
import type { FeatureFlagStrategy } from "@/lib/admin/feature-flags/schemas";

describe("getStrategyLabel", () => {
  it("GLOBAL_TOGGLE이 enabled true면 전역 ON으로 표시한다", () => {
    const strategy: FeatureFlagStrategy = { strategyType: "GLOBAL_TOGGLE", enabled: true };

    expect(getStrategyLabel(strategy)).toBe("전역 ON");
  });

  it("GLOBAL_TOGGLE이 enabled false면 전역 OFF로 표시한다", () => {
    const strategy: FeatureFlagStrategy = { strategyType: "GLOBAL_TOGGLE", enabled: false };

    expect(getStrategyLabel(strategy)).toBe("전역 OFF");
  });

  it("PERCENTAGE_ROLLOUT 50을 50% 롤아웃으로 표시한다", () => {
    const strategy: FeatureFlagStrategy = {
      strategyType: "PERCENTAGE_ROLLOUT",
      percentage: 50,
    };

    expect(getStrategyLabel(strategy)).toBe("50% 롤아웃");
  });

  it("ATTRIBUTE_MATCH를 attribute=value 형태로 표시한다", () => {
    const strategy: FeatureFlagStrategy = {
      strategyType: "ATTRIBUTE_MATCH",
      attribute: "plan",
      value: "PREMIUM",
    };

    expect(getStrategyLabel(strategy)).toBe("plan=PREMIUM");
  });

  it("VARIANT_BUCKETING을 A:50, B:50 형태로 표시한다", () => {
    const strategy: FeatureFlagStrategy = {
      strategyType: "VARIANT_BUCKETING",
      variants: [
        { name: "A", weight: 50 },
        { name: "B", weight: 50 },
      ],
    };

    expect(getStrategyLabel(strategy)).toBe("A:50, B:50");
  });

  it("VARIANT_BUCKETING variant가 3개 이상이어도 순서대로 콤마로 구분한다", () => {
    const strategy: FeatureFlagStrategy = {
      strategyType: "VARIANT_BUCKETING",
      variants: [
        { name: "A", weight: 20 },
        { name: "B", weight: 30 },
        { name: "C", weight: 50 },
      ],
    };

    expect(getStrategyLabel(strategy)).toBe("A:20, B:30, C:50");
  });
});
