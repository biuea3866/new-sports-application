/**
 * useMyBookings — GET /bookings/me TanStack Query 훅
 * useCancelBooking — POST /bookings/{id}/cancel mutation 훅
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { cancelBooking, createBooking, listMyBookings } from '../api/booking';
import type { BookingResponse, CreateBookingRequest, ListBookingsResponse } from '../api/types';

export const MY_BOOKINGS_QUERY_KEY = ['bookings', 'me'] as const;

export function useMyBookings(page = 0, size = 20) {
  return useQuery<ListBookingsResponse, Error>({
    queryKey: [...MY_BOOKINGS_QUERY_KEY, page, size],
    queryFn: () => listMyBookings(page, size),
  });
}

interface CancelBookingVariables {
  id: number;
  reason?: string;
}

export function useCancelBooking() {
  const queryClient = useQueryClient();

  return useMutation<BookingResponse, Error, CancelBookingVariables>({
    mutationFn: ({ id, reason }) => cancelBooking(id, { reason }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: MY_BOOKINGS_QUERY_KEY });
    },
  });
}

export function useCreateBooking() {
  const queryClient = useQueryClient();

  return useMutation<BookingResponse, Error, CreateBookingRequest>({
    mutationFn: (body) => createBooking(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: MY_BOOKINGS_QUERY_KEY });
    },
  });
}
