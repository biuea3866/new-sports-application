"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  type McpAnomalyEventResponse,
  type AnomalyEventStatus,
  type FetchAnomalyEventsParams,
  fetchAnomalyEvents,
  markFalsePositive,
} from "@/lib/admin/mcp/anomalies";

const PAGE_SIZE = 20;

const STATUS_LABEL: Record<AnomalyEventStatus, string> = {
  OPEN: "미처리",
  RESOLVED: "해결됨",
  FALSE_POSITIVE: "오탐",
};

const STATUS_CLASS: Record<AnomalyEventStatus, string> = {
  OPEN: "bg-red-100 text-red-800",
  RESOLVED: "bg-green-100 text-green-800",
  FALSE_POSITIVE: "bg-gray-100 text-gray-600",
};

interface FalsePositiveModalProps {
  eventId: number;
  onClose: () => void;
  onSuccess: () => void;
}

function FalsePositiveModal({
  eventId,
  onClose,
  onSuccess,
}: FalsePositiveModalProps): JSX.Element {
  const [note, setNote] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await markFalsePositive(eventId, note);
      onSuccess();
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "처리에 실패했습니다. 다시 시도해 주세요."
      );
    } finally {
      setSubmitting(false);
    }
  }

  function handleBackdropKeyDown(e: React.KeyboardEvent<HTMLDivElement>) {
    if (e.key === "Escape") onClose();
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="false-positive-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      onKeyDown={handleBackdropKeyDown}
    >
      <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
        <h2 id="false-positive-title" className="mb-4 text-lg font-semibold">
          오탐(False Positive) 처리
        </h2>
        <form onSubmit={(e) => void handleSubmit(e)} className="space-y-4">
          <div>
            <label htmlFor="fp-note" className="mb-1 block text-sm font-medium">
              사유 메모
            </label>
            <textarea
              id="fp-note"
              ref={inputRef}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              rows={3}
              placeholder="오탐으로 판단한 이유를 입력하세요."
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
          </div>
          {error !== null && (
            <p role="alert" className="text-sm text-red-600">
              {error}
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={onClose}
              disabled={submitting}
              aria-label="취소"
            >
              취소
            </Button>
            <Button
              type="submit"
              size="sm"
              disabled={submitting}
              aria-label="오탐 처리 확인"
            >
              {submitting ? "처리 중..." : "확인"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default function AnomaliesPage(): JSX.Element {
  const [events, setEvents] = useState<McpAnomalyEventResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [modalEventId, setModalEventId] = useState<number | null>(null);

  const loadEvents = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: FetchAnomalyEventsParams = { page, size: PAGE_SIZE };
      const data = await fetchAnomalyEvents(params);
      setEvents(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "이상 패턴 목록을 불러오지 못했습니다."
      );
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    void loadEvents();
  }, [loadEvents]);

  function handleFalsePositiveSuccess() {
    setModalEventId(null);
    void loadEvents();
  }

  return (
    <main className="min-h-screen space-y-6 p-6">
      <h1 className="text-2xl font-bold">이상 패턴 히스토리</h1>

      {/* 오류 */}
      {error !== null && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}

      {/* 로딩 */}
      {loading && (
        <p aria-busy="true" className="text-sm text-gray-500">
          이상 패턴 목록을 불러오는 중...
        </p>
      )}

      {/* 목록 테이블 */}
      {!loading && (
        <section aria-label="이상 패턴 목록">
          <p className="mb-2 text-sm text-gray-500">총 {totalElements}건</p>
          <div className="overflow-x-auto rounded-md border">
            <table className="min-w-full text-sm">
              <thead className="bg-gray-50">
                <tr>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                    감지 시각
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                    Token ID
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                    현재 시간 호출
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                    기준선 평균
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                    상태
                  </th>
                  <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                    처리
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {events.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-8 text-center text-gray-400">
                      조회된 이상 패턴이 없습니다.
                    </td>
                  </tr>
                ) : (
                  events.map((event) => (
                    <tr key={event.id} className="hover:bg-gray-50">
                      <td className="px-4 py-3 text-gray-600">
                        {new Date(event.detectedAt).toLocaleString("ko-KR")}
                      </td>
                      <td className="px-4 py-3 font-mono text-xs">{event.tokenId}</td>
                      <td className="px-4 py-3 font-semibold text-red-700">
                        {event.currentHourCount.toLocaleString("ko-KR")}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {event.baselineAverage.toFixed(1)}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_CLASS[event.status]}`}
                        >
                          {STATUS_LABEL[event.status]}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        {event.status === "OPEN" && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => setModalEventId(event.id)}
                            aria-label={`이벤트 ${event.id} 오탐 표시`}
                          >
                            오탐 표시
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {/* 페이지네이션 */}
      {totalPages > 1 && (
        <nav
          aria-label="이상 패턴 페이지 이동"
          className="mt-4 flex items-center justify-center gap-2"
        >
          <Button
            variant="outline"
            size="sm"
            disabled={loading || page === 0}
            onClick={() => setPage((p) => p - 1)}
            aria-label="이전 페이지"
          >
            이전
          </Button>
          <span aria-current="page" className="text-sm">
            {page + 1} / {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={loading || page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
            aria-label="다음 페이지"
          >
            다음
          </Button>
        </nav>
      )}

      {/* False Positive 모달 */}
      {modalEventId !== null && (
        <FalsePositiveModal
          eventId={modalEventId}
          onClose={() => setModalEventId(null)}
          onSuccess={handleFalsePositiveSuccess}
        />
      )}
    </main>
  );
}
