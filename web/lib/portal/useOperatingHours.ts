"use client";

/**
 * 운영시간 서버 상태 훅 + 등록/수정 액션.
 * 운영시간 전용 GET 엔드포인트는 없다 — 시설 상세 응답(FacilityResponse)에
 * operatingHours가 임베드되어 내려오므로 `/api/portal/facilities/{facilityId}`를
 * 조회해 operatingHours만 추출한다 (design-fe-web.md Open Questions 참조).
 */
import { useState, useEffect, useCallback } from "react";
import { FacilityScheduleSchema, RegisterOperatingHoursInputSchema } from "./schemas";
import type { OperatingHours, RegisterOperatingHoursInput } from "./types";

export type UseOperatingHoursStatus = "loading" | "success" | "error";

interface UseOperatingHoursResult {
  data: OperatingHours[] | null;
  status: UseOperatingHoursStatus;
  error: string | null;
  refetch: () => void;
}

export function useOperatingHours(facilityId: string): UseOperatingHoursResult {
  const [data, setData] = useState<OperatingHours[] | null>(null);
  const [status, setStatus] = useState<UseOperatingHoursStatus>("loading");
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
            setError(body?.message ?? "운영시간을 불러오지 못했습니다.");
            setStatus("error");
          }
          return;
        }
        const json: unknown = await response.json();
        const parsed = FacilityScheduleSchema.parse(json);
        if (!cancelled) {
          setData(parsed.operatingHours);
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

/** 운영시간 등록/수정 — PUT /api/portal/facilities/{facilityId}/operating-hours */
export async function updateOperatingHours(
  facilityId: string,
  input: RegisterOperatingHoursInput
): Promise<OperatingHours[]> {
  const validated = RegisterOperatingHoursInputSchema.parse(input);
  const response = await fetch(`/api/portal/facilities/${facilityId}/operating-hours`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(validated),
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `운영시간 저장 실패: ${response.status}`);
  }
  const json: unknown = await response.json();
  return FacilityScheduleSchema.parse(json).operatingHours;
}
