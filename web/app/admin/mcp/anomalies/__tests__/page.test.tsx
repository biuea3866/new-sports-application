// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import type { McpAnomalyEventResponse } from "@/lib/admin/mcp/anomalies";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

function makeListResponse(
  events: McpAnomalyEventResponse[],
  totalPages = 1
): Response {
  return new Response(
    JSON.stringify({
      content: events,
      totalElements: events.length,
      totalPages,
      pageNumber: 0,
      pageSize: 20,
    }),
    { status: 200, headers: { "Content-Type": "application/json" } }
  );
}

function makeOpenEvent(id = 1): McpAnomalyEventResponse {
  return {
    id,
    tokenId: 10,
    ownerUserId: 100,
    detectedAt: "2026-05-30T10:00:00Z",
    currentHourCount: 500,
    baselineAverage: 50.0,
    status: "OPEN",
    falsePositive: false,
    resolvedAt: null,
    note: null,
  };
}

describe("AnomaliesPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("[U-01] 이상 패턴 목록이 정상 렌더된다", async () => {
    mockFetch.mockResolvedValue(makeListResponse([makeOpenEvent(1)]));
    const { default: AnomaliesPage } = await import("../page");
    render(<AnomaliesPage />);

    await waitFor(() => {
      expect(screen.getByText("500")).toBeInTheDocument();
    });
    expect(screen.getByText("미처리")).toBeInTheDocument();
    expect(screen.getByText("10")).toBeInTheDocument();
  });

  it("[U-02] 빈 목록 시 '조회된 이상 패턴이 없습니다' 메시지가 표시된다", async () => {
    mockFetch.mockResolvedValue(makeListResponse([]));
    const { default: AnomaliesPage } = await import("../page");
    render(<AnomaliesPage />);

    await waitFor(() => {
      expect(
        screen.getByText("조회된 이상 패턴이 없습니다.")
      ).toBeInTheDocument();
    });
  });

  it("[U-03] API 오류 시 에러 메시지가 표시된다", async () => {
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ message: "서버 오류" }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      })
    );
    const { default: AnomaliesPage } = await import("../page");
    render(<AnomaliesPage />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
  });

  it("[U-04] OPEN 이벤트에 '오탐 표시' 버튼이 표시된다", async () => {
    mockFetch.mockResolvedValue(makeListResponse([makeOpenEvent(1)]));
    const { default: AnomaliesPage } = await import("../page");
    render(<AnomaliesPage />);

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "이벤트 1 오탐 표시" })
      ).toBeInTheDocument();
    });
  });

  it("[U-05] 오탐 표시 버튼 클릭 시 모달이 열린다", async () => {
    mockFetch.mockResolvedValue(makeListResponse([makeOpenEvent(1)]));
    const { default: AnomaliesPage } = await import("../page");
    render(<AnomaliesPage />);

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "이벤트 1 오탐 표시" })
      ).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "이벤트 1 오탐 표시" }));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByLabelText("사유 메모")).toBeInTheDocument();
  });

  it("[U-06] 오탐 처리 성공 후 모달이 닫히고 목록이 갱신된다", async () => {
    mockFetch
      .mockResolvedValueOnce(makeListResponse([makeOpenEvent(1)]))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(
        makeListResponse([
          { ...makeOpenEvent(1), status: "FALSE_POSITIVE", falsePositive: true },
        ])
      );

    const { default: AnomaliesPage } = await import("../page");
    render(<AnomaliesPage />);

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "이벤트 1 오탐 표시" })
      ).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "이벤트 1 오탐 표시" }));

    fireEvent.change(screen.getByLabelText("사유 메모"), {
      target: { value: "정상 배치 작업" },
    });

    fireEvent.click(screen.getByRole("button", { name: "오탐 처리 확인" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText("오탐")).toBeInTheDocument();
    });
  });

  it("[U-07] 페이지네이션: 2페이지 이상 시 이전/다음 버튼이 표시된다", async () => {
    mockFetch.mockResolvedValue(makeListResponse([makeOpenEvent(1)], 3));
    const { default: AnomaliesPage } = await import("../page");
    render(<AnomaliesPage />);

    await waitFor(() => {
      expect(screen.getByLabelText("이전 페이지")).toBeInTheDocument();
      expect(screen.getByLabelText("다음 페이지")).toBeInTheDocument();
    });
  });
});
