/**
 * useCommunityBookings — GET /communities/{communityId}/bookings TanStack Query 훅
 * useLinkCommunityBooking — POST /communities/{communityId}/bookings mutation 훅(방장 전용)
 *
 * slot 선택은 기존 `lib/useBooking.ts#useSlots` 재사용(design-fe-app "API 연동 표").
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { linkCommunityBooking, listCommunityBookings } from '../api/communityBooking';
import type {
  CommunityBookingListItemResponse,
  CommunityBookingResponse,
} from '../api/community-types';

export function communityBookingsQueryKey(communityId: number) {
  return ['communities', communityId, 'bookings'] as const;
}

export function useCommunityBookings(communityId: number) {
  return useQuery<CommunityBookingListItemResponse[], Error>({
    queryKey: communityBookingsQueryKey(communityId),
    queryFn: () => listCommunityBookings(communityId),
    enabled: communityId > 0,
  });
}

interface LinkCommunityBookingVariables {
  slotId: number;
}

/** 성공 시 연결 예약 목록 캐시를 무효화한다. */
export function useLinkCommunityBooking(communityId: number) {
  const queryClient = useQueryClient();

  return useMutation<CommunityBookingResponse, Error, LinkCommunityBookingVariables>({
    mutationFn: ({ slotId }) => linkCommunityBooking(communityId, slotId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: communityBookingsQueryKey(communityId) });
    },
  });
}
