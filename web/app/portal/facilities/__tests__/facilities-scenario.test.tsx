// @vitest-environment jsdom
/**
 * 시설 페이지 시나리오 테스트
 * S-01: 등록 성공 시 /api/portal/facilities POST를 호출하고 목록 페이지로 이동한다
 * S-02: 삭제 시 BE가 409를 반환하면 사용자 친화 메시지가 화면에 표시된다
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import * as React from "react";

// next/navigation 모킹
const mockPush = vi.fn();
const mockParams: { id: string } = { id: "fac-001" };

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
  useParams: () => mockParams,
}));

// ToastProvider 없이 useToast를 쓰면 throw하므로 모킹
vi.mock("@/components/ui/toast", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/components/ui/toast")>();
  return {
    ...original,
    useToast: () => ({ addToast: vi.fn(), toasts: [], removeToast: vi.fn() }),
    ToastProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  };
});

const mockFetch = vi.fn();

describe("[S-01] 시설 등록 성공 시 목록으로 이동", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", mockFetch);
    mockFetch.mockReset();
    mockPush.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("POST /api/portal/facilities 성공 시 router.push('/portal/facilities')를 호출한다", async () => {
    const { default: NewFacilityPage } = await import("../new/page");

    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          id: "fac-001",
          code: "GN-01",
          name: "강남 풋살장",
          gu: "강남구",
          type: "INDOOR",
          address: "서울특별시 강남구",
          location: "37.5,127.0",
          parking: true,
          tel: "02-1234-5678",
          homePage: null,
          eduYn: false,
          meta: null,
          ownerUserId: 1,
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
        }),
        { status: 201, headers: { "Content-Type": "application/json" } }
      )
    );

    render(<NewFacilityPage />);

    // 필수 필드 입력
    fireEvent.change(screen.getByLabelText(/시설 코드/i), { target: { value: "GN-01" } });
    fireEvent.change(screen.getByLabelText(/시설명/i), { target: { value: "강남 풋살장" } });
    fireEvent.change(screen.getByLabelText(/구/i), { target: { value: "강남구" } });
    fireEvent.change(screen.getByLabelText(/주소/i), { target: { value: "서울특별시 강남구" } });
    fireEvent.change(screen.getByLabelText(/위치 좌표/i), { target: { value: "37.5,127.0" } });
    fireEvent.change(screen.getByLabelText(/전화번호/i), { target: { value: "02-1234-5678" } });

    act(() => {
      fireEvent.click(screen.getByRole("button", { name: /등록/i }));
    });

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/portal/facilities",
        expect.objectContaining({ method: "POST" })
      );
      expect(mockPush).toHaveBeenCalledWith("/portal/facilities");
    });
  });

  it("시/도를 선택하고 등록하면 POST body에 sido 코드가 포함된다", async () => {
    const { default: NewFacilityPage } = await import("../new/page");

    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          id: "fac-001",
          code: "GN-01",
          name: "강남 풋살장",
          gu: "강남구",
          sido: "11",
          type: "INDOOR",
          address: "서울특별시 강남구",
          location: "37.5,127.0",
          parking: true,
          tel: "02-1234-5678",
          homePage: null,
          eduYn: false,
          meta: null,
          ownerUserId: 1,
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
        }),
        { status: 201, headers: { "Content-Type": "application/json" } }
      )
    );

    render(<NewFacilityPage />);

    fireEvent.change(screen.getByLabelText(/시설 코드/i), { target: { value: "GN-01" } });
    fireEvent.change(screen.getByLabelText(/시설명/i), { target: { value: "강남 풋살장" } });
    fireEvent.change(screen.getByLabelText(/^구/i), { target: { value: "강남구" } });
    fireEvent.change(screen.getByLabelText(/주소/i), { target: { value: "서울특별시 강남구" } });
    fireEvent.change(screen.getByLabelText(/위치 좌표/i), { target: { value: "37.5,127.0" } });
    fireEvent.change(screen.getByLabelText(/전화번호/i), { target: { value: "02-1234-5678" } });
    fireEvent.change(screen.getByRole("combobox", { name: "시/도" }), { target: { value: "11" } });

    act(() => {
      fireEvent.click(screen.getByRole("button", { name: /등록/i }));
    });

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/portal/facilities",
        expect.objectContaining({
          method: "POST",
          body: expect.stringContaining('"sido":"11"'),
        })
      );
    });
  });

  it("POST 실패 시 에러 메시지가 표시되고 이동하지 않는다", async () => {
    const { default: NewFacilityPage } = await import("../new/page");

    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ message: "등록 중 오류가 발생했습니다." }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      })
    );

    render(<NewFacilityPage />);

    fireEvent.change(screen.getByLabelText(/시설 코드/i), { target: { value: "GN-01" } });
    fireEvent.change(screen.getByLabelText(/시설명/i), { target: { value: "강남 풋살장" } });
    fireEvent.change(screen.getByLabelText(/구/i), { target: { value: "강남구" } });
    fireEvent.change(screen.getByLabelText(/주소/i), { target: { value: "서울특별시 강남구" } });
    fireEvent.change(screen.getByLabelText(/위치 좌표/i), { target: { value: "37.5,127.0" } });
    fireEvent.change(screen.getByLabelText(/전화번호/i), { target: { value: "02-1234-5678" } });

    fireEvent.click(screen.getByRole("button", { name: /등록/i }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
    expect(mockPush).not.toHaveBeenCalled();
  });
});

describe("[S-02] 시설 삭제 409 응답 시 사용자 친화 메시지 표시", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", mockFetch);
    mockFetch.mockReset();
    mockPush.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("삭제 시 409 응답이 오면 '활성 슬롯이 있는 시설은 삭제할 수 없습니다.' 메시지가 표시된다", async () => {
    const facilityData = {
      id: "fac-001",
      code: "GN-01",
      name: "강남 풋살장",
      gu: "강남구",
      type: "INDOOR",
      address: "서울특별시 강남구",
      location: "37.5,127.0",
      parking: true,
      tel: "02-1234-5678",
      homePage: null,
      eduYn: false,
      meta: null,
      ownerUserId: 1,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    };

    // GET 성공, DELETE 409
    mockFetch
      .mockResolvedValueOnce(
        new Response(JSON.stringify(facilityData), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            message: "이미 존재하거나 충돌이 발생했습니다.",
            detail: "활성 슬롯이 있는 시설은 삭제할 수 없습니다.",
          }),
          {
            status: 409,
            headers: { "Content-Type": "application/json" },
          }
        )
      );

    const { default: FacilityDetailPage } = await import("../[id]/page");
    render(<FacilityDetailPage />);

    // 데이터 로드 완료 대기
    await waitFor(() => {
      expect(screen.getByText("강남 풋살장")).toBeInTheDocument();
    });

    // 삭제 버튼 클릭 → confirm 다이얼로그 열기
    fireEvent.click(screen.getByRole("button", { name: /삭제/i }));

    // 다이얼로그의 삭제 확인 버튼 클릭
    fireEvent.click(screen.getByRole("button", { name: "삭제 확인" }));

    // 409 메시지가 표시되는지 확인
    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(
        "활성 슬롯이 있는 시설은 삭제할 수 없습니다."
      );
    });

    // 목록 페이지로 이동하지 않는다
    expect(mockPush).not.toHaveBeenCalledWith("/portal/facilities");
  });

  it("삭제 409 시 detail이 없으면 기본 메시지가 표시된다", async () => {
    const facilityData = {
      id: "fac-001",
      code: "GN-01",
      name: "강남 풋살장",
      gu: "강남구",
      type: "INDOOR",
      address: "서울특별시 강남구",
      location: "37.5,127.0",
      parking: true,
      tel: "02-1234-5678",
      homePage: null,
      eduYn: false,
      meta: null,
      ownerUserId: 1,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    };

    mockFetch
      .mockResolvedValueOnce(
        new Response(JSON.stringify(facilityData), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ message: "이미 존재하거나 충돌이 발생했습니다." }),
          { status: 409, headers: { "Content-Type": "application/json" } }
        )
      );

    const { default: FacilityDetailPage } = await import("../[id]/page");
    render(<FacilityDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("강남 풋살장")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /삭제/i }));

    fireEvent.click(screen.getByRole("button", { name: "삭제 확인" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(
        "활성 슬롯이 있는 시설은 삭제할 수 없습니다."
      );
    });
  });
});
