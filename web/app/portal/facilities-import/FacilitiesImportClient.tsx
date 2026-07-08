"use client";

import * as React from "react";
import { Button } from "@/components/ui/button";
import { parseCsvFacilities } from "./parseCsvFacilities";
import type { CsvFacilityRow, CsvRowError, ParseCsvResult } from "./parseCsvFacilities";
import type { CreateFacilityInput } from "@/lib/portal/types";

// ─── 타입 ─────────────────────────────────────────────────────────────────────

interface RowResult {
  rowIndex: number;
  code: string;
  name: string;
  status: "success" | "error";
  errorMessage?: string;
}

type ImportPhase = "idle" | "parsed" | "importing" | "done";

// ─── 유틸 ─────────────────────────────────────────────────────────────────────

function rowToCreateInput(row: CsvFacilityRow): CreateFacilityInput {
  return {
    code: row.code,
    name: row.name,
    sido: row.sido,
    gu: row.gu,
    type: row.type,
    address: row.address,
    location: row.location,
    parking: row.parking,
    tel: row.tel,
    homePage: row.homePage,
    eduYn: row.eduYn,
    meta: row.meta,
  };
}

async function postFacility(input: CreateFacilityInput): Promise<void> {
  const res = await fetch("/api/portal/facilities", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });

  if (!res.ok) {
    const body = (await res.json().catch(() => ({}))) as { message?: string; detail?: string };
    throw new Error(body.detail ?? body.message ?? `HTTP ${res.status.toString()}`);
  }
}

// ─── 컴포넌트 ─────────────────────────────────────────────────────────────────

export default function FacilitiesImportClient() {
  const [phase, setPhase] = React.useState<ImportPhase>("idle");
  const [parseResult, setParseResult] = React.useState<ParseCsvResult | null>(null);
  const [rowResults, setRowResults] = React.useState<RowResult[]>([]);
  const [progress, setProgress] = React.useState<{ done: number; total: number }>({ done: 0, total: 0 });
  const [fileError, setFileError] = React.useState<string | null>(null);
  const fileInputRef = React.useRef<HTMLInputElement>(null);

  // 파일 선택
  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;

    setFileError(null);
    setParseResult(null);
    setRowResults([]);
    setPhase("idle");

    if (!file.name.endsWith(".csv")) {
      setFileError("CSV 파일(.csv)만 업로드할 수 있습니다.");
      return;
    }

    const reader = new FileReader();
    reader.onload = (evt) => {
      const text = evt.target?.result;
      if (typeof text !== "string") {
        setFileError("파일을 읽을 수 없습니다.");
        return;
      }
      const result = parseCsvFacilities(text);
      setParseResult(result);
      setPhase("parsed");
    };
    reader.onerror = () => setFileError("파일 읽기 중 오류가 발생했습니다.");
    reader.readAsText(file, "utf-8");
  }

  // 일괄 등록 실행
  async function handleImport() {
    if (!parseResult || parseResult.valid.length === 0) return;

    const rows = parseResult.valid;
    setPhase("importing");
    setProgress({ done: 0, total: rows.length });
    setRowResults([]);

    const results: RowResult[] = [];

    for (const row of rows) {
      try {
        await postFacility(rowToCreateInput(row));
        results.push({ rowIndex: row.rowIndex, code: row.code, name: row.name, status: "success" });
      } catch (err) {
        const message = err instanceof Error ? err.message : "알 수 없는 오류";
        results.push({ rowIndex: row.rowIndex, code: row.code, name: row.name, status: "error", errorMessage: message });
      }
      setProgress({ done: results.length, total: rows.length });
      setRowResults([...results]);
    }

    setPhase("done");
  }

  // 초기화
  function handleReset() {
    setPhase("idle");
    setParseResult(null);
    setRowResults([]);
    setProgress({ done: 0, total: 0 });
    setFileError(null);
    if (fileInputRef.current) fileInputRef.current.value = "";
  }

  const successCount = rowResults.filter((r) => r.status === "success").length;
  const failCount = rowResults.filter((r) => r.status === "error").length;

  return (
    <div className="space-y-8">
      {/* 파일 업로드 영역 */}
      <section aria-labelledby="upload-heading">
        <h2 id="upload-heading" className="text-base font-semibold mb-3">
          1단계: CSV 파일 선택
        </h2>
        <p className="text-sm text-muted-foreground mb-2">
          컬럼 순서: <code className="text-xs bg-muted px-1 py-0.5 rounded">code, name, sido, gu, type, address, lat, lng, parking, tel, homePage, eduYn, meta</code>
        </p>
        <p className="text-sm text-muted-foreground mb-4">
          type: <code className="text-xs">INDOOR | OUTDOOR | MIXED</code> &nbsp;|&nbsp;
          parking / eduYn: <code className="text-xs">true / false</code> &nbsp;|&nbsp;
          sido: 시/도 표준코드(선택, 미입력 시 주소로 자동 판별)
        </p>

        <div className="flex items-center gap-3">
          <label htmlFor="csv-file-input" className="sr-only">
            CSV 파일 선택
          </label>
          <input
            id="csv-file-input"
            ref={fileInputRef}
            type="file"
            accept=".csv,text/csv"
            onChange={handleFileChange}
            disabled={phase === "importing"}
            aria-describedby={fileError ? "csv-file-error" : undefined}
            aria-invalid={!!fileError}
            className="block text-sm file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:text-sm file:font-medium file:bg-primary file:text-primary-foreground hover:file:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed"
          />
          {(phase === "parsed" || phase === "done") && (
            <Button type="button" variant="outline" size="sm" onClick={handleReset} aria-label="초기화하고 다른 파일 선택">
              초기화
            </Button>
          )}
        </div>

        {fileError && (
          <p id="csv-file-error" role="alert" className="mt-2 text-sm text-destructive">
            {fileError}
          </p>
        )}
      </section>

      {/* 파싱 결과 미리보기 */}
      {parseResult !== null && phase !== "idle" && (
        <section aria-labelledby="preview-heading">
          <h2 id="preview-heading" className="text-base font-semibold mb-3">
            2단계: 파싱 결과 확인
          </h2>

          <div className="flex gap-4 mb-4 text-sm">
            <span className="text-green-700 font-medium">유효 행: {parseResult.valid.length}건</span>
            {parseResult.errors.length > 0 && (
              <span className="text-destructive font-medium">오류 행: {parseResult.errors.length}건</span>
            )}
          </div>

          {/* 유효 행 미리보기 */}
          {parseResult.valid.length > 0 && (
            <div className="overflow-x-auto rounded-md border mb-4">
              <table className="min-w-full text-xs" aria-label="유효 행 미리보기">
                <thead className="bg-muted">
                  <tr>
                    <th scope="col" className="px-3 py-2 text-left font-medium">행</th>
                    <th scope="col" className="px-3 py-2 text-left font-medium">코드</th>
                    <th scope="col" className="px-3 py-2 text-left font-medium">시설명</th>
                    <th scope="col" className="px-3 py-2 text-left font-medium">시/도</th>
                    <th scope="col" className="px-3 py-2 text-left font-medium">구</th>
                    <th scope="col" className="px-3 py-2 text-left font-medium">유형</th>
                    <th scope="col" className="px-3 py-2 text-left font-medium">위치</th>
                    <th scope="col" className="px-3 py-2 text-left font-medium">전화번호</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {parseResult.valid.map((row) => (
                    <tr key={row.rowIndex}>
                      <td className="px-3 py-2 text-muted-foreground">{row.rowIndex}</td>
                      <td className="px-3 py-2 font-mono">{row.code}</td>
                      <td className="px-3 py-2">{row.name}</td>
                      <td className="px-3 py-2">{row.sido ?? "미지정"}</td>
                      <td className="px-3 py-2">{row.gu}</td>
                      <td className="px-3 py-2">{row.type}</td>
                      <td className="px-3 py-2 font-mono">{row.location}</td>
                      <td className="px-3 py-2">{row.tel}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* 오류 행 */}
          {parseResult.errors.length > 0 && (
            <div className="rounded-md border border-destructive/40 bg-destructive/5 p-4 mb-4">
              <h3 className="text-sm font-semibold text-destructive mb-2">오류 행 목록</h3>
              <ul className="space-y-2" aria-label="CSV 파싱 오류 목록">
                {parseResult.errors.map((err: CsvRowError) => (
                  <li key={err.rowIndex} className="text-xs">
                    <span className="font-medium">행 {err.rowIndex}:</span>{" "}
                    {err.errors.join(", ")}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {parseResult.valid.length === 0 && (
            <p role="alert" className="text-sm text-destructive">
              등록 가능한 유효 행이 없습니다. CSV를 확인해 주세요.
            </p>
          )}

          {parseResult.valid.length > 0 && phase === "parsed" && (
            <Button
              type="button"
              onClick={() => { void handleImport(); }}
              aria-label={`${parseResult.valid.length.toString()}건 일괄 등록 시작`}
            >
              일괄 등록 시작 ({parseResult.valid.length}건)
            </Button>
          )}
        </section>
      )}

      {/* 진행률 */}
      {(phase === "importing" || phase === "done") && progress.total > 0 && (
        <section aria-labelledby="progress-heading">
          <h2 id="progress-heading" className="text-base font-semibold mb-3">
            3단계: 등록 진행
          </h2>

          <div className="mb-2">
            <div
              className="h-2 rounded-full bg-muted overflow-hidden"
              role="progressbar"
              aria-valuenow={progress.done}
              aria-valuemin={0}
              aria-valuemax={progress.total}
              aria-label="일괄 등록 진행률"
            >
              <div
                className="h-full bg-primary transition-all duration-300"
                style={{ width: `${((progress.done / progress.total) * 100).toFixed(1)}%` }}
              />
            </div>
            <p className="text-xs text-muted-foreground mt-1" aria-live="polite">
              {progress.done} / {progress.total}건 처리됨
            </p>
          </div>

          {/* 행별 결과 */}
          {rowResults.length > 0 && (
            <div className="overflow-x-auto rounded-md border mt-4">
              <table className="min-w-full text-xs" aria-label="행별 등록 결과">
                <thead className="bg-muted">
                  <tr>
                    <th scope="col" className="px-3 py-2 text-left font-medium">행</th>
                    <th scope="col" className="px-3 py-2 text-left font-medium">코드</th>
                    <th scope="col" className="px-3 py-2 text-left font-medium">시설명</th>
                    <th scope="col" className="px-3 py-2 text-left font-medium">결과</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {rowResults.map((r) => (
                    <tr key={r.rowIndex}>
                      <td className="px-3 py-2 text-muted-foreground">{r.rowIndex}</td>
                      <td className="px-3 py-2 font-mono">{r.code}</td>
                      <td className="px-3 py-2">{r.name}</td>
                      <td className="px-3 py-2">
                        {r.status === "success" ? (
                          <span className="text-green-700 font-medium">성공</span>
                        ) : (
                          <span className="text-destructive">
                            실패 — {r.errorMessage}
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {/* 최종 요약 */}
      {phase === "done" && (
        <section aria-labelledby="summary-heading">
          <h2 id="summary-heading" className="text-base font-semibold mb-3">
            완료
          </h2>
          <div
            role="status"
            aria-live="polite"
            className="rounded-md border p-4 space-y-1 text-sm"
          >
            <p>
              <span className="text-green-700 font-semibold">성공:</span> {successCount}건
            </p>
            {failCount > 0 && (
              <p>
                <span className="text-destructive font-semibold">실패:</span> {failCount}건
              </p>
            )}
          </div>
          <Button type="button" variant="outline" className="mt-4" onClick={handleReset} aria-label="다시 처음부터 시작">
            새 파일 업로드
          </Button>
        </section>
      )}
    </div>
  );
}
