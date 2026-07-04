// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { StatusBadge } from "../StatusBadge";

describe("StatusBadge", () => {
  it("ACTIVE 전달 시 ACTIVE 텍스트와 success 계열 클래스로 렌더된다", () => {
    render(<StatusBadge status="ACTIVE" />);

    const badge = screen.getByText("ACTIVE");
    expect(badge).toBeInTheDocument();
    expect(badge.className).toMatch(/text-success/);
    expect(badge.className).toMatch(/bg-success/);
  });

  it("ARCHIVED 전달 시 muted 계열 클래스로 렌더된다", () => {
    render(<StatusBadge status="ARCHIVED" />);

    const badge = screen.getByText("ARCHIVED");
    expect(badge).toBeInTheDocument();
    expect(badge.className).toMatch(/bg-muted/);
    expect(badge.className).toMatch(/text-muted-foreground/);
  });
});
