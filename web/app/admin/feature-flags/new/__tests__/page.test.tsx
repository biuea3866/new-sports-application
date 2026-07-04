// @vitest-environment jsdom
/**
 * S2 생성 화면 컨테이너 — createFeatureFlag 연동·토스트·이동 시나리오.
 * 근거 티켓: FE-08-create-screen.md.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import * as React from "react";

const mockPush = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
}));

const mockAddToast = vi.fn();

// ToastProvider 없이 useToast를 쓰면 throw하므로 모킹 (facilities-scenario.test.tsx 선례)
vi.mock("@/components/ui/toast", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/components/ui/toast")>();
  return {
    ...original,
    useToast: () => ({ addToast: mockAddToast, toasts: [], removeToast: vi.fn() }),
    ToastProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  };
});

const mockFetch = vi.fn();

function fillRequiredFields(): void {
  fireEvent.change(screen.getByLabelText("Key"), { target: { value: "demo.feature.hello" } });
  fireEvent.change(screen.getByLabelText("설명"), {
    target: { value: "데모 인사 엔드포인트 킬스위치" },
  });
}

describe("NewFeatureFlagPage", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", mockFetch);
    mockFetch.mockReset();
    mockPush.mockReset();
    mockAddToast.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("유효 입력으로 생성 클릭 시 createFeatureFlag가 호출되고 성공 토스트 후 목록으로 이동한다", async () => {
    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 1,
          key: "demo.feature.hello",
          type: "RELEASE",
          status: "ACTIVE",
          description: "데모 인사 엔드포인트 킬스위치",
          strategy: { strategyType: "GLOBAL_TOGGLE", enabled: false },
          createdAt: "2026-07-03T00:00:00Z",
          updatedAt: "2026-07-03T00:00:00Z",
        }),
        { status: 201, headers: { "Content-Type": "application/json" } }
      )
    );

    const { default: NewFeatureFlagPage } = await import("../page");
    render(<NewFeatureFlagPage />);

    fillRequiredFields();
    fireEvent.click(screen.getByRole("button", { name: "생성" }));

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/admin/feature-flags",
        expect.objectContaining({ method: "POST" })
      );
      expect(mockAddToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: expect.stringContaining("생성") })
      );
      expect(mockPush).toHaveBeenCalledWith("/admin/feature-flags");
    });
  });

  it("key 중복 400 응답 시 폼 상단 alert 배너가 뜨고 화면 이동하지 않는다", async () => {
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ message: "이미 존재하는 key입니다." }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { default: NewFeatureFlagPage } = await import("../page");
    render(<NewFeatureFlagPage />);

    fillRequiredFields();
    fireEvent.click(screen.getByRole("button", { name: "생성" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("이미 존재하는 key입니다.");
    });
    expect(mockPush).not.toHaveBeenCalled();
  });
});
