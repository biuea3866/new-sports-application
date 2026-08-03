// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { ChangeTypeBadge } from "../ChangeTypeBadge";
import { FEATURE_FLAG_CHANGE_TYPE_LABELS } from "@/lib/admin/feature-flags/featureFlagChangeType";

describe("ChangeTypeBadge", () => {
  it.each([
    ["CREATED"] as const,
    ["UPDATED"] as const,
    ["ARCHIVED"] as const,
    ["ACTIVATED"] as const,
  ])("%s 변경 유형의 한글 라벨을 렌더한다", (changeType) => {
    render(<ChangeTypeBadge changeType={changeType} />);

    expect(screen.getByText(FEATURE_FLAG_CHANGE_TYPE_LABELS[changeType])).toBeInTheDocument();
  });

  // 계약 밖 변경 유형이 섞여도 그 행만 원문으로 남고 화면 전체가 죽으면 안 된다.
  it("모르는 변경 유형 값은 원문을 그대로 표시한다", () => {
    render(<ChangeTypeBadge changeType="ROLLED_BACK" />);

    expect(screen.getByText("ROLLED_BACK")).toBeInTheDocument();
  });
});
