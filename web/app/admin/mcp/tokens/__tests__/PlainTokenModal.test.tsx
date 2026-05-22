// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { PlainTokenModal } from "../_components/PlainTokenModal";

describe("PlainTokenModal", () => {
  const defaultProps = {
    open: true,
    tokenName: "테스트 토큰",
    plainToken: "mcp_test_secret_abc123",
    onClose: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("[U-01] open=true 이면 모달이 렌더링된다", () => {
    render(<PlainTokenModal {...defaultProps} />);
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("[U-02] 평문 토큰이 읽기 전용 textarea에 표시된다", () => {
    render(<PlainTokenModal {...defaultProps} />);
    const textarea = screen.getByLabelText<HTMLTextAreaElement>("발급된 MCP 토큰 (읽기 전용)");
    expect(textarea.value).toBe("mcp_test_secret_abc123");
    expect(textarea).toHaveAttribute("readonly");
  });

  it("[U-03] 닫기 버튼 클릭 시 onClose 콜백이 호출된다", () => {
    render(<PlainTokenModal {...defaultProps} />);
    fireEvent.click(screen.getByLabelText("토큰 모달 닫기"));
    expect(defaultProps.onClose).toHaveBeenCalledTimes(1);
  });

  it("[U-04] open=false 이면 모달이 렌더링되지 않는다", () => {
    render(<PlainTokenModal {...defaultProps} open={false} />);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("[U-05] 1회 노출 경고 문구가 표시된다", () => {
    render(<PlainTokenModal {...defaultProps} />);
    expect(screen.getByText(/이 화면을 닫으면 토큰을 다시 확인할 수 없습니다/)).toBeInTheDocument();
  });

  it("[U-06] 토큰 이름이 제목에 표시된다", () => {
    render(<PlainTokenModal {...defaultProps} />);
    expect(screen.getByRole("heading")).toHaveTextContent("테스트 토큰");
  });
});
