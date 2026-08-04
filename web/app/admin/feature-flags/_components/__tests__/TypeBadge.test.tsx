// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { TypeBadge } from "../TypeBadge";
import { FEATURE_FLAG_TYPE_LABELS } from "@/lib/admin/feature-flags/featureFlagType";

describe("TypeBadge", () => {
  it.each([
    ["RELEASE"] as const,
    ["OPERATIONAL"] as const,
    ["EXPERIMENT"] as const,
    ["ENTITLEMENT"] as const,
  ])("%s 타입의 한글 라벨을 렌더한다", (type) => {
    render(<TypeBadge type={type} />);

    expect(screen.getByText(FEATURE_FLAG_TYPE_LABELS[type])).toBeInTheDocument();
  });

  it("색 남용 없이 중립 accent 토큰만 사용한다", () => {
    render(<TypeBadge type="EXPERIMENT" />);

    const badge = screen.getByText(FEATURE_FLAG_TYPE_LABELS.EXPERIMENT);
    expect(badge.className).toMatch(/bg-accent/);
    expect(badge.className).toMatch(/text-accent-foreground/);
  });

  // 계약 밖 종류가 섞여도 그 행만 원문으로 남고 화면 전체가 죽으면 안 된다.
  it("모르는 종류 값은 원문을 그대로 표시한다", () => {
    render(<TypeBadge type="LEGACY_TYPE" />);

    expect(screen.getByText("LEGACY_TYPE")).toBeInTheDocument();
  });
});
