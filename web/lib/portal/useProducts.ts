/**
 * useProducts — 내 상품 목록 조회 훅
 * BFF 엔드포인트 /api/portal/products 만 호출한다.
 */
"use client";

import { useState, useEffect, useCallback } from "react";
import type { MyProduct, Page } from "./types";

interface UseProductsParams {
  page?: number;
  size?: number;
}

interface UseProductsResult {
  data: Page<MyProduct> | null;
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
}

export function useProducts({ page = 0, size = 10 }: UseProductsParams = {}): UseProductsResult {
  const [data, setData] = useState<Page<MyProduct> | null>(null);
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

      try {
        const res = await fetch(`/api/portal/products?${params.toString()}`);
        if (!res.ok) {
          const body = (await res.json()) as { message?: string };
          if (!cancelled) {
            setError(body.message ?? "상품 목록을 불러오지 못했습니다.");
          }
          return;
        }
        const json = (await res.json()) as Page<MyProduct>;
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
  }, [page, size, tick]);

  return { data, isLoading, error, refetch };
}
