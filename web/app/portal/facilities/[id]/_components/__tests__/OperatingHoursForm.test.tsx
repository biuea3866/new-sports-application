// @vitest-environment jsdom
/**
 * OperatingHoursForm(W-OH) — 요일별 운영시간 폼 테스트.
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

import { OperatingHoursForm } from "../OperatingHoursForm";

describe("OperatingHoursForm", () => {
  beforeEach(() => {
    mockAddToast.mockReset();
    mockFetch.mockReset();
    vi.stubGlobal("fetch", mockFetch);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("요일별 입력을 수정 후 저장하면 PUT 호출과 성공 토스트를 표시한다", async () => {
    mockFetch.mockImplementation(() =>
      Promise.resolve(jsonResponse({ id: "fac-1", operatingHours: [], holidays: [] }))
    );

    render(<OperatingHoursForm facilityId="fac-1" />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "운영시간 저장" })).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText("월요일 오픈 시각"), { target: { value: "08:00" } });

    fireEvent.click(screen.getByRole("button", { name: "운영시간 저장" }));

    await waitFor(() => {
      expect(mockAddToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: "운영시간이 저장됐습니다." })
      );
    });

    const putCall = mockFetch.mock.calls.find(
      (call) => (call[1] as RequestInit | undefined)?.method === "PUT"
    );
    expect(putCall).toBeDefined();
    const body = JSON.parse((putCall?.[1] as RequestInit).body as string) as {
      operatingHours: { openTime: string }[];
    };
    expect(body.operatingHours[0]?.openTime).toBe("08:00");
  });

  it("오픈 시각이 마감 시각보다 늦으면 인라인 오류를 표시하고 저장 요청을 보내지 않는다", async () => {
    mockFetch.mockResolvedValue(
      jsonResponse({ id: "fac-1", operatingHours: [], holidays: [] })
    );

    render(<OperatingHoursForm facilityId="fac-1" />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "운영시간 저장" })).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText("월요일 오픈 시각"), { target: { value: "20:00" } });
    fireEvent.change(screen.getByLabelText("월요일 마감 시각"), { target: { value: "09:00" } });

    fireEvent.click(screen.getByRole("button", { name: "운영시간 저장" }));

    await waitFor(() => {
      expect(screen.getByText("오픈 시각은 마감 시각보다 빨라야 합니다.")).toBeInTheDocument();
    });

    expect(
      mockFetch.mock.calls.some((call) => (call[1] as RequestInit | undefined)?.method === "PUT")
    ).toBe(false);
  });

  it("브레이크타임을 추가하고 삭제할 수 있다", async () => {
    mockFetch.mockResolvedValue(
      jsonResponse({ id: "fac-1", operatingHours: [], holidays: [] })
    );

    render(<OperatingHoursForm facilityId="fac-1" />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "운영시간 저장" })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "월요일 브레이크타임 추가" }));

    expect(screen.getByLabelText("월요일 브레이크 1 시작")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "월요일 브레이크 1 삭제" }));

    expect(screen.queryByLabelText("월요일 브레이크 1 시작")).not.toBeInTheDocument();
  });

  it("BE가 403을 반환하면 권한 안내를 표시한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse({ message: "권한이 없습니다." }, 403));

    render(<OperatingHoursForm facilityId="fac-1" />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("권한이 없습니다.");
    });
  });
});
