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
});
