/**
 * useSlots — GET /facilities/{facilityId}/slots TanStack Query 훅
 * useCreateBooking — POST /bookings mutation 훅
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createBooking, listSlots } from '../api/booking';
import type { CreateBookingBody, CreateBookingResult, SlotResponse } from '../api/types';

/** programId 지정 시 해당 시설상품(program) 회차 슬롯만 조회한다(A-F2). */
export function useSlots(facilityId: string, programId?: number) {
  return useQuery<SlotResponse[], Error>({
    queryKey: ['slots', facilityId, programId ?? null],
    queryFn: () => listSlots(facilityId, programId),
    enabled: facilityId.length > 0,
  });
}

export function useCreateBooking() {
  const queryClient = useQueryClient();

  return useMutation<CreateBookingResult, Error, CreateBookingBody>({
    mutationFn: (body) => createBooking(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['bookings', 'me'] });
    },
  });
}
