/**
 * FeatureFlagStrategy를 사람이 읽는 요약 문자열로 변환하는 순수 유틸.
 * 컴포넌트(StrategySummary)는 이 함수만 호출하고 가공 로직을 갖지 않는다(no-logic-in-component).
 * 근거 티켓: FE-05.
 */
import type { FeatureFlagStrategy } from "@/lib/admin/feature-flags/schemas";

function assertNever(value: never): never {
  throw new Error(`처리되지 않은 strategy 타입입니다: ${JSON.stringify(value)}`);
}

export function getStrategyLabel(strategy: FeatureFlagStrategy): string {
  switch (strategy.strategyType) {
    case "GLOBAL_TOGGLE":
      return strategy.enabled ? "전역 ON" : "전역 OFF";
    case "PERCENTAGE_ROLLOUT":
      return `${strategy.percentage}% 롤아웃`;
    case "ATTRIBUTE_MATCH":
      return `${strategy.attribute}=${strategy.value}`;
    case "VARIANT_BUCKETING":
      return strategy.variants
        .map((variant) => `${variant.name}:${variant.weight}`)
        .join(", ");
    default:
      return assertNever(strategy);
  }
}
