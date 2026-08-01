// @vitest-environment jsdom
/**
 * 공공시설 일괄 임포트 — 파일 선택 UI의 한국어 표기.
 *
 * 네이티브 `<input type="file">`은 브라우저가 "Choose File / No file chosen"을 직접 그리고
 * 그 문자열은 CSS(`file:` 의사요소)로 바꿀 수 없다. 한국어 화면에 영문 컨트롤이 그대로
 * 노출됐던 결함을 막기 위해, 네이티브 컨트롤은 화면에서 감추고 우리 라벨을 렌더한다.
 */
import { describe, it, expect } from "vitest";
import { render, screen, fireEvent, act } from "@testing-library/react";
import FacilitiesImportClient from "../FacilitiesImportClient";

function makeFile(name: string): File {
  return new File(["code,name\n"], name, { type: "text/csv" });
}

describe("CSV 파일 선택 컨트롤", () => {
  it("파일 선택 버튼을 한국어로 표시한다", () => {
    render(<FacilitiesImportClient />);

    expect(screen.getByText("파일 선택")).toBeInTheDocument();
  });

  it("파일을 고르기 전에는 선택된 파일이 없음을 한국어로 알린다", () => {
    render(<FacilitiesImportClient />);

    expect(screen.getByText("선택된 파일 없음")).toBeInTheDocument();
  });

  it("파일을 고르면 파일명을 표시한다", () => {
    render(<FacilitiesImportClient />);

    const input = screen.getByLabelText("CSV 파일 선택");
    act(() => {
      Object.defineProperty(input, "files", {
        value: [makeFile("공공시설.csv")],
        configurable: true,
      });
      fireEvent.change(input);
    });

    expect(screen.getByText("공공시설.csv")).toBeInTheDocument();
    expect(screen.queryByText("선택된 파일 없음")).not.toBeInTheDocument();
  });

  it("네이티브 파일 입력은 화면에서 감추되 접근성 트리에는 남긴다", () => {
    render(<FacilitiesImportClient />);

    // sr-only로 감춘다 — display:none/hidden으로 빼면 키보드 포커스와 라벨 연결이 끊긴다.
    const input = screen.getByLabelText("CSV 파일 선택");
    expect(input).toHaveClass("sr-only");
  });
});
