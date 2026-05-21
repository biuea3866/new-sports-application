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
  CreateFacilityInputSchema,
  CreateEventInputSchema,
  CreateProductInputSchema,
  RestoreStockInputSchema,
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
        description: "프리미엄 축구공",
        price: 50000,
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
    it("price 0은 통과한다 (비음수)", () => {
      const input = { name: "상품", description: "설명", price: 0 };
      expect(CreateProductInputSchema.safeParse(input).success).toBe(true);
    });

    it("price가 음수이면 실패한다", () => {
      const input = { name: "상품", description: "설명", price: -1 };
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
