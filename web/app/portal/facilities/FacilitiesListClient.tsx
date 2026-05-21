"use client";

import * as React from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import type { MyFacility, Page } from "@/lib/portal/types";

const FACILITY_TYPE_LABELS: Record<string, string> = {
  INDOOR: "실내",
  OUTDOOR: "야외",
  MIXED: "복합",
};

const PAGE_SIZE = 10;

function FacilityRow({ facility }: { facility: MyFacility }) {
  return (
    <tr className="border-b last:border-0 hover:bg-muted/30 transition-colors">
      <td className="py-3 px-4 text-sm font-medium">{facility.code}</td>
      <td className="py-3 px-4 text-sm">
        <Link
          href={`/portal/facilities/${facility.id}`}
          className="hover:underline text-primary"
          aria-label={`${facility.name} 상세 보기`}
        >
          {facility.name}
        </Link>
      </td>
      <td className="py-3 px-4 text-sm">{facility.gu}</td>
      <td className="py-3 px-4">
        <Badge variant="secondary" className="text-xs">
          {FACILITY_TYPE_LABELS[facility.type] ?? facility.type}
        </Badge>
      </td>
      <td className="py-3 px-4 text-sm">{facility.tel}</td>
      <td className="py-3 px-4">
        <Badge variant={facility.parking ? "default" : "outline"} className="text-xs">
          {facility.parking ? "가능" : "불가"}
        </Badge>
      </td>
      <td className="py-3 px-4">
        <Link href={`/portal/facilities/${facility.id}`} aria-label={`${facility.name} 편집`}>
          <Button variant="ghost" size="sm">
            편집
          </Button>
        </Link>
      </td>
    </tr>
  );
}

export default function FacilitiesListClient() {
  const [page, setPage] = React.useState(0);
  const [result, setResult] = React.useState<Page<MyFacility> | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [errorMessage, setErrorMessage] = React.useState<string | null>(null);

  React.useEffect(() => {
    async function fetchFacilities() {
      setLoading(true);
      setErrorMessage(null);
      try {
        const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
        const response = await fetch(`/api/portal/facilities?${params.toString()}`);
        if (!response.ok) {
          const body = (await response.json()) as { message?: string };
          setErrorMessage(body.message ?? "시설 목록을 불러오는 중 오류가 발생했습니다.");
          return;
        }
        const data = (await response.json()) as Page<MyFacility>;
        setResult(data);
      } catch {
        setErrorMessage("시설 목록을 불러오는 중 오류가 발생했습니다.");
      } finally {
        setLoading(false);
      }
    }
    void fetchFacilities();
  }, [page]);

  return (
    <>
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">내 시설 관리</h1>
        <Link href="/portal/facilities/new">
          <Button aria-label="새 시설 등록">새 시설 등록</Button>
        </Link>
      </div>

      {loading ? (
        <p className="text-sm text-muted-foreground py-8 text-center" aria-live="polite" aria-busy="true">
          불러오는 중...
        </p>
      ) : errorMessage ? (
        <div role="alert" className="rounded-md border border-destructive p-4 text-sm text-destructive">
          {errorMessage}
        </div>
      ) : !result || result.content.length === 0 ? (
        <div className="rounded-md border p-8 text-center text-sm text-muted-foreground">
          등록된 시설이 없습니다.{" "}
          <Link href="/portal/facilities/new" className="text-primary hover:underline">
            새 시설을 등록해 보세요.
          </Link>
        </div>
      ) : (
        <>
          <div className="rounded-md border overflow-x-auto">
            <table className="w-full text-left" aria-label="내 시설 목록">
              <thead className="bg-muted/50">
                <tr>
                  <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                    코드
                  </th>
                  <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                    시설명
                  </th>
                  <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                    구
                  </th>
                  <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                    유형
                  </th>
                  <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                    전화번호
                  </th>
                  <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                    주차
                  </th>
                  <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                    <span className="sr-only">액션</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((facility) => (
                  <FacilityRow key={facility.id} facility={facility} />
                ))}
              </tbody>
            </table>
          </div>

          {result.totalPages > 1 && (
            <nav aria-label="페이지 네비게이션" className="flex items-center justify-center gap-2">
              {page > 0 && (
                <Button
                  variant="outline"
                  size="sm"
                  aria-label="이전 페이지"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  이전
                </Button>
              )}
              <span className="text-sm text-muted-foreground" aria-live="polite">
                {page + 1} / {result.totalPages}
              </span>
              {page + 1 < result.totalPages && (
                <Button
                  variant="outline"
                  size="sm"
                  aria-label="다음 페이지"
                  onClick={() => setPage((p) => p + 1)}
                >
                  다음
                </Button>
              )}
            </nav>
          )}
        </>
      )}
    </>
  );
}
