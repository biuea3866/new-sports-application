// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, within } from "@testing-library/react";
import type { FeatureFlagResponse } from "@/lib/admin/feature-flags/schemas";

vi.mock("next/navigation", () => ({
  useRouter: vi.fn(),
}));

vi.mock("@/lib/admin/feature-flags/hooks", () => ({
  useFeatureFlags: vi.fn(),
}));

const FLAGS: FeatureFlagResponse[] = [
  {
    id: 1,
    key: "demo.feature.hello",
    type: "RELEASE",
    status: "ACTIVE",
    description: "데모 인사 엔드포인트 킬스위치",
    strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
    createdAt: "2026-07-01T00:00:00Z",
    updatedAt: "2026-07-01T00:00:00Z",
  },
  {
    id: 2,
    key: "old.experiment",
    type: "EXPERIMENT",
    status: "ARCHIVED",
    description: "종료된 실험",
    strategy: {
      strategyType: "VARIANT_BUCKETING",
      variants: [
        { name: "A", weight: 50 },
        { name: "B", weight: 50 },
      ],
    },
    createdAt: "2026-06-01T00:00:00Z",
    updatedAt: "2026-06-01T00:00:00Z",
  },
];

describe("FeatureFlagsPage", () => {
  const mockPush = vi.fn();

  beforeEach(async () => {
    vi.clearAllMocks();
    const { useRouter } = await import("next/navigation");
    vi.mocked(useRouter).mockReturnValue({ push: mockPush } as unknown as ReturnType<
      typeof useRouter
    >);
  });

  it("로딩 중이면 로딩 표시가 나타난다", async () => {
    const { useFeatureFlags } = await import("@/lib/admin/feature-flags/hooks");
    vi.mocked(useFeatureFlags).mockReturnValue({
      data: null,
      isLoading: true,
      error: null,
      refetch: vi.fn(),
    });

    const { default: FeatureFlagsPage } = await import("../page");
    render(<FeatureFlagsPage />);

    expect(screen.getByText("불러오는 중...")).toBeInTheDocument();
  });

  it("플래그 0건이면 빈 상태 문구와 플래그 추가 CTA가 보인다", async () => {
    const { useFeatureFlags } = await import("@/lib/admin/feature-flags/hooks");
    vi.mocked(useFeatureFlags).mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    const { default: FeatureFlagsPage } = await import("../page");
    render(<FeatureFlagsPage />);

    expect(screen.getByText("등록된 플래그가 없습니다")).toBeInTheDocument();
    const ctaLinks = screen.getAllByRole("link", { name: /플래그 추가/ });
    expect(ctaLinks.length).toBeGreaterThan(0);
  });

  it("필터 결과가 0건이면 전체 빈 상태와 다른 문구가 보인다", async () => {
    const { useFeatureFlags } = await import("@/lib/admin/feature-flags/hooks");
    vi.mocked(useFeatureFlags).mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    const { default: FeatureFlagsPage } = await import("../page");
    render(<FeatureFlagsPage />);

    fireEvent.change(screen.getByLabelText("상태"), { target: { value: "ARCHIVED" } });

    expect(screen.getByText("조건에 맞는 플래그가 없습니다.")).toBeInTheDocument();
    expect(screen.queryByText("등록된 플래그가 없습니다")).not.toBeInTheDocument();
  });

  it("오류 시 alert 배너와 다시 시도 버튼이 보이고, 클릭 시 refetch한다", async () => {
    const mockRefetch = vi.fn();
    const { useFeatureFlags } = await import("@/lib/admin/feature-flags/hooks");
    vi.mocked(useFeatureFlags).mockReturnValue({
      data: null,
      isLoading: false,
      error: "피처 플래그 목록을 불러오지 못했습니다.",
      refetch: mockRefetch,
    });

    const { default: FeatureFlagsPage } = await import("../page");
    render(<FeatureFlagsPage />);

    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("피처 플래그 목록을 불러오지 못했습니다.");

    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(mockRefetch).toHaveBeenCalledTimes(1);
  });

  it("데이터가 있으면 각 행에 key·상태 배지·전략 요약이 렌더된다", async () => {
    const { useFeatureFlags } = await import("@/lib/admin/feature-flags/hooks");
    vi.mocked(useFeatureFlags).mockReturnValue({
      data: FLAGS,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    const { default: FeatureFlagsPage } = await import("../page");
    render(<FeatureFlagsPage />);

    const table = screen.getByRole("table");
    expect(within(table).getByText("demo.feature.hello")).toBeInTheDocument();
    expect(within(table).getByText("ACTIVE")).toBeInTheDocument();
    expect(within(table).getByText("전역 ON")).toBeInTheDocument();
    expect(within(table).getByText("old.experiment")).toBeInTheDocument();
    expect(within(table).getByText("ARCHIVED")).toBeInTheDocument();
  });

  it("status 필터를 ARCHIVED로 바꾸면 해당 필터로 재조회한다", async () => {
    const { useFeatureFlags } = await import("@/lib/admin/feature-flags/hooks");
    vi.mocked(useFeatureFlags).mockReturnValue({
      data: FLAGS,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    const { default: FeatureFlagsPage } = await import("../page");
    render(<FeatureFlagsPage />);

    fireEvent.change(screen.getByLabelText("상태"), { target: { value: "ARCHIVED" } });

    const lastCall = vi.mocked(useFeatureFlags).mock.calls.at(-1);
    expect(lastCall?.[0]).toEqual({ status: "ARCHIVED", type: undefined });
  });

  it("행 클릭 시 상세 경로로 이동한다", async () => {
    const { useFeatureFlags } = await import("@/lib/admin/feature-flags/hooks");
    vi.mocked(useFeatureFlags).mockReturnValue({
      data: FLAGS,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    const { default: FeatureFlagsPage } = await import("../page");
    render(<FeatureFlagsPage />);

    fireEvent.click(screen.getByText("demo.feature.hello"));

    expect(mockPush).toHaveBeenCalledWith("/admin/feature-flags/demo.feature.hello");
  });
});
