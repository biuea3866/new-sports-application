// @vitest-environment jsdom
/**
 * 시설 폼 시/도 입력 컴포넌트 테스트
 * 근거 티켓: FE-08-web-form-sido-input.md
 *
 * - 폼에 시/도 드롭다운이 렌더되고 선택값이 폼 상태에 반영된다
 * - 시/도를 선택하지 않아도(빈 값) 나머지 필수 필드가 유효하면 제출된다
 * - 기존 필수 필드(name/gu/address 등) 검증이 회귀 없이 동작한다
 * - 제출 시 sido 값이 onSubmit payload에 포함된다
 * - 수정 모드에서 defaultValues로 전달된 sido 값이 프리필된다
 */
import * as React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { FacilityForm } from "../_components/FacilityForm";
import type { FacilityFormValues } from "../facility-form-schema";

function renderForm(overrides: Partial<React.ComponentProps<typeof FacilityForm>> = {}) {
  const onSubmit = vi.fn();
  const onCancel = vi.fn();
  render(
    <FacilityForm
      mode="create"
      isSubmitting={false}
      onSubmit={onSubmit}
      onCancel={onCancel}
      {...overrides}
    />
  );
  return { onSubmit, onCancel };
}

async function fillRequiredFieldsExceptName(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/시설 코드/i), "GN-01");
  await user.type(screen.getByLabelText(/^구/i), "강남구");
  await user.type(screen.getByLabelText(/주소/i), "서울특별시 강남구");
  await user.type(screen.getByLabelText(/위치 좌표/i), "37.5,127.0");
  await user.type(screen.getByLabelText(/전화번호/i), "02-1234-5678");
}

async function fillRequiredFields(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/시설명/i), "강남 풋살장");
  await fillRequiredFieldsExceptName(user);
}

describe("FacilityForm 시/도 입력", () => {
  it("시/도 드롭다운이 렌더되고 선택하면 값이 반영된다", async () => {
    const user = userEvent.setup();
    renderForm();

    const select = screen.getByRole("combobox", { name: "시/도" });
    await user.selectOptions(select, "부산광역시");

    expect(select).toHaveValue("26");
  });

  it("시/도를 선택하지 않아도(빈 값) 나머지 필수 필드가 유효하면 제출된다", async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderForm();

    await fillRequiredFields(user);
    await user.click(screen.getByRole("button", { name: /등록/i }));

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining<Partial<FacilityFormValues>>({ sido: "" })
    );
  });

  it("시/도를 선택하고 제출하면 onSubmit payload에 sido 코드가 포함된다", async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderForm();

    await fillRequiredFields(user);
    await user.selectOptions(screen.getByRole("combobox", { name: "시/도" }), "서울특별시");
    await user.click(screen.getByRole("button", { name: /등록/i }));

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining<Partial<FacilityFormValues>>({ sido: "11" })
    );
  });

  it("기존 필수 필드(name) 검증이 회귀 없이 동작해 미입력 시 제출을 막는다", async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderForm();

    await fillRequiredFieldsExceptName(user);
    await user.click(screen.getByRole("button", { name: /등록/i }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByText("시설명을 입력해 주세요.")).toBeInTheDocument();
  });

  it("수정 모드에서 defaultValues로 전달된 sido 값이 드롭다운에 프리필된다", () => {
    renderForm({ mode: "edit", defaultValues: { sido: "26" } });

    const select = screen.getByRole("combobox", { name: "시/도" });
    expect(select).toHaveValue("26");
  });
});
