"use client";

import { cn } from "@/lib/utils";

interface PercentageRolloutFieldProps {
  percentage: number;
  onChange: (percentage: number) => void;
  disabled?: boolean;
}

const MIN_PERCENTAGE = 0;
const MAX_PERCENTAGE = 100;

function clampPercentage(value: number): number {
  if (Number.isNaN(value)) return MIN_PERCENTAGE;
  return Math.min(MAX_PERCENTAGE, Math.max(MIN_PERCENTAGE, value));
}

/** PERCENTAGE_ROLLOUT 전략 입력(S4) — range 슬라이더 + 숫자 입력 동기화. */
export function PercentageRolloutField({
  percentage,
  onChange,
  disabled = false,
}: PercentageRolloutFieldProps): JSX.Element {
  return (
    <div>
      <label htmlFor="strategy-percentage-range" className="block text-sm font-medium text-foreground">
        노출 비율
      </label>
      <div className="mt-1 flex items-center gap-3">
        <input
          id="strategy-percentage-range"
          type="range"
          min={MIN_PERCENTAGE}
          max={MAX_PERCENTAGE}
          value={percentage}
          disabled={disabled}
          aria-label="노출 비율 슬라이더"
          onChange={(event) => onChange(clampPercentage(Number(event.target.value)))}
          className={cn("h-2 w-full cursor-pointer accent-primary", "disabled:cursor-not-allowed")}
        />
        <input
          type="number"
          min={MIN_PERCENTAGE}
          max={MAX_PERCENTAGE}
          value={percentage}
          disabled={disabled}
          aria-label="노출 비율 숫자 입력"
          onChange={(event) => onChange(clampPercentage(Number(event.target.value)))}
          className={cn(
            "w-20 rounded-md border border-input bg-background px-2 py-1 text-sm text-foreground",
            "disabled:cursor-not-allowed disabled:opacity-50"
          )}
        />
        <span className="text-sm text-foreground">%</span>
      </div>
      <p className="mt-1 text-xs text-muted-foreground">
        userId 해시 기반 — 동일 사용자는 일관되게 노출됩니다.
      </p>
    </div>
  );
}
