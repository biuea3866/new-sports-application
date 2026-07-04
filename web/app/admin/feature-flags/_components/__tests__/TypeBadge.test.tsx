// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { TypeBadge } from "../TypeBadge";

describe("TypeBadge", () => {
  it.each([
    ["RELEASE"] as const,
    ["OPERATIONAL"] as const,
    ["EXPERIMENT"] as const,
    ["ENTITLEMENT"] as const,
  ])("%s 타입의 라벨을 렌더한다", (type) => {
    render(<TypeBadge type={type} />);

    expect(screen.getByText(type)).toBeInTheDocument();
  });

  it("색 남용 없이 중립 accent 토큰만 사용한다", () => {
    render(<TypeBadge type="EXPERIMENT" />);

    const badge = screen.getByText("EXPERIMENT");
    expect(badge.className).toMatch(/bg-accent/);
    expect(badge.className).toMatch(/text-accent-foreground/);
  });
});
