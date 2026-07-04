"use client";

import { cn } from "@/lib/utils";

interface GlobalToggleFieldProps {
  enabled: boolean;
  onChange: (enabled: boolean) => void;
  disabled?: boolean;
}

/** GLOBAL_TOGGLE 전략 입력 — 라벨 있는 on/off 스위치(네이티브 button). */
export function GlobalToggleField({ enabled, onChange, disabled = false }: GlobalToggleFieldProps): JSX.Element {
  return (
    <div>
      <span id="strategy-global-toggle-label" className="block text-sm font-medium text-foreground">
        활성화
      </span>
      <button
        type="button"
        role="switch"
        aria-checked={enabled}
        aria-labelledby="strategy-global-toggle-label"
        disabled={disabled}
        onClick={() => onChange(!enabled)}
        className={cn(
          "mt-1 rounded-md border border-input px-3 py-1.5 text-sm font-semibold",
          enabled ? "text-success" : "text-muted-foreground",
          "disabled:cursor-not-allowed disabled:opacity-50"
        )}
      >
        {enabled ? "ON" : "OFF"}
      </button>
    </div>
  );
}
