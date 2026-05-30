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
  type PaymentSummary,
  type PaymentSummaryPage,
  fetchMyPayments,
} from "@/lib/portal/payments";

const STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: "대기",
  COMPLETED: "완료",
  FAILED: "실패",
  REFUNDED: "환불",
};

const STATUS_COLORS: Record<PaymentStatus, string> = {
  PENDING: "bg-yellow-100 text-yellow-800",
  COMPLETED: "bg-green-100 text-green-800",
  FAILED: "bg-red-100 text-red-700",
  REFUNDED: "bg-gray-100 text-gray-600",
};

const PAGE_SIZE = 20;

export default function PaymentsPage() {
  const [statusFilter, setStatusFilter] = useState<PaymentStatus | "">("");
  const [paidAtFrom, setPaidAtFrom] = useState("");
  const [paidAtTo, setPaidAtTo] = useState("");
  const [page, setPage] = useState(0);
  const [payments, setPayments] = useState<PaymentSummary[]>([]);
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
      const data: PaymentSummaryPage = await fetchMyPayments(params);
      setPayments(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err.message : "결제 목록을 불러오지 못했습니다.");
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

  function handleFromChange(e: React.ChangeEvent<HTMLInputElement>) {
    setPaidAtFrom(e.target.value);
    setPage(0);
  }

  function handleToChange(e: React.ChangeEvent<HTMLInputElement>) {
    setPaidAtTo(e.target.value);
    setPage(0);
  }

  const pageTotal = payments.reduce((sum, p) => sum + p.amount, 0);

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
            {(Object.keys(STATUS_LABELS) as PaymentStatus[]).map((s) => (
              <option key={s} value={s}>
                {STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="paid-at-from" className="block text-sm font-medium mb-1">
            결제일 시작
          </label>
          <input
            id="paid-at-from"
            type="date"
            value={paidAtFrom}
            onChange={handleFromChange}
            className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label="결제일 시작"
          />
        </div>

        <div>
          <label htmlFor="paid-at-to" className="block text-sm font-medium mb-1">
            결제일 종료
          </label>
          <input
            id="paid-at-to"
            type="date"
            value={paidAtTo}
            onChange={handleToChange}
            className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
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
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}

      {/* 로딩 */}
      {loading && (
        <p aria-busy="true" className="text-sm text-gray-500">
          결제 목록을 불러오는 중...
        </p>
      )}

      {/* 결제 목록 테이블 */}
      {!loading && (
        <section aria-label="결제 목록">
          <p className="text-sm text-gray-500 mb-2">
            총 <strong>{totalElements}</strong>건
          </p>
          <div className="overflow-x-auto rounded-md border">
            <table className="min-w-full text-sm">
              <thead className="bg-gray-50">
                <tr>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                    결제 ID
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                    주문 유형
                  </th>
                  <th scope="col" className="px-4 py-3 text-right font-medium text-gray-600">
                    금액
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                    결제 수단
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                    PG사
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                    상태
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                    결제 일시
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                    PG 트랜잭션 ID
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {payments.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="px-4 py-8 text-center text-gray-400">
                      결제 내역이 없습니다.
                    </td>
                  </tr>
                ) : (
                  payments.map((payment) => (
                    <tr key={payment.id} className="hover:bg-gray-50">
                      <td className="px-4 py-3 text-gray-700">{payment.id}</td>
                      <td className="px-4 py-3">{payment.orderType}</td>
                      <td className="px-4 py-3 text-right tabular-nums">
                        {payment.amount.toLocaleString("ko-KR")}원
                      </td>
                      <td className="px-4 py-3">{payment.method}</td>
                      <td className="px-4 py-3 text-gray-500">
                        {payment.provider ?? "-"}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLORS[payment.status]}`}
                        >
                          {STATUS_LABELS[payment.status]}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {payment.paidAt
                          ? new Date(payment.paidAt).toLocaleString("ko-KR")
                          : "-"}
                      </td>
                      <td className="px-4 py-3 text-gray-500 font-mono text-xs">
                        {payment.pgTransactionId ?? "-"}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
              {payments.length > 0 && (
                <tfoot>
                  <tr className="bg-gray-50 font-medium">
                    <td colSpan={2} className="px-4 py-3 text-gray-600">
                      이 페이지 합계
                    </td>
                    <td className="px-4 py-3 text-right tabular-nums">
                      {pageTotal.toLocaleString("ko-KR")}원
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
