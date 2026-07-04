// @vitest-environment jsdom
/**
 * S2 생성 화면 폼 — 사용자 관점 동작 검증(role·텍스트·인터랙션).
 * 근거 티켓: FE-08-create-screen.md.
 */
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FeatureFlagCreateForm } from "../FeatureFlagCreateForm";

function fillRequiredFields(key: string, description: string): void {
  fireEvent.change(screen.getByLabelText("Key"), { target: { value: key } });
  fireEvent.change(screen.getByLabelText("설명"), { target: { value: description } });
}

describe("FeatureFlagCreateForm", () => {
  it("유효 입력으로 생성 클릭 시 onSubmit이 key/type/description/strategy를 담아 호출된다", () => {
    const handleSubmit = vi.fn();
    render(
      <FeatureFlagCreateForm
        onSubmit={handleSubmit}
        onCancel={vi.fn()}
        isSubmitting={false}
        serverError={null}
      />
    );

    fillRequiredFields("demo.feature.hello", "데모 인사 엔드포인트 킬스위치");
    fireEvent.click(screen.getByRole("button", { name: "생성" }));

    expect(handleSubmit).toHaveBeenCalledWith({
      key: "demo.feature.hello",
      type: "RELEASE",
      description: "데모 인사 엔드포인트 킬스위치",
      strategy: { strategyType: "GLOBAL_TOGGLE", enabled: false },
    });
  });

  it("key 누락 시 인라인 에러가 뜨고 onSubmit이 호출되지 않는다", () => {
    const handleSubmit = vi.fn();
    render(
      <FeatureFlagCreateForm
        onSubmit={handleSubmit}
        onCancel={vi.fn()}
        isSubmitting={false}
        serverError={null}
      />
    );

    fireEvent.change(screen.getByLabelText("설명"), { target: { value: "설명만 입력" } });
    fireEvent.click(screen.getByRole("button", { name: "생성" }));

    expect(screen.getByText("key를 입력해 주세요.")).toBeInTheDocument();
    expect(handleSubmit).not.toHaveBeenCalled();
  });

  it("종류를 EXPERIMENT로 바꾸면 전략 폼이 variant 입력으로 전환된다", async () => {
    const user = userEvent.setup();
    render(
      <FeatureFlagCreateForm
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
        isSubmitting={false}
        serverError={null}
      />
    );

    expect(screen.getByRole("switch", { name: "활성화" })).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("종류"), "EXPERIMENT");

    expect(screen.queryByRole("switch", { name: "활성화" })).not.toBeInTheDocument();
    expect(screen.getByLabelText("variant 1 이름")).toBeInTheDocument();
    expect(screen.getByLabelText("variant 2 이름")).toBeInTheDocument();
  });

  it("제출 중에는 생성 버튼이 비활성된다", () => {
    render(
      <FeatureFlagCreateForm
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
        isSubmitting={true}
        serverError={null}
      />
    );

    expect(screen.getByRole("button", { name: "생성 중..." })).toBeDisabled();
  });

  it("weight 합이 100이 아니면 생성 버튼이 비활성된다", async () => {
    const user = userEvent.setup();
    render(
      <FeatureFlagCreateForm
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
        isSubmitting={false}
        serverError={null}
      />
    );

    await user.selectOptions(screen.getByLabelText("종류"), "EXPERIMENT");
    fireEvent.change(screen.getByLabelText("variant 1 weight"), { target: { value: "40" } });

    expect(screen.getByRole("button", { name: "생성" })).toBeDisabled();
  });

  it("서버 에러 메시지가 전달되면 폼 상단에 alert 배너로 표시된다", () => {
    render(
      <FeatureFlagCreateForm
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
        isSubmitting={false}
        serverError="이미 존재하는 key입니다."
      />
    );

    expect(screen.getByRole("alert")).toHaveTextContent("이미 존재하는 key입니다.");
  });
});
