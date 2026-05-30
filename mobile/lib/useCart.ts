/**
 * useCart — 장바구니 TanStack Query 훅 모음
 *
 * - useMyCartQuery: GET /cart
 * - useAddCartItemMutation: POST /cart/items
 * - useUpdateCartItemMutation: PUT /cart/items/{id}
 * - useRemoveCartItemMutation: DELETE /cart/items/{id}
 * - useClearCartMutation: DELETE /cart
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  addCartItem,
  clearCart,
  getMyCart,
  removeCartItem,
  updateCartItem,
} from '../api/cart';
import type { AddCartItemRequest, CartResponse, UpdateCartItemRequest } from '../api/types';

export const CART_QUERY_KEY = ['cart'] as const;

export function useMyCartQuery() {
  return useQuery<CartResponse, Error>({
    queryKey: CART_QUERY_KEY,
    queryFn: getMyCart,
  });
}

export function useAddCartItemMutation() {
  const queryClient = useQueryClient();

  return useMutation<CartResponse, Error, AddCartItemRequest>({
    mutationFn: addCartItem,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
    },
  });
}

interface UpdateCartItemVariables {
  cartItemId: number;
  body: UpdateCartItemRequest;
}

export function useUpdateCartItemMutation() {
  const queryClient = useQueryClient();

  return useMutation<CartResponse, Error, UpdateCartItemVariables>({
    mutationFn: ({ cartItemId, body }) => updateCartItem(cartItemId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
    },
  });
}

export function useRemoveCartItemMutation() {
  const queryClient = useQueryClient();

  return useMutation<CartResponse, Error, number>({
    mutationFn: (cartItemId) => removeCartItem(cartItemId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
    },
  });
}

export function useClearCartMutation() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, void>({
    mutationFn: clearCart,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
    },
  });
}
