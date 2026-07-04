// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { StrategySummary } from "../StrategySummary";
import type { FeatureFlagStrategy } from "@/lib/admin/feature-flags/schemas";

describe("StrategySummary", () => {
  it("PERCENTAGE_ROLLOUT 50을 50% 롤아웃 문자열로 표시한다", () => {
    const strategy: FeatureFlagStrategy = {
      strategyType: "PERCENTAGE_ROLLOUT",
      percentage: 50,
    };

    render(<StrategySummary strategy={strategy} />);

    expect(screen.getByText("50% 롤아웃")).toBeInTheDocument();
  });

  it("VARIANT_BUCKETING을 A:50, B:50 문자열로 표시한다", () => {
    const strategy: FeatureFlagStrategy = {
      strategyType: "VARIANT_BUCKETING",
      variants: [
        { name: "A", weight: 50 },
        { name: "B", weight: 50 },
      ],
    };

    render(<StrategySummary strategy={strategy} />);

    expect(screen.getByText("A:50, B:50")).toBeInTheDocument();
  });

  it("GLOBAL_TOGGLE enabled를 전역 ON 문자열로 표시한다", () => {
    const strategy: FeatureFlagStrategy = { strategyType: "GLOBAL_TOGGLE", enabled: true };

    render(<StrategySummary strategy={strategy} />);

    expect(screen.getByText("전역 ON")).toBeInTheDocument();
  });
});
