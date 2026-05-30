"use client";

import React, { useCallback, useEffect, useState } from "react";
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from "recharts";
import { Button } from "@/components/ui/button";
import {
  type UsageAnalyticsResponse,
  type FetchUsageAnalyticsParams,
  fetchUsageAnalytics,
} from "@/lib/admin/mcp/usageAnalytics";

function todayStr(): string {
  return new Date().toISOString().slice(0, 10);
}

function sevenDaysAgoStr(): string {
  const d = new Date();
  d.setDate(d.getDate() - 6);
  return d.toISOString().slice(0, 10);
}

/** dailyStats를 date별로 집계해 LineChart용 데이터로 변환한다. */
function aggregateDailyStats(
  dailyStats: UsageAnalyticsResponse["dailyStats"]
): { date: string; callCount: number }[] {
  const map = new Map<string, number>();
  for (const stat of dailyStats) {
    map.set(stat.date, (map.get(stat.date) ?? 0) + stat.callCount);
  }
  return Array.from(map.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, callCount]) => ({ date, callCount }));
}

export default function UsageAnalyticsPage(): JSX.Element {
  const [fromDate, setFromDate] = useState<string>(sevenDaysAgoStr());
  const [toDate, setToDate] = useState<string>(todayStr());
  const [data, setData] = useState<UsageAnalyticsResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: FetchUsageAnalyticsParams = {
        from: `${fromDate}T00:00:00.000Z`,
        to: `${toDate}T23:59:59.999Z`,
      };
      const result = await fetchUsageAnalytics(params);
      setData(result);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "사용 분석 데이터를 불러오지 못했습니다."
      );
    } finally {
      setLoading(false);
    }
  }, [fromDate, toDate]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  function handleSearch(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
  }

  const dailyAggregated = data ? aggregateDailyStats(data.dailyStats) : [];

  return (
    <main className="min-h-screen space-y-8 p-6">
      <h1 className="text-2xl font-bold">MCP 사용 분석</h1>

      {/* 기간 필터 */}
      <section aria-label="기간 필터">
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
            />
          </div>
          <Button type="submit" size="sm" aria-label="사용 분석 조회">
            조회
          </Button>
        </form>
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
          데이터를 불러오는 중...
        </p>
      )}

      {!loading && data !== null && (
        <>
          {/* 에러율 요약 카드 */}
          <section aria-label="에러율 요약">
            <h2 className="mb-3 text-lg font-semibold">에러율 요약</h2>
            <div className="grid grid-cols-3 gap-4">
              <div className="rounded-lg border bg-white p-4 shadow-sm">
                <p className="text-sm text-gray-500">총 호출</p>
                <p className="mt-1 text-2xl font-bold">
                  {data.errorRateStat.totalCount.toLocaleString("ko-KR")}
                </p>
              </div>
              <div className="rounded-lg border bg-white p-4 shadow-sm">
                <p className="text-sm text-gray-500">에러 수</p>
                <p className="mt-1 text-2xl font-bold text-red-600">
                  {data.errorRateStat.errorCount.toLocaleString("ko-KR")}
                </p>
              </div>
              <div className="rounded-lg border bg-white p-4 shadow-sm">
                <p className="text-sm text-gray-500">에러율</p>
                <p className="mt-1 text-2xl font-bold text-orange-600">
                  {data.errorRateStat.errorRatePercent.toFixed(2)}%
                </p>
              </div>
            </div>
          </section>

          {/* 일별 호출 추이 LineChart */}
          <section aria-label="일별 호출 추이">
            <h2 className="mb-3 text-lg font-semibold">일별 호출 추이</h2>
            {dailyAggregated.length === 0 ? (
              <p className="text-sm text-gray-400">데이터가 없습니다.</p>
            ) : (
              <div className="rounded-lg border bg-white p-4 shadow-sm">
                <ResponsiveContainer width="100%" height={260}>
                  <LineChart data={dailyAggregated}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="date" tick={{ fontSize: 12 }} />
                    <YAxis tick={{ fontSize: 12 }} />
                    <Tooltip />
                    <Legend />
                    <Line
                      type="monotone"
                      dataKey="callCount"
                      name="호출 수"
                      stroke="#3b82f6"
                      strokeWidth={2}
                      dot={false}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            )}
          </section>

          {/* Tool별 호출 TOP BarChart */}
          <section aria-label="Tool별 호출 현황">
            <h2 className="mb-3 text-lg font-semibold">Tool별 호출 TOP</h2>
            {data.toolCallStats.length === 0 ? (
              <p className="text-sm text-gray-400">데이터가 없습니다.</p>
            ) : (
              <div className="rounded-lg border bg-white p-4 shadow-sm">
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={data.toolCallStats} layout="vertical">
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis type="number" tick={{ fontSize: 12 }} />
                    <YAxis
                      type="category"
                      dataKey="toolName"
                      tick={{ fontSize: 11 }}
                      width={140}
                    />
                    <Tooltip />
                    <Bar dataKey="callCount" name="호출 수" fill="#3b82f6" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </section>

          {/* Tool별 P95 응답 시간 BarChart */}
          <section aria-label="Tool별 P95 응답 시간">
            <h2 className="mb-3 text-lg font-semibold">Tool별 P95 응답 시간 (ms)</h2>
            {data.toolLatencyStats.length === 0 ? (
              <p className="text-sm text-gray-400">데이터가 없습니다.</p>
            ) : (
              <div className="rounded-lg border bg-white p-4 shadow-sm">
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={data.toolLatencyStats} layout="vertical">
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis type="number" tick={{ fontSize: 12 }} unit="ms" />
                    <YAxis
                      type="category"
                      dataKey="toolName"
                      tick={{ fontSize: 11 }}
                      width={140}
                    />
                    <Tooltip formatter={(value: number) => [`${value}ms`, "P95"]} />
                    <Bar dataKey="p95LatencyMs" name="P95 (ms)" fill="#8b5cf6" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </section>

          {/* 토큰별 사용 테이블 */}
          <section aria-label="토큰별 사용 현황">
            <h2 className="mb-3 text-lg font-semibold">토큰별 사용 현황</h2>
            <div className="overflow-x-auto rounded-md border">
              <table className="min-w-full text-sm">
                <thead className="bg-gray-50">
                  <tr>
                    <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                      Token ID
                    </th>
                    <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                      호출 수
                    </th>
                    <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                      에러 수
                    </th>
                    <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                      에러율
                    </th>
                    <th scope="col" className="px-4 py-3 text-left font-medium text-gray-600">
                      마지막 호출
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {data.tokenUsageStats.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="px-4 py-8 text-center text-gray-400">
                        데이터가 없습니다.
                      </td>
                    </tr>
                  ) : (
                    data.tokenUsageStats.map((stat) => (
                      <tr key={stat.tokenId} className="hover:bg-gray-50">
                        <td className="px-4 py-3 font-mono text-xs">{stat.tokenId}</td>
                        <td className="px-4 py-3">{stat.callCount.toLocaleString("ko-KR")}</td>
                        <td className="px-4 py-3 text-red-600">
                          {stat.errorCount.toLocaleString("ko-KR")}
                        </td>
                        <td className="px-4 py-3 text-orange-600">
                          {stat.errorRatePercent.toFixed(2)}%
                        </td>
                        <td className="px-4 py-3 text-gray-600">
                          {stat.lastCalledAt !== null
                            ? new Date(stat.lastCalledAt).toLocaleString("ko-KR")
                            : "-"}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </main>
  );
}
