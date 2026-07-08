// @vitest-environment jsdom
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { AdminTopbar } from "../_components/AdminTopbar";

describe("AdminTopbar", () => {
  it("[U-04] operatorName 이 제공되면 운영자 이름을 노출한다", () => {
    render(<AdminTopbar operatorName="홍길동" />);
    expect(screen.getByText("홍길동")).toBeInTheDocument();
  });

  it("[U-05] operatorName 이 없으면 미인증 표시", () => {
    render(<AdminTopbar />);
    expect(screen.getByText("미인증")).toBeInTheDocument();
  });

  it("[U-06] Sports Admin 로고는 /admin 으로 링크된다", () => {
    render(<AdminTopbar operatorName="홍길동" />);
    expect(screen.getByText("Sports Admin").closest("a")?.getAttribute("href")).toBe("/admin");
  });

  it("[U-07] 로그아웃 링크가 표시된다", () => {
    render(<AdminTopbar operatorName="홍길동" />);
    expect(screen.getByText("로그아웃").closest("a")?.getAttribute("href")).toBe("/admin/logout");
  });
});
