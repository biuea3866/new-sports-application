/**
 * BE가 null 필드의 키를 통째로 생략해도 포털 스키마가 견디는지 검증한다.
 *
 * BE 전역 ObjectMapper가 NON_NULL로 직렬화하면 값이 null인 필드는 키 자체가 응답에서 빠진다.
 * `z.string().nullable()`은 null은 받지만 undefined는 거부하므로, 키가 빠진 순간 파싱이 실패해
 * 화면이 통째로 비거나 오류로 떨어진다. null과 키 생략 양쪽을 모두 받아야 한다.
 */
import { describe, it, expect } from "vitest";
import {
  MyFacilitySchema,
  NotificationSchema,
  BookingSchema,
  PaymentSummarySchema,
} from "../schemas";
import { AirQualityResponseSchema } from "../air-quality";

describe("null 필드 키 생략 내성", () => {
  it("MyFacility — homePage·meta 키가 없어도 파싱된다", () => {
    const withoutNullableKeys = {
      id: "6a6c334c3fc5f44fbb2c26d8",
      code: "DEMO-81812",
      name: "강남 스포츠센터",
      gu: "강남구",
      sidoCode: "11",
      sidoName: "서울특별시",
      sigunguCode: "11680",
      sigunguName: "강남구",
      type: "INDOOR",
      address: "서울 강남구 테헤란로 152",
      lat: 37.5006,
      lng: 127.0366,
      parking: true,
      tel: "02-555-0101",
      eduYn: true,
      ownerUserId: 69,
      createdAt: "2026-07-01T10:00:00+09:00",
      updatedAt: "2026-07-01T10:00:00+09:00",
    };

    expect(MyFacilitySchema.safeParse(withoutNullableKeys).success).toBe(true);
  });

  it("MyFacility — homePage·meta가 null이어도 파싱된다", () => {
    const withNullValues = {
      id: "6a6c334c3fc5f44fbb2c26d8",
      code: "DEMO-81812",
      name: "강남 스포츠센터",
      gu: "강남구",
      sidoCode: "11",
      sidoName: "서울특별시",
      sigunguCode: "11680",
      sigunguName: "강남구",
      type: "INDOOR",
      address: "서울 강남구 테헤란로 152",
      lat: 37.5006,
      lng: 127.0366,
      parking: true,
      tel: "02-555-0101",
      homePage: null,
      meta: null,
      eduYn: true,
      ownerUserId: 69,
      createdAt: "2026-07-01T10:00:00+09:00",
      updatedAt: "2026-07-01T10:00:00+09:00",
    };

    expect(MyFacilitySchema.safeParse(withNullValues).success).toBe(true);
  });

  it("Notification — 미읽음이라 readAt 키가 빠져도 파싱된다", () => {
    const unreadNotification = {
      id: 1,
      title: "예약이 확정됐습니다",
      content: "내 시설에 예약이 접수됐습니다. 예약 번호 6",
      category: "BOOKING",
      isRead: false,
      createdAt: "2026-07-01T10:00:00+09:00",
    };

    expect(NotificationSchema.safeParse(unreadNotification).success).toBe(true);
  });

  it("Booking — 미결제라 paymentId·paymentStatus 키가 빠져도 파싱된다", () => {
    const unpaidBooking = {
      id: 1,
      slotId: 2,
      userId: 68,
      status: "PENDING",
      createdAt: "2026-07-01T10:00:00+09:00",
      updatedAt: "2026-07-01T10:00:00+09:00",
    };

    expect(BookingSchema.safeParse(unpaidBooking).success).toBe(true);
  });

  it("Payment — 미결제라 paidAt 키가 빠져도 파싱된다", () => {
    const readyPayment = {
      id: 1,
      orderType: "BOOKING",
      orderId: 1,
      method: "CREDIT_CARD",
      amount: 50000,
      status: "PENDING",
      createdAt: "2026-07-01T10:00:00+09:00",
    };

    expect(PaymentSummarySchema.safeParse(readyPayment).success).toBe(true);
  });

  it("AirQuality — 측정값이 없어 pm10·pm25·측정소 키가 빠져도 파싱된다", () => {
    const emptyMeasurement = {
      pm10Grade: "UNKNOWN",
      pm25Grade: "UNKNOWN",
      representativeGrade: "UNKNOWN",
    };

    expect(AirQualityResponseSchema.safeParse(emptyMeasurement).success).toBe(true);
  });
});
