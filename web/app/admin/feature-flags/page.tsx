/**
 * S1 플래그 목록 화면 — 컨테이너.
 * useFeatureFlags(filters)로 서버 상태를 소비하고 4상태(loading/empty/error/success)를 분기 렌더한다.
 * 필터(status/type)는 이 화면이 지역 useState로 소유한다(no-global-by-default).
 * 근거 티켓: FE-07-list-screen.md, 근거 설계: design-fe-web.md "S1 텍스트 와이어프레임 / 화면별 상태 표".
 */
"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { useFeatureFlags } from "@/lib/admin/feature-flags/hooks";
import type { FeatureFlagStatus, FeatureFlagType } from "@/lib/admin/feature-flags/schemas";
import { FeatureFlagFilters } from "./_components/FeatureFlagFilters";
import { FeatureFlagTable } from "./_components/FeatureFlagTable";

export default function FeatureFlagsPage(): JSX.Element {
  const router = useRouter();
  const [status, setStatus] = useState<FeatureFlagStatus | undefined>(undefined);
  const [type, setType] = useState<FeatureFlagType | undefined>(undefined);

  const { data, isLoading, error, refetch } = useFeatureFlags({ status, type });

  const hasActiveFilter = status !== undefined || type !== undefined;

  function handleRowClick(key: string): void {
    router.push(`/admin/feature-flags/${encodeURIComponent(key)}`);
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">피처 플래그</h1>
          <p className="mt-1 text-sm text-muted-foreground">런타임 토글·롤아웃을 관리합니다.</p>
        </div>
        <Button asChild>
          <Link href="/admin/feature-flags/new">+ 플래그 추가</Link>
        </Button>
      </div>

      <FeatureFlagFilters
        status={status}
        type={type}
        onStatusChange={setStatus}
        onTypeChange={setType}
      />

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

      {!isLoading && error === null && data !== null && data.length === 0 && (
        <div className="rounded-md border border-border p-8 text-center">
          <p className="text-sm text-muted-foreground">
            {hasActiveFilter ? "조건에 맞는 플래그가 없습니다." : "등록된 플래그가 없습니다"}
          </p>
          {!hasActiveFilter && (
            <Button asChild className="mt-4">
              <Link href="/admin/feature-flags/new">+ 플래그 추가</Link>
            </Button>
          )}
        </div>
      )}

      {!isLoading && error === null && data !== null && data.length > 0 && (
        <FeatureFlagTable flags={data} onRowClick={handleRowClick} />
      )}
    </div>
  );
}
