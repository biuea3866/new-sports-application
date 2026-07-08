// @vitest-environment jsdom
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { GlobalToggleField } from "../GlobalToggleField";

describe("GlobalToggleField", () => {
  it("enabled가 true면 ON으로 표시하고 success 토큰 클래스를 적용한다", () => {
    render(<GlobalToggleField enabled={true} onChange={vi.fn()} />);

    const toggle = screen.getByRole("switch", { name: "활성화" });
    expect(toggle).toHaveTextContent("ON");
    expect(toggle.className).toContain("text-success");
  });

  it("enabled가 false면 OFF로 표시한다", () => {
    render(<GlobalToggleField enabled={false} onChange={vi.fn()} />);

    expect(screen.getByRole("switch", { name: "활성화" })).toHaveTextContent("OFF");
  });

  it("클릭하면 onChange에 반전된 값을 전달한다", async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(<GlobalToggleField enabled={false} onChange={handleChange} />);

    await user.click(screen.getByRole("switch", { name: "활성화" }));

    expect(handleChange).toHaveBeenCalledWith(true);
  });

  it("disabled가 true면 클릭해도 onChange가 호출되지 않는다", async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(<GlobalToggleField enabled={false} onChange={handleChange} disabled />);

    const toggle = screen.getByRole("switch", { name: "활성화" });
    expect(toggle).toBeDisabled();
    await user.click(toggle);

    expect(handleChange).not.toHaveBeenCalled();
  });
});
