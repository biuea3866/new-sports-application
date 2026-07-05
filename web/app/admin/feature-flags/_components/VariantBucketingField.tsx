"use client";

import { cn } from "@/lib/utils";
import type { FeatureFlagVariant } from "@/lib/admin/feature-flags/schemas";
import { canAddVariant, sumVariantWeights, TOTAL_VARIANT_WEIGHT } from "./strategyFormState";

interface VariantBucketingFieldProps {
  variants: FeatureFlagVariant[];
  onChange: (variants: FeatureFlagVariant[]) => void;
  disabled?: boolean;
}

/** VARIANT_BUCKETING 전략 입력 — variant(name+weight) 리스트, 추가/삭제(최대 4). */
export function VariantBucketingField({
  variants,
  onChange,
  disabled = false,
}: VariantBucketingFieldProps): JSX.Element {
  const weightSum = sumVariantWeights(variants);
  const isWeightSumValid = weightSum === TOTAL_VARIANT_WEIGHT;
  const canAdd = canAddVariant(variants);

  function updateVariantName(index: number, name: string): void {
    onChange(variants.map((variant, current) => (current === index ? { ...variant, name } : variant)));
  }

  function updateVariantWeight(index: number, weight: number): void {
    onChange(variants.map((variant, current) => (current === index ? { ...variant, weight } : variant)));
  }

  function removeVariant(index: number): void {
    onChange(variants.filter((_, current) => current !== index));
  }

  function addVariant(): void {
    onChange([...variants, { name: "", weight: 0 }]);
  }

  return (
    <div className="space-y-3">
      {variants.map((variant, index) => (
        <div key={index} className="flex items-center gap-2">
          <label htmlFor={`strategy-variant-name-${index}`} className="sr-only">
            variant {index + 1} 이름
          </label>
          <input
            id={`strategy-variant-name-${index}`}
            type="text"
            value={variant.name}
            disabled={disabled}
            aria-label={`variant ${index + 1} 이름`}
            onChange={(event) => updateVariantName(index, event.target.value)}
            className="w-24 rounded-md border border-input bg-background px-2 py-1 text-sm text-foreground disabled:cursor-not-allowed disabled:opacity-50"
          />
          <label htmlFor={`strategy-variant-weight-${index}`} className="sr-only">
            variant {index + 1} weight
          </label>
          <input
            id={`strategy-variant-weight-${index}`}
            type="number"
            value={variant.weight}
            disabled={disabled}
            aria-label={`variant ${index + 1} weight`}
            onChange={(event) => updateVariantWeight(index, Number(event.target.value))}
            className="w-20 rounded-md border border-input bg-background px-2 py-1 text-sm text-foreground disabled:cursor-not-allowed disabled:opacity-50"
          />
          <button
            type="button"
            disabled={disabled}
            aria-label="삭제"
            onClick={() => removeVariant(index)}
            className="rounded-md border border-input px-2 py-1 text-xs font-medium text-destructive disabled:cursor-not-allowed disabled:opacity-50"
          >
            삭제
          </button>
        </div>
      ))}

      <div className="flex items-center gap-3">
        <button
          type="button"
          disabled={disabled || !canAdd}
          aria-label="variant 추가"
          onClick={addVariant}
          className="rounded-md border border-input px-3 py-1.5 text-sm font-medium text-foreground disabled:cursor-not-allowed disabled:opacity-50"
        >
          + variant 추가
        </button>
        <span className="text-sm text-foreground">
          weight 합계: {weightSum} / {TOTAL_VARIANT_WEIGHT}
        </span>
      </div>

      {!isWeightSumValid && (
        <p role="alert" className={cn("text-xs font-medium text-warning")}>
          weight 합이 {TOTAL_VARIANT_WEIGHT}이 아닙니다 (합계: {weightSum})
        </p>
      )}
    </div>
  );
}
