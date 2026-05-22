// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

function makeTokensResponse(ids: number[] = [1]) {
  return new Response(
    JSON.stringify({
      tokens: ids.map((id) => ({
        tokenId: id,
        name: `토큰-${id}`,
        status: "ACTIVE",
        expiresAt: null,
        lastUsedAt: null,
        createdAt: "2026-01-01T00:00:00Z",
      })),
    }),
    { status: 200, headers: { "Content-Type": "application/json" } }
  );
}

describe("McpTokensPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue(makeTokensResponse());
  });

  it("[U-01] 발급 후 fetchTokens 재호출로 목록이 갱신된다", async () => {
    const { default: McpTokensPage } = await import("../page");
    render(<McpTokensPage />);

    await waitFor(() => {
      expect(screen.getByText("토큰-1")).toBeInTheDocument();
    });

    mockFetch.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          tokenId: 2,
          name: "새 토큰",
          plainToken: "mcp_plain_abc",
          status: "ACTIVE",
          expiresAt: null,
          createdAt: "2026-01-02T00:00:00Z",
        }),
        { status: 201, headers: { "Content-Type": "application/json" } }
      )
    );
    mockFetch.mockResolvedValueOnce(makeTokensResponse([1, 2]));

    const nameInput = screen.getByLabelText<HTMLInputElement>(/토큰 이름/, { exact: false });
    fireEvent.change(nameInput, { target: { value: "새 토큰" } });

    const scopeCheckbox = screen.getByLabelText("scope read:facility 선택");
    fireEvent.click(scopeCheckbox);

    const submitButton = screen.getByLabelText("MCP 토큰 발급");
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText("토큰-2")).toBeInTheDocument();
    });

    expect(mockFetch).toHaveBeenCalledTimes(3);
  });

  it("[U-02] 폐기 후 fetchTokens 재호출로 목록이 갱신된다", async () => {
    const { default: McpTokensPage } = await import("../page");
    render(<McpTokensPage />);

    await waitFor(() => {
      expect(screen.getByText("토큰-1")).toBeInTheDocument();
    });

    mockFetch.mockResolvedValueOnce(
      new Response(null, { status: 204 })
    );
    mockFetch.mockResolvedValueOnce(makeTokensResponse([]));

    const revokeButton = screen.getByLabelText("토큰-1 토큰 폐기");
    fireEvent.click(revokeButton);

    const confirmButton = screen.getByLabelText("토큰-1 토큰 폐기 확인");
    fireEvent.click(confirmButton);

    await waitFor(() => {
      expect(screen.getByText("발급된 토큰이 없습니다.")).toBeInTheDocument();
    });
  });

  it("[U-03] 모달 닫힘 후 issuedToken이 null이 되어 재오픈되지 않는다", async () => {
    const { default: McpTokensPage } = await import("../page");
    render(<McpTokensPage />);

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    mockFetch.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          tokenId: 2,
          name: "임시 토큰",
          plainToken: "mcp_plain_xyz",
          status: "ACTIVE",
          expiresAt: null,
          createdAt: "2026-01-02T00:00:00Z",
        }),
        { status: 201, headers: { "Content-Type": "application/json" } }
      )
    );
    mockFetch.mockResolvedValueOnce(makeTokensResponse([1, 2]));

    const nameInput = screen.getByLabelText<HTMLInputElement>(/토큰 이름/, { exact: false });
    fireEvent.change(nameInput, { target: { value: "임시 토큰" } });

    const scopeCheckbox = screen.getByLabelText("scope read:facility 선택");
    fireEvent.click(scopeCheckbox);

    const submitButton = screen.getByLabelText("MCP 토큰 발급");
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });

    const closeButton = screen.getByLabelText("토큰 모달 닫기");
    fireEvent.click(closeButton);

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });
});
