// @vitest-environment jsdom
/**
 * 시설 상세 페이지 — 시/도 표시 + 대기질 카드 통합 테스트 (FE-09).
 * useAirQuality(FE-06)·AirQualityCard(FE-07)를 재사용해 조립하는 컨테이너 동작만 검증한다.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import * as React from "react";

const mockPush = vi.fn();
const mockParams: { id: string } = { id: "fac-001" };

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
  useParams: () => mockParams,
}));

vi.mock("@/components/ui/toast", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/components/ui/toast")>();
  return {
    ...original,
    useToast: () => ({ addToast: vi.fn(), toasts: [], removeToast: vi.fn() }),
    ToastProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  };
});

const mockFetch = vi.fn();

function baseFacility(overrides: Record<string, unknown> = {}) {
  return {
    id: "fac-001",
    code: "BS-01",
    name: "해운대 풋살장",
    gu: "해운대구",
    sidoCode: "26",
    sidoName: "부산광역시",
    sigunguCode: "26290",
    sigunguName: "해운대구",
    type: "OUTDOOR",
    address: "부산 해운대구 ...",
    location: "35.1631,129.1636",
    parking: true,
    tel: "051-000-0000",
    homePage: null,
    eduYn: false,
    meta: null,
    ownerUserId: 1,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

const AIR_QUALITY_SUCCESS = {
  pm10: 92,
  pm25: 41,
  pm10Grade: "BAD",
  pm25Grade: "MODERATE",
  representativeGrade: "BAD",
  stationName: "해운대구",
  measuredAt: "2026-07-05T14:00:00+09:00",
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function toUrlString(input: RequestInfo | URL): string {
  if (typeof input === "string") return input;
  if (input instanceof URL) return input.toString();
  return input.url;
}

function stubFetchByUrl(handlers: {
  facility?: unknown;
  facilityStatus?: number;
  airQuality?: unknown;
  airQualityStatus?: number;
}) {
  mockFetch.mockImplementation((input: RequestInfo | URL) => {
    const url = toUrlString(input);
    if (url.includes("/air-quality")) {
      if (handlers.airQuality === undefined) {
        return Promise.reject(new Error("air-quality mock not configured"));
      }
      return Promise.resolve(jsonResponse(handlers.airQuality, handlers.airQualityStatus ?? 200));
    }
    if (url.includes("/api/portal/facilities/")) {
      return Promise.resolve(jsonResponse(handlers.facility, handlers.facilityStatus ?? 200));
    }
    return Promise.reject(new Error(`unexpected fetch url: ${url}`));
  });
}

describe("시설 상세 — 시/도 표시", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", mockFetch);
    mockFetch.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("시/도명이 지정돼 있으면 그대로 표시된다", async () => {
    stubFetchByUrl({ facility: baseFacility({ sidoName: "부산광역시" }) });

    const { default: FacilityDetailPage } = await import("../[id]/page");
    render(<FacilityDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("부산광역시")).toBeInTheDocument();
    });
  });

  it("시/도명이 미지정이면 '지역 미확인'으로 표시된다", async () => {
    stubFetchByUrl({
      facility: baseFacility({ sidoName: "미지정", location: "" }),
    });

    const { default: FacilityDetailPage } = await import("../[id]/page");
    render(<FacilityDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("지역 미확인")).toBeInTheDocument();
    });
  });
});

describe("시설 상세 — 대기질 카드", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", mockFetch);
    mockFetch.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("location 좌표로 대기질 카드가 success 상태로 렌더된다", async () => {
    stubFetchByUrl({
      facility: baseFacility(),
      airQuality: AIR_QUALITY_SUCCESS,
    });

    const { default: FacilityDetailPage } = await import("../[id]/page");
    render(<FacilityDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("해운대 풋살장")).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText("나쁨")).toBeInTheDocument();
    });
    expect(screen.getByText(/92/)).toBeInTheDocument();
  });

  it("대기질 조회 실패 시 상세 본체는 정상이고 카드만 폴백 문구를 표시한다", async () => {
    stubFetchByUrl({
      facility: baseFacility(),
      airQuality: { message: "서버 오류" },
      airQualityStatus: 500,
    });

    const { default: FacilityDetailPage } = await import("../[id]/page");
    render(<FacilityDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("해운대 풋살장")).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText("대기질 정보를 불러올 수 없습니다")).toBeInTheDocument();
    });
    // 본체 정보는 정상 표시된다
    expect(screen.getByText("부산광역시")).toBeInTheDocument();
  });

  it("location 파싱이 실패하면 대기질 카드가 렌더되지 않고 상세는 정상 표시된다", async () => {
    stubFetchByUrl({
      facility: baseFacility({ location: "invalid-location" }),
    });

    const { default: FacilityDetailPage } = await import("../[id]/page");
    render(<FacilityDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("해운대 풋살장")).toBeInTheDocument();
    });

    expect(screen.queryByText(/대기질/)).not.toBeInTheDocument();
    expect(mockFetch).not.toHaveBeenCalledWith(
      expect.stringContaining("/air-quality"),
      expect.anything()
    );
  });
});

describe("시설 상세 — 수정 모드 시/도", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", mockFetch);
    mockFetch.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("edit 모드에서 시/도 선택이 defaultValue로 채워지고 수정 제출에 sido가 포함된다", async () => {
    stubFetchByUrl({
      facility: baseFacility(),
      airQuality: AIR_QUALITY_SUCCESS,
    });

    const { default: FacilityDetailPage } = await import("../[id]/page");
    render(<FacilityDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("해운대 풋살장")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /수정/i }));

    const sidoSelect = screen.getByRole("combobox", { name: "시/도" });
    expect(sidoSelect).toHaveValue("26");

    mockFetch.mockImplementationOnce(() =>
      Promise.resolve(jsonResponse(baseFacility({ sido: "26" })))
    );

    fireEvent.click(screen.getByRole("button", { name: /저장/i }));

    await waitFor(() => {
      const patchCall = mockFetch.mock.calls.find(
        (call) => (call[1] as RequestInit | undefined)?.method === "PATCH"
      );
      expect(patchCall).toBeDefined();
      expect(patchCall?.[1]?.body).toEqual(expect.stringContaining('"sido":"26"'));
    });
  });
});
