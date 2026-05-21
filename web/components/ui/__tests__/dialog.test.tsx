// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect } from "vitest";
import {
  Dialog,
  DialogTrigger,
  DialogContent,
  DialogTitle,
  DialogDescription,
} from "../dialog";

function TestDialog() {
  return (
    <Dialog>
      <DialogTrigger asChild>
        <button>다이얼로그 열기</button>
      </DialogTrigger>
      <DialogContent>
        <DialogTitle>제목</DialogTitle>
        <DialogDescription>설명</DialogDescription>
      </DialogContent>
    </Dialog>
  );
}

describe("Dialog", () => {
  it("[U-02] ESC 키로 닫기가 동작하고 포커스가 호출 element로 복귀한다", async () => {
    const user = userEvent.setup();
    render(<TestDialog />);

    const trigger = screen.getByRole("button", { name: "다이얼로그 열기" });

    // 다이얼로그 열기
    await user.click(trigger);
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    // ESC 키로 닫기
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    // 포커스가 trigger로 복귀했는지 확인
    expect(document.activeElement).toBe(trigger);
  });

  it("트리거 클릭으로 다이얼로그가 열린다", async () => {
    const user = userEvent.setup();
    render(<TestDialog />);

    await user.click(screen.getByRole("button", { name: "다이얼로그 열기" }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("제목")).toBeVisible();
  });
});
