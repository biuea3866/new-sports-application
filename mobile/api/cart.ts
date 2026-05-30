/**
 * cart.ts — 장바구니 도메인 API 함수
 *
 * BE 경로:
 *   GET    /cart/me             — 내 장바구니 조회
 *   POST   /cart/items          — 아이템 추가
 *   PATCH  /cart/items/{id}     — 아이템 수량 변경
 *   DELETE /cart/items/{id}     — 아이템 제거
 *   DELETE /cart                — 장바구니 전체 비우기
 */
import { getBeClient } from './be-client';
import { PATHS } from './paths';

// ─── DTO 타입 ────────────────────────────────────────────────────────────────

export interface CartItemDto {
  id: number;
  productId: number;
  productName: string;
  productImageUrl: string | null;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
}

export interface CartDto {
  items: CartItemDto[];
  totalPrice: number;
}

export interface AddCartItemRequest {
  productId: number;
  quantity: number;
}

export interface UpdateCartItemRequest {
  quantity: number;
}

// ─── API 함수 ────────────────────────────────────────────────────────────────

export async function getMyCart(): Promise<CartDto> {
  const response = await getBeClient().get<CartDto>(PATHS.cartMe);
  return response.data;
}

export async function addCartItem(request: AddCartItemRequest): Promise<CartItemDto> {
  const response = await getBeClient().post<CartItemDto>(PATHS.cartItems, request);
  return response.data;
}

export async function updateCartItem(
  id: number,
  request: UpdateCartItemRequest
): Promise<CartItemDto> {
  const response = await getBeClient().patch<CartItemDto>(PATHS.cartItemById(id), request);
  return response.data;
}

export async function removeCartItem(id: number): Promise<void> {
  await getBeClient().delete(PATHS.cartItemById(id));
}

export async function clearCart(): Promise<void> {
  await getBeClient().delete(PATHS.cart);
}
