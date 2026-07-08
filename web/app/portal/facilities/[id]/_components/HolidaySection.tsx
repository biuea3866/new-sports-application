"use client";

/**
 * HolidaySection(W-HD) — 휴무일 목록 + 추가/삭제.
 */
import * as React from "react";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { useHolidays, addHoliday, removeHoliday } from "@/lib/portal/useHolidays";
import { HolidayChip } from "./HolidayChip";
import { DatePickerDialog } from "./DatePickerDialog";

export interface HolidaySectionProps {
  facilityId: string;
}

export function HolidaySection({ facilityId }: HolidaySectionProps) {
  const { data, status, error, refetch } = useHolidays(facilityId);
  const { addToast } = useToast();
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [dialogError, setDialogError] = React.useState<string | null>(null);
  const [submitting, setSubmitting] = React.useState(false);

  async function handleAdd(date: string) {
    if (!date) {
      setDialogError("날짜를 선택해 주세요.");
      return;
    }
    setSubmitting(true);
    setDialogError(null);
    try {
      await addHoliday(facilityId, date);
      addToast({ title: "휴무일이 추가됐습니다.", variant: "default" });
      setDialogOpen(false);
      refetch();
    } catch (err) {
      setDialogError(err instanceof Error ? err.message : "휴무일 추가에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleRemove(date: string) {
    try {
      await removeHoliday(facilityId, date);
      addToast({ title: "휴무일이 삭제됐습니다.", variant: "default" });
      refetch();
    } catch (err) {
      addToast({
        title: err instanceof Error ? err.message : "휴무일 삭제에 실패했습니다.",
        variant: "destructive",
      });
    }
  }

  if (status === "loading") {
    return (
      <p aria-busy="true" className="text-sm text-muted-foreground">
        휴무일을 불러오는 중...
      </p>
    );
  }

  if (status === "error") {
    return (
      <div role="alert" className="rounded-md border border-destructive p-3 text-sm text-destructive">
        {error ?? "휴무일을 불러오지 못했습니다."}
      </div>
    );
  }

  const holidays = data ?? [];

  return (
    <section aria-label="휴무일 관리" className="space-y-3">
      <div className="flex flex-wrap gap-2" aria-label="휴무일 목록">
        {holidays.length === 0 ? (
          <p className="text-sm text-muted-foreground">휴무일 없음</p>
        ) : (
          holidays.map((date) => (
            <HolidayChip key={date} date={date} onRemove={() => void handleRemove(date)} />
          ))
        )}
      </div>
      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={() => setDialogOpen(true)}
        aria-label="휴무일 추가"
      >
        + 날짜 추가
      </Button>

      <DatePickerDialog
        open={dialogOpen}
        onOpenChange={(open) => {
          setDialogOpen(open);
          if (!open) setDialogError(null);
        }}
        onConfirm={(date) => void handleAdd(date)}
        submitting={submitting}
        error={dialogError}
      />
    </section>
  );
}
