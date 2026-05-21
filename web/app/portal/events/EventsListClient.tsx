"use client";

import { useState } from "react";
import { useEvents } from "@/lib/portal/useEvents";
import type { EventStatus } from "@/lib/portal/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

const STATUS_LABELS: Record<EventStatus, string> = {
  SCHEDULED: "예정",
  OPEN: "오픈",
  CLOSED: "마감",
  CANCELLED: "취소",
};

const STATUS_BADGE_VARIANT: Record<
  EventStatus,
  "default" | "secondary" | "destructive" | "outline"
> = {
  SCHEDULED: "secondary",
  OPEN: "default",
  CLOSED: "outline",
  CANCELLED: "destructive",
};

const STATUS_FILTER_OPTIONS: { value: EventStatus | ""; label: string }[] = [
  { value: "", label: "전체" },
  { value: "SCHEDULED", label: "예정" },
  { value: "OPEN", label: "오픈" },
  { value: "CLOSED", label: "마감" },
];

export default function EventsListClient() {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<EventStatus | "">("");

  const { data, isLoading, error, refetch } = useEvents({ page, size: 10, status });

  function handleStatusChange(next: EventStatus | "") {
    setStatus(next);
    setPage(0);
  }

  return (
    <section aria-label="경기 목록">
      <div className="flex gap-2 mb-4" role="group" aria-label="상태 필터">
        {STATUS_FILTER_OPTIONS.map((opt) => (
          <button
            key={opt.value}
            onClick={() => handleStatusChange(opt.value)}
            aria-pressed={status === opt.value}
            className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors border ${
              status === opt.value
                ? "bg-primary text-primary-foreground border-primary"
                : "bg-background text-foreground border-input hover:bg-accent"
            }`}
          >
            {opt.label}
          </button>
        ))}
      </div>

      {isLoading && (
        <p
          className="text-sm text-muted-foreground py-8 text-center"
          aria-live="polite"
          aria-busy="true"
        >
          불러오는 중...
        </p>
      )}

      {error !== null && !isLoading && (
        <div
          className="rounded-md border border-destructive/50 bg-destructive/10 p-4 text-sm text-destructive"
          role="alert"
        >
          {error}
          <Button
            variant="outline"
            size="sm"
            className="ml-3"
            onClick={refetch}
            aria-label="다시 시도"
          >
            다시 시도
          </Button>
        </div>
      )}

      {!isLoading && error === null && data !== null && data.content.length === 0 && (
        <p className="text-sm text-muted-foreground py-8 text-center">
          등록된 경기가 없습니다.
        </p>
      )}

      {!isLoading && error === null && data !== null && data.content.length > 0 && (
        <>
          <ul className="space-y-3" aria-label="경기 카드 목록">
            {data.content.map((event) => (
              <li key={event.id}>
                <a
                  href={`/portal/events/${event.id}`}
                  className="block rounded-lg border border-border p-4 hover:bg-accent transition-colors"
                  aria-label={`${event.title} — ${STATUS_LABELS[event.status]}`}
                >
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="font-semibold">{event.title}</p>
                      <p className="text-sm text-muted-foreground mt-1">
                        {event.venue} &middot;{" "}
                        {new Date(event.startsAt).toLocaleString("ko-KR")}
                      </p>
                    </div>
                    <div className="flex flex-col items-end gap-1">
                      <Badge variant={STATUS_BADGE_VARIANT[event.status]}>
                        {STATUS_LABELS[event.status]}
                      </Badge>
                      <span className="text-xs text-muted-foreground">
                        판매 {event.soldSeats} / {event.totalSeats}석
                      </span>
                    </div>
                  </div>
                </a>
              </li>
            ))}
          </ul>

          <div className="flex items-center justify-between mt-6">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
              aria-label="이전 페이지"
            >
              이전
            </Button>
            <span className="text-sm text-muted-foreground" aria-live="polite">
              {page + 1} / {Math.max(data.totalPages, 1)} 페이지
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={page + 1 >= data.totalPages}
              onClick={() => setPage((p) => p + 1)}
              aria-label="다음 페이지"
            >
              다음
            </Button>
          </div>
        </>
      )}
    </section>
  );
}
