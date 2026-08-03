// @vitest-environment jsdom
/**
 * 경기 목록 화면 — BE `/api/event-host/events` 목록 응답을 카드로 렌더한다.
 *
 * 회귀 방지: 목록 응답에 좌석 집계(totalSeats/soldSeats)가 빠져 있어 카드에 숫자가 통째로
 * 사라지고 "판매 / 석"만 남았다. 같은 경기의 상세 화면은 값이 정상이라 목록만 깨진 상태였다.
 */
import { render, screen, within } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockUseEvents = vi.fn();
vi.mock("@/lib/portal/useEvents", () => ({
  useEvents: (params: unknown) => mockUseEvents(params) as unknown,
}));

import EventsListClient from "../EventsListClient";

function buildListResponse(
  overrides: Partial<{ totalSeats: number; soldSeats: number; availableSeats: number }> = {}
) {
  return {
    content: [
      {
        id: 54,
        title: "2026 시티리그 4강 홈경기",
        venue: "잠실 실내체육관",
        startsAt: "2026-08-10T19:00:00+09:00",
        status: "OPEN" as const,
        ownerId: 7,
        totalSeats: 90,
        soldSeats: 2,
        availableSeats: 88,
        createdAt: "2026-07-01T10:00:00+09:00",
        updatedAt: "2026-07-01T10:00:00+09:00",
        ...overrides,
      },
    ],
    totalPages: 1,
    totalElements: 1,
  };
}

describe("EventsListClient", () => {
  beforeEach(() => {
    mockUseEvents.mockReset();
  });

  it("목록 카드에 판매 좌석 수와 총 좌석 수를 표시한다", () => {
    mockUseEvents.mockReturnValue({
      data: buildListResponse(),
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    render(<EventsListClient />);

    const card = screen.getByRole("listitem");
    expect(within(card).getByText(/판매\s*2\s*\/\s*90석/)).toBeInTheDocument();
  });

  it("좌석이 등록되지 않은 경기는 0으로 표시한다", () => {
    mockUseEvents.mockReturnValue({
      data: buildListResponse({ totalSeats: 0, soldSeats: 0, availableSeats: 0 }),
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    render(<EventsListClient />);

    const card = screen.getByRole("listitem");
    expect(within(card).getByText(/판매\s*0\s*\/\s*0석/)).toBeInTheDocument();
  });

  it("좌석 집계가 없는 응답에서도 숫자 자리를 비워두지 않는다", () => {
    // BE 목록 응답에서 좌석 집계가 빠졌던 실제 결함 형태. undefined가 그대로 렌더되면
    // "판매 / 석"이 되어 사용자는 판매 현황을 전혀 읽을 수 없다.
    const responseWithoutSeatCounts = buildListResponse();
    const eventsWithoutSeatCounts = responseWithoutSeatCounts.content.map((event) => {
      const { totalSeats: _totalSeats, soldSeats: _soldSeats, ...rest } = event;
      return rest;
    });

    mockUseEvents.mockReturnValue({
      data: { ...responseWithoutSeatCounts, content: eventsWithoutSeatCounts },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    render(<EventsListClient />);

    const card = screen.getByRole("listitem");
    expect(within(card).queryByText(/판매\s*\/\s*석/)).not.toBeInTheDocument();
  });

  it("불러오는 중에는 로딩 상태를 알린다", () => {
    mockUseEvents.mockReturnValue({
      data: null,
      isLoading: true,
      error: null,
      refetch: vi.fn(),
    });

    render(<EventsListClient />);

    expect(screen.getByText("불러오는 중...")).toBeInTheDocument();
  });

  it("조회 실패 시 오류 메시지와 재시도 버튼을 보여준다", () => {
    mockUseEvents.mockReturnValue({
      data: null,
      isLoading: false,
      error: "경기 목록을 불러오지 못했습니다.",
      refetch: vi.fn(),
    });

    render(<EventsListClient />);

    expect(screen.getByRole("alert")).toHaveTextContent("경기 목록을 불러오지 못했습니다.");
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("등록된 경기가 없으면 빈 상태 문구를 보여준다", () => {
    mockUseEvents.mockReturnValue({
      data: { content: [], totalPages: 0, totalElements: 0 },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    render(<EventsListClient />);

    expect(screen.getByText("등록된 경기가 없습니다.")).toBeInTheDocument();
  });
});
