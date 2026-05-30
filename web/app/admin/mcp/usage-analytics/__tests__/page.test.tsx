// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import type { UsageAnalyticsResponse } from "@/lib/admin/mcp/usageAnalytics";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

// recharts는 SVG를 그리는데 jsdom 환경에서 layoutEffect 경고를 유발한다.
// ResponsiveContainer 사이즈가 0이면 차트를 렌더하지 않으므로 ResizeObserver를 stub한다.
vi.stubGlobal(
  "ResizeObserver",
  class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
);

function makeAnalyticsResponse(
  overrides?: Partial<UsageAnalyticsResponse>
): Response {
  const body: UsageAnalyticsResponse = {
    dailyStats: [
      { date: "2026-05-30", toolName: "list_facilities", callCount: 10 },
    ],
    toolCallStats: [{ toolName: "list_facilities", callCount: 10 }],
    errorRateStat: { totalCount: 10, errorCount: 1, errorRatePercent: 10.0 },
    toolLatencyStats: [{ toolName: "list_facilities", p95LatencyMs: 120 }],
    tokenUsageStats: [
      {
        tokenId: 1,
        callCount: 10,
        errorCount: 1,
        errorRatePercent: 10.0,
        lastCalledAt: "2026-05-30T12:00:00Z",
      },
    ],
    ...overrides,
  };
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

describe("UsageAnalyticsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("[U-01] 정상 응답 시 에러율 요약 카드와 토큰별 테이블이 렌더된다", async () => {
    mockFetch.mockResolvedValue(makeAnalyticsResponse());
    const { default: UsageAnalyticsPage } = await import("../page");
    render(<UsageAnalyticsPage />);

    await waitFor(() => {
      expect(screen.getByText("총 호출")).toBeInTheDocument();
    });
    // 에러율이 카드와 테이블에 각각 표시된다
    const errorRateCells = screen.getAllByText("10.00%");
    expect(errorRateCells.length).toBeGreaterThanOrEqual(1);
    // 토큰별 테이블 헤더 확인
    expect(screen.getByText("Token ID")).toBeInTheDocument();
  });

  it("[U-02] API 오류 시 에러 메시지가 표시된다", async () => {
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ message: "권한이 없습니다." }), {
        status: 403,
        headers: { "Content-Type": "application/json" },
      })
    );
    const { default: UsageAnalyticsPage } = await import("../page");
    render(<UsageAnalyticsPage />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
  });

  it("[U-03] 빈 데이터 시 '데이터가 없습니다' 텍스트가 표시된다", async () => {
    mockFetch.mockResolvedValue(
      makeAnalyticsResponse({
        dailyStats: [],
        toolCallStats: [],
        toolLatencyStats: [],
        tokenUsageStats: [],
        errorRateStat: { totalCount: 0, errorCount: 0, errorRatePercent: 0 },
      })
    );
    const { default: UsageAnalyticsPage } = await import("../page");
    render(<UsageAnalyticsPage />);

    await waitFor(() => {
      const emptyTexts = screen.getAllByText("데이터가 없습니다.");
      expect(emptyTexts.length).toBeGreaterThan(0);
    });
  });

  it("[U-04] 조회 버튼 클릭 시 fetch를 재호출한다", async () => {
    mockFetch.mockResolvedValue(makeAnalyticsResponse());
    const { default: UsageAnalyticsPage } = await import("../page");
    render(<UsageAnalyticsPage />);

    await waitFor(() => {
      expect(screen.getByText("총 호출")).toBeInTheDocument();
    });

    const callCountBefore = mockFetch.mock.calls.length;
    const searchButton = screen.getByRole("button", { name: "사용 분석 조회" });
    fireEvent.click(searchButton);

    await waitFor(() => {
      expect(mockFetch.mock.calls.length).toBeGreaterThan(callCountBefore);
    });
  });
});
