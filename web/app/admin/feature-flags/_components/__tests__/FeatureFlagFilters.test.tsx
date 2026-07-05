// @vitest-environment jsdom
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { FeatureFlagFilters } from "../FeatureFlagFilters";

describe("FeatureFlagFilters", () => {
  it("상태 select에서 ARCHIVED를 선택하면 onStatusChange가 ARCHIVED로 호출된다", () => {
    const onStatusChange = vi.fn();
    render(
      <FeatureFlagFilters
        status={undefined}
        type={undefined}
        onStatusChange={onStatusChange}
        onTypeChange={vi.fn()}
      />
    );

    fireEvent.change(screen.getByLabelText("상태"), { target: { value: "ARCHIVED" } });

    expect(onStatusChange).toHaveBeenCalledWith("ARCHIVED");
  });

  it("종류 select에서 EXPERIMENT를 선택하면 onTypeChange가 EXPERIMENT로 호출된다", () => {
    const onTypeChange = vi.fn();
    render(
      <FeatureFlagFilters
        status={undefined}
        type={undefined}
        onStatusChange={vi.fn()}
        onTypeChange={onTypeChange}
      />
    );

    fireEvent.change(screen.getByLabelText("종류"), { target: { value: "EXPERIMENT" } });

    expect(onTypeChange).toHaveBeenCalledWith("EXPERIMENT");
  });

  it("상태 select에서 전체를 선택하면 onStatusChange가 undefined로 호출된다", () => {
    const onStatusChange = vi.fn();
    render(
      <FeatureFlagFilters
        status="ACTIVE"
        type={undefined}
        onStatusChange={onStatusChange}
        onTypeChange={vi.fn()}
      />
    );

    fireEvent.change(screen.getByLabelText("상태"), { target: { value: "" } });

    expect(onStatusChange).toHaveBeenCalledWith(undefined);
  });
});
