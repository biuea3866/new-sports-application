// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import type { FeatureFlagAuditLogPageView } from "@/lib/admin/feature-flags/schemas";

vi.mock("next/navigation", () => ({
  useParams: vi.fn(),
}));

vi.mock("@/lib/admin/feature-flags/hooks", () => ({
  useFlagAuditLogs: vi.fn(),
}));

type AuditLogEntry = FeatureFlagAuditLogPageView["logs"][number];

const ARCHIVED_LOG: AuditLogEntry = {
  changeType: "ARCHIVED",
  actorUserId: 12,
  before: {
    key: "demo.feature.hello",
    type: "RELEASE",
    status: "ACTIVE",
    description: "데모 인사 엔드포인트 킬스위치",
    strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
  },
  after: {
    key: "demo.feature.hello",
    type: "RELEASE",
    status: "ARCHIVED",
    description: "데모 인사 엔드포인트 킬스위치",
    strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
  },
  occurredAt: "2026-07-03T14:20:00Z",
};

const CREATED_LOG: AuditLogEntry = {
  changeType: "CREATED",
  actorUserId: 12,
  before: null,
  after: {
    key: "demo.feature.hello",
    type: "RELEASE",
    status: "ACTIVE",
    description: "데모 인사 엔드포인트 킬스위치",
    strategy: { strategyType: "GLOBAL_TOGGLE", enabled: false },
  },
  occurredAt: "2026-07-03T09:50:00Z",
};

describe("FeatureFlagAuditLogsPage", () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    const { useParams } = await import("next/navigation");
    vi.mocked(useParams).mockReturnValue({ key: "demo.feature.hello" });
  });

  it("로딩 중이면 aria-busy 표시가 나타난다", async () => {
    const { useFlagAuditLogs } = await import("@/lib/admin/feature-flags/hooks");
    vi.mocked(useFlagAuditLogs).mockReturnValue({
      data: null,
      isLoading: true,
      error: null,
      refetch: vi.fn(),
    });

    const { default: FeatureFlagAuditLogsPage } = await import("../page");
    render(<FeatureFlagAuditLogsPage />);

    expect(screen.getByText("불러오는 중...")).toHaveAttribute("aria-busy", "true");
  });

  it("이력이 있으면 각 행에 변경 유형 배지와 before→after 요약이 렌더된다", async () => {
    const { useFlagAuditLogs } = await import("@/lib/admin/feature-flags/hooks");
    vi.mocked(useFlagAuditLogs).mockReturnValue({
      data: { logs: [ARCHIVED_LOG], total: 1, page: 0, size: 10, totalPages: 1 },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    const { default: FeatureFlagAuditLogsPage } = await import("../page");
    render(<FeatureFlagAuditLogsPage />);

    expect(screen.getByText("ARCHIVED")).toBeInTheDocument();
    expect(screen.getByText("#12")).toBeInTheDocument();
    expect(screen.getAllByText("전역 ON").length).toBeGreaterThan(0);
  });

  it("before가 null인 CREATED 행은 (없음) → 이후 요약으로 표시된다", async () => {
    const { useFlagAuditLogs } = await import("@/lib/admin/feature-flags/hooks");
    vi.mocked(useFlagAuditLogs).mockReturnValue({
      data: { logs: [CREATED_LOG], total: 1, page: 0, size: 10, totalPages: 1 },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    const { default: FeatureFlagAuditLogsPage } = await import("../page");
    render(<FeatureFlagAuditLogsPage />);

    expect(screen.getByText("CREATED")).toBeInTheDocument();
    expect(screen.getByText("(없음)")).toBeInTheDocument();
    expect(screen.getByText("전역 OFF")).toBeInTheDocument();
  });

  it("이력 0건이면 변경 이력이 없습니다 문구가 보인다", async () => {
    const { useFlagAuditLogs } = await import("@/lib/admin/feature-flags/hooks");
    vi.mocked(useFlagAuditLogs).mockReturnValue({
      data: { logs: [], total: 0, page: 0, size: 10, totalPages: 0 },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    const { default: FeatureFlagAuditLogsPage } = await import("../page");
    render(<FeatureFlagAuditLogsPage />);

    expect(screen.getByText("변경 이력이 없습니다")).toBeInTheDocument();
  });

  it("오류 시 alert와 다시 시도 버튼이 보이고 클릭 시 refetch한다", async () => {
    const mockRefetch = vi.fn();
    const { useFlagAuditLogs } = await import("@/lib/admin/feature-flags/hooks");
    vi.mocked(useFlagAuditLogs).mockReturnValue({
      data: null,
      isLoading: false,
      error: "변경 이력을 불러오지 못했습니다.",
      refetch: mockRefetch,
    });

    const { default: FeatureFlagAuditLogsPage } = await import("../page");
    render(<FeatureFlagAuditLogsPage />);

    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("변경 이력을 불러오지 못했습니다.");

    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(mockRefetch).toHaveBeenCalledTimes(1);
  });

  it("total 기반 총페이지가 1 / N으로 표시된다(total 25·size 10 → 1 / 3)", async () => {
    const { useFlagAuditLogs } = await import("@/lib/admin/feature-flags/hooks");
    vi.mocked(useFlagAuditLogs).mockReturnValue({
      data: { logs: [ARCHIVED_LOG], total: 25, page: 0, size: 10, totalPages: 3 },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    const { default: FeatureFlagAuditLogsPage } = await import("../page");
    render(<FeatureFlagAuditLogsPage />);

    expect(screen.getByText("1 / 3")).toBeInTheDocument();
  });

  it("마지막 페이지에서 다음 버튼이 비활성된다", async () => {
    const { useFlagAuditLogs } = await import("@/lib/admin/feature-flags/hooks");
    vi.mocked(useFlagAuditLogs).mockReturnValue({
      data: { logs: [ARCHIVED_LOG], total: 25, page: 0, size: 10, totalPages: 3 },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    const { default: FeatureFlagAuditLogsPage } = await import("../page");
    render(<FeatureFlagAuditLogsPage />);

    fireEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
    fireEvent.click(screen.getByRole("button", { name: "다음 페이지" }));

    expect(screen.getByText("3 / 3")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다음 페이지" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "이전 페이지" })).not.toBeDisabled();
  });

  it("다음/이전 페이지 버튼으로 페이지를 이동한다", async () => {
    const { useFlagAuditLogs } = await import("@/lib/admin/feature-flags/hooks");
    vi.mocked(useFlagAuditLogs).mockReturnValue({
      data: { logs: [ARCHIVED_LOG], total: 25, page: 0, size: 10, totalPages: 3 },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    const { default: FeatureFlagAuditLogsPage } = await import("../page");
    render(<FeatureFlagAuditLogsPage />);

    fireEvent.click(screen.getByRole("button", { name: "다음 페이지" }));

    const lastCall = vi.mocked(useFlagAuditLogs).mock.calls.at(-1);
    expect(lastCall?.[1]).toBe(1);

    fireEvent.click(screen.getByRole("button", { name: "이전 페이지" }));
    const secondLastCall = vi.mocked(useFlagAuditLogs).mock.calls.at(-1);
    expect(secondLastCall?.[1]).toBe(0);
  });
});
