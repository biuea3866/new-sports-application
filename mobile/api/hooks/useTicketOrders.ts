/**
 * useTicketOrders.ts — 티켓 주문 도메인 react-query 훅
 */
import { useMutation, type UseMutationOptions } from '@tanstack/react-query';
import {
  createTicketOrder,
  type CreateTicketOrderRequest,
  type TicketOrderDto,
} from '../ticketOrders';

export function useCreateTicketOrderMutation(
  options?: UseMutationOptions<TicketOrderDto, Error, CreateTicketOrderRequest>
) {
  return useMutation({
    mutationFn: createTicketOrder,
    ...options,
  });
}
