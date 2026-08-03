// @vitest-environment jsdom
/**
 * 포털 "예약 관리" 표의 결제 상태 컬럼 표기 계약.
 *
 * 회귀 방지: 같은 표에서 `상태` 컬럼은 `확정`·`대기`로 한글화돼 있는데 `결제 상태` 컬럼만
 * `COMPLETED` 영문 원문이 노출됐다(02-파트너포털/07 캡쳐). 매출 화면과 같은 라벨 매핑을
 * 공유해 화면마다 표기가 갈리지 않게 한다.
 */
import { render, screen, waitFor, within } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockFetchMyBookings = vi.fn();
vi.mock("@/lib/portal/bookings", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/portal/bookings")>();
  return {
    ...actual,
    fetchMyBookings: (params: unknown) => mockFetchMyBookings(params) as unknown,
  };
});

vi.mock("@/components/ui/toast", () => ({
  useToast: () => ({ addToast: vi.fn() }),
}));

import BookingsPage from "../page";

function buildBooking(paymentStatus: string | null) {
  return {
    id: 42,
    slotId: 7,
    userId: 3,
    status: "CONFIRMED",
    paymentId: 15,
    paymentStatus,
    createdAt: "2026-07-20T12:00:00+09:00",
    updatedAt: "2026-07-20T12:00:00+09:00",
  };
}

function buildResponse(paymentStatus: string | null) {
  return {
    bookings: [buildBooking(paymentStatus)],
    totalElements: 1,
    totalPages: 1,
    page: 0,
    size: 10,
  };
}

describe("예약 관리 — 결제 상태 표기", () => {
  beforeEach(() => {
    mockFetchMyBookings.mockReset();
  });

  it("결제 상태를 한글 라벨로 보여준다", async () => {
    mockFetchMyBookings.mockResolvedValue(buildResponse("COMPLETED"));

    render(<BookingsPage />);

    const row = await screen.findByRole("row", { name: /42/ });
    expect(within(row).getByText("완료")).toBeInTheDocument();
    expect(within(row).queryByText("COMPLETED")).not.toBeInTheDocument();
  });

  it("READY 결제도 한글 라벨로 보여준다", async () => {
    mockFetchMyBookings.mockResolvedValue(buildResponse("READY"));

    render(<BookingsPage />);

    const row = await screen.findByRole("row", { name: /42/ });
    expect(within(row).getByText("승인 대기")).toBeInTheDocument();
  });

  it("결제 상태가 없으면 자리표시자를 보여준다", async () => {
    mockFetchMyBookings.mockResolvedValue(buildResponse(null));

    render(<BookingsPage />);

    const row = await screen.findByRole("row", { name: /42/ });
    expect(within(row).getByText("-")).toBeInTheDocument();
  });

  it("계약에 없는 결제 상태가 와도 화면이 죽지 않는다", async () => {
    mockFetchMyBookings.mockResolvedValue(buildResponse("SOMETHING_NEW"));

    render(<BookingsPage />);

    await waitFor(() => {
      expect(screen.getByRole("row", { name: /42/ })).toBeInTheDocument();
    });
    expect(screen.queryByText("예약이 없습니다.")).not.toBeInTheDocument();
  });
});
