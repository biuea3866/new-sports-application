/**
 * 피처 플래그 서버 상태 훅.
 * 레포 관례(`lib/portal/useProducts.ts`)를 따른다 — useState/useEffect + BFF fetch, refetch 노출.
 * TanStack Query 미도입(레포 정착 스택). 서버 상태를 스토어에 복사 보관하지 않는다.
 * 근거 티켓: `FE-04-hooks-api-client.md`.
 */
"use client";

import { useState, useEffect, useCallback } from "react";

import { fetchFeatureFlags, fetchFeatureFlag, fetchFlagAuditLogs } from "./api";
import type { FetchFeatureFlagsFilters } from "./api";
import type { FeatureFlagResponse, FeatureFlagAuditLogPageView } from "./schemas";

function toUserMessage(err: unknown): string {
  return err instanceof Error ? err.message : "알 수 없는 오류가 발생했습니다.";
}

interface UseFeatureFlagsResult {
  data: FeatureFlagResponse[] | null;
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
}

/** 목록(S1) 서버 상태 훅 — status/type 필터로 조회한다. */
export function useFeatureFlags(filters: FetchFeatureFlagsFilters = {}): UseFeatureFlagsResult {
  const { status, type } = filters;
  const [data, setData] = useState<FeatureFlagResponse[] | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const refetch = useCallback(() => {
    setTick((t) => t + 1);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);

    fetchFeatureFlags({ status, type })
      .then((flags) => {
        if (!cancelled) {
          setData(flags);
          setIsLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(toUserMessage(err));
          setIsLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [status, type, tick]);

  return { data, isLoading, error, refetch };
}

interface UseFeatureFlagResult {
  data: FeatureFlagResponse | null;
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
}

/** 상세(S3) 서버 상태 훅 — key로 단일 플래그를 조회한다. */
export function useFeatureFlag(key: string): UseFeatureFlagResult {
  const [data, setData] = useState<FeatureFlagResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const refetch = useCallback(() => {
    setTick((t) => t + 1);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);

    fetchFeatureFlag(key)
      .then((flag) => {
        if (!cancelled) {
          setData(flag);
          setIsLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(toUserMessage(err));
          setIsLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [key, tick]);

  return { data, isLoading, error, refetch };
}

interface UseFlagAuditLogsResult {
  data: FeatureFlagAuditLogPageView | null;
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
}

/** 변경 이력(S5) 서버 상태 훅 — key·page·size로 감사 로그를 페이징 조회한다. */
export function useFlagAuditLogs(key: string, page = 0, size = 20): UseFlagAuditLogsResult {
  const [data, setData] = useState<FeatureFlagAuditLogPageView | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const refetch = useCallback(() => {
    setTick((t) => t + 1);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);

    fetchFlagAuditLogs(key, page, size)
      .then((pageView) => {
        if (!cancelled) {
          setData(pageView);
          setIsLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(toUserMessage(err));
          setIsLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [key, page, size, tick]);

  return { data, isLoading, error, refetch };
}
