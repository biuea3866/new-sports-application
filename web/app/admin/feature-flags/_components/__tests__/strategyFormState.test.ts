import { describe, it, expect } from "vitest";
import {
  strategyTypeOptionsFor,
  createDefaultStrategyFor,
  sumVariantWeights,
  canAddVariant,
  isStrategyValid,
  MAX_VARIANT_COUNT,
  TOTAL_VARIANT_WEIGHT,
} from "../strategyFormState";
import type { FeatureFlagStrategy } from "@/lib/admin/feature-flags/schemas";

describe("strategyTypeOptionsFor", () => {
  it("flagType이 EXPERIMENT면 VARIANT_BUCKETING만 반환한다", () => {
    expect(strategyTypeOptionsFor("EXPERIMENT")).toEqual(["VARIANT_BUCKETING"]);
  });

  it("flagType이 RELEASE면 VARIANT_BUCKETING을 제외한 나머지 3종을 반환한다", () => {
    const options = strategyTypeOptionsFor("RELEASE");
    expect(options).toEqual(["GLOBAL_TOGGLE", "PERCENTAGE_ROLLOUT", "ATTRIBUTE_MATCH"]);
  });

  it("flagType이 OPERATIONAL/ENTITLEMENT여도 나머지 3종을 반환한다", () => {
    expect(strategyTypeOptionsFor("OPERATIONAL")).toEqual([
      "GLOBAL_TOGGLE",
      "PERCENTAGE_ROLLOUT",
      "ATTRIBUTE_MATCH",
    ]);
    expect(strategyTypeOptionsFor("ENTITLEMENT")).toEqual([
      "GLOBAL_TOGGLE",
      "PERCENTAGE_ROLLOUT",
      "ATTRIBUTE_MATCH",
    ]);
  });
});

describe("createDefaultStrategyFor", () => {
  it("GLOBAL_TOGGLE 기본값은 enabled false다", () => {
    expect(createDefaultStrategyFor("GLOBAL_TOGGLE")).toEqual({
      strategyType: "GLOBAL_TOGGLE",
      enabled: false,
    });
  });

  it("PERCENTAGE_ROLLOUT 기본값은 percentage 0이다", () => {
    expect(createDefaultStrategyFor("PERCENTAGE_ROLLOUT")).toEqual({
      strategyType: "PERCENTAGE_ROLLOUT",
      percentage: 0,
    });
  });

  it("ATTRIBUTE_MATCH 기본값은 attribute/value 빈 문자열이다", () => {
    expect(createDefaultStrategyFor("ATTRIBUTE_MATCH")).toEqual({
      strategyType: "ATTRIBUTE_MATCH",
      attribute: "",
      value: "",
    });
  });

  it("VARIANT_BUCKETING 기본값은 weight 합 100인 variant 2개다", () => {
    const strategy = createDefaultStrategyFor("VARIANT_BUCKETING");
    if (strategy.strategyType !== "VARIANT_BUCKETING") throw new Error("unexpected strategy type");
    expect(strategy.variants.length).toBe(2);
    expect(sumVariantWeights(strategy.variants)).toBe(TOTAL_VARIANT_WEIGHT);
  });
});

describe("sumVariantWeights", () => {
  it("variant weight 합을 계산한다", () => {
    expect(
      sumVariantWeights([
        { name: "A", weight: 30 },
        { name: "B", weight: 70 },
      ])
    ).toBe(100);
  });

  it("빈 배열은 0을 반환한다", () => {
    expect(sumVariantWeights([])).toBe(0);
  });
});

describe("canAddVariant", () => {
  it(`variant가 ${MAX_VARIANT_COUNT}개 미만이면 true를 반환한다`, () => {
    expect(canAddVariant([{ name: "A", weight: 100 }])).toBe(true);
  });

  it(`variant가 ${MAX_VARIANT_COUNT}개면 false를 반환한다`, () => {
    const variants = Array.from({ length: MAX_VARIANT_COUNT }, (_, index) => ({
      name: `V${index}`,
      weight: 100 / MAX_VARIANT_COUNT,
    }));
    expect(canAddVariant(variants)).toBe(false);
  });
});

describe("isStrategyValid", () => {
  it("GLOBAL_TOGGLE은 항상 유효하다", () => {
    const strategy: FeatureFlagStrategy = { strategyType: "GLOBAL_TOGGLE", enabled: true };
    expect(isStrategyValid(strategy)).toBe(true);
  });

  it("PERCENTAGE_ROLLOUT은 0~100 정수 범위면 유효하다", () => {
    expect(isStrategyValid({ strategyType: "PERCENTAGE_ROLLOUT", percentage: 60 })).toBe(true);
  });

  it("PERCENTAGE_ROLLOUT이 범위를 벗어나면 무효하다", () => {
    expect(isStrategyValid({ strategyType: "PERCENTAGE_ROLLOUT", percentage: 101 })).toBe(false);
    expect(isStrategyValid({ strategyType: "PERCENTAGE_ROLLOUT", percentage: -1 })).toBe(false);
  });

  it("ATTRIBUTE_MATCH는 attribute/value가 모두 비어있지 않아야 유효하다", () => {
    expect(
      isStrategyValid({ strategyType: "ATTRIBUTE_MATCH", attribute: "plan", value: "PREMIUM" })
    ).toBe(true);
    expect(isStrategyValid({ strategyType: "ATTRIBUTE_MATCH", attribute: "", value: "PREMIUM" })).toBe(
      false
    );
  });

  it("VARIANT_BUCKETING은 weight 합이 100이어야 유효하다", () => {
    expect(
      isStrategyValid({
        strategyType: "VARIANT_BUCKETING",
        variants: [
          { name: "A", weight: 50 },
          { name: "B", weight: 50 },
        ],
      })
    ).toBe(true);
    expect(
      isStrategyValid({
        strategyType: "VARIANT_BUCKETING",
        variants: [
          { name: "A", weight: 50 },
          { name: "B", weight: 40 },
        ],
      })
    ).toBe(false);
  });

  it(`VARIANT_BUCKETING은 variant가 ${MAX_VARIANT_COUNT}개를 초과하면 무효하다`, () => {
    const variants = Array.from({ length: MAX_VARIANT_COUNT + 1 }, (_, index) => ({
      name: `V${index}`,
      weight: 20,
    }));
    expect(isStrategyValid({ strategyType: "VARIANT_BUCKETING", variants })).toBe(false);
  });
});
