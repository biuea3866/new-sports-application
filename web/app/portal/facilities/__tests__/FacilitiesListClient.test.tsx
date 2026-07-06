// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import FacilitiesListClient from "../FacilitiesListClient";
import type { MyFacility, Page } from "@/lib/portal/types";

function buildFacility(overrides: Partial<MyFacility> = {}): MyFacility {
  return {
    id: "fac-001",
    code: "GN-01",
    name: "강남 풋살장",
    gu: "강남구",
    sidoCode: "11",
    sidoName: "서울특별시",
    sigunguCode: "11680",
    sigunguName: "강남구",
    type: "INDOOR",
    address: "서울특별시 강남구",
    lat: 37.5,
    lng: 127.0,
    parking: true,
    tel: "02-1234-5678",
    homePage: null,
    eduYn: false,
    meta: null,
    ownerUserId: 1,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function buildPage(content: MyFacility[]): Page<MyFacility> {
  return {
    content,
    page: 0,
    size: 10,
    totalElements: content.length,
    totalPages: 1,
  };
}

const mockFetch = vi.fn();

describe("FacilitiesListClient", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", mockFetch);
    mockFetch.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("목록 표에 시/도 컬럼과 각 시설의 시도명이 표시된다", async () => {
    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify(
          buildPage([
            buildFacility({ id: "fac-001", name: "강남 풋살장", sidoName: "서울특별시" }),
            buildFacility({ id: "fac-002", name: "해운대 풋살장", sidoName: "부산광역시" }),
          ])
        ),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    );

    render(<FacilitiesListClient />);

    await waitFor(() => {
      expect(screen.getByText("강남 풋살장")).toBeInTheDocument();
    });

    expect(screen.getByRole("columnheader", { name: "시/도" })).toBeInTheDocument();
    expect(screen.getByText("서울특별시")).toBeInTheDocument();
    expect(screen.getByText("부산광역시")).toBeInTheDocument();
  });

  it("sidoName이 미지정인 시설은 지역 미확인으로 표시된다", async () => {
    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify(buildPage([buildFacility({ name: "레거시 시설", sidoName: "미지정" })])),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    );

    render(<FacilitiesListClient />);

    await waitFor(() => {
      expect(screen.getByText("레거시 시설")).toBeInTheDocument();
    });

    expect(screen.getByText("지역 미확인")).toBeInTheDocument();
  });

  it("시설 0건일 때 등록된 시설이 없습니다 문구가 표시된다", async () => {
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify(buildPage([])), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    render(<FacilitiesListClient />);

    await waitFor(() => {
      expect(screen.getByText("등록된 시설이 없습니다.")).toBeInTheDocument();
    });
  });

  it("로딩 중에는 불러오는 중 문구가 표시된다", () => {
    mockFetch.mockReturnValue(new Promise(() => {}));

    render(<FacilitiesListClient />);

    expect(screen.getByText("불러오는 중...")).toBeInTheDocument();
  });

  it("응답 실패 시 alert 역할 요소에 에러 메시지가 표시된다", async () => {
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ message: "목록을 불러올 수 없습니다." }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      })
    );

    render(<FacilitiesListClient />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("목록을 불러올 수 없습니다.");
    });
  });
});
