// @vitest-environment jsdom
/**
 * 한국어 날짜 입력 컴포넌트 계약.
 *
 * 회귀 방지: 네이티브 `<input type="date">`·`type="datetime-local">` 의 표시 형식은 문서 `lang`
 * 이 아니라 **브라우저 UI 로케일**을 따른다. 한국어 화면인데 `mm/dd/yyyy`·
 * `mm/dd/yyyy, --:-- --` 로 찍히던 결함이 포털·어드민 5화면에 있었고, `lang="ko-KR"` 을 붙이는
 * 시도는 효과가 없음을 Chromium 에서 확인했다(no-lang/lang 지정 모두 mm/dd/yyyy).
 *
 * 따라서 표시 형식을 브라우저에 맡기지 않고 직접 제어한다. 값 계약(`yyyy-MM-dd` /
 * `yyyy-MM-ddTHH:mm`)은 네이티브 입력과 동일하게 유지해 호출부 로직을 바꾸지 않는다.
 */
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { DateField, DateTimeField } from "../date-field";

describe("DateField", () => {
  // 이 테스트가 결함의 재발을 직접 막는다 — 네이티브 date 입력으로 되돌아가면 실패한다.
  it("브라우저 로케일에 표시를 맡기는 네이티브 date 입력을 쓰지 않는다", () => {
    render(<DateField value="" onChange={vi.fn()} aria-label="결제일 시작" />);

    const input = screen.getByLabelText("결제일 시작");
    expect(input).toHaveAttribute("type", "text");
  });

  it("한국식 자리표시자를 보여준다", () => {
    render(<DateField value="" onChange={vi.fn()} aria-label="결제일 시작" />);

    const input = screen.getByLabelText("결제일 시작");
    expect(input).toHaveAttribute("placeholder", "YYYY-MM-DD");
    // 미국식 표기가 화면에 남아 있으면 안 된다.
    expect(input.getAttribute("placeholder")).not.toMatch(/mm\/dd/i);
  });

  it("값을 연-월-일 순서로 보여준다", () => {
    render(<DateField value="2026-07-28" onChange={vi.fn()} aria-label="만료일" />);

    expect(screen.getByLabelText("만료일")).toHaveValue("2026-07-28");
  });

  it("숫자만 입력해도 구분자를 자동으로 넣는다", async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(<DateField value="" onChange={handleChange} aria-label="만료일" />);

    await user.type(screen.getByLabelText("만료일"), "20260728");

    expect(handleChange).toHaveBeenLastCalledWith("2026-07-28");
  });

  it("입력이 끝나지 않았으면 빈 값을 알린다", async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(<DateField value="" onChange={handleChange} aria-label="만료일" />);

    await user.type(screen.getByLabelText("만료일"), "2026");

    expect(handleChange).toHaveBeenLastCalledWith("");
  });

  it("달력에 없는 날짜는 값으로 확정하지 않는다", async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(<DateField value="" onChange={handleChange} aria-label="만료일" />);

    await user.type(screen.getByLabelText("만료일"), "20261345");

    expect(handleChange).toHaveBeenLastCalledWith("");
  });

  it("값을 지우면 빈 값을 알린다", async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(<DateField value="2026-07-28" onChange={handleChange} aria-label="만료일" />);

    await user.clear(screen.getByLabelText("만료일"));

    expect(handleChange).toHaveBeenLastCalledWith("");
  });

  it("비활성화 상태를 전달한다", () => {
    render(<DateField value="" onChange={vi.fn()} aria-label="만료일" disabled />);

    expect(screen.getByLabelText("만료일")).toBeDisabled();
  });

  it("id를 붙여 label과 연결할 수 있다", () => {
    render(
      <>
        <label htmlFor="paid-at-from">결제일 시작</label>
        <DateField id="paid-at-from" value="" onChange={vi.fn()} />
      </>
    );

    expect(screen.getByLabelText("결제일 시작")).toBeInTheDocument();
  });
});

describe("DateTimeField", () => {
  it("네이티브 datetime-local 입력을 쓰지 않는다", () => {
    render(<DateTimeField value="" onChange={vi.fn()} aria-label="경기 시작 시각" />);

    expect(screen.getByLabelText("경기 시작 시각")).toHaveAttribute("type", "text");
  });

  it("24시간 표기 자리표시자를 보여준다", () => {
    render(<DateTimeField value="" onChange={vi.fn()} aria-label="경기 시작 시각" />);

    const input = screen.getByLabelText("경기 시작 시각");
    expect(input).toHaveAttribute("placeholder", "YYYY-MM-DD HH:mm");
    // `--:-- --`(12시간제 AM/PM) 표기가 남아 있으면 안 된다.
    expect(input.getAttribute("placeholder")).not.toContain("--");
  });

  it("값을 연-월-일 시:분으로 보여준다", () => {
    render(<DateTimeField value="2026-08-13T19:30" onChange={vi.fn()} aria-label="경기 시작 시각" />);

    expect(screen.getByLabelText("경기 시작 시각")).toHaveValue("2026-08-13 19:30");
  });

  it("초까지 붙은 값도 분 단위로 보여준다", () => {
    render(
      <DateTimeField value="2026-08-13T19:30:00" onChange={vi.fn()} aria-label="경기 시작 시각" />
    );

    expect(screen.getByLabelText("경기 시작 시각")).toHaveValue("2026-08-13 19:30");
  });

  it("숫자만 입력해도 datetime-local 값 형식으로 알린다", async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(<DateTimeField value="" onChange={handleChange} aria-label="경기 시작 시각" />);

    await user.type(screen.getByLabelText("경기 시작 시각"), "202608131930");

    expect(handleChange).toHaveBeenLastCalledWith("2026-08-13T19:30");
  });

  it("시각이 덜 채워지면 빈 값을 알린다", async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(<DateTimeField value="" onChange={handleChange} aria-label="경기 시작 시각" />);

    await user.type(screen.getByLabelText("경기 시작 시각"), "202608131");

    expect(handleChange).toHaveBeenLastCalledWith("");
  });

  it("없는 시각은 값으로 확정하지 않는다", async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(<DateTimeField value="" onChange={handleChange} aria-label="경기 시작 시각" />);

    await user.type(screen.getByLabelText("경기 시작 시각"), "202608132599");

    expect(handleChange).toHaveBeenLastCalledWith("");
  });
});
