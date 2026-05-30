"use client";

import React, { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  type Notification,
  type NotificationPage,
  fetchMyNotifications,
  markNotificationRead,
} from "@/lib/portal/notifications";

const PAGE_SIZE = 20;

interface TabValue {
  label: string;
  onlyUnread: boolean;
}

const TABS: Record<string, TabValue> = {
  all: { label: "전체", onlyUnread: false },
  unread: { label: "미읽음", onlyUnread: true },
};

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

interface NotificationCardProps {
  notification: Notification;
  onMarkRead: (id: number) => void;
  markingId: number | null;
}

function NotificationCard({
  notification,
  onMarkRead,
  markingId,
}: NotificationCardProps) {
  const isRead = notification.readAt !== null;
  const isMarking = markingId === notification.id;

  return (
    <article
      aria-label={`알림 ${notification.id}: ${notification.templateId}`}
      className={`rounded-lg border p-4 space-y-2 ${
        isRead ? "bg-white" : "bg-blue-50 border-blue-200"
      }`}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 min-w-0">
          <p className="font-medium text-sm truncate">{notification.templateId}</p>
          <p className="text-xs text-muted-foreground mt-0.5">
            채널: {notification.channel} &middot; 상태: {notification.status}
          </p>
        </div>
        {!isRead && (
          <Button
            variant="outline"
            size="sm"
            disabled={isMarking}
            onClick={() => onMarkRead(notification.id)}
            aria-label={`알림 ${notification.id} 읽음 처리`}
            className="shrink-0 text-xs"
          >
            {isMarking ? "처리 중..." : "읽음"}
          </Button>
        )}
      </div>

      <dl className="text-xs text-muted-foreground space-y-0.5">
        <div className="flex gap-2">
          <dt className="w-16 shrink-0">생성</dt>
          <dd>{formatDate(notification.createdAt)}</dd>
        </div>
        {notification.sentAt && (
          <div className="flex gap-2">
            <dt className="w-16 shrink-0">발송</dt>
            <dd>{formatDate(notification.sentAt)}</dd>
          </div>
        )}
        {notification.readAt && (
          <div className="flex gap-2">
            <dt className="w-16 shrink-0">읽음</dt>
            <dd>{formatDate(notification.readAt)}</dd>
          </div>
        )}
      </dl>

      {!isRead && (
        <span
          aria-label="미읽음 알림"
          className="inline-block w-2 h-2 rounded-full bg-blue-500"
          aria-hidden="true"
        />
      )}
    </article>
  );
}

export default function NotificationsClient() {
  const [activeTab, setActiveTab] = useState<string>("all");
  const [page, setPage] = useState(0);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [markingId, setMarkingId] = useState<number | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const onlyUnread = TABS[activeTab]?.onlyUnread ?? false;

  const loadNotifications = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data: NotificationPage = await fetchMyNotifications({
        onlyUnread,
        page,
        size: PAGE_SIZE,
      });
      setNotifications(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "알림 목록을 불러오지 못했습니다."
      );
    } finally {
      setLoading(false);
    }
  }, [onlyUnread, page]);

  useEffect(() => {
    void loadNotifications();
  }, [loadNotifications]);

  useEffect(() => {
    if (toast === null) return;
    const timer = setTimeout(() => setToast(null), 3000);
    return () => clearTimeout(timer);
  }, [toast]);

  function handleTabChange(value: string) {
    setActiveTab(value);
    setPage(0);
  }

  async function handleMarkRead(notificationId: number) {
    setMarkingId(notificationId);
    try {
      await markNotificationRead(notificationId);
      setToast("알림을 읽음 처리했습니다.");
      void loadNotifications();
    } catch (err) {
      setToast(
        err instanceof Error ? err.message : "읽음 처리에 실패했습니다."
      );
    } finally {
      setMarkingId(null);
    }
  }

  return (
    <div className="space-y-6">
      <Tabs value={activeTab} onValueChange={handleTabChange}>
        <TabsList aria-label="알림 필터 탭">
          {Object.entries(TABS).map(([value, { label }]) => (
            <TabsTrigger key={value} value={value}>
              {label}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      {/* 토스트 */}
      {toast && (
        <div
          role="status"
          aria-live="polite"
          className="rounded-md bg-gray-800 text-white text-sm px-4 py-2 inline-block"
        >
          {toast}
        </div>
      )}

      {/* 에러 */}
      {error && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}

      {/* 로딩 */}
      {loading && (
        <p aria-busy="true" className="text-sm text-muted-foreground">
          알림 목록을 불러오는 중...
        </p>
      )}

      {/* 알림 목록 */}
      {!loading && (
        <section aria-label="알림 목록">
          <p className="text-sm text-muted-foreground mb-3">
            총 <strong>{totalElements}</strong>건
          </p>

          {notifications.length === 0 ? (
            <p className="text-sm text-muted-foreground py-8 text-center">
              {onlyUnread ? "미읽음 알림이 없습니다." : "알림이 없습니다."}
            </p>
          ) : (
            <ul className="space-y-3" aria-label="알림 카드 목록">
              {notifications.map((notification) => (
                <li key={notification.id}>
                  <NotificationCard
                    notification={notification}
                    onMarkRead={(id) => void handleMarkRead(id)}
                    markingId={markingId}
                  />
                </li>
              ))}
            </ul>
          )}

          {/* 페이지네이션 */}
          {totalPages > 1 && (
            <nav
              aria-label="알림 목록 페이지 이동"
              className="flex items-center justify-center gap-2 mt-6"
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
    </div>
  );
}
