"use client";

/**
 * HolidayChip — 휴무일 칩(날짜 표시 + 삭제 버튼).
 */
import * as React from "react";
import { Button } from "@/components/ui/button";

export interface HolidayChipProps {
  date: string;
  onRemove: () => void;
}

export function formatHolidayLabel(date: string): string {
  const parsed = new Date(`${date}T00:00:00`);
  if (Number.isNaN(parsed.getTime())) return date;
  const weekday = ["일", "월", "화", "수", "목", "금", "토"][parsed.getDay()];
  return `${parsed.getMonth() + 1}/${parsed.getDate()}(${weekday})`;
}

export function HolidayChip({ date, onRemove }: HolidayChipProps) {
  const label = formatHolidayLabel(date);

  return (
    <span className="inline-flex items-center gap-1 rounded-full border bg-secondary px-3 py-1 text-sm text-secondary-foreground">
      {label}
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className="h-4 w-4 p-0"
        onClick={onRemove}
        aria-label={`${label} 휴무일 삭제`}
      >
        <span aria-hidden="true">✕</span>
      </Button>
    </span>
  );
}
