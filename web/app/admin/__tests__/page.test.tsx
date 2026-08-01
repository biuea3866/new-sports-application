// @vitest-environment jsdom
/**
 * 어드민 홈(01 캡쳐) 다크 모드 회귀 테스트.
 *
 * 페이지 제목과 카드 제목이 `text-gray-900`로 하드코딩돼 다크 모드에서 배경과 같은
 * 어두운 남색으로 렌더돼 판독 불가였다. 본문 설명글만 보이던 증상의 원인이다.
 */
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import AdminHome from "../page";

const PALETTE_CLASS = /\b(?:text|bg|border)-(?:gray|slate|zinc|neutral|white|black)(?:-\d{2,3})?\b/;

describe("AdminHome", () => {
  it("페이지 제목이 시맨틱 전경색 토큰으로 렌더된다", () => {
    render(<AdminHome />);

    const heading = screen.getByRole("heading", { name: "어드민 홈", level: 1 });
    expect(heading.className).toMatch(/text-foreground/);
    expect(heading.className).not.toMatch(PALETTE_CLASS);
  });

  it("카드 제목 3개가 모두 시맨틱 토큰으로 렌더된다", () => {
    render(<AdminHome />);

    const cardTitles = screen.getAllByRole("heading", { level: 2 });
    expect(cardTitles).toHaveLength(3);
    for (const title of cardTitles) {
      expect(title.className).toMatch(/text-card-foreground/);
      expect(title.className).not.toMatch(PALETTE_CLASS);
    }
  });

  it("카드 컨테이너가 card·border 토큰을 사용한다", () => {
    render(<AdminHome />);

    const cardLink = screen.getByRole("link", { name: /MCP 토큰 관리/ });
    expect(cardLink.className).toMatch(/bg-card/);
    expect(cardLink.className).toMatch(/border-border/);
    expect(cardLink.className).not.toMatch(PALETTE_CLASS);
  });

  it("설명 문구는 muted-foreground 토큰을 쓴다", () => {
    render(<AdminHome />);

    const description = screen.getByText(/B2B MCP Server MVP/);
    expect(description.className).toMatch(/text-muted-foreground/);
  });
});
