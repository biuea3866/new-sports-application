/**
 * usePaymentHistory — 결제 내역 TanStack Query 훅
 *
 * GET /payments/me?page=&size=&status=
 */
import { useQuery } from '@tanstack/react-query';
import { getMyPayments, PaymentStatus } from '../api/payment';
import type { PaymentHistoryListResponse } from '../api/types';

export const PAYMENT_HISTORY_QUERY_KEY = ['paymentHistory'] as const;

export function usePaymentHistoryQuery(page = 0, size = 20, status?: PaymentStatus) {
  return useQuery<PaymentHistoryListResponse, Error>({
    queryKey: [...PAYMENT_HISTORY_QUERY_KEY, page, size, status],
    queryFn: () => getMyPayments(page, size, status),
  });
}
