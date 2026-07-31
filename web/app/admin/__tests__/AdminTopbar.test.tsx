// @vitest-environment jsdom
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { AdminTopbar } from "../_components/AdminTopbar";

describe("AdminTopbar", () => {
  it("[U-04] operatorName 이 제공되면 운영자 이름을 노출한다", () => {
    render(<AdminTopbar operatorName="홍길동" />);
    expect(screen.getByText("홍길동")).toBeInTheDocument();
  });

  it("[U-05] operatorName 이 없으면 미인증 표시", () => {
    render(<AdminTopbar />);
    expect(screen.getByText("미인증")).toBeInTheDocument();
  });

  it("[U-06] Sports Admin 로고는 /admin 으로 링크된다", () => {
    render(<AdminTopbar operatorName="홍길동" />);
    expect(screen.getByText("Sports Admin").closest("a")?.getAttribute("href")).toBe("/admin");
  });

  it("[U-07] 로그아웃 링크가 표시된다", () => {
    render(<AdminTopbar operatorName="홍길동" />);
    expect(screen.getByText("로그아웃").closest("a")?.getAttribute("href")).toBe("/admin/logout");
  });

  // 다크 모드 대비 회귀 — 상단바도 하드코딩 색 대신 시맨틱 토큰을 쓴다.
  it("상단바가 하드코딩 색 없이 시맨틱 토큰으로 렌더된다", () => {
    render(<AdminTopbar operatorName="홍길동" />);
    const header = screen.getByText("Sports Admin").closest("header");
    const className = header?.getAttribute("class") ?? "";
    expect(className).not.toMatch(/(gray|slate|zinc|neutral|white|black)(-\d+)?\b/);
    expect(className).toMatch(/(background|card|border)/);
  });

  it("상단바 로고·상태 텍스트도 시맨틱 토큰을 쓴다", () => {
    render(<AdminTopbar />);
    const logoClass = screen.getByText("Sports Admin").getAttribute("class") ?? "";
    const statusClass = screen.getByText("미인증").getAttribute("class") ?? "";
    expect(logoClass).not.toMatch(/(gray|slate|zinc|neutral|white|black)-\d/);
    expect(statusClass).not.toMatch(/(gray|slate|zinc|neutral|white|black)-\d/);
    expect(logoClass).toMatch(/foreground/);
    expect(statusClass).toMatch(/(muted|foreground)/);
  });
});
