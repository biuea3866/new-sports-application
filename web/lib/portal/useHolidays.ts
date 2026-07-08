"use client";

/**
 * 휴무일 서버 상태 훅 + 추가/삭제 액션.
 * 휴무일도 운영시간과 동일하게 시설 상세 응답에 임베드되어 내려온다.
 */
import { useState, useEffect, useCallback } from "react";
import { FacilityScheduleSchema, HolidayInputSchema } from "./schemas";

export type UseHolidaysStatus = "loading" | "success" | "error";

interface UseHolidaysResult {
  data: string[] | null;
  status: UseHolidaysStatus;
  error: string | null;
  refetch: () => void;
}

export function useHolidays(facilityId: string): UseHolidaysResult {
  const [data, setData] = useState<string[] | null>(null);
  const [status, setStatus] = useState<UseHolidaysStatus>("loading");
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const refetch = useCallback(() => setTick((t) => t + 1), []);

  useEffect(() => {
    let cancelled = false;
    setStatus("loading");
    setError(null);

    async function load() {
      try {
        const response = await fetch(`/api/portal/facilities/${facilityId}`, {
          cache: "no-store",
        });
        if (!response.ok) {
          const body = (await response.json().catch(() => null)) as { message?: string } | null;
          if (!cancelled) {
            setError(body?.message ?? "휴무일을 불러오지 못했습니다.");
            setStatus("error");
          }
          return;
        }
        const json: unknown = await response.json();
        const parsed = FacilityScheduleSchema.parse(json);
        if (!cancelled) {
          setData(parsed.holidays);
          setStatus("success");
        }
      } catch {
        if (!cancelled) {
          setError("네트워크 오류가 발생했습니다.");
          setStatus("error");
        }
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, [facilityId, tick]);

  return { data, status, error, refetch };
}

/** 휴무일 추가 — POST /api/portal/facilities/{facilityId}/holidays */
export async function addHoliday(facilityId: string, date: string): Promise<string[]> {
  const validated = HolidayInputSchema.parse({ date });
  const response = await fetch(`/api/portal/facilities/${facilityId}/holidays`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(validated),
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `휴무일 추가 실패: ${response.status}`);
  }
  const json: unknown = await response.json();
  return FacilityScheduleSchema.parse(json).holidays;
}

/** 휴무일 삭제 — DELETE /api/portal/facilities/{facilityId}/holidays?date=... */
export async function removeHoliday(facilityId: string, date: string): Promise<string[]> {
  const validated = HolidayInputSchema.parse({ date });
  const response = await fetch(
    `/api/portal/facilities/${facilityId}/holidays?date=${encodeURIComponent(validated.date)}`,
    { method: "DELETE" }
  );
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `휴무일 삭제 실패: ${response.status}`);
  }
  const json: unknown = await response.json();
  return FacilityScheduleSchema.parse(json).holidays;
}
