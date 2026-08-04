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
  it("각 행에 key·상태 배지·전략 요약이 한글 라벨로 렌더된다", () => {
    render(<FeatureFlagTable flags={FLAGS} onRowClick={vi.fn()} />);

    expect(screen.getByText("demo.feature.hello")).toBeInTheDocument();
    expect(screen.getByText("활성")).toBeInTheDocument();
    expect(screen.getByText("릴리즈")).toBeInTheDocument();
    expect(screen.getByText("전역 ON")).toBeInTheDocument();

    expect(screen.getByText("old.experiment")).toBeInTheDocument();
    expect(screen.getByText("아카이브됨")).toBeInTheDocument();
    expect(screen.getByText("실험")).toBeInTheDocument();
    expect(screen.getByText("A:50, B:50")).toBeInTheDocument();
  });

  // 계약 밖 status/type이 섞여도 그 행만 원문으로 남고 나머지 행은 정상 렌더돼야 한다
  // (재캡쳐 검수 후속 결함 #388과 동일 실패 모드 재발 방지).
  it("계약 밖 status·type 값이 섞여도 화면이 죽지 않고 모든 행이 렌더된다", () => {
    const flagsWithUnknownValue: FeatureFlagResponse[] = [
      ...FLAGS,
      {
        id: 3,
        key: "demo.feature.unknown",
        type: "LEGACY_TYPE",
        status: "DELETED",
        description: "계약 밖 값",
        strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
        createdAt: "2026-07-02T00:00:00Z",
        updatedAt: "2026-07-02T00:00:00Z",
      },
    ];

    render(<FeatureFlagTable flags={flagsWithUnknownValue} onRowClick={vi.fn()} />);

    expect(screen.getByText("demo.feature.hello")).toBeInTheDocument();
    expect(screen.getByText("old.experiment")).toBeInTheDocument();
    expect(screen.getByText("demo.feature.unknown")).toBeInTheDocument();
    expect(screen.getByText("DELETED")).toBeInTheDocument();
    expect(screen.getByText("LEGACY_TYPE")).toBeInTheDocument();
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
