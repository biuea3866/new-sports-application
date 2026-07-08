"use client";

/**
 * OperatingHoursForm(W-OH) — 요일별 운영시간 등록 폼 (design-fe-web.md 텍스트 와이어프레임).
 * 프레젠테이션은 WeekdayRow(요일별 행) + BreakTimeInput(브레이크 목록)에 위임하고,
 * 이 컴포넌트는 서버 상태 로딩·zod 검증·저장 오케스트레이션만 담당한다.
 */
import * as React from "react";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { useOperatingHours, updateOperatingHours } from "@/lib/portal/useOperatingHours";
import { RegisterOperatingHoursInputSchema } from "@/lib/portal/schemas";
import type { DayOfWeek, OperatingHours, TimeRange } from "@/lib/portal/types";
import { WeekdayRow } from "./WeekdayRow";

const WEEKDAY_ORDER: DayOfWeek[] = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
];

export const WEEKDAY_LABELS: Record<DayOfWeek, string> = {
  MONDAY: "월",
  TUESDAY: "화",
  WEDNESDAY: "수",
  THURSDAY: "목",
  FRIDAY: "금",
  SATURDAY: "토",
  SUNDAY: "일",
};

export interface OperatingHoursRowValue {
  dayOfWeek: DayOfWeek;
  openTime: string;
  closeTime: string;
  slotDurationMinutes: string;
  capacity: string;
  breaks: TimeRange[];
}

export interface WeekdayFieldErrors {
  openTime?: string;
  closeTime?: string;
  slotDurationMinutes?: string;
  capacity?: string;
  breaks?: Record<number, { start?: string; end?: string }>;
}

function defaultRow(dayOfWeek: DayOfWeek): OperatingHoursRowValue {
  return {
    dayOfWeek,
    openTime: "09:00",
    closeTime: "18:00",
    slotDurationMinutes: "60",
    capacity: "1",
    breaks: [],
  };
}

function buildRows(data: OperatingHours[] | null): OperatingHoursRowValue[] {
  return WEEKDAY_ORDER.map((day) => {
    const existing = data?.find((entry) => entry.dayOfWeek === day);
    if (!existing) return defaultRow(day);
    return {
      dayOfWeek: day,
      openTime: existing.openTime.slice(0, 5),
      closeTime: existing.closeTime.slice(0, 5),
      slotDurationMinutes: String(existing.slotDurationMinutes),
      capacity: String(existing.capacity),
      breaks: existing.breaks,
    };
  });
}

function toApiShape(rows: OperatingHoursRowValue[]) {
  return rows.map((row) => ({
    dayOfWeek: row.dayOfWeek,
    openTime: row.openTime,
    closeTime: row.closeTime,
    slotDurationMinutes: Number(row.slotDurationMinutes) || 0,
    capacity: Number(row.capacity) || 0,
    breaks: row.breaks,
  }));
}

export interface OperatingHoursFormProps {
  facilityId: string;
}

export function OperatingHoursForm({ facilityId }: OperatingHoursFormProps) {
  const { data, status, error, refetch } = useOperatingHours(facilityId);
  const { addToast } = useToast();
  const [rows, setRows] = React.useState<OperatingHoursRowValue[]>(() => buildRows(null));
  const [fieldErrors, setFieldErrors] = React.useState<Record<number, WeekdayFieldErrors>>({});
  const [saving, setSaving] = React.useState(false);

  React.useEffect(() => {
    if (status === "success") {
      setRows(buildRows(data));
    }
  }, [status, data]);

  function updateRow(index: number, patch: Partial<OperatingHoursRowValue>) {
    setRows((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setFieldErrors({});

    const parsed = RegisterOperatingHoursInputSchema.safeParse({
      operatingHours: toApiShape(rows),
    });

    if (!parsed.success) {
      const nextErrors: Record<number, WeekdayFieldErrors> = {};
      for (const issue of parsed.error.issues) {
        const [, rowIndexRaw, field, breakIndexRaw, breakField] = issue.path;
        if (typeof rowIndexRaw !== "number") continue;
        const rowErrors = (nextErrors[rowIndexRaw] ??= {});

        if (field === "breaks" && typeof breakIndexRaw === "number") {
          const breaksErrors = (rowErrors.breaks ??= {});
          const breakEntry = (breaksErrors[breakIndexRaw] ??= {});
          if (breakField === "start" || breakField === "end") {
            breakEntry[breakField] = issue.message;
          }
          continue;
        }

        if (
          field === "openTime" ||
          field === "closeTime" ||
          field === "slotDurationMinutes" ||
          field === "capacity"
        ) {
          rowErrors[field] = issue.message;
        }
      }
      setFieldErrors(nextErrors);
      return;
    }

    setSaving(true);
    try {
      await updateOperatingHours(facilityId, parsed.data);
      addToast({ title: "운영시간이 저장됐습니다.", variant: "default" });
      refetch();
    } catch (err) {
      addToast({
        title: err instanceof Error ? err.message : "운영시간 저장에 실패했습니다.",
        variant: "destructive",
      });
    } finally {
      setSaving(false);
    }
  }

  if (status === "loading") {
    return (
      <p aria-busy="true" className="text-sm text-muted-foreground">
        운영시간을 불러오는 중...
      </p>
    );
  }

  if (status === "error") {
    return (
      <div role="alert" className="rounded-md border border-destructive p-3 text-sm text-destructive">
        {error ?? "운영시간을 불러오지 못했습니다."}
      </div>
    );
  }

  return (
    <form
      onSubmit={(e) => void handleSubmit(e)}
      noValidate
      aria-label="운영시간 등록 폼"
      className="space-y-4"
    >
      <div className="space-y-3">
        {rows.map((row, index) => (
          <WeekdayRow
            key={row.dayOfWeek}
            label={WEEKDAY_LABELS[row.dayOfWeek]}
            value={row}
            errors={fieldErrors[index]}
            onChange={(patch) => updateRow(index, patch)}
          />
        ))}
      </div>
      <div className="flex justify-end">
        <Button type="submit" disabled={saving} aria-label="운영시간 저장" aria-busy={saving}>
          {saving ? "저장 중..." : "저장"}
        </Button>
      </div>
    </form>
  );
}
