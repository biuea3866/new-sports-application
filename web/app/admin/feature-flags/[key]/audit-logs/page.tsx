/**
 * S5 변경 이력(감사 로그) 화면 — 컨테이너.
 * useFlagAuditLogs(key,page,size)로 서버 상태를 소비하고 4상태(loading/empty/error/success)를 분기 렌더한다.
 * 페이지는 훅의 canonical FeatureFlagAuditLogPageView(totalPages)로 "1 / N"을 표시한다 — 배열 전용 폴백 없음.
 * 근거 티켓: FE-10-audit-log-screen.md, 근거 설계: design-fe-web.md "S5 와이어프레임 / 상태 표".
 */
"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { useFlagAuditLogs } from "@/lib/admin/feature-flags/hooks";
import { AuditLogTable } from "./_components/AuditLogTable";

const PAGE_SIZE = 10;

export default function FeatureFlagAuditLogsPage(): JSX.Element {
  const params = useParams<{ key: string }>();
  const flagKey = params.key;
  const [page, setPage] = useState(0);

  const { data, isLoading, error, refetch } = useFlagAuditLogs(flagKey, page, PAGE_SIZE);

  const totalPages = data?.totalPages ?? 0;
  const isFirstPage = page === 0;
  const isLastPage = totalPages === 0 || page >= totalPages - 1;

  return (
    <div className="space-y-6">
      <div>
        <Link
          href={`/admin/feature-flags/${encodeURIComponent(flagKey)}`}
          className="text-sm text-primary hover:underline"
        >
          ← {flagKey} 변경 이력
        </Link>
      </div>

      {isLoading && (
        <p aria-busy="true" className="text-sm text-muted-foreground">
          불러오는 중...
        </p>
      )}

      {!isLoading && error !== null && (
        <div
          role="alert"
          className="rounded-md border border-destructive/50 bg-destructive/10 p-4 text-sm text-destructive"
        >
          <p>{error}</p>
          <Button variant="outline" size="sm" className="mt-3" onClick={refetch}>
            다시 시도
          </Button>
        </div>
      )}

      {!isLoading && error === null && data !== null && data.logs.length === 0 && (
        <div className="rounded-md border border-border p-8 text-center">
          <p className="text-sm text-muted-foreground">변경 이력이 없습니다</p>
        </div>
      )}

      {!isLoading && error === null && data !== null && data.logs.length > 0 && (
        <>
          <AuditLogTable logs={data.logs} />
          <nav
            aria-label="변경 이력 페이지 이동"
            className="flex items-center justify-center gap-2"
          >
            <Button
              variant="outline"
              size="sm"
              disabled={isFirstPage}
              onClick={() => setPage((p) => p - 1)}
              aria-label="이전 페이지"
            >
              이전
            </Button>
            <span aria-current="page" className="text-sm text-muted-foreground">
              {page + 1} / {totalPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={isLastPage}
              onClick={() => setPage((p) => p + 1)}
              aria-label="다음 페이지"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </div>
  );
}
