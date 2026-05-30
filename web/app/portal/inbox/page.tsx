"use client";

/**
 * /portal/inbox — 운영자 알림센터
 * 알림 목록, type/status 필터, unread 뱃지, 읽음 처리, link 이동, 페이지네이션.
 */
import React, { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import {
  type InboxNotification,
  type InboxNotificationType,
  type InboxNotificationStatus,
  type ListInboxParams,
  fetchInbox,
  fetchUnreadCount,
  markInboxRead,
} from "@/lib/portal/operatorInbox";

const TYPE_LABELS: Record<InboxNotificationType, string> = {
  ANOMALY: "이상 감지",
  LOW_INVENTORY: "재고 부족",
  BOOKING_CONFLICT: "예약 충돌",
  POLICY_VIOLATION: "정책 위반",
  AUTOMATION_FAILURE: "자동화 실패",
};

const STATUS_LABELS: Record<InboxNotificationStatus, string> = {
  UNREAD: "읽지 않음",
  READ: "읽음",
  ARCHIVED: "보관됨",
};

const STATUS_COLORS: Record<InboxNotificationStatus, string> = {
  UNREAD: "bg-blue-100 text-blue-800",
  READ: "bg-gray-100 text-gray-600",
  ARCHIVED: "bg-yellow-100 text-yellow-700",
};

const PAGE_SIZE = 20;

export default function InboxPage(): JSX.Element {
  const { addToast } = useToast();
  const [typeFilter, setTypeFilter] = useState<InboxNotificationType | "">("");
  const [statusFilter, setStatusFilter] = useState<InboxNotificationStatus | "">("");
  const [page, setPage] = useState(0);
  const [notifications, setNotifications] = useState<InboxNotification[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [readingId, setReadingId] = useState<number | null>(null);

  const loadNotifications = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: ListInboxParams = { page, size: PAGE_SIZE };
      if (typeFilter !== "") params.type = typeFilter;
      if (statusFilter !== "") params.status = statusFilter;

      const [listData, countData] = await Promise.all([
        fetchInbox(params),
        fetchUnreadCount(),
      ]);
      setNotifications(listData.content);
      setTotalPages(listData.totalPages);
      setTotalElements(listData.totalElements);
      setUnreadCount(countData.count);
    } catch (err) {
      setError(err instanceof Error ? err.message : "알림 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [page, typeFilter, statusFilter]);

  useEffect(() => {
    void loadNotifications();
  }, [loadNotifications]);

  async function handleRead(notification: InboxNotification) {
    if (notification.status !== "UNREAD") {
      if (notification.link) {
        window.open(notification.link, "_blank", "noopener,noreferrer");
      }
      return;
    }

    setReadingId(notification.id);
    try {
      await markInboxRead(notification.id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === notification.id ? { ...n, status: "READ" as const } : n))
      );
      setUnreadCount((prev) => Math.max(0, prev - 1));

      if (notification.link) {
        window.open(notification.link, "_blank", "noopener,noreferrer");
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : "읽음 처리에 실패했습니다.";
      addToast({ title: "오류", description: msg, variant: "destructive" });
    } finally {
      setReadingId(null);
    }
  }

  function handleTypeChange(e: React.ChangeEvent<HTMLSelectElement>) {
    setTypeFilter(e.target.value as InboxNotificationType | "");
    setPage(0);
  }

  function handleStatusChange(e: React.ChangeEvent<HTMLSelectElement>) {
    setStatusFilter(e.target.value as InboxNotificationStatus | "");
    setPage(0);
  }

  return (
    <main className="min-h-screen p-6 space-y-6">
      <div className="flex items-center gap-3">
        <h1 className="text-2xl font-bold">알림센터</h1>
        {unreadCount > 0 && (
          <span
            className="inline-flex items-center justify-center rounded-full bg-blue-600 px-2 py-0.5 text-xs font-bold text-white min-w-[1.5rem]"
            aria-label={`읽지 않은 알림 ${unreadCount}개`}
          >
            {unreadCount}
          </span>
        )}
      </div>

      {/* 필터 */}
      <section aria-label="알림 필터" className="flex items-center gap-4 flex-wrap">
        <div>
          <label htmlFor="type-filter" className="block text-sm font-medium mb-1">
            유형
          </label>
          <select
            id="type-filter"
            value={typeFilter}
            onChange={handleTypeChange}
            className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label="알림 유형 필터"
          >
            <option value="">전체</option>
            {(Object.keys(TYPE_LABELS) as InboxNotificationType[]).map((t) => (
              <option key={t} value={t}>
                {TYPE_LABELS[t]}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="status-filter" className="block text-sm font-medium mb-1">
            상태
          </label>
          <select
            id="status-filter"
            value={statusFilter}
            onChange={handleStatusChange}
            className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label="알림 상태 필터"
          >
            <option value="">전체</option>
            {(Object.keys(STATUS_LABELS) as InboxNotificationStatus[]).map((s) => (
              <option key={s} value={s}>
                {STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={() => void loadNotifications()}
          aria-label="알림 목록 새로고침"
          className="mt-5"
        >
          새로고침
        </Button>
      </section>

      {/* 오류 */}
      {error !== null && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}

      {/* 로딩 */}
      {loading && (
        <p aria-busy="true" className="text-sm text-gray-500">
          알림 목록을 불러오는 중...
        </p>
      )}

      {/* 알림 목록 */}
      {!loading && (
        <section aria-label="알림 목록">
          <p className="text-sm text-gray-500 mb-2">
            총 <strong>{totalElements}</strong>건
          </p>

          {notifications.length === 0 ? (
            <p className="text-sm text-gray-400 py-8 text-center">알림이 없습니다.</p>
          ) : (
            <ul className="space-y-2" aria-label="알림 항목 목록">
              {notifications.map((notification) => (
                <li
                  key={notification.id}
                  className={`rounded-lg border p-4 transition-colors ${
                    notification.status === "UNREAD"
                      ? "border-blue-200 bg-blue-50"
                      : "bg-white hover:bg-gray-50"
                  }`}
                >
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1 flex-wrap">
                        <span
                          className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLORS[notification.status]}`}
                        >
                          {STATUS_LABELS[notification.status]}
                        </span>
                        <span className="text-xs text-gray-500">
                          {TYPE_LABELS[notification.type]}
                        </span>
                        <span className="text-xs text-gray-400">
                          {new Date(notification.createdAt).toLocaleString("ko-KR")}
                        </span>
                      </div>
                      <p className="font-medium text-sm truncate">{notification.title}</p>
                      <p className="text-sm text-gray-600 mt-1">{notification.body}</p>
                    </div>
                    <Button
                      variant={notification.status === "UNREAD" ? "default" : "outline"}
                      size="sm"
                      onClick={() => void handleRead(notification)}
                      disabled={readingId === notification.id}
                      aria-label={
                        notification.status === "UNREAD"
                          ? `알림 "${notification.title}" 읽음으로 표시${notification.link ? " 및 링크 이동" : ""}`
                          : `알림 "${notification.title}" ${notification.link ? "링크 이동" : "보기"}`
                      }
                    >
                      {readingId === notification.id
                        ? "처리 중..."
                        : notification.status === "UNREAD"
                        ? notification.link
                          ? "읽고 이동"
                          : "읽음"
                        : notification.link
                        ? "이동"
                        : "확인"}
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}

          {/* 페이지네이션 */}
          {totalPages > 1 && (
            <nav
              aria-label="알림 목록 페이지 이동"
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
