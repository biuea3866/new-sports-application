/**
 * 슬롯 관련 타입 정의 및 BFF API 클라이언트 훅.
 * Client Component에서는 /api/portal/slots/{facilityId} BFF 엔드포인트만 호출한다.
 */
import { SlotSchema } from "./schemas";
import type { SlotStatus } from "./types";

export interface SlotResponse {
  id: number;
  facilityId: string;
  date: string;
  timeRange: string;
  capacity: number;
  ownerId: number;
  status: SlotStatus;
  programId: number | null;
}

export interface CreateSlotInput {
  date: string;
  timeRange: string;
  capacity: number;
}

export interface UpdateSlotInput {
  timeRange?: string;
  capacity?: number;
}

/** 슬롯 목록 조회 */
export async function fetchSlots(facilityId: string): Promise<SlotResponse[]> {
  const res = await fetch(`/api/portal/slots/${facilityId}`, { cache: "no-store" });
  if (!res.ok) {
    throw new Error(`슬롯 목록 조회 실패: ${res.status}`);
  }
  return res.json() as Promise<SlotResponse[]>;
}

/** 슬롯 등록 */
export async function createSlot(
  facilityId: string,
  input: CreateSlotInput
): Promise<SlotResponse> {
  const res = await fetch(`/api/portal/slots/${facilityId}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `슬롯 등록 실패: ${res.status}`);
  }
  return res.json() as Promise<SlotResponse>;
}

/** 슬롯 수정 */
export async function updateSlot(
  facilityId: string,
  slotId: number,
  input: UpdateSlotInput
): Promise<SlotResponse> {
  const res = await fetch(`/api/portal/slots/${facilityId}/${slotId}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `슬롯 수정 실패: ${res.status}`);
  }
  return res.json() as Promise<SlotResponse>;
}

/** 슬롯 삭제 */
export async function deleteSlot(facilityId: string, slotId: number): Promise<void> {
  const res = await fetch(`/api/portal/slots/${facilityId}/${slotId}`, {
    method: "DELETE",
  });
  if (!res.ok && res.status !== 204) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `슬롯 삭제 실패: ${res.status}`);
  }
}

/** 슬롯 마감(신규 예약 차단, 기존 예약은 유지) */
export async function closeSlot(facilityId: string, slotId: number): Promise<SlotResponse> {
  const res = await fetch(`/api/portal/facilities/${facilityId}/slots/${slotId}/close`, {
    method: "PATCH",
  });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `슬롯 마감 실패: ${res.status}`);
  }
  const json: unknown = await res.json();
  return SlotSchema.parse(json);
}

/** 슬롯 오픈(신규 예약 허용) */
export async function openSlot(facilityId: string, slotId: number): Promise<SlotResponse> {
  const res = await fetch(`/api/portal/facilities/${facilityId}/slots/${slotId}/open`, {
    method: "PATCH",
  });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `슬롯 오픈 실패: ${res.status}`);
  }
  const json: unknown = await res.json();
  return SlotSchema.parse(json);
}
