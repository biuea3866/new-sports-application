// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { TokenList } from "../_components/TokenList";
import type { McpTokenSummary } from "@/lib/admin/mcp/schemas";

const TOKENS: McpTokenSummary[] = [
  {
    tokenId: 1,
    name: "활성 토큰",
    status: "ACTIVE",
    expiresAt: null,
    lastUsedAt: "2026-05-01T10:00:00Z",
    createdAt: "2026-01-01T00:00:00Z",
  },
  {
    tokenId: 2,
    name: "폐기된 토큰",
    status: "REVOKED",
    expiresAt: null,
    lastUsedAt: null,
    createdAt: "2026-01-02T00:00:00Z",
  },
];

describe("TokenList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("[U-01] 토큰 목록이 테이블 행으로 렌더링된다", () => {
    render(<TokenList tokens={TOKENS} onRevoke={vi.fn()} />);
    expect(screen.getByText("활성 토큰")).toBeInTheDocument();
    expect(screen.getByText("폐기된 토큰")).toBeInTheDocument();
  });

  it("[U-02] 빈 목록이면 '발급된 토큰이 없습니다' 메시지를 표시한다", () => {
    render(<TokenList tokens={[]} onRevoke={vi.fn()} />);
    expect(screen.getByText("발급된 토큰이 없습니다.")).toBeInTheDocument();
  });

  it("[U-03] ACTIVE 상태 토큰에 폐기 버튼이 표시된다", () => {
    render(<TokenList tokens={TOKENS} onRevoke={vi.fn()} />);
    expect(screen.getByLabelText("활성 토큰 토큰 폐기")).toBeInTheDocument();
  });

  it("[U-04] REVOKED 상태 토큰에는 폐기 버튼이 없다", () => {
    render(<TokenList tokens={TOKENS} onRevoke={vi.fn()} />);
    expect(screen.queryByLabelText("폐기된 토큰 토큰 폐기")).not.toBeInTheDocument();
  });

  it("[U-05] 폐기 버튼 클릭 시 확인/취소 버튼이 나타난다", () => {
    render(<TokenList tokens={TOKENS} onRevoke={vi.fn()} />);
    fireEvent.click(screen.getByLabelText("활성 토큰 토큰 폐기"));
    expect(screen.getByLabelText("활성 토큰 토큰 폐기 확인")).toBeInTheDocument();
    expect(screen.getByLabelText("폐기 취소")).toBeInTheDocument();
  });

  it("[U-06] 확인 버튼 클릭 시 onRevoke(tokenId)가 호출된다", () => {
    const onRevoke = vi.fn();
    render(<TokenList tokens={TOKENS} onRevoke={onRevoke} />);
    fireEvent.click(screen.getByLabelText("활성 토큰 토큰 폐기"));
    fireEvent.click(screen.getByLabelText("활성 토큰 토큰 폐기 확인"));
    expect(onRevoke).toHaveBeenCalledWith(1);
  });

  it("[U-07] 취소 버튼 클릭 시 onRevoke가 호출되지 않는다", () => {
    const onRevoke = vi.fn();
    render(<TokenList tokens={TOKENS} onRevoke={onRevoke} />);
    fireEvent.click(screen.getByLabelText("활성 토큰 토큰 폐기"));
    fireEvent.click(screen.getByLabelText("폐기 취소"));
    expect(onRevoke).not.toHaveBeenCalled();
  });

  it("[U-08] 테이블에 aria-label이 설정된다", () => {
    render(<TokenList tokens={TOKENS} onRevoke={vi.fn()} />);
    expect(screen.getByRole("table")).toHaveAttribute("aria-label", "MCP 토큰 목록");
  });
});
