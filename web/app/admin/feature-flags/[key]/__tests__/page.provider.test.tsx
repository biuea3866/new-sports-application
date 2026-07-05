// @vitest-environment jsdom
/**
 * S3 플래그 수정 화면 — ToastProvider 부재로 인한 마운트 시 크래시 회귀 테스트.
 * `@/components/ui/toast`를 모킹하지 않고 실제 ToastProvider를 사용해,
 * useToast()가 Provider 없이 throw하는 회귀를 재현·차단한다.
 * hooks(useFeatureFlag)·api만 모킹해 정상 플래그 데이터를 반환시킨다.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";

const mockParams: { key: string } = { key: "demo.feature.hello" };

vi.mock("next/navigation", () => ({
  useParams: () => mockParams,
}));

const mockRefetch = vi.fn();

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

vi.mock("@/lib/admin/feature-flags/hooks", () => ({
  useFeatureFlag: () => ({
    data: ACTIVE_FLAG,
    isLoading: false,
    error: null,
    refetch: mockRefetch,
  }),
}));

vi.mock("@/lib/admin/feature-flags/api", () => ({
  updateFeatureFlag: vi.fn(),
  archiveFeatureFlag: vi.fn(),
  activateFeatureFlag: vi.fn(),
}));

describe("[key]/page — ToastProvider 없이도 크래시하지 않는다", () => {
  beforeEach(() => {
    mockRefetch.mockReset();
  });

  it("실제 ToastProvider 환경에서 렌더링해도 throw 없이 플래그 key 헤딩이 보인다", async () => {
    const { default: EditFeatureFlagPage } = await import("../page");

    expect(() => render(<EditFeatureFlagPage />)).not.toThrow();

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "demo.feature.hello" })).toBeInTheDocument();
    });
  });
});
