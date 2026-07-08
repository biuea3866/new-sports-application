/**
 * communityBooking.ts — 소모임 예약(community↔booking) API 호출
 *
 * 근거: `20260707-모집-시설상품-소모임예약연동-tdd.md` "REST API 계약" B3(FR-13~15), BE
 * `CommunityBookingApiController`. slot 선택은 기존 `api/booking.ts#listSlots` 재사용.
 * 컴포넌트에서 직접 호출 금지 — `lib/useCommunityBooking.ts` 훅을 통해서만 사용한다.
 */
import { getBeClient } from './be-client';
import type {
  CommunityBookingListItemResponse,
  CommunityBookingResponse,
  LinkCommunityBookingRequest,
} from './community-types';

/**
 * `GET /communities/{communityId}/bookings` — `community.booking.enabled` 플래그가 꺼져
 * 있으면 BE가 빈 자체를 등록하지 않아 404를 반환한다(Release Scenario 즉시 롤백 지점).
 */
export async function listCommunityBookings(
  communityId: number
): Promise<CommunityBookingListItemResponse[]> {
  const res = await getBeClient().get<CommunityBookingListItemResponse[]>(
    `/communities/${communityId}/bookings`
  );
  return res.data;
}

/** `POST /communities/{communityId}/bookings` — 방장만 가능(FR-13). */
export async function linkCommunityBooking(
  communityId: number,
  slotId: number
): Promise<CommunityBookingResponse> {
  const body: LinkCommunityBookingRequest = { slotId };
  const res = await getBeClient().post<CommunityBookingResponse>(
    `/communities/${communityId}/bookings`,
    body
  );
  return res.data;
}
