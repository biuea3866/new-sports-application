"use client";

/**
 * /portal/insights — 운영 통합 KPI
 * 시설/굿즈/티켓 3영역 KPI 위젯과 인기 시설 TOP5 순위 리스트.
 */
import React, { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { formatKrw } from "./format-krw";
import {
  type OperationKpiResponse,
  type FetchKpiParams,
  fetchOperationKpi,
} from "@/lib/portal/operationKpi";

type DateRangePreset = "today" | "week" | "month" | "custom";

function toDateStr(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function todayStr(): string {
  return toDateStr(new Date());
}

function nDaysAgoStr(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return toDateStr(d);
}

interface KpiCardProps {
  label: string;
  value: string;
}

function KpiCard({ label, value }: KpiCardProps) {
  return (
    <div className="rounded-lg border bg-card p-4 shadow-sm" role="group" aria-label={`${label} 지표`}>
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="mt-1 text-2xl font-bold">{value}</p>
    </div>
  );
}

export default function InsightsPage(): JSX.Element {
  const [preset, setPreset] = useState<DateRangePreset>("week");
  const [fromDate, setFromDate] = useState<string>(nDaysAgoStr(6));
  const [toDate, setToDate] = useState<string>(todayStr());
  const [data, setData] = useState<OperationKpiResponse | null>(null);
  // 초기 로딩을 true로 — 마운트 직후 "데이터 없음" 깜빡임 방지
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  function applyPreset(p: DateRangePreset) {
    setPreset(p);
    const today = todayStr();
    if (p === "today") {
      setFromDate(today);
      setToDate(today);
    } else if (p === "week") {
      setFromDate(nDaysAgoStr(6));
      setToDate(today);
    } else if (p === "month") {
      setFromDate(nDaysAgoStr(29));
      setToDate(today);
    }
    // "custom"은 날짜 입력을 직접 사용
  }

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // BE는 UTC 기준으로 기간을 해석한다. KST 사용자는 9시간 차이가 있으므로
      // 필요 시 BE 담당자와 타임존 기준 맞춤 필요.
      const params: FetchKpiParams = {
        from: `${fromDate}T00:00:00.000Z`,
        to: `${toDate}T23:59:59.999Z`,
      };
      const result = await fetchOperationKpi(params);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "KPI 데이터를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [fromDate, toDate]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  function handleSearch(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    void loadData();
  }

  // BE 응답 topFacilities는 순위순 배열 (index 0 = 1위)이며 시설명을 함께 싣는다.
  // 수치 없이 순위만 있으므로 막대 차트 대신 순위 리스트로 표시한다.
  const topFacilities = data?.facility.topFacilities ?? [];

  const PRESET_LABELS: Record<DateRangePreset, string> = {
    today: "오늘",
    week: "최근 7일",
    month: "최근 30일",
    custom: "사용자 지정",
  };

  return (
    <main className="min-h-screen space-y-8 p-6">
      <h1 className="text-2xl font-bold">운영 인사이트</h1>

      {/* 기간 필터 */}
      <section aria-label="기간 필터">
        <div className="flex flex-wrap gap-2 mb-4">
          {(["today", "week", "month", "custom"] as DateRangePreset[]).map((p) => (
            <Button
              key={p}
              variant={preset === p ? "default" : "outline"}
              size="sm"
              onClick={() => applyPreset(p)}
              aria-pressed={preset === p}
              aria-label={`기간 ${PRESET_LABELS[p]} 선택`}
            >
              {PRESET_LABELS[p]}
            </Button>
          ))}
        </div>

        {preset === "custom" && (
          <form onSubmit={handleSearch} className="flex flex-wrap items-end gap-4">
            <div>
              <label htmlFor="from-date" className="mb-1 block text-sm font-medium">
                시작일
              </label>
              <input
                id="from-date"
                type="date"
                value={fromDate}
                onChange={(e) => setFromDate(e.target.value)}
                max={toDate}
                className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                aria-label="조회 시작일"
              />
            </div>
            <div>
              <label htmlFor="to-date" className="mb-1 block text-sm font-medium">
                종료일
              </label>
              <input
                id="to-date"
                type="date"
                value={toDate}
                onChange={(e) => setToDate(e.target.value)}
                min={fromDate}
                max={todayStr()}
                className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                aria-label="조회 종료일"
              />
            </div>
            <Button type="submit" size="sm" aria-label="KPI 조회">
              조회
            </Button>
          </form>
        )}
      </section>

      {/* 오류 */}
      {error !== null && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}

      {/* 로딩 */}
      {loading && (
        <p aria-busy="true" className="text-sm text-muted-foreground">
          데이터를 불러오는 중...
        </p>
      )}

      {!loading && data !== null && (
        <>
          {/* 시설 KPI */}
          <section aria-label="시설 KPI">
            <h2 className="mb-3 text-lg font-semibold">시설</h2>
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
              <KpiCard
                label="가동률"
                value={`${data.facility.utilizationRate.toFixed(1)}%`}
              />
              <KpiCard
                label="노쇼율"
                value={`${data.facility.noShowRate.toFixed(1)}%`}
              />
              <KpiCard
                label="인기 시설 수"
                value={`${data.facility.topFacilities.length}개`}
              />
            </div>
          </section>

          {/* 굿즈 KPI */}
          <section aria-label="굿즈 KPI">
            <h2 className="mb-3 text-lg font-semibold">굿즈</h2>
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
              <KpiCard
                label="일 매출 합계"
                value={formatKrw(data.goods.dailyRevenueTotal)}
              />
              {/*
                BE 필드명은 inventoryTurnoverRate지만 실제 계산은 `기간매출 ÷ 활성상품수`라
                회전율(횟수)이 아니라 상품당 평균 매출액(원)이다(GoodsDomainService#aggregateGoodsKpi).
                옆 칸 "일 매출 합계"(`기간매출 ÷ 기간일수`)와 값이 같아 보일 수 있는데, 분자가 같고
                분모가 우연히 일치할 때(활성상품수 == 기간일수)뿐이다 — 같은 소스가 아니다.
                진짜 회전율(판매수량 ÷ 평균재고)은 집계 쿼리가 없어 별도 과제로 남아 있다.
              */}
              <KpiCard
                label="상품당 평균 매출"
                value={formatKrw(data.goods.inventoryTurnoverRate)}
              />
              <KpiCard
                label="품절 SKU"
                value={`${data.goods.outOfStockSkuCount}개`}
              />
            </div>
          </section>

          {/* 티켓 KPI */}
          <section aria-label="티켓 KPI">
            <h2 className="mb-3 text-lg font-semibold">티켓</h2>
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
              <KpiCard
                label="판매 수량"
                value={`${data.ticket.totalSoldCount.toLocaleString("ko-KR")}장`}
              />
              <KpiCard
                label="환불율"
                value={`${data.ticket.refundRate.toFixed(1)}%`}
              />
              <KpiCard
                label="무료 증정"
                value={`${data.ticket.complimentaryCount}장`}
              />
            </div>
          </section>

          {/* 인기 시설 TOP5 순위 리스트 */}
          <section aria-label="인기 시설 TOP5">
            <h2 className="mb-3 text-lg font-semibold">인기 시설 TOP5</h2>
            {topFacilities.length === 0 ? (
              <p className="text-sm text-muted-foreground">데이터가 없습니다.</p>
            ) : (
              <ol
                className="rounded-lg border bg-card divide-y"
                aria-label="인기 시설 순위 목록"
              >
                {topFacilities.map((facility, index) => (
                  <li
                    key={facility.id}
                    className="flex items-center gap-4 px-4 py-3 text-sm"
                  >
                    <span
                      className="w-6 h-6 rounded-full bg-primary text-primary-foreground text-xs font-bold flex items-center justify-center shrink-0"
                      aria-label={`${index + 1}위`}
                    >
                      {index + 1}
                    </span>
                    <span>{facility.name}</span>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </>
      )}

      {!loading && data === null && error === null && (
        <p className="text-sm text-muted-foreground">표시할 데이터가 없습니다.</p>
      )}
    </main>
  );
}
