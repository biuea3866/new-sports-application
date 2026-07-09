/**
 * virtualQueue.ts — 가상 대기열 진입·상태 조회·이탈 API 함수
 *
 * BE API 계약(`20260709-가상대기열-tdd.md` "FE/외부 계약 — API 명세"):
 * - POST   /virtual-queues/{type}/{targetId}/entries      → 200 QueueEntryResponse | 429 QUEUE_FULL
 * - GET    /virtual-queues/{type}/{targetId}/entries/me   → 200 QueueEntryResponse | 404 없음
 * - DELETE /virtual-queues/{type}/{targetId}/entries/me   → 204 (best-effort)
 *
 * 429/404는 정상 실패 경로이므로 예외를 던지지 않고 판별 유니온으로 반환한다
 * (`limitedDrops.ts#mapPurchaseFailure` 선례와 동형). 5xx·네트워크 오류는 그대로 전파한다.
 *
 * `X-User-Id` 헤더 값은 호출부(훅 — `api/goods.ts#useCurrentUserId`)가 주입한다.
 * 이 파일은 훅을 알지 못한다(순수 함수 계층).
 */
import { AxiosError } from 'axios';

import { getBeClient } from './be-client';

/** BE `{type}` 경로 세그먼트 — 한정판/티케팅 두 도메인만 대기열을 경유한다 */
export type QueueTargetType = 'limited-drop' | 'ticketing-event';

export type QueueEntryStatus = 'WAITING' | 'ADMITTED' | 'DIRECT_ADMITTED';

export interface QueueEntryResponse {
  status: QueueEntryStatus;
  /** 순번(1-based). ADMITTED/DIRECT_ADMITTED는 null */
  position: number | null;
  /** 앞선 대기 인원. WAITING만 */
  aheadCount: number | null;
  /** 예상 대기 초. WAITING만 */
  etaSeconds: number | null;
  /** ADMITTED/DIRECT_ADMITTED일 때만 발급 */
  entryToken: string | null;
  /** ISO-8601, entryToken 있을 때 */
  tokenExpiresAt: string | null;
}

/**
 * 대기열 진입 결과.
 * - ENTERED: 200 성공 — WAITING/ADMITTED/DIRECT_ADMITTED 중 하나의 상태를 받는다
 * - FULL: 429 — 큐 포화(`QUEUE_FULL`)
 */
export type QueueEnterResult =
  | { outcome: 'ENTERED'; data: QueueEntryResponse }
  | { outcome: 'FULL' };

/**
 * 순번·상태 조회(폴링/heartbeat) 결과.
 * - OK: 200 성공
 * - NOT_IN_QUEUE: 404 — 큐에 없음(이탈·미진입) → 재진입 유도
 */
export type QueueStatusResult =
  | { outcome: 'OK'; data: QueueEntryResponse }
  | { outcome: 'NOT_IN_QUEUE' };

function userIdHeader(userId: number): { 'X-User-Id': string } {
  return { 'X-User-Id': String(userId) };
}

export async function enterQueue(
  type: QueueTargetType,
  targetId: number,
  userId: number
): Promise<QueueEnterResult> {
  try {
    const response = await getBeClient().post<QueueEntryResponse>(
      `/virtual-queues/${type}/${targetId}/entries`,
      undefined,
      { headers: userIdHeader(userId) }
    );
    return { outcome: 'ENTERED', data: response.data };
  } catch (error) {
    return mapEnterFailure(error);
  }
}

function mapEnterFailure(error: unknown): QueueEnterResult {
  if (!(error instanceof AxiosError) || !error.response) {
    throw error;
  }
  if (error.response.status === 429) {
    return { outcome: 'FULL' };
  }
  throw error;
}

export async function getQueueStatus(
  type: QueueTargetType,
  targetId: number,
  userId: number
): Promise<QueueStatusResult> {
  try {
    const response = await getBeClient().get<QueueEntryResponse>(
      `/virtual-queues/${type}/${targetId}/entries/me`,
      { headers: userIdHeader(userId) }
    );
    return { outcome: 'OK', data: response.data };
  } catch (error) {
    return mapStatusFailure(error);
  }
}

function mapStatusFailure(error: unknown): QueueStatusResult {
  if (!(error instanceof AxiosError) || !error.response) {
    throw error;
  }
  if (error.response.status === 404) {
    return { outcome: 'NOT_IN_QUEUE' };
  }
  throw error;
}

/**
 * 대기열 명시적 이탈 — best-effort.
 * 실패해도 60초 heartbeat 미갱신으로 자동 방출되므로, 실패 시 무시할지는 호출부(훅)가 결정한다.
 * 이 함수 자체는 오류를 삼키지 않고 그대로 전파한다.
 */
export async function leaveQueue(
  type: QueueTargetType,
  targetId: number,
  userId: number
): Promise<void> {
  await getBeClient().delete(`/virtual-queues/${type}/${targetId}/entries/me`, {
    headers: userIdHeader(userId),
  });
}
