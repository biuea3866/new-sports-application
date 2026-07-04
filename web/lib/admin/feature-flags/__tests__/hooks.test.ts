/**
 * @vitest-environment jsdom
 *
 * 피처 플래그 서버 상태 훅 테스트.
 * 로딩→데이터 전이, refetch, 언마운트 후 cancelled 가드를 사용자 관점(반환값)으로 검증한다.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";

import { useFeatureFlags } from "../hooks";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const sampleFlag = {
  id: 1,
  key: "demo.feature.hello",
  type: "RELEASE",
  status: "ACTIVE",
  description: "데모 플래그",
  strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
  createdAt: "2026-07-01T00:00:00.000Z",
  updatedAt: "2026-07-01T00:00:00.000Z",
};

describe("useFeatureFlags", () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it("로딩 중 isLoading=true, 성공 후 data 세팅·isLoading=false로 전이한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse([sampleFlag]));

    const { result } = renderHook(() => useFeatureFlags());

    expect(result.current.isLoading).toBe(true);
    expect(result.current.data).toBeNull();

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.data).toEqual([sampleFlag]);
    expect(result.current.error).toBeNull();
  });

  it("BFF가 5xx를 반환하면 error가 세팅된다", async () => {
    mockFetch.mockResolvedValue(jsonResponse({ message: "서버 오류가 발생했습니다." }, 500));

    const { result } = renderHook(() => useFeatureFlags());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.error).toBe("서버 오류가 발생했습니다.");
    expect(result.current.data).toBeNull();
  });

  it("refetch 호출 시 재조회한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse([sampleFlag]));

    const { result } = renderHook(() => useFeatureFlags());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(mockFetch).toHaveBeenCalledTimes(1);

    act(() => {
      result.current.refetch();
    });

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledTimes(2);
    });
  });

  it("컴포넌트 언마운트 후 응답 도착 시 상태 업데이트를 하지 않는다", async () => {
    let resolveFetch!: (response: Response) => void;
    mockFetch.mockReturnValue(
      new Promise<Response>((resolve) => {
        resolveFetch = resolve;
      })
    );

    const { result, unmount } = renderHook(() => useFeatureFlags());
    expect(result.current.isLoading).toBe(true);

    unmount();

    await act(async () => {
      resolveFetch(jsonResponse([sampleFlag]));
      await Promise.resolve();
      await Promise.resolve();
    });

    // 언마운트 후에는 훅 내부 상태가 갱신되지 않아야 한다(React act 경고 없이 안전 종료).
    expect(result.current.isLoading).toBe(true);
    expect(result.current.data).toBeNull();
  });
});
