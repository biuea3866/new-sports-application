"use client";

/**
 * 대기질 서버 상태 훅.
 * 레포 관례(`lib/portal/useProducts.ts`)를 따른다 — useState/useEffect + BFF fetch.
 * 컴포넌트가 직접 fetch하지 않도록 캡슐화한다(`no-direct-fetch`).
 * 응답은 zod 스키마로 `.parse`해 좁힌다(`no-loose-assertion`).
 * 근거 티켓: FE-06-web-airquality-bff-hook.md.
 */
import { useState, useEffect } from "react";

import { AirQualityResponseSchema } from "@/lib/portal/air-quality";
import type { AirQualityResponse } from "@/lib/portal/air-quality";

export type UseAirQualityStatus = "idle" | "loading" | "success" | "error";

export interface UseAirQualityResult {
  data: AirQualityResponse | null;
  status: UseAirQualityStatus;
}

function isValidCoordinate(value: number | null | undefined): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

/**
 * `(lat, lng)`가 유효한 좌표일 때만 `/api/portal/air-quality`를 조회한다.
 * 좌표가 없거나(NaN·null·undefined) 파싱 불가하면 조회하지 않고 idle을 유지한다.
 */
export function useAirQuality(
  lat: number | null | undefined,
  lng: number | null | undefined
): UseAirQualityResult {
  const [data, setData] = useState<AirQualityResponse | null>(null);
  const [status, setStatus] = useState<UseAirQualityStatus>("idle");

  useEffect(() => {
    if (!isValidCoordinate(lat) || !isValidCoordinate(lng)) {
      setStatus("idle");
      setData(null);
      return;
    }

    let cancelled = false;
    setStatus("loading");

    const query = new URLSearchParams({ lat: String(lat), lng: String(lng) });
    fetch(`/api/portal/air-quality?${query.toString()}`, { cache: "no-store" })
      .then(async (response) => {
        if (!response.ok) {
          const text = await response.text();
          throw new Error(text || response.statusText);
        }
        return response.json() as Promise<unknown>;
      })
      .then((json) => {
        if (cancelled) return;
        setData(AirQualityResponseSchema.parse(json));
        setStatus("success");
      })
      .catch(() => {
        if (cancelled) return;
        setData(null);
        setStatus("error");
      });

    return () => {
      cancelled = true;
    };
  }, [lat, lng]);

  return { data, status };
}
