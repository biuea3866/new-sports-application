/**
 * 결제 관련 타입 정의 및 BFF API 클라이언트.
 * Client Component에서는 /api/portal/payments BFF 엔드포인트만 호출한다.
 */
import type { z } from "zod";
import type {
  PaymentSummarySchema,
  PaymentSummaryPageSchema,
  PartnerSaleSchema,
  PartnerSalesResponseSchema,
} from "./schemas";

export type PaymentStatus = "PENDING" | "COMPLETED" | "FAILED" | "REFUNDED";
export type PaymentMethod =
  | "CREDIT_CARD"
  | "BANK_TRANSFER"
  | "KAKAO"
  | "TOSS"
  | "NAVER"
  | "DANAL";
export type OrderType = "BOOKING" | "TICKETING" | "GOODS";

export type PaymentSummary = z.infer<typeof PaymentSummarySchema>;
export type PaymentSummaryPage = z.infer<typeof PaymentSummaryPageSchema>;
export type PartnerSale = z.infer<typeof PartnerSaleSchema>;
export type PartnerSalesResponse = z.infer<typeof PartnerSalesResponseSchema>;

export interface ListPaymentsParams {
  status?: PaymentStatus;
  paidAtFrom?: string;
  paidAtTo?: string;
  page?: number;
  size?: number;
}

/** 내 결제 목록 조회 (BFF /api/portal/payments 경유) */
export async function fetchMyPayments(
  params: ListPaymentsParams = {}
): Promise<PaymentSummaryPage> {
  const query = new URLSearchParams();
  if (params.status !== undefined) query.set("status", params.status);
  if (params.paidAtFrom !== undefined) query.set("paidAtFrom", params.paidAtFrom);
  if (params.paidAtTo !== undefined) query.set("paidAtTo", params.paidAtTo);
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));
  const qs = query.toString();
  const url = qs ? `/api/portal/payments?${qs}` : "/api/portal/payments";

  const res = await fetch(url, { cache: "no-store" });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `결제 목록 조회 실패: ${res.status}`);
  }
  return res.json() as Promise<PaymentSummaryPage>;
}

/**
 * 파트너 매출 내역 조회 (BFF /api/portal/payments 경유).
 *
 * 구매자 스코프(`fetchMyPayments`)와 반대다 — 내가 **판** 건의 결제를 반환한다.
 * 조회 대상 판매자는 BE가 인증 주체로 정하므로 파라미터로 넘기지 않는다.
 */
export async function fetchPartnerSales(
  params: ListPaymentsParams = {}
): Promise<PartnerSalesResponse> {
  const query = new URLSearchParams();
  if (params.status !== undefined) query.set("status", params.status);
  if (params.paidAtFrom !== undefined) query.set("paidAtFrom", params.paidAtFrom);
  if (params.paidAtTo !== undefined) query.set("paidAtTo", params.paidAtTo);
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));
  const qs = query.toString();
  const url = qs ? `/api/portal/payments?${qs}` : "/api/portal/payments";

  const res = await fetch(url, { cache: "no-store" });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `매출 내역 조회 실패: ${res.status}`);
  }
  return res.json() as Promise<PartnerSalesResponse>;
}
