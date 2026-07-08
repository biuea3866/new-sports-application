"use client";

/**
 * SlotCloseConfirmDialog — 슬롯 마감 확인 다이얼로그 (design-fe-web.md W-SL 와이어프레임).
 * "신규 예약만 차단, 기존 예약은 유지됩니다" 고지를 노출한다.
 */
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";

export interface SlotCloseConfirmDialogProps {
  open: boolean;
  timeRange: string | null;
  submitting: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function SlotCloseConfirmDialog({
  open,
  timeRange,
  submitting,
  onConfirm,
  onCancel,
}: SlotCloseConfirmDialogProps) {
  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next) onCancel(); }}>
      <DialogContent aria-labelledby="slot-close-confirm-title">
        <DialogHeader>
          <DialogTitle id="slot-close-confirm-title">슬롯 마감</DialogTitle>
        </DialogHeader>
        <p className="py-2 text-sm text-muted-foreground">
          {timeRange} 슬롯을 마감하시겠습니까?
          <br />
          신규 예약만 차단되고, 기존 예약은 유지됩니다.
        </p>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onCancel} disabled={submitting}>
            취소
          </Button>
          <Button
            type="button"
            variant="destructive"
            onClick={onConfirm}
            disabled={submitting}
            aria-busy={submitting}
          >
            {submitting ? "마감 중..." : "마감"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
