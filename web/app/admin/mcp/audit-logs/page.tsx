"use client";

import React, { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  type McpAuditLogResponse,
  type FetchAuditLogsParams,
  fetchAuditLogs,
} from "@/lib/admin/auditLogs";

function toIsoString(dateStr: string, endOfDay = false): string {
  // dateStr은 "YYYY-MM-DD" 형식. 로컬 시간대 영향 없이 UTC 기준으로 변환한다.
  const suffix = endOfDay ? "T23:59:59.999Z" : "T00:00:00.000Z";
  return `${dateStr}${suffix}`;
}

function todayStr(): string {
  return new Date().toISOString().slice(0, 10);
}

function sevenDaysAgoStr(): string {
  const d = new Date();
  d.setDate(d.getDate() - 6);
  return d.toISOString().slice(0, 10);
}

const PAGE_SIZE = 20;

export default function AuditLogsPage(): JSX.Element {
  const [fromDate, setFromDate] = useState<string>(sevenDaysAgoStr());
  const [toDate, setToDate] = useState<string>(todayStr());
  const [page, setPage] = useState(0);
  const [logs, setLogs] = useState<McpAuditLogResponse[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadLogs = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: FetchAuditLogsParams = {
        from: toIsoString(fromDate, false),
        to: toIsoString(toDate, true),
        page,
        size: PAGE_SIZE,
      };
      const data = await fetchAuditLogs(params);
      setLogs(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "감사 로그를 불러오지 못했습니다."
      );
    } finally {
      setLoading(false);
    }
  }, [fromDate, toDate, page]);

  useEffect(() => {
    void loadLogs();
  }, [loadLogs]);

  function handleSearch(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setPage(0);
    void loadLogs();
  }

  return (
    <main className="min-h-screen space-y-6 p-6">
      <h1 className="text-2xl font-bold">감사 로그</h1>

      {/* 기간 필터 */}
      <section aria-label="기간 필터">
        <form
          onSubmit={handleSearch}
          className="flex flex-wrap items-end gap-4"
        >
          <div>
            <label
              htmlFor="from-date"
              className="mb-1 block text-sm font-medium"
            >
              시작일
            </label>
            <input
              id="from-date"
              type="date"
              value={fromDate}
              onChange={(e) => {
                setFromDate(e.target.value);
                setPage(0);
              }}
              max={toDate}
              className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              aria-label="조회 시작일"
            />
          </div>
          <div>
            <label
              htmlFor="to-date"
              className="mb-1 block text-sm font-medium"
            >
              종료일
            </label>
            <input
              id="to-date"
              type="date"
              value={toDate}
              onChange={(e) => {
                setToDate(e.target.value);
                setPage(0);
              }}
              min={fromDate}
              max={todayStr()}
              className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              aria-label="조회 종료일"
            />
          </div>
          <Button type="submit" size="sm" aria-label="감사 로그 검색">
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
          감사 로그를 불러오는 중...
        </p>
      )}

      {/* 로그 테이블 */}
      {!loading && (
        <section aria-label="감사 로그 목록">
          <p className="mb-2 text-sm text-gray-500">
            총 {totalElements}건
          </p>
          <div className="overflow-x-auto rounded-md border">
            <table className="min-w-full text-sm">
              <thead className="bg-gray-50">
                <tr>
                  <th
                    scope="col"
                    className="px-4 py-3 text-left font-medium text-gray-600"
                  >
                    Tool Name
                  </th>
                  <th
                    scope="col"
                    className="px-4 py-3 text-left font-medium text-gray-600"
                  >
                    Status Code
                  </th>
                  <th
                    scope="col"
                    className="px-4 py-3 text-left font-medium text-gray-600"
                  >
                    Latency (ms)
                  </th>
                  <th
                    scope="col"
                    className="px-4 py-3 text-left font-medium text-gray-600"
                  >
                    Called At
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {logs.length === 0 ? (
                  <tr>
                    <td
                      colSpan={4}
                      className="px-4 py-8 text-center text-gray-400"
                    >
                      조회된 로그가 없습니다.
                    </td>
                  </tr>
                ) : (
                  logs.map((log) => (
                    <tr key={log.id} className="hover:bg-gray-50">
                      <td className="px-4 py-3 font-mono text-xs">
                        {log.toolName}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={
                            log.statusCode >= 200 && log.statusCode < 300
                              ? "inline-block rounded-full px-2 py-0.5 text-xs font-medium bg-green-100 text-green-800"
                              : "inline-block rounded-full px-2 py-0.5 text-xs font-medium bg-red-100 text-red-700"
                          }
                        >
                          {log.statusCode}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {log.latencyMs.toLocaleString("ko-KR")}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {new Date(log.calledAt).toLocaleString("ko-KR")}
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
              aria-label="감사 로그 페이지 이동"
              className="mt-4 flex items-center justify-center gap-2"
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
              <span aria-current="page" className="text-sm">
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
