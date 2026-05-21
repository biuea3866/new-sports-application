"use client";

import { useState } from "react";
import { useEvent } from "@/lib/portal/useEvent";
import type { EventStatus } from "@/lib/portal/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

interface EventDetailClientProps {
  id: string;
}

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

export default function EventDetailClient({ id }: EventDetailClientProps) {
  const { data, isLoading, error, refetch } = useEvent(id);
  const [actionError, setActionError] = useState<string | null>(null);
  const [isActing, setIsActing] = useState(false);

  async function handleAction(type: "open" | "close") {
    setIsActing(true);
    setActionError(null);

    try {
      const res = await fetch(`/api/portal/events/${id}/${type}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
      });
      if (!res.ok) {
        const body = (await res.json()) as { message?: string };
        setActionError(body.message ?? "상태 변경에 실패했습니다.");
        return;
      }
      refetch();
    } catch {
      setActionError("네트워크 오류가 발생했습니다.");
    } finally {
      setIsActing(false);
    }
  }

  if (isLoading) {
    return (
      <p className="text-sm text-muted-foreground py-8 text-center" aria-live="polite" aria-busy="true">
        불러오는 중...
      </p>
    );
  }

  if (error !== null) {
    return (
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
    );
  }

  if (data === null) return null;

  const canOpen = data.status === "SCHEDULED";
  const canClose = data.status === "OPEN";

  return (
    <article aria-labelledby="event-title">
      <div className="flex items-start justify-between mb-6">
        <div>
          <h1 id="event-title" className="text-2xl font-bold">
            {data.title}
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            {data.venue} &middot; {new Date(data.startsAt).toLocaleString("ko-KR")}
          </p>
        </div>
        <Badge variant={STATUS_BADGE_VARIANT[data.status]}>
          {STATUS_LABELS[data.status]}
        </Badge>
      </div>

      {actionError !== null && (
        <div
          className="rounded-md border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive mb-4"
          role="alert"
        >
          {actionError}
        </div>
      )}

      <div className="flex gap-3 mb-8">
        <Button
          onClick={() => void handleAction("open")}
          disabled={!canOpen || isActing}
          aria-disabled={!canOpen || isActing}
          aria-label="경기 오픈"
        >
          {isActing && canOpen ? "처리 중..." : "오픈"}
        </Button>
        <Button
          variant="destructive"
          onClick={() => void handleAction("close")}
          disabled={!canClose || isActing}
          aria-disabled={!canClose || isActing}
          aria-label="경기 마감"
        >
          {isActing && canClose ? "처리 중..." : "마감"}
        </Button>
      </div>

      <section aria-labelledby="sales-heading">
        <h2 id="sales-heading" className="text-lg font-semibold mb-3">
          판매 현황
        </h2>
        <dl className="grid grid-cols-3 gap-4 rounded-lg border border-border p-4 mb-4">
          <div>
            <dt className="text-xs text-muted-foreground">총 좌석</dt>
            <dd className="text-xl font-bold mt-1">{data.totalSeats}석</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">판매</dt>
            <dd className="text-xl font-bold mt-1 text-primary">{data.soldSeats}석</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">잔여</dt>
            <dd className="text-xl font-bold mt-1">{data.availableSeats}석</dd>
          </div>
        </dl>

        {data.seats.length > 0 && (
          <div className="overflow-auto rounded-lg border border-border">
            <table className="w-full text-sm" aria-label="좌석별 판매 현황">
              <thead>
                <tr className="bg-muted/50">
                  <th scope="col" className="px-4 py-2 text-left font-medium">
                    좌석
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-medium">
                    상태
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.seats.map((seat) => (
                  <tr key={seat.id} className="border-t border-border">
                    <td className="px-4 py-2">{seat.label}</td>
                    <td className="px-4 py-2">
                      {seat.sold ? (
                        <Badge variant="destructive">판매됨</Badge>
                      ) : (
                        <Badge variant="outline">잔여</Badge>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </article>
  );
}
