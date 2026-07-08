"use client";

/**
 * 시설상품(program) 서버 상태 훅 + 등록 액션.
 */
import { useState, useEffect, useCallback } from "react";
import { ProgramListSchema, ProgramSchema, CreateProgramInputSchema } from "./schemas";
import type { Program, CreateProgramInput } from "./types";

export type UseProgramsStatus = "loading" | "success" | "error";

interface UseProgramsResult {
  data: Program[] | null;
  status: UseProgramsStatus;
  error: string | null;
  refetch: () => void;
}

export function usePrograms(facilityId: string): UseProgramsResult {
  const [data, setData] = useState<Program[] | null>(null);
  const [status, setStatus] = useState<UseProgramsStatus>("loading");
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const refetch = useCallback(() => setTick((t) => t + 1), []);

  useEffect(() => {
    let cancelled = false;
    setStatus("loading");
    setError(null);

    async function load() {
      try {
        const response = await fetch(`/api/portal/facilities/${facilityId}/programs`, {
          cache: "no-store",
        });
        if (!response.ok) {
          const body = (await response.json().catch(() => null)) as { message?: string } | null;
          if (!cancelled) {
            setError(body?.message ?? "시설상품을 불러오지 못했습니다.");
            setStatus("error");
          }
          return;
        }
        const json: unknown = await response.json();
        const parsed = ProgramListSchema.parse(json);
        if (!cancelled) {
          setData(parsed);
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

/** 시설상품 등록 — POST /api/portal/facilities/{facilityId}/programs */
export async function createProgram(
  facilityId: string,
  input: CreateProgramInput
): Promise<Program> {
  const validated = CreateProgramInputSchema.parse(input);
  const response = await fetch(`/api/portal/facilities/${facilityId}/programs`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(validated),
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `시설상품 등록 실패: ${response.status}`);
  }
  const json: unknown = await response.json();
  return ProgramSchema.parse(json);
}
