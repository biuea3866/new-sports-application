import * as React from "react";
import { cn } from "@/lib/utils";
import { SIDO_OPTIONS, EMPTY_SIDO_OPTION } from "@/app/portal/facilities/sido-options";

export interface SidoSelectProps {
  value: string;
  onChange: (sidoCode: string) => void;
  label: string;
  id?: string;
  className?: string;
  disabled?: boolean;
}

/**
 * 시/도 선택 드롭다운.
 * 레포에 공용 Select 컴포넌트가 없어 네이티브 `<select>`를 래핑한다.
 * "선택 안 함" 선택 시 빈 문자열을 전달하며, 이는 서버가 주소로 시/도를 자동 판별함을 의미한다.
 */
export function SidoSelect({
  value,
  onChange,
  label,
  id = "sido-select",
  className,
  disabled,
}: SidoSelectProps) {
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <select
        id={id}
        aria-label={label}
        value={value}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
        className={cn(
          "flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50",
          className
        )}
      >
        <option value={EMPTY_SIDO_OPTION.code}>{EMPTY_SIDO_OPTION.name}</option>
        {SIDO_OPTIONS.map((option) => (
          <option key={option.code} value={option.code}>
            {option.name}
          </option>
        ))}
      </select>
    </div>
  );
}
