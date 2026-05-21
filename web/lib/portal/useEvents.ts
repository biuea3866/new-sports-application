/**
 * useEvents — 내 경기 목록 조회 훅
 * BFF 엔드포인트 /api/portal/events 만 호출한다.
 */
"use client";

import { useState, useEffect, useCallback } from "react";
import type { MyEvent, EventStatus, Page } from "./types";

interface UseEventsParams {
  page?: number;
  size?: number;
  status?: EventStatus | "";
}

interface UseEventsResult {
  data: Page<MyEvent> | null;
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
}

export function useEvents({
  page = 0,
  size = 10,
  status = "",
}: UseEventsParams = {}): UseEventsResult {
  const [data, setData] = useState<Page<MyEvent> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const refetch = useCallback(() => {
    setTick((t) => t + 1);
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setError(null);

      const params = new URLSearchParams();
      params.set("page", String(page));
      params.set("size", String(size));
      if (status) {
        params.set("status", status);
      }

      try {
        const res = await fetch(`/api/portal/events?${params.toString()}`);
        if (!res.ok) {
          const body = (await res.json()) as { message?: string };
          if (!cancelled) {
            setError(body.message ?? "경기 목록을 불러오지 못했습니다.");
          }
          return;
        }
        const json = (await res.json()) as Page<MyEvent>;
        if (!cancelled) {
          setData(json);
        }
      } catch {
        if (!cancelled) {
          setError("네트워크 오류가 발생했습니다.");
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    void load();

    return () => {
      cancelled = true;
    };
  }, [page, size, status, tick]);

  return { data, isLoading, error, refetch };
}
