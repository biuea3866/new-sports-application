// @vitest-environment jsdom
import { useState } from "react";
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StrategyForm } from "../StrategyForm";
import type { FeatureFlagStrategy, FeatureFlagType } from "@/lib/admin/feature-flags/schemas";

/** 실사용(부모가 state를 소유하는 controlled 컴포넌트)을 재현하는 테스트 하네스. */
function StrategyFormHarness({
  initialStrategy,
  flagType,
  disabled,
  onValidityChange,
}: {
  initialStrategy: FeatureFlagStrategy;
  flagType: FeatureFlagType;
  disabled?: boolean;
  onValidityChange?: (valid: boolean) => void;
}) {
  const [strategy, setStrategy] = useState<FeatureFlagStrategy>(initialStrategy);
  return (
    <StrategyForm
      value={strategy}
      onChange={setStrategy}
      flagType={flagType}
      disabled={disabled}
      onValidityChange={onValidityChange}
    />
  );
}

describe("StrategyForm", () => {
  it("전략 유형을 퍼센티지 롤아웃으로 바꾸면 슬라이더가 나타나고 토글 필드는 사라진다", async () => {
    const user = userEvent.setup();
    render(
      <StrategyFormHarness
        initialStrategy={{ strategyType: "GLOBAL_TOGGLE", enabled: false }}
        flagType="RELEASE"
      />
    );

    expect(screen.getByRole("switch", { name: "활성화" })).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("전략 유형"), "PERCENTAGE_ROLLOUT");

    expect(screen.getByLabelText("노출 비율 슬라이더")).toBeInTheDocument();
    expect(screen.queryByRole("switch", { name: "활성화" })).not.toBeInTheDocument();
  });

  it("슬라이더를 60으로 옮기면 숫자 입력이 60으로 동기화되고 onChange가 percentage=60을 전달한다", () => {
    render(
      <StrategyFormHarness
        initialStrategy={{ strategyType: "PERCENTAGE_ROLLOUT", percentage: 50 }}
        flagType="RELEASE"
      />
    );

    fireEvent.change(screen.getByLabelText("노출 비율 슬라이더"), { target: { value: "60" } });

    expect(screen.getByLabelText("노출 비율 숫자 입력")).toHaveValue(60);
    expect(screen.getByLabelText("노출 비율 슬라이더")).toHaveValue("60");
  });

  it("variant weight 합이 100이 아니면 경고 caption이 뜨고 유효성 false를 알린다", () => {
    const handleValidityChange = vi.fn();
    render(
      <StrategyForm
        value={{
          strategyType: "VARIANT_BUCKETING",
          variants: [
            { name: "A", weight: 50 },
            { name: "B", weight: 40 },
          ],
        }}
        onChange={vi.fn()}
        flagType="EXPERIMENT"
        onValidityChange={handleValidityChange}
      />
    );

    expect(screen.getByRole("alert").className).toContain("text-warning");
    expect(handleValidityChange).toHaveBeenCalledWith(false);
  });

  it("variant를 4개 초과로 추가하려 하면 추가 버튼이 비활성된다", () => {
    render(
      <StrategyForm
        value={{
          strategyType: "VARIANT_BUCKETING",
          variants: [
            { name: "A", weight: 25 },
            { name: "B", weight: 25 },
            { name: "C", weight: 25 },
            { name: "D", weight: 25 },
          ],
        }}
        onChange={vi.fn()}
        flagType="EXPERIMENT"
      />
    );

    expect(screen.getByRole("button", { name: "variant 추가" })).toBeDisabled();
  });

  it("flagType=EXPERIMENT면 전략 유형 select에 VARIANT_BUCKETING만 노출된다", () => {
    render(
      <StrategyForm
        value={{ strategyType: "VARIANT_BUCKETING", variants: [{ name: "A", weight: 100 }] }}
        onChange={vi.fn()}
        flagType="EXPERIMENT"
      />
    );

    const options = screen.getAllByRole<HTMLOptionElement>("option");
    expect(options.map((option) => option.value)).toEqual(["VARIANT_BUCKETING"]);
  });

  it("disabled=true(ARCHIVED)면 모든 입력이 비활성된다", () => {
    render(
      <StrategyForm
        value={{ strategyType: "ATTRIBUTE_MATCH", attribute: "plan", value: "PREMIUM" }}
        onChange={vi.fn()}
        flagType="RELEASE"
        disabled
      />
    );

    expect(screen.getByLabelText("전략 유형")).toBeDisabled();
    expect(screen.getByLabelText("속성 이름")).toBeDisabled();
    expect(screen.getByLabelText("기대 값")).toBeDisabled();
  });

  it("ATTRIBUTE_MATCH에서 attribute/value 입력이 onChange로 반영된다", () => {
    const handleChange = vi.fn();
    render(
      <StrategyForm
        value={{ strategyType: "ATTRIBUTE_MATCH", attribute: "plan", value: "" }}
        onChange={handleChange}
        flagType="RELEASE"
      />
    );

    fireEvent.change(screen.getByLabelText("기대 값"), { target: { value: "PREMIUM" } });

    expect(handleChange).toHaveBeenCalledWith({
      strategyType: "ATTRIBUTE_MATCH",
      attribute: "plan",
      value: "PREMIUM",
    });
  });
});
