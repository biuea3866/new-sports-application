/**
 * StrategyForm 순수 매핑 유틸 — strategy 타입 게이팅·기본값 생성·유효성 검증.
 * 컴포넌트 내부 로직(no-logic-in-component)을 여기로 분리한다.
 * 근거 티켓: FE-06-strategy-form.md / 설계: design-fe-web.md "전략 입력 섹션".
 */
import type {
  FeatureFlagStrategy,
  FeatureFlagType,
  FeatureFlagVariant,
  StrategyType,
} from "@/lib/admin/feature-flags/schemas";

export const MAX_VARIANT_COUNT = 4;
export const TOTAL_VARIANT_WEIGHT = 100;

const STRATEGY_TYPE_OPTIONS_FOR_EXPERIMENT: StrategyType[] = ["VARIANT_BUCKETING"];
const STRATEGY_TYPE_OPTIONS_FOR_OTHERS: StrategyType[] = [
  "GLOBAL_TOGGLE",
  "PERCENTAGE_ROLLOUT",
  "ATTRIBUTE_MATCH",
];

/** flagType에 따른 전략 유형 선택지 게이팅 — EXPERIMENT는 VARIANT_BUCKETING만 노출한다. */
export function strategyTypeOptionsFor(flagType: FeatureFlagType): StrategyType[] {
  return flagType === "EXPERIMENT" ? STRATEGY_TYPE_OPTIONS_FOR_EXPERIMENT : STRATEGY_TYPE_OPTIONS_FOR_OTHERS;
}

/** 전략 유형 전환 시 초기값을 생성한다. */
export function createDefaultStrategyFor(strategyType: StrategyType): FeatureFlagStrategy {
  switch (strategyType) {
    case "GLOBAL_TOGGLE":
      return { strategyType: "GLOBAL_TOGGLE", enabled: false };
    case "PERCENTAGE_ROLLOUT":
      return { strategyType: "PERCENTAGE_ROLLOUT", percentage: 0 };
    case "ATTRIBUTE_MATCH":
      return { strategyType: "ATTRIBUTE_MATCH", attribute: "", value: "" };
    case "VARIANT_BUCKETING":
      return {
        strategyType: "VARIANT_BUCKETING",
        variants: [
          { name: "A", weight: 50 },
          { name: "B", weight: 50 },
        ],
      };
  }
}

export function sumVariantWeights(variants: FeatureFlagVariant[]): number {
  return variants.reduce((sum, variant) => sum + variant.weight, 0);
}

export function canAddVariant(variants: FeatureFlagVariant[]): boolean {
  return variants.length < MAX_VARIANT_COUNT;
}

/** 전략 값의 폼 단계 유효성 — BE가 최종 검증하되 FE는 라운드트립 절감을 위해 미리 좁힌다. */
export function isStrategyValid(strategy: FeatureFlagStrategy): boolean {
  switch (strategy.strategyType) {
    case "GLOBAL_TOGGLE":
      return true;
    case "PERCENTAGE_ROLLOUT":
      return (
        Number.isInteger(strategy.percentage) && strategy.percentage >= 0 && strategy.percentage <= 100
      );
    case "ATTRIBUTE_MATCH":
      return strategy.attribute.trim().length > 0 && strategy.value.trim().length > 0;
    case "VARIANT_BUCKETING":
      return (
        strategy.variants.length > 0 &&
        strategy.variants.length <= MAX_VARIANT_COUNT &&
        strategy.variants.every((variant) => variant.name.trim().length > 0) &&
        sumVariantWeights(strategy.variants) === TOTAL_VARIANT_WEIGHT
      );
  }
}
