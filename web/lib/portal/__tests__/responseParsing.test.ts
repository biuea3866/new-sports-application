/**
 * BFF 응답 파싱 계약 — "검증을 실제로 하는가" + "미지 값 한 건에 목록 전체가 죽지 않는가".
 *
 * 두 가지 반대 방향의 실패를 함께 막는다:
 *
 * 1. **파싱을 안 해서 조용히 비는 것** — `fetchMyNotifications` 가 `as` 캐스팅만 하던 탓에 BE 가
 *    응답 필드를 바꿨을 때 오류 없이 전 필드가 `undefined` 가 되어 카드 7장이 통째로 비었다
 *    (02-파트너포털/17 캡쳐). 스키마를 정의만 해 두면 런타임에서 죽은 코드다.
 * 2. **파싱이 너무 엄해서 통째로 죽는 것** — 계약 밖 상태값 한 건에 `sales[]` 전량 파싱이 실패해
 *    매출 화면이 `총 0건` 이 됐다(02-파트너포털/14 캡쳐). enum 을 넓히기만 하면 다음 값에서 같은
 *    사고가 반복된다 — 미지 값은 **그 행만** 원문으로 남고 나머지는 보여야 한다.
 */
import { describe, it, expect, vi, afterEach } from "vitest";
import { fetchMyNotifications } from "../notifications";
import { fetchPartnerSales } from "../payments";

function stubFetchJson(body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn(() => Promise.resolve(new Response(JSON.stringify(body), { status: 200 })))
  );
}

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

function buildSale(overrides: Record<string, unknown> = {}) {
  return {
    paymentId: 15,
    orderType: "GOODS",
    orderId: 2,
    sellerAmount: 158000,
    method: "CREDIT_CARD",
    provider: "TOSS",
    status: "COMPLETED",
    paidAt: "2026-07-20T12:00:00+09:00",
    pgTransactionId: "tid-15",
    ...overrides,
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("fetchMyNotifications — 검증을 실제로 수행한다", () => {
  it("계약대로면 파싱해서 돌려준다", async () => {
    stubFetchJson({ content: [buildNotification()], totalElements: 1, totalPages: 1, page: 0, size: 20 });

    const page = await fetchMyNotifications();

    expect(page.content[0]?.title).toBe("신규 예약 접수");
    expect(page.content[0]?.content).toBe("내 시설에 예약이 접수됐습니다. 예약 번호 6");
  });

  // 이 테스트가 p1(캐스팅만 하던 구조)의 재발을 직접 막는다 — 캐스팅으로 되돌리면 통과해 버린다.
  it("제목·본문이 빠진 응답은 조용히 통과시키지 않고 실패한다", async () => {
    const { title, ...withoutTitle } = buildNotification();
    void title;
    stubFetchJson({ content: [withoutTitle], totalElements: 1, totalPages: 1, page: 0, size: 20 });

    await expect(fetchMyNotifications()).rejects.toThrow();
  });

  it("옛 계약(channel·templateId·status) 응답이 오면 실패한다", async () => {
    stubFetchJson({
      content: [
        {
          id: 6,
          userId: 1,
          channel: "IN_APP",
          templateId: "booking-received-owner",
          status: "SENT",
          sentAt: null,
          readAt: null,
          createdAt: "2026-08-03T15:31:29+09:00",
        },
      ],
      totalElements: 1,
      totalPages: 1,
      page: 0,
      size: 20,
    });

    await expect(fetchMyNotifications()).rejects.toThrow();
  });

  // 분류는 BE 가 새로 추가할 수 있는 값이다 — 하나 늘었다고 알림함 전체가 비면 안 된다.
  it("모르는 분류가 섞여도 목록 전체가 살아 있고 그 값은 원문으로 남는다", async () => {
    stubFetchJson({
      content: [buildNotification(), buildNotification({ id: 7, category: "BRAND_NEW" })],
      totalElements: 2,
      totalPages: 1,
      page: 0,
      size: 20,
    });

    const page = await fetchMyNotifications();

    expect(page.content).toHaveLength(2);
    expect(page.content[1]?.category).toBe("BRAND_NEW");
  });
});

describe("fetchPartnerSales — 미지 상태에 목록 전체가 죽지 않는다", () => {
  it("계약대로면 파싱해서 돌려준다", async () => {
    stubFetchJson({ sales: [buildSale()], totalElements: 1, totalPages: 1, page: 0, size: 20 });

    const response = await fetchPartnerSales();

    expect(response.sales[0]?.status).toBe("COMPLETED");
  });

  // 이번 결함과 같은 실패 모드(전량 실패 → 총 0건)의 재발 방지.
  it("계약 밖 상태가 한 건 섞여도 나머지 행이 모두 살아 있다", async () => {
    stubFetchJson({
      sales: [buildSale(), buildSale({ paymentId: 16, status: "SOMETHING_NEW" })],
      totalElements: 2,
      totalPages: 1,
      page: 0,
      size: 20,
    });

    const response = await fetchPartnerSales();

    expect(response.sales).toHaveLength(2);
    // 값을 임의의 기본값으로 바꿔치기하지 않는다 — 원문이 남아야 무슨 값이 새로 생겼는지 알 수 있다.
    expect(response.sales[1]?.status).toBe("SOMETHING_NEW");
  });

  it("BE 상태 6종은 그대로 파싱된다", async () => {
    const statuses = ["PENDING", "READY", "COMPLETED", "CANCELLED", "FAILED", "REFUNDED"];
    stubFetchJson({
      sales: statuses.map((status, index) => buildSale({ paymentId: 100 + index, status })),
      totalElements: statuses.length,
      totalPages: 1,
      page: 0,
      size: 20,
    });

    const response = await fetchPartnerSales();

    expect(response.sales.map((sale) => sale.status)).toEqual(statuses);
  });

  // 내성은 상태값에만 준다 — 구조가 깨진 응답은 여전히 즉시 실패해야 한다.
  it("구조가 깨진 응답(sellerAmount 누락)은 실패한다", async () => {
    const { sellerAmount, ...withoutAmount } = buildSale();
    void sellerAmount;
    stubFetchJson({ sales: [withoutAmount], totalElements: 1, totalPages: 1, page: 0, size: 20 });

    await expect(fetchPartnerSales()).rejects.toThrow();
  });
});
