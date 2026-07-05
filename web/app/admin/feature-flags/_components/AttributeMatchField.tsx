"use client";

import { cn } from "@/lib/utils";

interface AttributeMatchFieldProps {
  attribute: string;
  value: string;
  onChange: (next: { attribute: string; value: string }) => void;
  disabled?: boolean;
}

const inputClassName = cn(
  "mt-1 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground",
  "disabled:cursor-not-allowed disabled:opacity-50"
);

/** ATTRIBUTE_MATCH 전략 입력 — attribute == value 매칭 조건. */
export function AttributeMatchField({
  attribute,
  value,
  onChange,
  disabled = false,
}: AttributeMatchFieldProps): JSX.Element {
  return (
    <div className="space-y-3">
      <div>
        <label htmlFor="strategy-attribute-name" className="block text-sm font-medium text-foreground">
          속성 이름
        </label>
        <input
          id="strategy-attribute-name"
          type="text"
          value={attribute}
          disabled={disabled}
          onChange={(event) => onChange({ attribute: event.target.value, value })}
          className={inputClassName}
        />
      </div>
      <div>
        <label htmlFor="strategy-attribute-value" className="block text-sm font-medium text-foreground">
          기대 값
        </label>
        <input
          id="strategy-attribute-value"
          type="text"
          value={value}
          disabled={disabled}
          onChange={(event) => onChange({ attribute, value: event.target.value })}
          className={inputClassName}
        />
      </div>
    </div>
  );
}
