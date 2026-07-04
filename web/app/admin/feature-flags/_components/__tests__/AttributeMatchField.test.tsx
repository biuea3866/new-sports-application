// @vitest-environment jsdom
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { AttributeMatchField } from "../AttributeMatchField";

describe("AttributeMatchField", () => {
  it("attribute 입력이 onChange로 반영된다", () => {
    const handleChange = vi.fn();
    render(<AttributeMatchField attribute="" value="" onChange={handleChange} />);

    fireEvent.change(screen.getByLabelText("속성 이름"), { target: { value: "plan" } });

    expect(handleChange).toHaveBeenCalledWith({ attribute: "plan", value: "" });
  });

  it("value 입력이 onChange로 반영된다", () => {
    const handleChange = vi.fn();
    render(<AttributeMatchField attribute="plan" value="" onChange={handleChange} />);

    fireEvent.change(screen.getByLabelText("기대 값"), { target: { value: "PREMIUM" } });

    expect(handleChange).toHaveBeenCalledWith({ attribute: "plan", value: "PREMIUM" });
  });

  it("초기값을 그대로 렌더링한다", () => {
    render(<AttributeMatchField attribute="plan" value="PREMIUM" onChange={vi.fn()} />);

    expect(screen.getByLabelText("속성 이름")).toHaveValue("plan");
    expect(screen.getByLabelText("기대 값")).toHaveValue("PREMIUM");
  });

  it("disabled면 두 입력 모두 비활성된다", () => {
    render(<AttributeMatchField attribute="" value="" onChange={vi.fn()} disabled />);

    expect(screen.getByLabelText("속성 이름")).toBeDisabled();
    expect(screen.getByLabelText("기대 값")).toBeDisabled();
  });
});
