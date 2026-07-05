// @vitest-environment jsdom
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { SidoSelect } from "../SidoSelect";

describe("SidoSelect", () => {
  it("17개 시도 옵션과 선택 안 함 기본 옵션을 렌더한다", () => {
    render(<SidoSelect value="" onChange={() => {}} label="시/도" />);

    const select = screen.getByRole("combobox", { name: "시/도" });
    const options = within(select);

    expect(options.getAllByRole("option")).toHaveLength(18);
    expect(options.getByRole("option", { name: "선택 안 함" })).toBeInTheDocument();
    expect(options.getByRole("option", { name: "서울특별시" })).toBeInTheDocument();
    expect(options.getByRole("option", { name: "부산광역시" })).toBeInTheDocument();
  });

  it("선택 변경 시 onChange에 선택한 시도 코드가 전달된다", async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(<SidoSelect value="" onChange={handleChange} label="시/도" />);

    const select = screen.getByRole("combobox", { name: "시/도" });
    await user.selectOptions(select, "부산광역시");

    expect(handleChange).toHaveBeenCalledWith("26");
  });

  it("연결된 접근성 라벨로 요소를 찾을 수 있다", () => {
    render(<SidoSelect value="" onChange={() => {}} label="시/도" />);

    expect(screen.getByLabelText("시/도")).toBeInTheDocument();
  });

  it("value prop으로 전달된 코드가 선택된 상태로 표시된다", () => {
    render(<SidoSelect value="11" onChange={() => {}} label="시/도" />);

    const select = screen.getByRole("combobox", { name: "시/도" });
    expect(select).toHaveValue("11");
  });
});
