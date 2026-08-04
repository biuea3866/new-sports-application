// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { StatusBadge } from "../StatusBadge";

describe("StatusBadge", () => {
  it("ACTIVE 전달 시 활성 라벨과 success 계열 클래스로 렌더된다", () => {
    render(<StatusBadge status="ACTIVE" />);

    const badge = screen.getByText("활성");
    expect(badge).toBeInTheDocument();
    expect(badge.className).toMatch(/text-success/);
    expect(badge.className).toMatch(/bg-success/);
  });

  it("ARCHIVED 전달 시 아카이브됨 라벨과 muted 계열 클래스로 렌더된다", () => {
    render(<StatusBadge status="ARCHIVED" />);

    const badge = screen.getByText("아카이브됨");
    expect(badge).toBeInTheDocument();
    expect(badge.className).toMatch(/bg-muted/);
    expect(badge.className).toMatch(/text-muted-foreground/);
  });

  // 계약 밖 상태가 섞여도 그 행만 원문으로 남고 화면 전체가 죽으면 안 된다.
  it("모르는 상태 값은 원문을 그대로 표시하고 중립색으로 렌더된다", () => {
    render(<StatusBadge status="DELETED" />);

    const badge = screen.getByText("DELETED");
    expect(badge).toBeInTheDocument();
    expect(badge.className).toMatch(/bg-muted/);
    expect(badge.className).toMatch(/text-muted-foreground/);
  });
});
