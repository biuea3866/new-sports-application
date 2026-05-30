/**
 * useSlots — GET /facilities/{facilityId}/slots TanStack Query 훅
 * useCreateBooking — POST /bookings mutation 훅
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createBooking, listSlots } from '../api/booking';
import type { CreateBookingBody, CreateBookingResult, SlotResponse } from '../api/types';

export function useSlots(facilityId: string) {
  return useQuery<SlotResponse[], Error>({
    queryKey: ['slots', facilityId],
    queryFn: () => listSlots(facilityId),
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
