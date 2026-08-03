// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";

vi.mock("@/lib/admin/auditLogs", () => ({
  fetchAuditLogs: vi.fn(),
}));

import { fetchAuditLogs } from "@/lib/admin/auditLogs";
import AuditLogsPage from "../page";

const mockFetchAuditLogs = vi.mocked(fetchAuditLogs);

const EMPTY_RESPONSE = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  pageNumber: 0,
  pageSize: 20,
};

const SINGLE_PAGE_RESPONSE = {
  content: [
    {
      id: 1,
      tokenId: 10,
      toolName: "search_players",
      paramsMasked: null,
      statusCode: 200,
      latencyMs: 123,
      ipAddr: "127.0.0.1",
      calledAt: "2026-05-20T10:00:00Z",
    },
    {
      id: 2,
      tokenId: 10,
      toolName: "get_schedule",
      paramsMasked: null,
      statusCode: 500,
      latencyMs: 456,
      ipAddr: "127.0.0.1",
      calledAt: "2026-05-20T11:00:00Z",
    },
  ],
  totalElements: 2,
  totalPages: 1,
  pageNumber: 0,
  pageSize: 20,
};

const MULTI_PAGE_RESPONSE = {
  content: [
    {
      id: 1,
      tokenId: 10,
      toolName: "search_players",
      paramsMasked: null,
      statusCode: 200,
      latencyMs: 100,
      ipAddr: "127.0.0.1",
      calledAt: "2026-05-20T10:00:00Z",
    },
  ],
  totalElements: 40,
  totalPages: 2,
  pageNumber: 0,
  pageSize: 20,
};

describe("AuditLogsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("[U-01] 페이지 제목이 렌더링된다", () => {
    mockFetchAuditLogs.mockResolvedValue(EMPTY_RESPONSE);
    render(<AuditLogsPage />);
    expect(screen.getByRole("heading", { name: "감사 로그" })).toBeInTheDocument();
  });

  it("[U-02] 시작일·종료일 입력 필드와 조회 버튼이 렌더링된다", () => {
    mockFetchAuditLogs.mockResolvedValue(EMPTY_RESPONSE);
    render(<AuditLogsPage />);
    expect(screen.getByLabelText("시작일")).toBeInTheDocument();
    expect(screen.getByLabelText("종료일")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "감사 로그 검색" })).toBeInTheDocument();
  });

  it("[U-03] 로그 목록이 로딩 후 테이블에 렌더링된다", async () => {
    mockFetchAuditLogs.mockResolvedValue(SINGLE_PAGE_RESPONSE);
    render(<AuditLogsPage />);

    await waitFor(() => {
      expect(screen.getByText("search_players")).toBeInTheDocument();
    });
    expect(screen.getByText("get_schedule")).toBeInTheDocument();
    expect(screen.getByText(/총 2건/)).toBeInTheDocument();
  });

  // 배지 색은 시맨틱 토큰으로만 구분한다(no-hardcoded-color) — 라이트/다크 두 모드에서 모두 판독 가능해야 하므로
  // `green`/`red` 팔레트 클래스가 아니라 success/destructive 토큰을 검증한다.
  it("[U-04] 성공(2xx) statusCode는 success, 실패(5xx)는 destructive 토큰 배지로 렌더링된다", async () => {
    mockFetchAuditLogs.mockResolvedValue(SINGLE_PAGE_RESPONSE);
    render(<AuditLogsPage />);

    await waitFor(() => {
      expect(screen.getByText("200")).toBeInTheDocument();
    });
    const badge200 = screen.getByText("200");
    const badge500 = screen.getByText("500");
    expect(badge200.className).toContain("success");
    expect(badge500.className).toContain("destructive");
    expect(badge200.className).not.toMatch(/green|red/);
    expect(badge500.className).not.toMatch(/green|red/);
  });

  it("[U-05] 결과가 없을 때 빈 상태 메시지를 표시한다", async () => {
    mockFetchAuditLogs.mockResolvedValue(EMPTY_RESPONSE);
    render(<AuditLogsPage />);

    await waitFor(() => {
      expect(screen.getByText("조회된 로그가 없습니다.")).toBeInTheDocument();
    });
  });

  it("[U-06] API 오류 시 에러 메시지를 role=alert으로 표시한다", async () => {
    mockFetchAuditLogs.mockRejectedValue(new Error("네트워크 오류"));
    render(<AuditLogsPage />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
    expect(screen.getByRole("alert")).toHaveTextContent("네트워크 오류");
  });

  it("[U-07] totalPages > 1이면 페이지네이션이 렌더링된다", async () => {
    mockFetchAuditLogs.mockResolvedValue(MULTI_PAGE_RESPONSE);
    render(<AuditLogsPage />);

    await waitFor(() => {
      expect(screen.getByRole("navigation", { name: "감사 로그 페이지 이동" })).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: "이전 페이지" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "다음 페이지" })).not.toBeDisabled();
  });

  it("[U-08] totalPages === 1이면 페이지네이션이 렌더링되지 않는다", async () => {
    mockFetchAuditLogs.mockResolvedValue(SINGLE_PAGE_RESPONSE);
    render(<AuditLogsPage />);

    await waitFor(() => {
      expect(screen.getByText("search_players")).toBeInTheDocument();
    });
    expect(
      screen.queryByRole("navigation", { name: "감사 로그 페이지 이동" })
    ).not.toBeInTheDocument();
  });

  it("[U-09] 시작일 변경 시 fetchAuditLogs가 새 파라미터로 재호출된다", async () => {
    mockFetchAuditLogs.mockResolvedValue(EMPTY_RESPONSE);
    render(<AuditLogsPage />);

    await waitFor(() => {
      expect(mockFetchAuditLogs).toHaveBeenCalledTimes(1);
    });

    const fromInput = screen.getByLabelText("시작일");
    fireEvent.change(fromInput, { target: { value: "2026-05-01" } });

    await waitFor(() => {
      expect(mockFetchAuditLogs).toHaveBeenCalledTimes(2);
    });
    const lastCall = mockFetchAuditLogs.mock.calls[1]?.[0];
    expect(lastCall?.from).toBe("2026-05-01T00:00:00.000Z");
  });

  // 한국어 콘솔인데 표 헤더 4개만 영문으로 남아 있던 결함(03-관리자콘솔/07 캡쳐).
  it("[U-10] 감사 로그 테이블 헤더를 한글로 보여준다", async () => {
    mockFetchAuditLogs.mockResolvedValue(EMPTY_RESPONSE);
    render(<AuditLogsPage />);

    await waitFor(() => {
      expect(screen.getByRole("columnheader", { name: "도구 이름" })).toBeInTheDocument();
    });
    expect(screen.getByRole("columnheader", { name: "응답 코드" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "응답 시간(ms)" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "호출 시각" })).toBeInTheDocument();
  });

  it("[U-10] 영문 표 헤더가 남아 있지 않다", async () => {
    mockFetchAuditLogs.mockResolvedValue(EMPTY_RESPONSE);
    render(<AuditLogsPage />);

    await waitFor(() => {
      expect(screen.getByRole("columnheader", { name: "도구 이름" })).toBeInTheDocument();
    });
    for (const englishHeader of ["Tool Name", "Status Code", "Latency (ms)", "Called At"]) {
      expect(screen.queryByText(englishHeader)).not.toBeInTheDocument();
    }
  });
});
