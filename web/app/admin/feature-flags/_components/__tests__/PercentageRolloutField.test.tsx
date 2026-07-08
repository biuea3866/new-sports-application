// @vitest-environment jsdom
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { PercentageRolloutField } from "../PercentageRolloutField";

describe("PercentageRolloutField", () => {
  it("슬라이더를 60으로 옮기면 onChange가 percentage=60을 전달한다", () => {
    const handleChange = vi.fn();
    render(<PercentageRolloutField percentage={50} onChange={handleChange} />);

    fireEvent.change(screen.getByLabelText("노출 비율 슬라이더"), { target: { value: "60" } });

    expect(handleChange).toHaveBeenCalledWith(60);
  });

  it("숫자 입력값이 percentage와 동일하게 렌더링된다", () => {
    render(<PercentageRolloutField percentage={60} onChange={vi.fn()} />);

    expect(screen.getByLabelText("노출 비율 숫자 입력")).toHaveValue(60);
    expect(screen.getByLabelText("노출 비율 슬라이더")).toHaveValue("60");
  });

  it("숫자 입력을 변경하면 onChange가 호출된다", () => {
    const handleChange = vi.fn();
    render(<PercentageRolloutField percentage={50} onChange={handleChange} />);

    fireEvent.change(screen.getByLabelText("노출 비율 숫자 입력"), { target: { value: "80" } });

    expect(handleChange).toHaveBeenCalledWith(80);
  });

  it("userId 해시 기반 sticky 안내 문구를 표시한다", () => {
    render(<PercentageRolloutField percentage={50} onChange={vi.fn()} />);

    expect(
      screen.getByText("userId 해시 기반 — 동일 사용자는 일관되게 노출됩니다.")
    ).toBeInTheDocument();
  });

  it("disabled면 슬라이더와 숫자 입력이 모두 비활성된다", () => {
    render(<PercentageRolloutField percentage={50} onChange={vi.fn()} disabled />);

    expect(screen.getByLabelText("노출 비율 슬라이더")).toBeDisabled();
    expect(screen.getByLabelText("노출 비율 숫자 입력")).toBeDisabled();
  });
});
