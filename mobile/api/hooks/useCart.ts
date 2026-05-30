/**
 * useCart.ts — 장바구니 도메인 react-query 훅
 */
import {
  useQuery,
  useMutation,
  useQueryClient,
  type UseQueryOptions,
  type UseMutationOptions,
} from '@tanstack/react-query';
import {
  getMyCart,
  addCartItem,
  updateCartItem,
  removeCartItem,
  clearCart,
  type CartDto,
  type CartItemDto,
  type AddCartItemRequest,
  type UpdateCartItemRequest,
} from '../cart';
import { cartKeys } from '../queryKeys';

export function useMyCartQuery(
  options?: Omit<UseQueryOptions<CartDto>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: cartKeys.mine(),
    queryFn: getMyCart,
    ...options,
  });
}

export function useAddCartItemMutation(
  options?: UseMutationOptions<CartItemDto, Error, AddCartItemRequest>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: addCartItem,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cartKeys.mine() });
    },
    ...options,
  });
}

export function useUpdateCartItemMutation(
  options?: UseMutationOptions<CartItemDto, Error, { id: number; request: UpdateCartItemRequest }>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }) => updateCartItem(id, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cartKeys.mine() });
    },
    ...options,
  });
}

export function useRemoveCartItemMutation(
  options?: UseMutationOptions<void, Error, number>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => removeCartItem(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cartKeys.mine() });
    },
    ...options,
  });
}

export function useClearCartMutation(
  options?: UseMutationOptions<void, Error, void>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: clearCart,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cartKeys.mine() });
    },
    ...options,
  });
}
