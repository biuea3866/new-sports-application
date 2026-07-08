// @vitest-environment jsdom
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import McpDocsPage from "../page";

describe("McpDocsPage", () => {
  it("[U-01] 페이지 제목 'MCP 사용 가이드'를 렌더링한다", () => {
    render(<McpDocsPage />);
    expect(
      screen.getByRole("heading", { name: "MCP 사용 가이드", level: 1 }),
    ).toBeInTheDocument();
  });

  it("[U-02] Claude Desktop 섹션을 렌더링한다", () => {
    render(<McpDocsPage />);
    expect(
      screen.getByRole("heading", { name: "Claude Desktop", level: 2 }),
    ).toBeInTheDocument();
  });

  it("[U-03] Cursor 섹션을 렌더링한다", () => {
    render(<McpDocsPage />);
    expect(
      screen.getByRole("heading", { name: "Cursor", level: 2 }),
    ).toBeInTheDocument();
  });

  it("[U-04] Continue.dev 섹션을 렌더링한다", () => {
    render(<McpDocsPage />);
    expect(
      screen.getByRole("heading", { name: "Continue.dev", level: 2 }),
    ).toBeInTheDocument();
  });

  it("[U-05] ChatGPT 섹션을 렌더링한다", () => {
    render(<McpDocsPage />);
    expect(
      screen.getByRole("heading", { name: "ChatGPT (OpenAI Actions)", level: 2 }),
    ).toBeInTheDocument();
  });

  it("[U-06] n8n 섹션을 렌더링한다", () => {
    render(<McpDocsPage />);
    expect(
      screen.getByRole("heading", { name: "n8n", level: 2 }),
    ).toBeInTheDocument();
  });

  it("[U-07] 외부 LLM 한계 고지 섹션을 렌더링한다", () => {
    render(<McpDocsPage />);
    expect(
      screen.getByRole("heading", { name: "외부 LLM 클라이언트 한계 고지", level: 2 }),
    ).toBeInTheDocument();
  });

  it("[U-08] MCP 토큰 관리 링크가 /admin/mcp/tokens를 가리킨다", () => {
    render(<McpDocsPage />);
    const link = screen.getByRole("link", { name: "MCP 토큰 관리" });
    expect(link).toHaveAttribute("href", "/admin/mcp/tokens");
  });

  it("[U-09] 클라이언트 섹션 바로가기 nav가 5개 항목을 가진다", () => {
    render(<McpDocsPage />);
    const nav = screen.getByRole("navigation", { name: "클라이언트 섹션 바로가기" });
    const links = nav.querySelectorAll("a");
    expect(links).toHaveLength(5);
  });

  it("[U-10] 각 클라이언트 섹션에 코드 블록이 존재한다", () => {
    render(<McpDocsPage />);
    const codeElements = document.querySelectorAll("pre > code");
    expect(codeElements.length).toBeGreaterThanOrEqual(5);
  });
});
