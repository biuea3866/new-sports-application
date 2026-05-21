"use client";

/**
 * /portal/bookings — 예약 현황 조회
 * 시설 소유자가 자신에게 들어온 예약 목록을 확인한다.
 */
import React, { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  type BookingResponse,
  type BookingStatus,
  type ListBookingsParams,
  fetchBooking,
  fetchMyBookings,
} from "@/lib/portal/bookings";

const STATUS_LABELS: Record<BookingStatus, string> = {
  PENDING: "대기",
  CONFIRMED: "확정",
  CANCELLED: "취소",
  EXPIRED: "만료",
};

const STATUS_COLORS: Record<BookingStatus, string> = {
  PENDING: "bg-yellow-100 text-yellow-800",
  CONFIRMED: "bg-green-100 text-green-800",
  CANCELLED: "bg-gray-100 text-gray-600",
  EXPIRED: "bg-red-100 text-red-700",
};

const PAGE_SIZE = 10;

interface DetailModalState {
  bookingId: number;
  booking: BookingResponse | null;
  loading: boolean;
  error: string | null;
}

export default function BookingsPage() {
  const [statusFilter, setStatusFilter] = useState<BookingStatus | "">("");
  const [page, setPage] = useState(0);
  const [bookings, setBookings] = useState<BookingResponse[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [detailModal, setDetailModal] = useState<DetailModalState | null>(null);

  const loadBookings = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: ListBookingsParams = { page, size: PAGE_SIZE };
      if (statusFilter !== "") params.status = statusFilter;
      const data = await fetchMyBookings(params);
      setBookings(data.bookings);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err.message : "예약 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter]);

  useEffect(() => {
    void loadBookings();
  }, [loadBookings]);

  async function openDetail(bookingId: number) {
    setDetailModal({ bookingId, booking: null, loading: true, error: null });
    try {
      const booking = await fetchBooking(bookingId);
      setDetailModal((prev) => (prev ? { ...prev, booking, loading: false } : null));
    } catch (err) {
      const msg = err instanceof Error ? err.message : "예약 상세를 불러오지 못했습니다.";
      setDetailModal((prev) => (prev ? { ...prev, loading: false, error: msg } : null));
    }
  }

  function closeDetail() {
    setDetailModal(null);
  }

  function handleStatusChange(e: React.ChangeEvent<HTMLSelectElement>) {
    setStatusFilter(e.target.value as BookingStatus | "");
    setPage(0);
  }

  return (
    <main className="min-h-screen p-6 space-y-6">
      <h1 className="text-2xl font-bold">예약 현황</h1>

      {/* 필터 */}
      <section aria-label="예약 필터" className="flex items-center gap-4 flex-wrap">
        <div>
          <label htmlFor="status-filter" className="block text-sm font-medium mb-1">
            상태 필터
          </label>
          <select
            id="status-filter"
            value={statusFilter}
            onChange={handleStatusChange}
            className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label="예약 상태 필터"
          >
            <option value="">전체</option>
            {(Object.keys(STATUS_LABELS) as BookingStatus[]).map((s) => (
              <option key={s} value={s}>
                {STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={() => void loadBookings()}
          aria-label="예약 목록 새로고침"
          className="mt-5"
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
          예약 목록을 불러오는 중...
        </p>
      )}

      {/* 예약 목록 테이블 */}
      {!loading && (
        <section aria-label="예약 목록">
          <p className="text-sm text-gray-500 mb-2">
            총 <strong>{totalElements}</strong>건
          </p>
          <div className="overflow-x-auto rounded-md border">
            <table className="min-w-full text-sm">
              <thead className="bg-gray-50">
                <tr>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                    예약 ID
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                    슬롯 ID
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                    상태
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                    예약 일시
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                    상세
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {bookings.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-4 py-8 text-center text-gray-400">
                      예약이 없습니다.
                    </td>
                  </tr>
                ) : (
                  bookings.map((booking) => (
                    <tr key={booking.id} className="hover:bg-gray-50">
                      <td className="px-4 py-3">{booking.id}</td>
                      <td className="px-4 py-3">{booking.slotId}</td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLORS[booking.status]}`}
                        >
                          {STATUS_LABELS[booking.status]}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {new Date(booking.createdAt).toLocaleString("ko-KR")}
                      </td>
                      <td className="px-4 py-3">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => void openDetail(booking.id)}
                          aria-label={`예약 ${booking.id} 상세 보기`}
                        >
                          상세
                        </Button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {/* 페이지네이션 */}
          {totalPages > 1 && (
            <nav
              aria-label="예약 목록 페이지 이동"
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

      {/* 예약 상세 모달 */}
      <Dialog open={detailModal !== null} onOpenChange={(open) => !open && closeDetail()}>
        <DialogContent aria-labelledby="booking-detail-title">
          <DialogHeader>
            <DialogTitle id="booking-detail-title">
              예약 상세{detailModal ? ` #${detailModal.bookingId}` : ""}
            </DialogTitle>
          </DialogHeader>

          {detailModal?.loading && (
            <p aria-busy="true" className="text-sm text-gray-500 py-4">
              불러오는 중...
            </p>
          )}

          {detailModal?.error && (
            <p role="alert" className="text-sm text-red-600 py-4">
              {detailModal.error}
            </p>
          )}

          {detailModal?.booking && (
            <dl className="space-y-3 text-sm py-2">
              <div className="flex gap-2">
                <dt className="w-28 font-medium text-gray-500">예약 ID</dt>
                <dd>{detailModal.booking.id}</dd>
              </div>
              <div className="flex gap-2">
                <dt className="w-28 font-medium text-gray-500">슬롯 ID</dt>
                <dd>{detailModal.booking.slotId}</dd>
              </div>
              <div className="flex gap-2">
                <dt className="w-28 font-medium text-gray-500">사용자 ID</dt>
                <dd>{detailModal.booking.userId}</dd>
              </div>
              <div className="flex gap-2">
                <dt className="w-28 font-medium text-gray-500">상태</dt>
                <dd>
                  <span
                    className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLORS[detailModal.booking.status]}`}
                  >
                    {STATUS_LABELS[detailModal.booking.status]}
                  </span>
                </dd>
              </div>
              {detailModal.booking.paymentId !== null && (
                <div className="flex gap-2">
                  <dt className="w-28 font-medium text-gray-500">결제 ID</dt>
                  <dd>{detailModal.booking.paymentId}</dd>
                </div>
              )}
              {detailModal.booking.paymentStatus !== null && (
                <div className="flex gap-2">
                  <dt className="w-28 font-medium text-gray-500">결제 상태</dt>
                  <dd>{detailModal.booking.paymentStatus}</dd>
                </div>
              )}
              <div className="flex gap-2">
                <dt className="w-28 font-medium text-gray-500">예약 일시</dt>
                <dd>{new Date(detailModal.booking.createdAt).toLocaleString("ko-KR")}</dd>
              </div>
              <div className="flex gap-2">
                <dt className="w-28 font-medium text-gray-500">수정 일시</dt>
                <dd>{new Date(detailModal.booking.updatedAt).toLocaleString("ko-KR")}</dd>
              </div>
            </dl>
          )}
        </DialogContent>
      </Dialog>
    </main>
  );
}
