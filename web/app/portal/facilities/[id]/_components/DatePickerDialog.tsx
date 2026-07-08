"use client";

/**
 * DatePickerDialog — 휴무일 추가용 날짜 선택 다이얼로그.
 */
import * as React from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";

export interface DatePickerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: (date: string) => void;
  submitting: boolean;
  error: string | null;
}

export function DatePickerDialog({
  open,
  onOpenChange,
  onConfirm,
  submitting,
  error,
}: DatePickerDialogProps) {
  const [dateInput, setDateInput] = React.useState("");

  React.useEffect(() => {
    if (!open) setDateInput("");
  }, [open]);

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    onConfirm(dateInput);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent aria-labelledby="holiday-dialog-title">
        <DialogHeader>
          <DialogTitle id="holiday-dialog-title">휴무일 추가</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} noValidate>
          <div className="space-y-2 py-2">
            <label htmlFor="holiday-date" className="block text-sm font-medium">
              날짜
            </label>
            <Input
              id="holiday-date"
              type="date"
              value={dateInput}
              onChange={(e) => setDateInput(e.target.value)}
              aria-required="true"
            />
            {error && (
              <p role="alert" className="text-sm text-destructive">
                {error}
              </p>
            )}
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={submitting}
            >
              취소
            </Button>
            <Button type="submit" disabled={submitting} aria-busy={submitting}>
              {submitting ? "추가 중..." : "추가"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
