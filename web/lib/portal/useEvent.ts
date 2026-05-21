/**
 * useEvent — 경기 단건 조회 훅
 * BFF 엔드포인트 /api/portal/events/[id] 만 호출한다.
 */
"use client";

import { useState, useEffect, useCallback } from "react";
import type { MyEventDetail } from "./types";

interface UseEventResult {
  data: MyEventDetail | null;
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
}

export function useEvent(id: string): UseEventResult {
  const [data, setData] = useState<MyEventDetail | null>(null);
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

      try {
        const res = await fetch(`/api/portal/events/${id}`);
        if (!res.ok) {
          const body = (await res.json()) as { message?: string };
          if (!cancelled) {
            setError(body.message ?? "경기 정보를 불러오지 못했습니다.");
          }
          return;
        }
        const json = (await res.json()) as MyEventDetail;
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
  }, [id, tick]);

  return { data, isLoading, error, refetch };
}
