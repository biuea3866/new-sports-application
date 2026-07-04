// @vitest-environment jsdom
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import type { FeatureFlagResponse } from "@/lib/admin/feature-flags/schemas";
import { FeatureFlagTable } from "../FeatureFlagTable";

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

describe("FeatureFlagTable", () => {
  it("각 행에 key·상태 배지·전략 요약이 렌더된다", () => {
    render(<FeatureFlagTable flags={FLAGS} onRowClick={vi.fn()} />);

    expect(screen.getByText("demo.feature.hello")).toBeInTheDocument();
    expect(screen.getByText("ACTIVE")).toBeInTheDocument();
    expect(screen.getByText("전역 ON")).toBeInTheDocument();

    expect(screen.getByText("old.experiment")).toBeInTheDocument();
    expect(screen.getByText("ARCHIVED")).toBeInTheDocument();
    expect(screen.getByText("A:50, B:50")).toBeInTheDocument();
  });

  it("행을 클릭하면 onRowClick이 해당 flag key로 호출된다", () => {
    const onRowClick = vi.fn();
    render(<FeatureFlagTable flags={FLAGS} onRowClick={onRowClick} />);

    fireEvent.click(screen.getByText("demo.feature.hello"));

    expect(onRowClick).toHaveBeenCalledWith("demo.feature.hello");
  });

  it("flags가 빈 배열이면 행이 렌더되지 않는다", () => {
    render(<FeatureFlagTable flags={[]} onRowClick={vi.fn()} />);

    expect(screen.queryAllByRole("row")).toHaveLength(1); // 헤더 행만 존재
  });
});
