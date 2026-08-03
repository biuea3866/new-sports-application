// @vitest-environment jsdom
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, within } from "@testing-library/react";
import { FeatureFlagFilters } from "../FeatureFlagFilters";

describe("FeatureFlagFilters", () => {
  // 목록 화면의 상태·종류 select가 옵션을 영문 원문(ACTIVE 등)으로 노출한 결함 재발 방지.
  it("상태 select 옵션이 한글 라벨로 렌더된다", () => {
    render(
      <FeatureFlagFilters
        status={undefined}
        type={undefined}
        onStatusChange={vi.fn()}
        onTypeChange={vi.fn()}
      />
    );

    const statusSelect = screen.getByLabelText("상태");
    expect(within(statusSelect).getByText("활성")).toBeInTheDocument();
    expect(within(statusSelect).getByText("아카이브됨")).toBeInTheDocument();
  });

  it("종류 select 옵션이 한글 라벨로 렌더된다", () => {
    render(
      <FeatureFlagFilters
        status={undefined}
        type={undefined}
        onStatusChange={vi.fn()}
        onTypeChange={vi.fn()}
      />
    );

    const typeSelect = screen.getByLabelText("종류");
    expect(within(typeSelect).getByText("릴리즈")).toBeInTheDocument();
    expect(within(typeSelect).getByText("운영")).toBeInTheDocument();
    expect(within(typeSelect).getByText("실험")).toBeInTheDocument();
    expect(within(typeSelect).getByText("권한")).toBeInTheDocument();
  });

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
