// @vitest-environment jsdom
/**
 * FacilitiesImportClient 시나리오 테스트
 *
 * [S-01] 유효 CSV 업로드 → 미리보기 표시 → "일괄 등록 시작" → 성공 N건 요약
 * [S-02] 파싱 에러 행이 있을 때 오류 목록이 표시된다
 * [S-03] POST /api/portal/facilities 실패 행은 "실패" 결과로 표시된다
 * [S-04] 비CSV 파일 선택 시 파일 에러 메시지가 표시된다
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import * as React from "react";
import FacilitiesImportClient from "../FacilitiesImportClient";

const VALID_CSV = [
  "code,name,gu,type,address,lat,lng,parking,tel,homePage,eduYn,meta",
  "GN-01,강남 풋살장,강남구,INDOOR,서울특별시 강남구 테헤란로 1,37.5,127.0,true,02-1234-5678,,false,",
  "GN-02,강남 야외장,강남구,OUTDOOR,서울특별시 강남구 역삼로 1,37.51,127.01,false,02-2222-3333,,true,",
].join("\n");

const CSV_WITH_ERROR = [
  "code,name,gu,type,address,lat,lng,parking,tel,homePage,eduYn,meta",
  "GN-01,강남 풋살장,강남구,INDOOR,서울특별시 강남구,37.5,127.0,true,02-1234-5678,,false,",
  ",에러 행,강북구,INDOOR,서울특별시 강북구,37.6,127.1,false,02-9999-9999,,false,",
].join("\n");

function makeFile(content: string, name = "facilities.csv", type = "text/csv"): File {
  return new File([content], name, { type });
}

const mockFetch = vi.fn();

describe("[S-01] 유효 CSV 미리보기 후 일괄 등록 성공", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", mockFetch);
    mockFetch.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("CSV 파일 업로드 후 유효 행 미리보기 테이블이 표시된다", async () => {
    render(<FacilitiesImportClient />);

    const input = screen.getByLabelText("CSV 파일 선택");
    act(() => {
      Object.defineProperty(input, "files", {
        value: [makeFile(VALID_CSV)],
        configurable: true,
      });
      fireEvent.change(input);
    });

    await waitFor(() => {
      expect(screen.getByText("유효 행: 2건")).toBeInTheDocument();
    });
    expect(screen.getByText("강남 풋살장")).toBeInTheDocument();
    expect(screen.getByText("강남 야외장")).toBeInTheDocument();
  });

  it("일괄 등록 시작 버튼 클릭 시 POST /api/portal/facilities를 각 유효 행마다 호출한다", async () => {
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ id: "fac-001" }), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      })
    );

    render(<FacilitiesImportClient />);

    const input = screen.getByLabelText("CSV 파일 선택");
    act(() => {
      Object.defineProperty(input, "files", {
        value: [makeFile(VALID_CSV)],
        configurable: true,
      });
      fireEvent.change(input);
    });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /일괄 등록 시작/ })).toBeInTheDocument();
    });

    act(() => {
      fireEvent.click(screen.getByRole("button", { name: /일괄 등록 시작/ }));
    });

    await waitFor(() => {
      expect(screen.getByRole("status")).toBeInTheDocument();
    });

    expect(mockFetch).toHaveBeenCalledTimes(2);
    expect(mockFetch).toHaveBeenCalledWith(
      "/api/portal/facilities",
      expect.objectContaining({ method: "POST" })
    );
  });

  it("모든 행 성공 시 요약에 '성공: 2건'이 표시된다", async () => {
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ id: "fac-001" }), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      })
    );

    render(<FacilitiesImportClient />);

    const input = screen.getByLabelText("CSV 파일 선택");
    act(() => {
      Object.defineProperty(input, "files", {
        value: [makeFile(VALID_CSV)],
        configurable: true,
      });
      fireEvent.change(input);
    });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /일괄 등록 시작/ })).toBeInTheDocument();
    });

    act(() => {
      fireEvent.click(screen.getByRole("button", { name: /일괄 등록 시작/ }));
    });

    await waitFor(() => {
      const status = screen.getByRole("status");
      expect(status).toHaveTextContent("성공:");
      expect(status).toHaveTextContent("2건");
    });
  });
});

describe("[S-02] CSV 파싱 에러 행 표시", () => {
  it("code가 없는 행이 있으면 '오류 행 목록'이 표시된다", async () => {
    render(<FacilitiesImportClient />);

    const input = screen.getByLabelText("CSV 파일 선택");
    act(() => {
      Object.defineProperty(input, "files", {
        value: [makeFile(CSV_WITH_ERROR)],
        configurable: true,
      });
      fireEvent.change(input);
    });

    await waitFor(() => {
      expect(screen.getByText("오류 행 목록")).toBeInTheDocument();
    });
    expect(screen.getByText(/오류 행: 1건/)).toBeInTheDocument();
    expect(screen.getByText(/code: 필수 값이 없습니다/)).toBeInTheDocument();
  });

  it("유효 행이 없고 에러 행만 있으면 '등록 가능한 유효 행이 없습니다' 메시지가 표시된다", async () => {
    const allErrorCsv = [
      "code,name,gu,type,address,lat,lng,parking,tel,homePage,eduYn,meta",
      ",에러만,강북구,INDOOR,서울,37.6,127.1,false,02-9999-9999,,false,",
    ].join("\n");

    render(<FacilitiesImportClient />);

    const input = screen.getByLabelText("CSV 파일 선택");
    act(() => {
      Object.defineProperty(input, "files", {
        value: [makeFile(allErrorCsv)],
        configurable: true,
      });
      fireEvent.change(input);
    });

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(
        "등록 가능한 유효 행이 없습니다"
      );
    });
  });
});

describe("[S-03] POST 실패 행 표시", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", mockFetch);
    mockFetch.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("첫 번째 행 POST 성공, 두 번째 행 POST 실패 시 결과 테이블에 각각 표시된다", async () => {
    mockFetch
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ id: "fac-001" }), {
          status: 201,
          headers: { "Content-Type": "application/json" },
        })
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ message: "이미 존재합니다.", detail: "코드 중복" }), {
          status: 409,
          headers: { "Content-Type": "application/json" },
        })
      );

    render(<FacilitiesImportClient />);

    const input = screen.getByLabelText("CSV 파일 선택");
    act(() => {
      Object.defineProperty(input, "files", {
        value: [makeFile(VALID_CSV)],
        configurable: true,
      });
      fireEvent.change(input);
    });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /일괄 등록 시작/ })).toBeInTheDocument();
    });

    act(() => {
      fireEvent.click(screen.getByRole("button", { name: /일괄 등록 시작/ }));
    });

    await waitFor(() => {
      expect(screen.getByRole("status")).toBeInTheDocument();
    });

    expect(screen.getByText("성공")).toBeInTheDocument();
    expect(screen.getByText(/코드 중복/)).toBeInTheDocument();
  });

  it("완료 요약에 성공 1건 / 실패 1건이 표시된다", async () => {
    mockFetch
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ id: "fac-001" }), {
          status: 201,
          headers: { "Content-Type": "application/json" },
        })
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ message: "서버 오류" }), {
          status: 500,
          headers: { "Content-Type": "application/json" },
        })
      );

    render(<FacilitiesImportClient />);

    const input = screen.getByLabelText("CSV 파일 선택");
    act(() => {
      Object.defineProperty(input, "files", {
        value: [makeFile(VALID_CSV)],
        configurable: true,
      });
      fireEvent.change(input);
    });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /일괄 등록 시작/ })).toBeInTheDocument();
    });

    act(() => {
      fireEvent.click(screen.getByRole("button", { name: /일괄 등록 시작/ }));
    });

    await waitFor(() => {
      const status = screen.getByRole("status");
      expect(status).toHaveTextContent("성공:");
      expect(status).toHaveTextContent("실패:");
      expect(status).toHaveTextContent("1건");
    });
  });
});

describe("[S-04] 비CSV 파일 에러", () => {
  it(".csv가 아닌 파일을 선택하면 파일 에러 메시지가 표시된다", async () => {
    render(<FacilitiesImportClient />);

    const input = screen.getByLabelText("CSV 파일 선택");
    act(() => {
      Object.defineProperty(input, "files", {
        value: [makeFile("some content", "facilities.xlsx", "application/vnd.openxmlformats")],
        configurable: true,
      });
      fireEvent.change(input);
    });

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("CSV 파일(.csv)만 업로드할 수 있습니다.");
    });
  });
});
