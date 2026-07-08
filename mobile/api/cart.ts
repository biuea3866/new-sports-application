/**
 * cart.ts — 장바구니 API 함수
 */
import { getBeClient } from './be-client';
import type {
  AddCartItemRequest,
  CartResponse,
  UpdateCartItemRequest,
} from './types';

export async function getMyCart(): Promise<CartResponse> {
  const res = await getBeClient().get<CartResponse>('/cart');
  return res.data;
}

export async function addCartItem(body: AddCartItemRequest): Promise<CartResponse> {
  const res = await getBeClient().post<CartResponse>('/cart/items', body);
  return res.data;
}

export async function updateCartItem(
  cartItemId: number,
  body: UpdateCartItemRequest
): Promise<CartResponse> {
  const res = await getBeClient().put<CartResponse>(`/cart/items/${cartItemId}`, body);
  return res.data;
}

export async function removeCartItem(cartItemId: number): Promise<CartResponse> {
  const res = await getBeClient().delete<CartResponse>(`/cart/items/${cartItemId}`);
  return res.data;
}

export async function clearCart(): Promise<void> {
  await getBeClient().delete('/cart');
}
