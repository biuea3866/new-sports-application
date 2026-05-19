// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { Button } from "../button";

describe("Button", () => {
  it("[U-01] aria-disabled 속성이 disabled prop과 일치한다", () => {
    const { rerender } = render(<Button disabled>저장</Button>);
    const btn = screen.getByRole("button", { name: "저장" });

    expect(btn).toBeDisabled();
    expect(btn).toHaveAttribute("aria-disabled", "true");

    rerender(<Button>저장</Button>);
    expect(btn).not.toBeDisabled();
    expect(btn).toHaveAttribute("aria-disabled", "false");
  });

  it("variant와 size props가 className에 반영된다", () => {
    render(
      <Button variant="destructive" size="lg">
        삭제
      </Button>
    );
    const btn = screen.getByRole("button", { name: "삭제" });
    expect(btn.className).toMatch(/destructive/);
  });
});
