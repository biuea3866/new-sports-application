// @vitest-environment jsdom
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { AdminSidebar } from "../_components/AdminSidebar";

describe("AdminSidebar", () => {
  it("[U-01] MCP 메뉴 3개를 모두 렌더링한다", () => {
    render(<AdminSidebar />);
    expect(screen.getByRole("navigation")).toBeInTheDocument();
    expect(screen.getByText("MCP 토큰")).toBeInTheDocument();
    expect(screen.getByText("감사 로그")).toBeInTheDocument();
    expect(screen.getByText("MCP 사용 가이드")).toBeInTheDocument();
  });

  it("[U-02] 각 메뉴 항목이 올바른 href를 가진다", () => {
    render(<AdminSidebar />);
    expect(screen.getByText("MCP 토큰").closest("a")?.getAttribute("href")).toBe("/admin/mcp/tokens");
    expect(screen.getByText("감사 로그").closest("a")?.getAttribute("href")).toBe("/admin/mcp/audit-logs");
    expect(screen.getByText("MCP 사용 가이드").closest("a")?.getAttribute("href")).toBe("/admin/mcp/docs");
  });

  it("[U-03] aria-label 이 어드민 사이드바로 설정된다", () => {
    render(<AdminSidebar />);
    expect(screen.getByRole("complementary")).toHaveAttribute("aria-label", "어드민 사이드바");
  });

  it("피처 플래그 nav 항목이 렌더되고 /admin/feature-flags 로 링크된다", () => {
    render(<AdminSidebar />);
    const link = screen.getByRole("link", { name: /피처 플래그/ });
    expect(link).toHaveAttribute("href", "/admin/feature-flags");
  });

  it("기존 MCP nav 항목이 그대로 유지된다", () => {
    render(<AdminSidebar />);
    expect(screen.getByText("MCP 토큰")).toBeInTheDocument();
    expect(screen.getByText("MCP 사용 가이드")).toBeInTheDocument();
    expect(screen.getByText("이상 패턴")).toBeInTheDocument();
    expect(screen.getAllByRole("navigation")).toHaveLength(1);
  });

  it("피처 플래그 nav 항목이 하드코딩 색 없이 시맨틱 토큰으로 렌더된다", () => {
    render(<AdminSidebar />);
    const link = screen.getByRole("link", { name: /피처 플래그/ });
    const className = link.getAttribute("class") ?? "";
    expect(className).not.toMatch(/(gray|blue|red|green|slate|zinc|neutral|white|black)-\d/);
    expect(className).not.toMatch(/#[0-9a-fA-F]{3,6}/);
    expect(className).toMatch(/(foreground|accent|ring|muted|primary)/);
  });

  it("피처 플래그 nav 링크가 키보드 포커스 가능하다", () => {
    render(<AdminSidebar />);
    const link = screen.getByRole("link", { name: /피처 플래그/ });
    link.focus();
    expect(link).toHaveFocus();
  });
});
