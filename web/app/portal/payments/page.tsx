"use client";

/**
 * /portal/payments — 매출/결제 내역 조회
 * 운영자(사업자)가 자신의 결제 목록을 확인한다.
 */
import React, { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  type ListPaymentsParams,
  type PaymentStatus,
  type PartnerSale,
  type PartnerSalesResponse,
  fetchPartnerSales,
} from "@/lib/portal/payments";
import {
  PAYMENT_STATUS_VALUES,
  paymentStatusBadgeClass,
  paymentStatusLabel,
} from "@/lib/portal/paymentStatus";
import { toUserMessage } from "@/lib/portal/toUserMessage";
import { DateField } from "@/components/ui/date-field";

const PAGE_SIZE = 20;

export default function PaymentsPage() {
  const [statusFilter, setStatusFilter] = useState<PaymentStatus | "">("");
  const [paidAtFrom, setPaidAtFrom] = useState("");
  const [paidAtTo, setPaidAtTo] = useState("");
  const [page, setPage] = useState(0);
  const [payments, setPayments] = useState<PartnerSale[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadPayments = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: ListPaymentsParams = { page, size: PAGE_SIZE };
      if (statusFilter !== "") params.status = statusFilter;
      if (paidAtFrom !== "") params.paidAtFrom = `${paidAtFrom}T00:00:00Z`;
      if (paidAtTo !== "") params.paidAtTo = `${paidAtTo}T23:59:59Z`;
      const data: PartnerSalesResponse = await fetchPartnerSales(params);
      setPayments(data.sales);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      // 스키마 검증 실패 원문(Zod issue 배열)이 화면에 새지 않도록 사람이 읽는 문장으로 치환한다.
      setError(toUserMessage(err));
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter, paidAtFrom, paidAtTo]);

  useEffect(() => {
    void loadPayments();
  }, [loadPayments]);

  function handleStatusChange(e: React.ChangeEvent<HTMLSelectElement>) {
    setStatusFilter(e.target.value as PaymentStatus | "");
    setPage(0);
  }

  function handleFromChange(nextValue: string) {
    setPaidAtFrom(nextValue);
    setPage(0);
  }

  function handleToChange(nextValue: string) {
    setPaidAtTo(nextValue);
    setPage(0);
  }

  // 파트너가 실제로 번 금액만 합산한다 — 결제 총액은 혼합 주문에서 남의 몫을 포함해
  // 응답에 싣지 않는다.
  const pageSellerTotal = payments.reduce((sum, sale) => sum + sale.sellerAmount, 0);

  return (
    <main className="min-h-screen p-6 space-y-6">
      <h1 className="text-2xl font-bold">매출</h1>

      {/* 필터 */}
      <section aria-label="결제 필터" className="flex items-end gap-4 flex-wrap">
        <div>
          <label htmlFor="status-filter" className="block text-sm font-medium mb-1">
            상태
          </label>
          <select
            id="status-filter"
            value={statusFilter}
            onChange={handleStatusChange}
            className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label="결제 상태 필터"
          >
            <option value="">전체</option>
            {PAYMENT_STATUS_VALUES.map((status) => (
              <option key={status} value={status}>
                {paymentStatusLabel(status)}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="paid-at-from" className="block text-sm font-medium mb-1">
            결제일 시작
          </label>
          <DateField
            id="paid-at-from"
            value={paidAtFrom}
            onChange={handleFromChange}
            aria-label="결제일 시작"
          />
        </div>

        <div>
          <label htmlFor="paid-at-to" className="block text-sm font-medium mb-1">
            결제일 종료
          </label>
          <DateField
            id="paid-at-to"
            value={paidAtTo}
            onChange={handleToChange}
            aria-label="결제일 종료"
          />
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={() => void loadPayments()}
          aria-label="결제 목록 새로고침"
        >
          새로고침
        </Button>
      </section>

      {/* 오류 */}
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}

      {/* 로딩 */}
      {loading && (
        <p aria-busy="true" className="text-sm text-muted-foreground">
          결제 목록을 불러오는 중...
        </p>
      )}

      {/* 결제 목록 테이블 */}
      {!loading && (
        <section aria-label="결제 목록">
          <p className="text-sm text-muted-foreground mb-2">
            총 <strong>{totalElements}</strong>건
          </p>
          <div className="overflow-x-auto rounded-md border">
            <table className="min-w-full text-sm">
              <thead className="bg-muted/50">
                <tr>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-muted-foreground">
                    결제 ID
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-muted-foreground">
                    주문 유형
                  </th>
                  <th scope="col" className="px-4 py-3 text-right font-medium text-muted-foreground">
                    내 매출
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-muted-foreground">
                    결제 수단
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-muted-foreground">
                    PG사
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-muted-foreground">
                    상태
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-muted-foreground">
                    결제 일시
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-muted-foreground">
                    PG 트랜잭션 ID
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {payments.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="px-4 py-8 text-center text-muted-foreground">
                      결제 내역이 없습니다.
                    </td>
                  </tr>
                ) : (
                  payments.map((payment) => (
                    <tr key={payment.paymentId} className="hover:bg-muted/50">
                      <td className="px-4 py-3 text-foreground">{payment.paymentId}</td>
                      <td className="px-4 py-3">{payment.orderType}</td>
                      <td className="px-4 py-3 text-right tabular-nums font-medium">
                        {payment.sellerAmount.toLocaleString("ko-KR")}원
                      </td>
                      <td className="px-4 py-3">{payment.method}</td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {payment.provider ?? "-"}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${paymentStatusBadgeClass(payment.status)}`}
                        >
                          {paymentStatusLabel(payment.status)}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {payment.paidAt
                          ? new Date(payment.paidAt).toLocaleString("ko-KR")
                          : "-"}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground font-mono text-xs">
                        {payment.pgTransactionId ?? "-"}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
              {payments.length > 0 && (
                <tfoot>
                  <tr className="bg-muted/50 font-medium">
                    <td colSpan={2} className="px-4 py-3 text-muted-foreground">
                      이 페이지 합계
                    </td>
                    <td className="px-4 py-3 text-right tabular-nums">
                      {pageSellerTotal.toLocaleString("ko-KR")}원
                    </td>
                    <td colSpan={5} />
                  </tr>
                </tfoot>
              )}
            </table>
          </div>

          {/* 페이지네이션 */}
          {totalPages > 1 && (
            <nav
              aria-label="결제 목록 페이지 이동"
              className="flex items-center justify-center gap-2 mt-4"
            >
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
                aria-label="이전 페이지"
              >
                이전
              </Button>
              <span className="text-sm" aria-current="page">
                {page + 1} / {totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                aria-label="다음 페이지"
              >
                다음
              </Button>
            </nav>
          )}
        </section>
      )}
    </main>
  );
}
