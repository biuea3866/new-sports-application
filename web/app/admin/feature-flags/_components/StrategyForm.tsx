"use client";

import { useEffect } from "react";
import type {
  FeatureFlagStrategy,
  FeatureFlagType,
  StrategyType,
} from "@/lib/admin/feature-flags/schemas";
import { GlobalToggleField } from "./GlobalToggleField";
import { PercentageRolloutField } from "./PercentageRolloutField";
import { AttributeMatchField } from "./AttributeMatchField";
import { VariantBucketingField } from "./VariantBucketingField";
import { createDefaultStrategyFor, isStrategyValid, strategyTypeOptionsFor } from "./strategyFormState";

interface StrategyFormProps {
  value: FeatureFlagStrategy;
  onChange: (next: FeatureFlagStrategy) => void;
  flagType: FeatureFlagType;
  disabled?: boolean;
  onValidityChange?: (valid: boolean) => void;
}

const STRATEGY_TYPE_LABELS: Record<StrategyType, string> = {
  GLOBAL_TOGGLE: "전역 ON/OFF",
  PERCENTAGE_ROLLOUT: "퍼센티지 롤아웃",
  ATTRIBUTE_MATCH: "속성 매칭",
  VARIANT_BUCKETING: "variant 버케팅",
};

/**
 * S2/S3 공용 전략 입력 복합 컴포넌트(S4 퍼센티지 롤아웃 포함).
 * 전략 유형 select + 유형별 하위 필드 조건 렌더. strategy↔폼 상태 매핑은 strategyFormState.ts(순수 함수)로 분리.
 * 근거 티켓: FE-06-strategy-form.md.
 */
export function StrategyForm({
  value,
  onChange,
  flagType,
  disabled = false,
  onValidityChange,
}: StrategyFormProps): JSX.Element {
  const strategyTypeOptions = strategyTypeOptionsFor(flagType);
  const isValid = isStrategyValid(value);

  useEffect(() => {
    onValidityChange?.(isValid);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isValid]);

  function handleStrategyTypeChange(nextType: StrategyType): void {
    onChange(createDefaultStrategyFor(nextType));
  }

  return (
    <div className="space-y-4">
      <div>
        <label htmlFor="strategy-type-select" className="block text-sm font-medium text-foreground">
          전략 유형
        </label>
        <select
          id="strategy-type-select"
          value={value.strategyType}
          disabled={disabled}
          onChange={(event) => handleStrategyTypeChange(event.target.value as StrategyType)}
          className="mt-1 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground disabled:cursor-not-allowed disabled:opacity-50"
        >
          {strategyTypeOptions.map((strategyType) => (
            <option key={strategyType} value={strategyType}>
              {STRATEGY_TYPE_LABELS[strategyType]}
            </option>
          ))}
        </select>
      </div>

      {value.strategyType === "GLOBAL_TOGGLE" && (
        <GlobalToggleField
          enabled={value.enabled}
          disabled={disabled}
          onChange={(enabled) => onChange({ strategyType: "GLOBAL_TOGGLE", enabled })}
        />
      )}

      {value.strategyType === "PERCENTAGE_ROLLOUT" && (
        <PercentageRolloutField
          percentage={value.percentage}
          disabled={disabled}
          onChange={(percentage) => onChange({ strategyType: "PERCENTAGE_ROLLOUT", percentage })}
        />
      )}

      {value.strategyType === "ATTRIBUTE_MATCH" && (
        <AttributeMatchField
          attribute={value.attribute}
          value={value.value}
          disabled={disabled}
          onChange={(next) => onChange({ strategyType: "ATTRIBUTE_MATCH", ...next })}
        />
      )}

      {value.strategyType === "VARIANT_BUCKETING" && (
        <VariantBucketingField
          variants={value.variants}
          disabled={disabled}
          onChange={(variants) => onChange({ strategyType: "VARIANT_BUCKETING", variants })}
        />
      )}
    </div>
  );
}
