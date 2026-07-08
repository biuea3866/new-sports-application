"use client";

/**
 * SlotRow — 캘린더 셀 안의 슬롯 1건 (design-fe-web.md W-SL 와이어프레임).
 * 상태 배지(OPEN/CLOSED) + close/open 액션 + 기존 편집/삭제 액션을 표시한다.
 */
import { Badge } from "@/components/ui/badge";
import type { SlotResponse } from "@/lib/portal/slots";

export interface SlotRowProps {
  slot: SlotResponse;
  onEdit: () => void;
  onDelete: () => void;
  onClose: () => void;
  onOpen: () => void;
  closing: boolean;
  opening: boolean;
}

export function SlotRow({ slot, onEdit, onDelete, onClose, onOpen, closing, opening }: SlotRowProps) {
  const isOpen = slot.status === "OPEN";

  return (
    <li className="flex flex-wrap items-center gap-1">
      <button
        type="button"
        className="flex-1 truncate rounded bg-accent px-1 py-0.5 text-left text-xs text-accent-foreground hover:opacity-80 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        onClick={(e) => {
          e.stopPropagation();
          onEdit();
        }}
        aria-label={`슬롯 ${slot.timeRange} 편집`}
      >
        {slot.timeRange}
      </button>

      <Badge
        variant="outline"
        className={
          isOpen
            ? "border-transparent bg-success text-success-foreground"
            : "border-transparent bg-warning text-warning-foreground"
        }
        aria-label={`슬롯 상태 ${isOpen ? "OPEN" : "CLOSED"}`}
      >
        {isOpen ? "OPEN" : "CLOSED"}
      </Badge>

      {isOpen ? (
        <button
          type="button"
          className="rounded px-1 py-0.5 text-xs text-destructive hover:bg-destructive/10 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:opacity-50"
          onClick={(e) => {
            e.stopPropagation();
            onClose();
          }}
          disabled={closing}
          aria-label={`슬롯 ${slot.timeRange} 마감`}
        >
          {closing ? "마감 중..." : "마감"}
        </button>
      ) : (
        <button
          type="button"
          className="rounded px-1 py-0.5 text-xs text-success hover:bg-success/10 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:opacity-50"
          onClick={(e) => {
            e.stopPropagation();
            onOpen();
          }}
          disabled={opening}
          aria-label={`슬롯 ${slot.timeRange} 오픈`}
        >
          {opening ? "오픈 중..." : "오픈"}
        </button>
      )}

      <button
        type="button"
        className="rounded px-1 py-0.5 text-xs text-destructive hover:bg-destructive/10 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        onClick={(e) => {
          e.stopPropagation();
          onDelete();
        }}
        aria-label={`슬롯 ${slot.timeRange} 삭제`}
      >
        ×
      </button>
    </li>
  );
}
