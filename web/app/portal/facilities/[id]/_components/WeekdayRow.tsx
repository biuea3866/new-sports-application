"use client";

/**
 * WeekdayRow — 요일 1개의 운영시간 행 (오픈~마감·슬롯 단위·정원·브레이크타임 목록).
 */
import * as React from "react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import type { TimeRange } from "@/lib/portal/types";
import type { OperatingHoursRowValue, WeekdayFieldErrors } from "./OperatingHoursForm";
import { BreakTimeInput } from "./BreakTimeInput";

export interface WeekdayRowProps {
  label: string;
  value: OperatingHoursRowValue;
  errors?: WeekdayFieldErrors;
  onChange: (patch: Partial<OperatingHoursRowValue>) => void;
}

export function WeekdayRow({ label, value, errors, onChange }: WeekdayRowProps) {
  function addBreak() {
    onChange({ breaks: [...value.breaks, { start: "12:00", end: "13:00" }] });
  }

  function updateBreak(index: number, patch: Partial<TimeRange>) {
    onChange({
      breaks: value.breaks.map((brk, i) => (i === index ? { ...brk, ...patch } : brk)),
    });
  }

  function removeBreak(index: number) {
    onChange({ breaks: value.breaks.filter((_, i) => i !== index) });
  }

  return (
    <fieldset className="rounded-md border border-input p-3 space-y-2" aria-label={`${label}요일 운영시간`}>
      <legend className="px-1 text-sm font-medium">{label}</legend>

      <div className="flex flex-wrap items-center gap-2">
        <label htmlFor={`open-${label}`} className="sr-only">
          {label}요일 오픈 시각
        </label>
        <Input
          id={`open-${label}`}
          type="time"
          value={value.openTime}
          onChange={(e) => onChange({ openTime: e.target.value })}
          aria-label={`${label}요일 오픈 시각`}
          className="w-28"
        />
        <span aria-hidden="true">~</span>
        <label htmlFor={`close-${label}`} className="sr-only">
          {label}요일 마감 시각
        </label>
        <Input
          id={`close-${label}`}
          type="time"
          value={value.closeTime}
          onChange={(e) => onChange({ closeTime: e.target.value })}
          aria-label={`${label}요일 마감 시각`}
          className="w-28"
        />
        <label htmlFor={`slot-${label}`} className="text-sm text-muted-foreground">
          슬롯
        </label>
        <Input
          id={`slot-${label}`}
          type="number"
          min={1}
          value={value.slotDurationMinutes}
          onChange={(e) => onChange({ slotDurationMinutes: e.target.value })}
          aria-label={`${label}요일 슬롯 단위(분)`}
          className="w-20"
        />
        <span className="text-sm text-muted-foreground">분</span>
        <label htmlFor={`capacity-${label}`} className="text-sm text-muted-foreground">
          정원
        </label>
        <Input
          id={`capacity-${label}`}
          type="number"
          min={1}
          value={value.capacity}
          onChange={(e) => onChange({ capacity: e.target.value })}
          aria-label={`${label}요일 정원`}
          className="w-20"
        />
      </div>

      {(errors?.openTime ?? errors?.closeTime) && (
        <p role="alert" className="text-xs text-destructive">
          {errors?.closeTime ?? errors?.openTime}
        </p>
      )}
      {errors?.slotDurationMinutes && (
        <p role="alert" className="text-xs text-destructive">
          {errors.slotDurationMinutes}
        </p>
      )}
      {errors?.capacity && (
        <p role="alert" className="text-xs text-destructive">
          {errors.capacity}
        </p>
      )}

      <div className="space-y-1">
        {value.breaks.map((brk, index) => (
          <BreakTimeInput
            key={index}
            dayLabel={label}
            index={index}
            value={brk}
            error={errors?.breaks?.[index]}
            onChange={(patch) => updateBreak(index, patch)}
            onRemove={() => removeBreak(index)}
          />
        ))}
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={addBreak}
          aria-label={`${label}요일 브레이크타임 추가`}
        >
          + 브레이크 추가
        </Button>
      </div>
    </fieldset>
  );
}
