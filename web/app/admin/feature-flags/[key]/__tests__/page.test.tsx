// @vitest-environment jsdom
/**
 * S3 플래그 수정 화면(`[key]/page.tsx`) 시나리오 테스트.
 * 근거 티켓: `FE-09-edit-screen.md` 테스트 케이스 7건.
 * BFF(fetch) 레벨에서 모킹해 훅(useFeatureFlag) → api.ts → 화면까지 실제 통합 흐름을 검증한다.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, waitFor, fireEvent, act } from "@testing-library/react";
import * as React from "react";

const mockPush = vi.fn();
const mockParams: { key: string } = { key: "demo.feature.hello" };

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
  useParams: () => mockParams,
}));

// ToastProvider 없이 useToast를 쓰면 throw하므로 모킹 (레포 선례: facilities-scenario.test.tsx)
const mockAddToast = vi.fn();
vi.mock("@/components/ui/toast", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/components/ui/toast")>();
  return {
    ...original,
    useToast: () => ({ addToast: mockAddToast, toasts: [], removeToast: vi.fn() }),
    ToastProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  };
});

const mockFetch = vi.fn();

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const ACTIVE_FLAG = {
  id: 1,
  key: "demo.feature.hello",
  type: "RELEASE",
  status: "ACTIVE",
  description: "데모 인사 엔드포인트 킬스위치",
  strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
  createdAt: "2026-07-01T00:00:00.000Z",
  updatedAt: "2026-07-01T00:00:00.000Z",
};

const ARCHIVED_FLAG = {
  ...ACTIVE_FLAG,
  status: "ARCHIVED",
};

describe("[key]/page — S3 플래그 수정 화면", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", mockFetch);
    mockFetch.mockReset();
    mockAddToast.mockReset();
    mockPush.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("진입 시 기존 값(description/strategy)이 폼에 프리필된다", async () => {
    mockFetch.mockResolvedValue(jsonResponse(ACTIVE_FLAG));
    const { default: EditFeatureFlagPage } = await import("../page");

    render(<EditFeatureFlagPage />);

    await waitFor(() => {
      expect(screen.getByLabelText("설명")).toHaveValue("데모 인사 엔드포인트 킬스위치");
    });
    expect(screen.getByRole("switch")).toHaveAttribute("aria-checked", "true");
  });

  it("변경 저장 클릭 시 updateFeatureFlag(PUT)가 호출되고 저장 토스트가 뜬다", async () => {
    mockFetch.mockImplementation((url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      if (method === "PUT") {
        return jsonResponse({ ...ACTIVE_FLAG, description: "변경된 설명" });
      }
      return jsonResponse(ACTIVE_FLAG);
    });
    const { default: EditFeatureFlagPage } = await import("../page");

    render(<EditFeatureFlagPage />);

    await waitFor(() => {
      expect(screen.getByLabelText("설명")).toHaveValue("데모 인사 엔드포인트 킬스위치");
    });

    fireEvent.change(screen.getByLabelText("설명"), { target: { value: "변경된 설명" } });

    act(() => {
      fireEvent.click(screen.getByRole("button", { name: "변경 저장" }));
    });

    await waitFor(() => {
      const putCall = mockFetch.mock.calls.find(
        (call) => (call[1] as RequestInit | undefined)?.method === "PUT"
      );
      expect(putCall).toBeDefined();
      expect(putCall?.[0]).toBe("/api/admin/feature-flags/demo.feature.hello");
    });
    expect(mockAddToast).toHaveBeenCalledWith(
      expect.objectContaining({ title: expect.stringContaining("저장") })
    );
  });

  it("아카이브 클릭 시 archiveFeatureFlag가 호출되고 재활성 버튼이 노출된다", async () => {
    let archived = false;
    mockFetch.mockImplementation((url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      if (typeof url === "string" && url.endsWith("/archive") && method === "POST") {
        archived = true;
        return jsonResponse({ key: ACTIVE_FLAG.key, status: "ARCHIVED" });
      }
      return jsonResponse(archived ? ARCHIVED_FLAG : ACTIVE_FLAG);
    });
    const { default: EditFeatureFlagPage } = await import("../page");

    render(<EditFeatureFlagPage />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "아카이브" })).toBeInTheDocument();
    });

    act(() => {
      fireEvent.click(screen.getByRole("button", { name: "아카이브" }));
    });

    await waitFor(() => {
      expect(
        mockFetch.mock.calls.some(
          (call) =>
            typeof call[0] === "string" &&
            call[0].endsWith("/archive") &&
            (call[1] as RequestInit | undefined)?.method === "POST"
        )
      ).toBe(true);
    });
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "재활성" })).toBeInTheDocument();
    });
  });

  it("ARCHIVED 상태면 전략 입력이 비활성이고 저장 버튼이 없다", async () => {
    mockFetch.mockResolvedValue(jsonResponse(ARCHIVED_FLAG));
    const { default: EditFeatureFlagPage } = await import("../page");

    render(<EditFeatureFlagPage />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "재활성" })).toBeInTheDocument();
    });
    expect(screen.getByLabelText("설명")).toBeDisabled();
    expect(screen.getByRole("switch")).toBeDisabled();
    expect(screen.queryByRole("button", { name: "변경 저장" })).not.toBeInTheDocument();
  });

  it("404면 '찾을 수 없습니다' 빈 상태와 목록 링크가 보인다", async () => {
    mockFetch.mockResolvedValue(jsonResponse({ message: "플래그를 찾을 수 없습니다." }, 404));
    const { default: EditFeatureFlagPage } = await import("../page");

    render(<EditFeatureFlagPage />);

    await waitFor(() => {
      expect(screen.getByText(/찾을 수 없습니다/)).toBeInTheDocument();
    });
    const listLink = screen.getByRole("link", { name: /목록/ });
    expect(listLink).toHaveAttribute("href", "/admin/feature-flags");
  });

  it("409(이미 ARCHIVED) 응답 시 alert 배너가 뜬다", async () => {
    mockFetch.mockImplementation((url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      if (method === "PUT") {
        return jsonResponse({ message: "이미 ARCHIVED 상태인 플래그는 수정할 수 없습니다." }, 409);
      }
      return jsonResponse(ACTIVE_FLAG);
    });
    const { default: EditFeatureFlagPage } = await import("../page");

    render(<EditFeatureFlagPage />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "변경 저장" })).toBeInTheDocument();
    });

    act(() => {
      fireEvent.click(screen.getByRole("button", { name: "변경 저장" }));
    });

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("이미 ARCHIVED 상태인 플래그는 수정할 수 없습니다.");
    });
  });

  it("'변경 이력 보기' 클릭 시 audit-logs 경로로 이동한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse(ACTIVE_FLAG));
    const { default: EditFeatureFlagPage } = await import("../page");

    render(<EditFeatureFlagPage />);

    await waitFor(() => {
      expect(screen.getByRole("link", { name: "변경 이력 보기" })).toBeInTheDocument();
    });
    expect(screen.getByRole("link", { name: "변경 이력 보기" })).toHaveAttribute(
      "href",
      "/admin/feature-flags/demo.feature.hello/audit-logs"
    );
  });
});
