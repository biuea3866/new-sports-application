// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { ChangeTypeBadge } from "../ChangeTypeBadge";

describe("ChangeTypeBadge", () => {
  it.each([
    ["CREATED"] as const,
    ["UPDATED"] as const,
    ["ARCHIVED"] as const,
    ["ACTIVATED"] as const,
  ])("%s 변경 유형의 라벨을 렌더한다", (changeType) => {
    render(<ChangeTypeBadge changeType={changeType} />);

    expect(screen.getByText(changeType)).toBeInTheDocument();
  });
});
