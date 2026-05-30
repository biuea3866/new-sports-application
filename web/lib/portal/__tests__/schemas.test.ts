/**
 * U-01: zod 스키마가 각 응답 형태 검증
 */
import { describe, it, expect } from "vitest";
import {
  MyFacilitySchema,
  MyFacilityPageSchema,
  MyEventSchema,
  MyEventDetailSchema,
  MyProductSchema,
  DashboardSummarySchema,
  AdminUserSchema,
  AdminUserPageSchema,
  CreateFacilityInputSchema,
  CreateEventInputSchema,
  CreateProductInputSchema,
  RestoreStockInputSchema,
  PaymentSummarySchema,
  PaymentSummaryPageSchema,
} from "../schemas";

describe("[U-01] zod 스키마 응답 형태 검증", () => {
  describe("MyFacilitySchema", () => {
    it("유효한 Facility 응답을 파싱한다", () => {
      const data = {
        id: "fac-001",
        code: "GN-01",
        name: "강남 풋살장",
        gu: "강남구",
        type: "INDOOR",
        address: "서울특별시 강남구 테헤란로 123",
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
      const result = MyFacilitySchema.safeParse(data);
      expect(result.success).toBe(true);
    });

    it("type이 유효하지 않으면 파싱에 실패한다", () => {
      const data = { id: "x", code: "x", name: "x", gu: "x", type: "UNKNOWN" };
      const result = MyFacilitySchema.safeParse(data);
      expect(result.success).toBe(false);
    });
  });

  describe("MyFacilityPageSchema", () => {
    it("Page<MyFacility> 형태를 파싱한다", () => {
      const data = {
        content: [],
        page: 0,
        size: 10,
        totalElements: 0,
        totalPages: 0,
      };
      const result = MyFacilityPageSchema.safeParse(data);
      expect(result.success).toBe(true);
    });
  });

  describe("MyEventSchema", () => {
    it("유효한 Event 응답을 파싱한다", () => {
      const data = {
        id: 1,
        title: "테스트 이벤트",
        venue: "강남구",
        startsAt: "2026-06-01T10:00:00Z",
        status: "SCHEDULED",
        ownerId: 1,
        totalSeats: 100,
        soldSeats: 0,
        availableSeats: 100,
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
      };
      const result = MyEventSchema.safeParse(data);
      expect(result.success).toBe(true);
    });

    it("status가 CLOSED인 이벤트를 파싱한다", () => {
      const data = {
        id: 2,
        title: "마감 이벤트",
        venue: "서초구",
        startsAt: "2026-05-01T10:00:00Z",
        status: "CLOSED",
        ownerId: 1,
        totalSeats: 50,
        soldSeats: 50,
        availableSeats: 0,
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
      };
      const result = MyEventSchema.safeParse(data);
      expect(result.success).toBe(true);
    });
  });

  describe("MyEventDetailSchema", () => {
    it("seats 배열이 포함된 EventDetail을 파싱한다", () => {
      const data = {
        id: 1,
        title: "이벤트",
        venue: "venue",
        startsAt: "2026-06-01T10:00:00Z",
        status: "OPEN",
        ownerId: 1,
        totalSeats: 2,
        soldSeats: 1,
        availableSeats: 1,
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
        seats: [
          { id: 1, label: "A1", sold: true },
          { id: 2, label: "A2", sold: false },
        ],
      };
      const result = MyEventDetailSchema.safeParse(data);
      expect(result.success).toBe(true);
    });
  });

  describe("MyProductSchema", () => {
    it("유효한 Product 응답을 파싱한다", () => {
      const data = {
        id: 1,
        name: "축구공",
        category: "EQUIPMENT",
        description: "프리미엄 축구공",
        price: 50000,
        imageUrl: "https://cdn.example.com/products/ball.png",
        status: "ACTIVE",
        stockQuantity: 10,
        ownerId: 1,
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
      };
      const result = MyProductSchema.safeParse(data);
      expect(result.success).toBe(true);
    });
  });

  describe("DashboardSummarySchema", () => {
    it("모든 필드가 있는 Dashboard를 파싱한다", () => {
      const data = {
        facilities: { totalFacilities: 3, totalSlots: 15 },
        events: { totalEvents: 5, scheduledEvents: 2, openEvents: 2 },
        products: { totalProducts: 10, activeProducts: 8, outOfStockProducts: 1 },
      };
      const result = DashboardSummarySchema.safeParse(data);
      expect(result.success).toBe(true);
    });

    it("모든 필드가 null인 Dashboard를 파싱한다", () => {
      const data = { facilities: null, events: null, products: null };
      const result = DashboardSummarySchema.safeParse(data);
      expect(result.success).toBe(true);
    });
  });

  describe("[U-02] AdminUserSchema", () => {
    it("유효한 AdminUser 응답을 파싱한다", () => {
      const data = {
        userId: 1,
        email: "admin@example.com",
        status: "ACTIVE",
        roleNames: ["ADMIN"],
        joinedAt: "2026-01-01T00:00:00Z",
      };
      const result = AdminUserSchema.safeParse(data);
      expect(result.success).toBe(true);
    });

    it("status가 SUSPENDED인 회원을 파싱한다", () => {
      const data = {
        userId: 2,
        email: "user@example.com",
        status: "SUSPENDED",
        roleNames: [],
        joinedAt: "2026-01-15T09:00:00Z",
      };
      const result = AdminUserSchema.safeParse(data);
      expect(result.success).toBe(true);
    });

    it("roleNames가 빈 배열인 회원을 파싱한다", () => {
      const data = {
        userId: 3,
        email: "user2@example.com",
        status: "INACTIVE",
        roleNames: [],
        joinedAt: "2026-02-01T00:00:00Z",
      };
      const result = AdminUserSchema.safeParse(data);
      expect(result.success).toBe(true);
    });

    it("유효하지 않은 status는 파싱에 실패한다", () => {
      const data = {
        userId: 1,
        email: "admin@example.com",
        status: "BANNED",
        roleNames: [],
        joinedAt: "2026-01-01T00:00:00Z",
      };
      const result = AdminUserSchema.safeParse(data);
      expect(result.success).toBe(false);
    });

    it("email 형식이 유효하지 않으면 파싱에 실패한다", () => {
      const data = {
        userId: 1,
        email: "not-an-email",
        status: "ACTIVE",
        roleNames: [],
        joinedAt: "2026-01-01T00:00:00Z",
      };
      const result = AdminUserSchema.safeParse(data);
      expect(result.success).toBe(false);
    });
  });

  describe("[U-03] AdminUserPageSchema", () => {
    it("Page<AdminUser> 형태를 파싱한다", () => {
      const data = {
        content: [
          {
            userId: 1,
            email: "admin@example.com",
            status: "ACTIVE",
            roleNames: ["ADMIN"],
            joinedAt: "2026-01-01T00:00:00Z",
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      };
      const result = AdminUserPageSchema.safeParse(data);
      expect(result.success).toBe(true);
    });

    it("content가 빈 배열인 경우를 파싱한다", () => {
      const data = {
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      };
      const result = AdminUserPageSchema.safeParse(data);
      expect(result.success).toBe(true);
    });
  });
});

describe("입력 스키마 검증", () => {
  describe("CreateFacilityInputSchema", () => {
    it("필수 필드가 모두 있으면 통과한다", () => {
      const input = {
        code: "GN-01",
        name: "강남 풋살장",
        gu: "강남구",
        type: "INDOOR",
        address: "서울특별시 강남구",
        location: "37.5,127.0",
        parking: true,
        tel: "02-1234-5678",
        eduYn: false,
      };
      expect(CreateFacilityInputSchema.safeParse(input).success).toBe(true);
    });

    it("code가 빈 문자열이면 실패한다", () => {
      const input = {
        code: "",
        name: "강남 풋살장",
        gu: "강남구",
        type: "INDOOR",
        address: "서울특별시 강남구",
        location: "37.5,127.0",
        parking: true,
        tel: "02-1234-5678",
        eduYn: false,
      };
      expect(CreateFacilityInputSchema.safeParse(input).success).toBe(false);
    });
  });

  describe("CreateEventInputSchema", () => {
    it("좌석 500개까지 허용한다", () => {
      const input = {
        title: "이벤트",
        venue: "venue",
        startsAt: "2026-06-01T10:00:00.000Z",
        seats: Array.from({ length: 500 }, (_, i) => `A${i + 1}`),
      };
      expect(CreateEventInputSchema.safeParse(input).success).toBe(true);
    });

    it("좌석 501개는 실패한다", () => {
      const input = {
        title: "이벤트",
        venue: "venue",
        startsAt: "2026-06-01T10:00:00.000Z",
        seats: Array.from({ length: 501 }, (_, i) => `A${i + 1}`),
      };
      const result = CreateEventInputSchema.safeParse(input);
      expect(result.success).toBe(false);
    });

    it("좌석 0개는 실패한다", () => {
      const input = {
        title: "이벤트",
        venue: "venue",
        startsAt: "2026-06-01T10:00:00.000Z",
        seats: [],
      };
      expect(CreateEventInputSchema.safeParse(input).success).toBe(false);
    });
  });

  describe("CreateProductInputSchema", () => {
    it("price 0은 실패한다 (positive 검증)", () => {
      const input = {
        name: "상품",
        category: "EQUIPMENT",
        description: "설명",
        price: 0,
        imageUrl: "https://cdn.example.com/x.png",
      };
      expect(CreateProductInputSchema.safeParse(input).success).toBe(false);
    });

    it("price가 음수이면 실패한다", () => {
      const input = {
        name: "상품",
        category: "EQUIPMENT",
        description: "설명",
        price: -1,
        imageUrl: "https://cdn.example.com/x.png",
      };
      expect(CreateProductInputSchema.safeParse(input).success).toBe(false);
    });
  });

  describe("RestoreStockInputSchema", () => {
    it("quantity 1 이상이면 통과한다", () => {
      expect(RestoreStockInputSchema.safeParse({ quantity: 1 }).success).toBe(true);
    });

    it("quantity 0이면 실패한다", () => {
      expect(RestoreStockInputSchema.safeParse({ quantity: 0 }).success).toBe(false);
    });

    it("quantity 음수이면 실패한다", () => {
      expect(RestoreStockInputSchema.safeParse({ quantity: -5 }).success).toBe(false);
    });
  });
});

describe("[U-04] PaymentSummarySchema 검증", () => {
  const validPayment = {
    id: 1,
    orderType: "BOOKING",
    orderId: 10,
    method: "KAKAO",
    amount: 50000,
    status: "COMPLETED",
    createdAt: "2026-06-01T09:00:00Z",
    paidAt: "2026-06-01T09:01:00Z",
    pgTransactionId: "pg-tx-001",
    provider: "kakao",
  };

  it("유효한 Payment 응답을 파싱한다", () => {
    expect(PaymentSummarySchema.safeParse(validPayment).success).toBe(true);
  });

  it("paidAt이 null인 PENDING 결제를 파싱한다", () => {
    const pending = { ...validPayment, status: "PENDING", paidAt: null, pgTransactionId: null, provider: null };
    expect(PaymentSummarySchema.safeParse(pending).success).toBe(true);
  });

  it("pgTransactionId/provider가 없는 응답(BE-07 이전)을 파싱한다", () => {
    const { pgTransactionId: _pg, provider: _pv, ...withoutPg } = validPayment;
    expect(PaymentSummarySchema.safeParse(withoutPg).success).toBe(true);
  });

  it("status가 유효하지 않으면 파싱에 실패한다", () => {
    const invalid = { ...validPayment, status: "UNKNOWN" };
    expect(PaymentSummarySchema.safeParse(invalid).success).toBe(false);
  });

  it("method가 유효하지 않으면 파싱에 실패한다", () => {
    const invalid = { ...validPayment, method: "PAYPAL" };
    expect(PaymentSummarySchema.safeParse(invalid).success).toBe(false);
  });

  it("orderType이 유효하지 않으면 파싱에 실패한다", () => {
    const invalid = { ...validPayment, orderType: "SUBSCRIPTION" };
    expect(PaymentSummarySchema.safeParse(invalid).success).toBe(false);
  });

  it("PaymentSummaryPageSchema — content가 있는 페이지를 파싱한다", () => {
    const page = {
      content: [validPayment],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };
    expect(PaymentSummaryPageSchema.safeParse(page).success).toBe(true);
  });

  it("PaymentSummaryPageSchema — content가 빈 배열인 페이지를 파싱한다", () => {
    const page = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
    expect(PaymentSummaryPageSchema.safeParse(page).success).toBe(true);
  });
});
