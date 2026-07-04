// @vitest-environment jsdom
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { VariantBucketingField } from "../VariantBucketingField";
import type { FeatureFlagVariant } from "@/lib/admin/feature-flags/schemas";

const twoVariants: FeatureFlagVariant[] = [
  { name: "A", weight: 50 },
  { name: "B", weight: 50 },
];

describe("VariantBucketingField", () => {
  it("weight 합계가 100이면 경고 없이 합계를 표시한다", () => {
    render(<VariantBucketingField variants={twoVariants} onChange={vi.fn()} />);

    expect(screen.getByText("weight 합계: 100 / 100")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("weight 합이 100이 아니면 warning 캡션을 표시한다", () => {
    const variants: FeatureFlagVariant[] = [
      { name: "A", weight: 50 },
      { name: "B", weight: 40 },
    ];
    render(<VariantBucketingField variants={variants} onChange={vi.fn()} />);

    const warning = screen.getByRole("alert");
    expect(warning).toHaveTextContent("90");
    expect(warning.className).toContain("text-warning");
  });

  it("variant를 4개 초과로 추가하려 하면 추가 버튼이 비활성된다", () => {
    const fourVariants: FeatureFlagVariant[] = [
      { name: "A", weight: 25 },
      { name: "B", weight: 25 },
      { name: "C", weight: 25 },
      { name: "D", weight: 25 },
    ];
    render(<VariantBucketingField variants={fourVariants} onChange={vi.fn()} />);

    expect(screen.getByRole("button", { name: "variant 추가" })).toBeDisabled();
  });

  it("variant가 4개 미만이면 추가 버튼이 활성 상태이고 클릭 시 variant가 추가된다", async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(<VariantBucketingField variants={twoVariants} onChange={handleChange} />);

    const addButton = screen.getByRole("button", { name: "variant 추가" });
    expect(addButton).not.toBeDisabled();
    await user.click(addButton);

    expect(handleChange).toHaveBeenCalledWith([...twoVariants, { name: "", weight: 0 }]);
  });

  it("삭제 버튼을 누르면 해당 variant가 제거된다", async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(<VariantBucketingField variants={twoVariants} onChange={handleChange} />);

    const [firstDeleteButton] = screen.getAllByRole("button", { name: "삭제" });
    if (firstDeleteButton === undefined) throw new Error("삭제 버튼을 찾지 못했습니다.");
    await user.click(firstDeleteButton);

    expect(handleChange).toHaveBeenCalledWith([{ name: "B", weight: 50 }]);
  });

  it("disabled면 입력·추가·삭제 버튼이 모두 비활성된다", () => {
    render(<VariantBucketingField variants={twoVariants} onChange={vi.fn()} disabled />);

    expect(screen.getByRole("button", { name: "variant 추가" })).toBeDisabled();
    screen.getAllByRole("button", { name: "삭제" }).forEach((button) => expect(button).toBeDisabled());
    screen.getAllByLabelText(/variant \d+ 이름/).forEach((input) => expect(input).toBeDisabled());
  });
});
