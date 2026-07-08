/**
 * program.ts — 시설상품(program) 조회 API 타입 및 호출
 *
 * 근거: `20260707-모집-시설상품-소모임예약연동-tdd.md` "REST API 계약", BE
 * `ProgramApiController`(`application/facility/dto/ProgramResponse`). 등록·수정은
 * 웹 운영 포털 전용(design-fe-app "기능별 담당 플랫폼") — 앱은 목록 조회만 다룬다.
 * 컴포넌트에서 직접 호출 금지 — `lib/useProgram.ts` 훅을 통해서만 사용한다.
 */
import { getBeClient } from './be-client';

/** `application/facility/dto/ProgramResponse` — Controller가 그대로 반환한다. */
export interface ProgramResponse {
  id: number;
  facilityId: string;
  ownerUserId: number;
  name: string;
  description: string | null;
  /** BigDecimal → JSON number(Jackson 기본 직렬화) */
  price: number;
  capacity: number;
  durationMinutes: number;
}

/**
 * `GET /facilities/{facilityId}/programs` — `facility.program.enabled` 플래그가 꺼져 있으면
 * BE가 빈 자체를 등록하지 않아 404를 반환한다(Release Scenario 즉시 롤백 지점).
 */
export async function listPrograms(facilityId: string): Promise<ProgramResponse[]> {
  const res = await getBeClient().get<ProgramResponse[]>(`/facilities/${facilityId}/programs`);
  return res.data;
}
