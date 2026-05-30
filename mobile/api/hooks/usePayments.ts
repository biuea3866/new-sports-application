/**
 * usePayments.ts — 결제 도메인 react-query 훅
 */
import {
  useQuery,
  useMutation,
  useQueryClient,
  type UseQueryOptions,
  type UseMutationOptions,
} from '@tanstack/react-query';
import {
  executePayment,
  getMyPayments,
  getPaymentById,
  type ExecutePaymentRequest,
  type PaymentDto,
  type PaymentListParams,
} from '../payments';
import { type PageResponse } from '../facilities';
import { paymentsKeys, bookingsKeys, goodsOrdersKeys } from '../queryKeys';

export function useMyPaymentsQuery(
  params?: PaymentListParams,
  options?: Omit<UseQueryOptions<PageResponse<PaymentDto>>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: paymentsKeys.myList(params ?? {}),
    queryFn: () => getMyPayments(params),
    ...options,
  });
}

export function usePaymentDetailQuery(
  id: number,
  options?: Omit<UseQueryOptions<PaymentDto>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: paymentsKeys.detail(id),
    queryFn: () => getPaymentById(id),
    enabled: id > 0,
    ...options,
  });
}

export function useExecutePaymentMutation(
  options?: UseMutationOptions<PaymentDto, Error, ExecutePaymentRequest>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: executePayment,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: paymentsKeys.mine() });
      // 결제 완료 시 예약/주문 상태도 변경되므로 무효화
      queryClient.invalidateQueries({ queryKey: bookingsKeys.mine() });
      queryClient.invalidateQueries({ queryKey: goodsOrdersKeys.mine() });
    },
    ...options,
  });
}
