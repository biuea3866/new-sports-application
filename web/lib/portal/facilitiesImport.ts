/**
 * 시설 레거시 임포트 BFF API 클라이언트.
 * Client Component에서는 /api/portal/facilities/import BFF 엔드포인트만 호출한다.
 */
import type { ImportFacilitiesInput, ImportFacilitiesResult } from "./types";

/** 레거시 시설 데이터 일괄 임포트 */
export async function importLegacyFacilities(
  input: ImportFacilitiesInput
): Promise<ImportFacilitiesResult> {
  const res = await fetch("/api/portal/facilities/import", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `시설 임포트 실패: ${res.status}`);
  }
  return res.json() as Promise<ImportFacilitiesResult>;
}
