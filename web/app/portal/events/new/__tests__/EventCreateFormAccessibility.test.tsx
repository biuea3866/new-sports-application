// @vitest-environment jsdom
/**
 * 경기 등록 폼의 "경기 시작 시각" 접근성 계약.
 *
 * 회귀 방지: 네이티브 `datetime-local` 을 커스텀 입력으로 교체할 때 `aria-required` 와
 * **`aria-invalid`** 가 조용히 빠졌다. 이 필드는 필수이고 인라인 오류(`role="alert"`)를 띄우는데
 * `aria-invalid` 가 없으면 보조기술이 **어느 필드가 틀렸는지 알 수 없다.**
 */
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }),
}));

vi.mock("@/components/ui/toast", () => ({
  useToast: () => ({ addToast: vi.fn() }),
}));

import EventCreateForm from "../EventCreateForm";

describe("경기 등록 — 시작 시각 필드 접근성", () => {
  it("필수 입력임을 알린다", () => {
    render(<EventCreateForm />);

    expect(screen.getByLabelText(/경기 시작 시각/)).toHaveAttribute("aria-required", "true");
  });

  it("입력 형식이 어긋나면 오류 상태를 알린다", async () => {
    const user = userEvent.setup();
    render(<EventCreateForm />);

    const input = screen.getByLabelText(/경기 시작 시각/);
    await user.type(input, "202608");
    await user.tab();

    expect(input).toHaveAttribute("aria-invalid", "true");
  });

  it("올바르게 입력하면 오류 상태가 아니다", async () => {
    const user = userEvent.setup();
    render(<EventCreateForm />);

    const input = screen.getByLabelText(/경기 시작 시각/);
    await user.type(input, "202608131930");

    expect(input).toHaveAttribute("aria-invalid", "false");
  });
});
