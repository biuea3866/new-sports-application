// @vitest-environment jsdom
/**
 * FacilityTabs — 시설 상세 탭(정보/운영시간/휴무일/시설상품) 통합 테스트.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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

import { FacilityTabs } from "../FacilityTabs";

describe("FacilityTabs", () => {
  beforeEach(() => {
    mockAddToast.mockReset();
    mockFetch.mockReset();
    vi.stubGlobal("fetch", mockFetch);
    mockFetch.mockImplementation((input: RequestInfo | URL) => {
      const url = toUrlString(input);
      if (url.includes("/programs")) {
        return Promise.resolve(jsonResponse([]));
      }
      return Promise.resolve(jsonResponse({ id: "fac-1", operatingHours: [], holidays: [] }));
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("기본으로 정보 탭 콘텐츠를 렌더한다", () => {
    render(<FacilityTabs facilityId="fac-1" infoContent={<p>정보 콘텐츠</p>} />);
    expect(screen.getByText("정보 콘텐츠")).toBeInTheDocument();
  });

  it("운영시간 탭을 클릭하면 운영시간 폼이 노출된다", async () => {
    const user = userEvent.setup();
    render(<FacilityTabs facilityId="fac-1" infoContent={<p>정보 콘텐츠</p>} />);

    await user.click(screen.getByRole("tab", { name: "운영시간" }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "운영시간 저장" })).toBeInTheDocument();
    });
  });

  it("시설상품 탭을 클릭하면 상품 섹션이 노출된다", async () => {
    const user = userEvent.setup();
    render(<FacilityTabs facilityId="fac-1" infoContent={<p>정보 콘텐츠</p>} />);

    await user.click(screen.getByRole("tab", { name: "시설상품" }));

    await waitFor(() => {
      expect(screen.getByText("등록된 상품이 없어요")).toBeInTheDocument();
    });
  });

  it("휴무일 탭을 클릭하면 휴무일 섹션이 노출된다", async () => {
    const user = userEvent.setup();
    render(<FacilityTabs facilityId="fac-1" infoContent={<p>정보 콘텐츠</p>} />);

    await user.click(screen.getByRole("tab", { name: "휴무일" }));

    await waitFor(() => {
      expect(screen.getByText("휴무일 없음")).toBeInTheDocument();
    });
  });
});
