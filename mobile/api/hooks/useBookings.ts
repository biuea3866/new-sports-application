/**
 * useBookings.ts — 예약 도메인 react-query 훅
 */
import {
  useQuery,
  useMutation,
  useQueryClient,
  type UseQueryOptions,
  type UseMutationOptions,
} from '@tanstack/react-query';
import {
  createBooking,
  getMyBookings,
  getBookingById,
  cancelBooking,
  type CreateBookingRequest,
  type BookingDto,
  type BookingListParams,
} from '../bookings';
import { type PageResponse } from '../facilities';
import { bookingsKeys } from '../queryKeys';

export function useMyBookingsQuery(
  params?: BookingListParams,
  options?: Omit<UseQueryOptions<PageResponse<BookingDto>>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: bookingsKeys.myList(params ?? {}),
    queryFn: () => getMyBookings(params),
    ...options,
  });
}

export function useBookingDetailQuery(
  id: number,
  options?: Omit<UseQueryOptions<BookingDto>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: bookingsKeys.detail(id),
    queryFn: () => getBookingById(id),
    enabled: id > 0,
    ...options,
  });
}

export function useCreateBookingMutation(
  options?: UseMutationOptions<BookingDto, Error, CreateBookingRequest>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createBooking,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: bookingsKeys.mine() });
    },
    ...options,
  });
}

export function useCancelBookingMutation(
  options?: UseMutationOptions<BookingDto, Error, number>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => cancelBooking(id),
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: bookingsKeys.mine() });
      queryClient.invalidateQueries({ queryKey: bookingsKeys.detail(id) });
    },
    ...options,
  });
}
