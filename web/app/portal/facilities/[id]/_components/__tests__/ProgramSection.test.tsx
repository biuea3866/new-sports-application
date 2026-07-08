// @vitest-environment jsdom
/**
 * ProgramSection(W-PG) — 시설상품 목록·등록 테스트.
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

const sampleProgram = {
  id: 1,
  facilityId: "fac-1",
  ownerUserId: 1,
  name: "PT 1:1",
  description: null,
  price: 50000,
  capacity: 1,
  durationMinutes: 60,
};

import { ProgramSection } from "../ProgramSection";

describe("ProgramSection", () => {
  beforeEach(() => {
    mockAddToast.mockReset();
    mockFetch.mockReset();
    vi.stubGlobal("fetch", mockFetch);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("등록된 상품이 없으면 안내 문구를 표시한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse([]));

    render(<ProgramSection facilityId="fac-1" />);

    await waitFor(() => {
      expect(screen.getByText("등록된 상품이 없어요")).toBeInTheDocument();
    });
  });

  it("시설상품 목록을 카드로 렌더한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse([sampleProgram]));

    render(<ProgramSection facilityId="fac-1" />);

    await waitFor(() => {
      expect(screen.getByText("PT 1:1")).toBeInTheDocument();
    });
    expect(screen.getByText("50,000원")).toBeInTheDocument();
  });

  it("상품을 등록하면 목록이 갱신된다", async () => {
    mockFetch.mockImplementation((_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === "POST") {
        return Promise.resolve(jsonResponse(sampleProgram, 201));
      }
      return Promise.resolve(jsonResponse([]));
    });

    render(<ProgramSection facilityId="fac-1" />);

    await waitFor(() => {
      expect(screen.getByText("등록된 상품이 없어요")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "시설상품 등록" }));

    await waitFor(() => {
      expect(screen.getByLabelText(/^이름/)).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText(/^이름/), { target: { value: "PT 1:1" } });
    fireEvent.change(screen.getByLabelText("가격"), { target: { value: "50000" } });
    fireEvent.change(screen.getByLabelText("정원"), { target: { value: "1" } });
    fireEvent.change(screen.getByLabelText("소요(분)"), { target: { value: "60" } });

    mockFetch.mockImplementation((_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === "POST") {
        return Promise.resolve(jsonResponse(sampleProgram, 201));
      }
      return Promise.resolve(jsonResponse([sampleProgram]));
    });

    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(screen.getByText("PT 1:1")).toBeInTheDocument();
    });
  });

  it("가격이 음수이거나 정원이 0이면 인라인 오류를 표시하고 등록 요청을 보내지 않는다", async () => {
    mockFetch.mockResolvedValue(jsonResponse([]));

    render(<ProgramSection facilityId="fac-1" />);

    await waitFor(() => {
      expect(screen.getByText("등록된 상품이 없어요")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "시설상품 등록" }));

    await waitFor(() => {
      expect(screen.getByLabelText(/^이름/)).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText(/^이름/), { target: { value: "PT 1:1" } });
    fireEvent.change(screen.getByLabelText("가격"), { target: { value: "-1000" } });
    fireEvent.change(screen.getByLabelText("정원"), { target: { value: "0" } });
    fireEvent.change(screen.getByLabelText("소요(분)"), { target: { value: "60" } });

    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(screen.getByText("가격은 0 이상이어야 합니다.")).toBeInTheDocument();
    });
    expect(screen.getByText("정원은 1 이상이어야 합니다.")).toBeInTheDocument();
    expect(
      mockFetch.mock.calls.some((call) => (call[1] as RequestInit | undefined)?.method === "POST")
    ).toBe(false);
  });
});
