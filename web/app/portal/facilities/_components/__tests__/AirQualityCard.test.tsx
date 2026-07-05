// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { AirQualityCard } from "../AirQualityCard";
import type { AirQualityResponse } from "@/lib/portal/air-quality";

const SUCCESS_DATA: AirQualityResponse = {
  pm10: 92,
  pm25: 41,
  pm10Grade: "BAD",
  pm25Grade: "MODERATE",
  representativeGrade: "BAD",
  stationName: "해운대구",
  measuredAt: "2026-07-05T14:00:00+09:00",
};

describe("AirQualityCard", () => {
  it("success 상태에서 PM10/PM2.5 수치, 대표 등급 배지, 측정소·시각을 표시한다", () => {
    render(<AirQualityCard status="success" data={SUCCESS_DATA} />);

    expect(screen.getByText(/92/)).toBeInTheDocument();
    expect(screen.getByText(/41/)).toBeInTheDocument();
    expect(screen.getByText("나쁨")).toBeInTheDocument();
    expect(screen.getByText(/해운대구/)).toBeInTheDocument();
    expect(screen.getByText(/14:00/)).toBeInTheDocument();
  });

  it("loading 상태에서 대기질 정보를 불러오는 중 문구가 표시된다", () => {
    render(<AirQualityCard status="loading" data={null} />);

    expect(screen.getByText("대기질 정보를 불러오는 중…")).toBeInTheDocument();
  });

  it("error 상태에서 대기질 정보를 불러올 수 없습니다 폴백이 표시되고 배지는 숨겨진다", () => {
    render(<AirQualityCard status="error" data={null} />);

    expect(screen.getByText("대기질 정보를 불러올 수 없습니다")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("success 상태에서 대표 등급이 UNKNOWN이면 폴백 문구가 표시되고 배지는 숨겨진다", () => {
    const unknownData: AirQualityResponse = {
      pm10: null,
      pm25: null,
      pm10Grade: "UNKNOWN",
      pm25Grade: "UNKNOWN",
      representativeGrade: "UNKNOWN",
      stationName: null,
      measuredAt: null,
    };
    render(<AirQualityCard status="success" data={unknownData} />);

    expect(screen.getByText("대기질 정보를 불러올 수 없습니다")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("pm2.5만 null일 때 pm10 값만 표시되고 크래시하지 않는다", () => {
    const partialData: AirQualityResponse = {
      pm10: 55,
      pm25: null,
      pm10Grade: "MODERATE",
      pm25Grade: "UNKNOWN",
      representativeGrade: "MODERATE",
      stationName: "강남구",
      measuredAt: "2026-07-05T09:30:00+09:00",
    };
    render(<AirQualityCard status="success" data={partialData} />);

    expect(screen.getByText(/55/)).toBeInTheDocument();
    expect(screen.getByText("보통")).toBeInTheDocument();
    expect(screen.queryByText(/초미세먼지/)).not.toBeInTheDocument();
    expect(screen.queryByText("null")).not.toBeInTheDocument();
  });

  it("배지에 등급별 aq 토큰 클래스가 적용된다", () => {
    render(<AirQualityCard status="success" data={SUCCESS_DATA} />);

    const badge = screen.getByText("나쁨");
    expect(badge.className).toMatch(/bg-aq-bad/);
  });
});
