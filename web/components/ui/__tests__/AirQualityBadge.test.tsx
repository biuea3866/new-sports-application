// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { AirQualityBadge } from "../AirQualityBadge";

describe("AirQualityBadge", () => {
  it("GOOD 등급이면 좋음 라벨과 aq-good 토큰 클래스를 렌더한다", () => {
    render(<AirQualityBadge grade="GOOD" />);

    const badge = screen.getByText("좋음");
    expect(badge.className).toMatch(/bg-aq-good/);
    expect(badge.className).toMatch(/text-aq-good-foreground/);
  });

  it("BAD 등급이면 나쁨 라벨과 aq-bad 토큰 클래스를 렌더한다", () => {
    render(<AirQualityBadge grade="BAD" />);

    const badge = screen.getByText("나쁨");
    expect(badge.className).toMatch(/bg-aq-bad/);
    expect(badge.className).toMatch(/text-aq-bad-foreground/);
  });

  it("VERY_BAD 등급이면 매우나쁨 라벨을 렌더한다", () => {
    render(<AirQualityBadge grade="VERY_BAD" />);

    expect(screen.getByText("매우나쁨")).toBeInTheDocument();
  });

  it("MODERATE 등급이면 보통 라벨과 aq-moderate 토큰 클래스를 렌더한다", () => {
    render(<AirQualityBadge grade="MODERATE" />);

    const badge = screen.getByText("보통");
    expect(badge.className).toMatch(/bg-aq-moderate/);
  });

  it("UNKNOWN 등급이면 배지를 렌더하지 않는다", () => {
    const { container } = render(<AirQualityBadge grade="UNKNOWN" />);

    expect(screen.queryByText("정보없음")).not.toBeInTheDocument();
    expect(container).toBeEmptyDOMElement();
  });

  it("색은 하드코딩 값 없이 aq 시맨틱 토큰 클래스만 사용한다", () => {
    render(<AirQualityBadge grade="GOOD" />);

    const badge = screen.getByText("좋음");
    expect(badge.className).not.toMatch(/#[0-9a-fA-F]{3,6}/);
    expect(badge.className).not.toMatch(/rgb\(/);
  });
});
