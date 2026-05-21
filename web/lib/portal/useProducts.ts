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

export function useProducts(params: UseProductsParams = {}): UseProductsResult {
  const { page = 0, size = 20 } = params;
  const [data, setData] = useState<Page<MyProduct> | null>(null);
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

    const url = `/api/portal/products?page=${page}&size=${size}`;
    fetch(url)
      .then(async (res) => {
        if (!res.ok) {
          const text = await res.text();
          throw new Error(text || res.statusText);
        }
        return res.json() as Promise<Page<MyProduct>>;
      })
      .then((json) => {
        if (!cancelled) {
          setData(json);
          setIsLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "알 수 없는 오류가 발생했습니다.");
          setIsLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [page, size, tick]);

  return { data, isLoading, error, refetch };
}
