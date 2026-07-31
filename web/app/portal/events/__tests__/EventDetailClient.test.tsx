// @vitest-environment jsdom
/**
 * 경기 상세 화면 — BE `/api/event-host/events/{id}` 응답을 그대로 받아 렌더한다.
 *
 * 회귀 방지: 응답 필드명이 화면 기대와 어긋나면(예: 좌석 목록 누락) 렌더 도중 예외로
 * 페이지 전체가 백지가 됐다. 실제 응답 형태로 렌더가 끝까지 도달하는지 검증한다.
 */
import { render, screen, within } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockUseEvent = vi.fn();
vi.mock("@/lib/portal/useEvent", () => ({
  useEvent: (id: string) => mockUseEvent(id) as unknown,
}));

import EventDetailClient from "../[id]/EventDetailClient";

const backendResponse = {
  id: 54,
  title: "2026 시티리그 4강 홈경기",
  venue: "잠실 실내체육관",
  startsAt: "2026-08-10T19:00:00+09:00",
  status: "OPEN" as const,
  totalSeats: 3,
  totalSold: 1,
  totalAvailable: 2,
  seats: [
    { id: 101, label: "R석 1열 R석-01", sold: false },
    { id: 102, label: "R석 1열 R석-02", sold: true },
    { id: 103, label: "S석 2열 S석-01", sold: false },
  ],
};

describe("EventDetailClient", () => {
  beforeEach(() => {
    mockUseEvent.mockReset();
  });

  it("BE 응답 필드로 판매 현황 수치를 표시한다", () => {
    mockUseEvent.mockReturnValue({
      data: backendResponse,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    render(<EventDetailClient id="54" />);

    expect(screen.getByText("2026 시티리그 4강 홈경기")).toBeTruthy();
    expect(screen.getByText("3석")).toBeTruthy(); // 총 좌석
    expect(screen.getByText("1석")).toBeTruthy(); // 판매
    expect(screen.getByText("2석")).toBeTruthy(); // 잔여
  });

  it("좌석별 판매 여부 표를 렌더한다", () => {
    mockUseEvent.mockReturnValue({
      data: backendResponse,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    render(<EventDetailClient id="54" />);

    const table = screen.getByLabelText("좌석별 판매 현황");
    expect(screen.getByText("R석 1열 R석-02")).toBeTruthy();
    expect(within(table).getAllByText("잔여").length).toBe(2);
    expect(within(table).getAllByText("판매됨").length).toBe(1);
  });

  it("좌석 목록이 비어 있어도 백지가 되지 않고 판매 현황을 렌더한다", () => {
    mockUseEvent.mockReturnValue({
      data: { ...backendResponse, totalSeats: 0, totalSold: 0, totalAvailable: 0, seats: [] },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    render(<EventDetailClient id="54" />);

    expect(screen.getByText("판매 현황")).toBeTruthy();
    expect(screen.queryByLabelText("좌석별 판매 현황")).toBeNull();
  });
});
