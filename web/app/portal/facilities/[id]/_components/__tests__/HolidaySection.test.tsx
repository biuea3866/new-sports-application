// @vitest-environment jsdom
/**
 * HolidaySection(W-HD) — 휴무일 관리 테스트.
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

import { HolidaySection } from "../HolidaySection";

describe("HolidaySection", () => {
  beforeEach(() => {
    mockAddToast.mockReset();
    mockFetch.mockReset();
    vi.stubGlobal("fetch", mockFetch);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("휴무일이 없으면 안내 문구를 표시한다", async () => {
    mockFetch.mockResolvedValue(
      jsonResponse({ id: "fac-1", operatingHours: [], holidays: [] })
    );

    render(<HolidaySection facilityId="fac-1" />);

    await waitFor(() => {
      expect(screen.getByText("휴무일 없음")).toBeInTheDocument();
    });
  });

  it("날짜를 추가하면 칩으로 렌더된다", async () => {
    let holidays: string[] = [];
    mockFetch.mockImplementation((_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === "POST") {
        holidays = ["2026-07-15"];
      }
      return Promise.resolve(
        jsonResponse({ id: "fac-1", operatingHours: [], holidays })
      );
    });

    render(<HolidaySection facilityId="fac-1" />);

    await waitFor(() => {
      expect(screen.getByText("휴무일 없음")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "휴무일 추가" }));

    await waitFor(() => {
      expect(screen.getByLabelText("날짜")).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText("날짜"), { target: { value: "2026-07-15" } });
    fireEvent.click(screen.getByRole("button", { name: "추가" }));

    await waitFor(() => {
      expect(screen.getByText(/7\/15/)).toBeInTheDocument();
    });
  });

  it("칩의 삭제 버튼을 클릭하면 휴무일이 제거된다", async () => {
    let holidays: string[] = ["2026-07-15"];
    mockFetch.mockImplementation((_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === "DELETE") {
        holidays = [];
      }
      return Promise.resolve(
        jsonResponse({ id: "fac-1", operatingHours: [], holidays })
      );
    });

    render(<HolidaySection facilityId="fac-1" />);

    await waitFor(() => {
      expect(screen.getByText(/7\/15/)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /휴무일 삭제/ }));

    await waitFor(() => {
      expect(screen.getByText("휴무일 없음")).toBeInTheDocument();
    });
  });

  it("날짜 미입력 상태로 추가를 시도하면 인라인 오류를 표시한다", async () => {
    mockFetch.mockResolvedValue(
      jsonResponse({ id: "fac-1", operatingHours: [], holidays: [] })
    );

    render(<HolidaySection facilityId="fac-1" />);

    await waitFor(() => {
      expect(screen.getByText("휴무일 없음")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "휴무일 추가" }));

    await waitFor(() => {
      expect(screen.getByLabelText("날짜")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "추가" }));

    await waitFor(() => {
      expect(screen.getByText("날짜를 선택해 주세요.")).toBeInTheDocument();
    });
  });
});
