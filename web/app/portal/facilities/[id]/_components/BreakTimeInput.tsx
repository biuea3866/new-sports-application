"use client";

/**
 * 브레이크타임 인풋 — WeekdayRow 내부에서 브레이크 1개(시작~종료)를 편집한다.
 */
import * as React from "react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import type { TimeRange } from "@/lib/portal/types";

export interface BreakTimeInputProps {
  dayLabel: string;
  index: number;
  value: TimeRange;
  error?: { start?: string; end?: string };
  onChange: (patch: Partial<TimeRange>) => void;
  onRemove: () => void;
}

export function BreakTimeInput({
  dayLabel,
  index,
  value,
  error,
  onChange,
  onRemove,
}: BreakTimeInputProps) {
  const startId = `break-start-${dayLabel}-${index}`;
  const endId = `break-end-${dayLabel}-${index}`;

  return (
    <div className="flex flex-wrap items-center gap-2">
      <label htmlFor={startId} className="sr-only">
        {dayLabel}요일 브레이크 {index + 1} 시작
      </label>
      <Input
        id={startId}
        type="time"
        value={value.start}
        onChange={(e) => onChange({ start: e.target.value })}
        aria-label={`${dayLabel}요일 브레이크 ${index + 1} 시작`}
        className="w-28"
      />
      <span aria-hidden="true">~</span>
      <label htmlFor={endId} className="sr-only">
        {dayLabel}요일 브레이크 {index + 1} 종료
      </label>
      <Input
        id={endId}
        type="time"
        value={value.end}
        onChange={(e) => onChange({ end: e.target.value })}
        aria-label={`${dayLabel}요일 브레이크 ${index + 1} 종료`}
        className="w-28"
      />
      <Button
        type="button"
        variant="ghost"
        size="sm"
        onClick={onRemove}
        aria-label={`${dayLabel}요일 브레이크 ${index + 1} 삭제`}
      >
        삭제
      </Button>
      {(error?.start ?? error?.end) && (
        <p role="alert" className="text-xs text-destructive">
          {error?.end ?? error?.start}
        </p>
      )}
    </div>
  );
}
