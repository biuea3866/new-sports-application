// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TokenIssueForm } from "../_components/TokenIssueForm";
import type { IssueMcpTokenResponse } from "@/lib/admin/mcp/schemas";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("TokenIssueForm", () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it("[U-01] scope 체크박스 6개가 모두 렌더링된다", () => {
    render(<TokenIssueForm onIssued={vi.fn()} />);
    expect(screen.getByLabelText(/scope read:facility 선택/)).toBeInTheDocument();
    expect(screen.getByLabelText(/scope write:facility 선택/)).toBeInTheDocument();
    expect(screen.getByLabelText(/scope read:booking 선택/)).toBeInTheDocument();
    expect(screen.getByLabelText(/scope write:booking 선택/)).toBeInTheDocument();
    expect(screen.getByLabelText(/scope read:product 선택/)).toBeInTheDocument();
    expect(screen.getByLabelText(/scope write:product 선택/)).toBeInTheDocument();
  });

  it("[U-02] PII 토글이 렌더링된다", () => {
    render(<TokenIssueForm onIssued={vi.fn()} />);
    expect(screen.getByLabelText("PII 조회 권한 포함")).toBeInTheDocument();
  });

  it("[U-03] 비대화형 자동화 토글이 렌더링된다", () => {
    render(<TokenIssueForm onIssued={vi.fn()} />);
    expect(screen.getByLabelText("비대화형 자동화 권한 포함")).toBeInTheDocument();
  });

  it("[U-04] 이름 미입력 시 제출하면 에러 메시지가 표시된다", async () => {
    render(<TokenIssueForm onIssued={vi.fn()} />);
    // HTML required 속성을 우회하기 위해 form submit 이벤트를 직접 발생시킨다
    const form = document.querySelector("form");
    if (form) fireEvent.submit(form);
    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("토큰 이름을 입력해 주세요.");
    });
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it("[U-05] scope 미선택 시 제출하면 에러 메시지가 표시된다", async () => {
    render(<TokenIssueForm onIssued={vi.fn()} />);
    await userEvent.type(screen.getByLabelText(/토큰 이름/), "테스트");
    fireEvent.click(screen.getByLabelText("MCP 토큰 발급"));
    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("scope를 1개 이상 선택해 주세요.");
    });
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it("[U-06] 유효한 입력으로 제출하면 fetch를 호출하고 onIssued 콜백을 실행한다", async () => {
    const issuedResponse: IssueMcpTokenResponse = {
      tokenId: 1,
      name: "테스트 토큰",
      plainToken: "mcp_test_abc",
      status: "ACTIVE",
      expiresAt: null,
      createdAt: "2026-01-01T00:00:00Z",
    };
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify(issuedResponse), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      })
    );

    const onIssued = vi.fn();
    render(<TokenIssueForm onIssued={onIssued} />);

    await userEvent.type(screen.getByLabelText(/토큰 이름/), "테스트 토큰");
    await userEvent.click(screen.getByLabelText("scope read:facility 선택"));
    await userEvent.click(screen.getByLabelText("MCP 토큰 발급"));

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/admin/mcp/tokens",
        expect.objectContaining({ method: "POST" })
      );
      expect(onIssued).toHaveBeenCalledWith(issuedResponse);
    });
  });

  it("[U-07] 발급 성공 후 폼이 초기화된다", async () => {
    const issuedResponse: IssueMcpTokenResponse = {
      tokenId: 2,
      name: "자동화 토큰",
      plainToken: "mcp_abc_xyz",
      status: "ACTIVE",
      expiresAt: null,
      createdAt: "2026-01-01T00:00:00Z",
    };
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify(issuedResponse), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      })
    );

    render(<TokenIssueForm onIssued={vi.fn()} />);
    const nameInput = screen.getByLabelText<HTMLInputElement>(/토큰 이름/);

    await userEvent.type(nameInput, "자동화 토큰");
    await userEvent.click(screen.getByLabelText("scope read:booking 선택"));
    await userEvent.click(screen.getByLabelText("MCP 토큰 발급"));

    await waitFor(() => {
      expect(nameInput.value).toBe("");
    });
  });

  it("[U-08] BE 오류 응답 시 에러 메시지가 표시된다", async () => {
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ message: "권한이 없습니다." }), {
        status: 403,
        headers: { "Content-Type": "application/json" },
      })
    );

    render(<TokenIssueForm onIssued={vi.fn()} />);
    await userEvent.type(screen.getByLabelText(/토큰 이름/), "테스트 토큰");
    await userEvent.click(screen.getByLabelText("scope read:facility 선택"));
    await userEvent.click(screen.getByLabelText("MCP 토큰 발급"));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("권한이 없습니다.");
    });
  });
});
