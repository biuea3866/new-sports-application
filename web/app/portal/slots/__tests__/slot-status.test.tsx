// @vitest-environment jsdom
/**
 * /portal/slots — 슬롯 open/close(W-SL) 테스트.
 * 기존 슬롯 CRUD 달력 위에 상태 배지 + close/open 액션이 추가됐는지 검증한다.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import * as React from "react";

const mockAddToast = vi.fn();
vi.mock("@/components/ui/toast", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/components/ui/toast")>();
  return {
    ...original,
    useToast: () => ({ addToast: mockAddToast, toasts: [], removeToast: vi.fn() }),
  };
});

const mockFetch = vi.fn();

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function toUrlString(input: RequestInfo | URL): string {
  if (typeof input === "string") return input;
  if (input instanceof URL) return input.toString();
  return input.url;
}

function formatDate(date: Date): string {
  const y = date.getFullYear();
  const mo = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${mo}-${d}`;
}

const today = new Date();
const todayDateString = `${formatDate(today)}T00:00:00Z`;

function baseSlot(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    facilityId: "fac-1",
    date: todayDateString,
    timeRange: "14:00-15:00",
    capacity: 8,
    ownerId: 1,
    status: "OPEN",
    programId: null,
    ...overrides,
  };
}

import SlotsPage from "../page";

describe("슬롯 open/close", () => {
  let slotsState: ReturnType<typeof baseSlot>[];
  let closeShouldConflict: boolean;

  beforeEach(() => {
    slotsState = [baseSlot()];
    closeShouldConflict = false;
    mockAddToast.mockReset();
    mockFetch.mockReset();
    vi.stubGlobal("fetch", mockFetch);

    mockFetch.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
      const url = toUrlString(input);
      const method = init?.method ?? "GET";

      if (url.includes("/api/portal/facilities?")) {
        return Promise.resolve(jsonResponse({ content: [{ id: "fac-1", name: "테스트 시설" }] }));
      }
      if (url.includes("/api/portal/slots/fac-1") && method === "GET") {
        return Promise.resolve(jsonResponse(slotsState));
      }
      if (url.includes("/slots/1/close") && method === "PATCH") {
        if (closeShouldConflict) {
          return Promise.resolve(jsonResponse({ message: "이미 처리된 슬롯입니다." }, 409));
        }
        slotsState = slotsState.map((s) => (s.id === 1 ? { ...s, status: "CLOSED" } : s));
        return Promise.resolve(jsonResponse(slotsState[0]));
      }
      if (url.includes("/slots/1/open") && method === "PATCH") {
        slotsState = slotsState.map((s) => (s.id === 1 ? { ...s, status: "OPEN" } : s));
        return Promise.resolve(jsonResponse(slotsState[0]));
      }

      return Promise.reject(new Error(`unexpected fetch: ${method} ${url}`));
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("OPEN 슬롯에는 상태 배지와 마감 버튼이 노출된다", async () => {
    render(<SlotsPage />);

    await waitFor(() => {
      expect(screen.getByText("OPEN")).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: /마감/ })).toBeInTheDocument();
  });

  it("마감 버튼을 클릭하면 확인 다이얼로그가 노출된다", async () => {
    render(<SlotsPage />);

    await waitFor(() => {
      expect(screen.getByText("OPEN")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /마감/ }));

    await waitFor(() => {
      expect(screen.getByText(/신규 예약만 차단/)).toBeInTheDocument();
    });
  });

  it("확인 다이얼로그에서 마감을 확정하면 상태가 CLOSED로 전환된다", async () => {
    render(<SlotsPage />);

    await waitFor(() => {
      expect(screen.getByText("OPEN")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /마감/ }));

    await waitFor(() => {
      expect(screen.getByText(/신규 예약만 차단/)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "마감" }));

    await waitFor(() => {
      expect(screen.getByText("CLOSED")).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: /오픈/ })).toBeInTheDocument();
  });

  it("마감 요청이 409로 실패하면 오류 토스트를 표시한다", async () => {
    closeShouldConflict = true;
    render(<SlotsPage />);

    await waitFor(() => {
      expect(screen.getByText("OPEN")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /마감/ }));

    await waitFor(() => {
      expect(screen.getByText(/신규 예약만 차단/)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "마감" }));

    await waitFor(() => {
      expect(mockAddToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: "이미 처리된 슬롯입니다.", variant: "destructive" })
      );
    });
  });
});
