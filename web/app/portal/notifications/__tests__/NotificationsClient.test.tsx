// @vitest-environment jsdom
/**
 * 포털 "알림 수신함" 카드 렌더링 계약.
 *
 * 회귀 방지: BE `GET /notifications/me` 는 발송 내부 값(channel·templateId·status)이 아니라
 * 사용자 관점 필드(title·content·category·isRead)를 담은 `MyNotificationResponse` 를 준다.
 * FE 타입이 리팩터링 이전 형태에 멈춰 있어 모든 필드가 undefined 로 읽혔고, 카드 7장이
 * `채널: · 상태:` 만 남긴 채 제목·본문 없이 비어 보였다(02-파트너포털/17 캡쳐).
 *
 * 같은 데이터를 쓰는 알림센터(16)는 정상이었으므로 데이터가 아니라 이 화면의 필드 매핑 문제다.
 */
import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockFetchMyNotifications = vi.fn();
const mockMarkNotificationRead = vi.fn();
vi.mock("@/lib/portal/notifications", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/portal/notifications")>();
  return {
    ...actual,
    fetchMyNotifications: (params: unknown) => mockFetchMyNotifications(params) as unknown,
    markNotificationRead: (id: number) => mockMarkNotificationRead(id) as unknown,
  };
});

import NotificationsClient from "../NotificationsClient";

/** BE `MyNotificationResponse` 그대로의 형태. */
function buildNotification(overrides: Record<string, unknown> = {}) {
  return {
    id: 6,
    title: "신규 예약 접수",
    content: "내 시설에 예약이 접수됐습니다. 예약 번호 6",
    category: "BOOKING",
    isRead: false,
    readAt: null,
    createdAt: "2026-08-03T15:31:29+09:00",
    ...overrides,
  };
}

function buildPage(content: ReturnType<typeof buildNotification>[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: 1,
    page: 0,
    size: 20,
  };
}

describe("포털 알림 수신함", () => {
  beforeEach(() => {
    mockFetchMyNotifications.mockReset();
    mockMarkNotificationRead.mockReset();
  });

  it("알림 제목과 본문을 보여준다", async () => {
    mockFetchMyNotifications.mockResolvedValue(buildPage([buildNotification()]));

    render(<NotificationsClient />);

    await waitFor(() => {
      expect(screen.getByText("신규 예약 접수")).toBeInTheDocument();
    });
    expect(screen.getByText("내 시설에 예약이 접수됐습니다. 예약 번호 6")).toBeInTheDocument();
  });

  it("발송 내부 값(채널·상태) 라벨을 노출하지 않는다", async () => {
    mockFetchMyNotifications.mockResolvedValue(buildPage([buildNotification()]));

    render(<NotificationsClient />);

    await waitFor(() => {
      expect(screen.getByText("신규 예약 접수")).toBeInTheDocument();
    });
    // 값이 빈 `채널: · 상태:` 만 남던 카드가 결함의 증상이었다.
    expect(screen.queryByText(/채널:/)).not.toBeInTheDocument();
    expect(screen.queryByText(/상태:/)).not.toBeInTheDocument();
  });

  it("분류를 한글 배지로 보여준다", async () => {
    mockFetchMyNotifications.mockResolvedValue(
      buildPage([buildNotification({ category: "PAYMENT" })])
    );

    render(<NotificationsClient />);

    await waitFor(() => {
      expect(screen.getByText("결제")).toBeInTheDocument();
    });
    expect(screen.queryByText("PAYMENT")).not.toBeInTheDocument();
  });

  it("모르는 분류가 와도 화면이 죽지 않는다", async () => {
    mockFetchMyNotifications.mockResolvedValue(
      buildPage([buildNotification({ category: "BRAND_NEW" })])
    );

    render(<NotificationsClient />);

    await waitFor(() => {
      expect(screen.getByText("신규 예약 접수")).toBeInTheDocument();
    });
  });

  it("읽은 알림에는 읽음 처리 버튼을 두지 않는다", async () => {
    mockFetchMyNotifications.mockResolvedValue(
      buildPage([buildNotification({ isRead: true, readAt: "2026-08-03T16:00:00+09:00" })])
    );

    render(<NotificationsClient />);

    await waitFor(() => {
      expect(screen.getByText("신규 예약 접수")).toBeInTheDocument();
    });
    expect(screen.queryByRole("button", { name: /읽음 처리/ })).not.toBeInTheDocument();
  });

  it("미읽음 알림에는 읽음 처리 버튼을 둔다", async () => {
    mockFetchMyNotifications.mockResolvedValue(buildPage([buildNotification()]));

    render(<NotificationsClient />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /읽음 처리/ })).toBeInTheDocument();
    });
  });

  it("알림이 없으면 빈 상태 문구를 보여준다", async () => {
    mockFetchMyNotifications.mockResolvedValue(buildPage([]));

    render(<NotificationsClient />);

    await waitFor(() => {
      expect(screen.getByText("알림이 없습니다.")).toBeInTheDocument();
    });
  });

  it("조회 실패 시 오류 메시지를 보여준다", async () => {
    mockFetchMyNotifications.mockRejectedValue(new Error("알림 목록을 불러오지 못했습니다."));

    render(<NotificationsClient />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("알림 목록을 불러오지 못했습니다.");
    });
  });
});
