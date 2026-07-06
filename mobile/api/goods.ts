/**
 * goods.ts — 상품/장바구니/주문 API 타입 및 함수
 *
 * BE 직접 호출 (X-User-Id 헤더는 임시 패턴, AUTH-04 통합 전까지 유지)
 */
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getBeClient } from './be-client';
import { useAuthStore } from '../lib/auth';

// ─── 타입 정의 ────────────────────────────────────────────────────────────────

export type PaymentMethod = 'CREDIT_CARD' | 'BANK_TRANSFER' | 'VIRTUAL_ACCOUNT' | 'MOBILE_PAY';

export type ProductCategory = 'SPORTS' | 'APPAREL' | 'EQUIPMENT' | 'SUPPLEMENT' | 'ETC';

export type ProductStatus = 'ACTIVE' | 'INACTIVE';

export interface ProductWithStock {
  id: number;
  name: string;
  category: ProductCategory;
  price: number;
  description: string;
  imageUrl: string;
  status: ProductStatus;
  stockQuantity: number;
  /**
   * 이 상품에 연결된 활성 한정판 회차 ID. BE `/products`, `/products/{id}` 응답이 결합해 반환한다
   * (활성 회차가 없으면 필드 자체가 없거나 null) — 값이 없으면 진입점 배너를 노출하지 않는다.
   */
  limitedDropId?: number;
  /**
   * 판매자 userId. FE-14(채팅하기 CTA) 본인 상품 판정용.
   * BE `ProductWithStockResponse`(presentation/goods)가 아직 이 필드를 응답에 포함하지 않아
   * 현재는 항상 undefined로 수신된다 — 값이 없으면 본인 여부를 판정하지 않고 CTA를 노출한다.
   * BE가 필드를 추가하면(후속 티켓) 별도 변경 없이 판정이 활성화된다.
   */
  ownerId?: number;
}

export interface CartItemDto {
  id: number;
  productId: number;
  quantity: number;
}

export interface CartDto {
  cartId: number;
  userId: number;
  items: CartItemDto[];
}

export interface GoodsOrderCreatedDto {
  id: number;
  totalAmount: number;
  paymentId: number | null;
}

export interface AddCartItemBody {
  productId: number;
  quantity: number;
}

export interface UpdateCartItemBody {
  quantity: number;
}

export interface OrderItemEntry {
  productId: number;
  quantity: number;
}

export interface CreateGoodsOrderBody {
  method: PaymentMethod;
  fromCart: boolean;
  items: OrderItemEntry[];
}

// ─── Query Keys ───────────────────────────────────────────────────────────────

export const goodsQueryKeys = {
  products: (params?: Record<string, string>) => ['products', params] as const,
  cart: () => ['cart'] as const,
} as const;

// ─── X-User-Id 헤더 헬퍼 ──────────────────────────────────────────────────────
// TODO(AUTH-04): SecurityContext 통합 후 제거 예정
// 임시 방편: JWT payload의 userId를 decode하거나 서버가 주입하는 방식으로 전환 필요.
// 현재는 X-User-Id 없이 호출하면 서버가 임시 permitAll 처리 (SecurityConfig 참조)

function getUserIdHeader(): Record<string, string> {
  // accessToken에서 userId를 추출하지 않고, 서버 측 permitAll 모드에서 임시 헤더 불필요.
  // 현재 서버는 테스트/개발 환경에서 X-User-Id를 직접 받으므로 클라이언트에서 하드코딩이 불가능.
  // 실제 운영에서는 JWT 파싱 또는 서버가 SecurityContext에서 추출해야 함.
  return {};
}

// ─── 상품 목록 훅 ─────────────────────────────────────────────────────────────

export function useProducts(params?: { keyword?: string; category?: ProductCategory }) {
  return useQuery({
    queryKey: goodsQueryKeys.products(
      params
        ? Object.fromEntries(
            Object.entries(params).filter(([, v]) => v !== undefined) as [string, string][]
          )
        : undefined
    ),
    queryFn: async () => {
      const client = getBeClient();
      const query = new URLSearchParams();
      if (params?.keyword) query.set('keyword', params.keyword);
      if (params?.category) query.set('category', params.category);
      const res = await client.get<{ content: ProductWithStock[] }>(
        `/products?${query.toString()}`
      );
      return res.data.content;
    },
  });
}

// ─── 장바구니 훅 ──────────────────────────────────────────────────────────────

export function useCart(userId: number) {
  return useQuery({
    queryKey: goodsQueryKeys.cart(),
    queryFn: async () => {
      const client = getBeClient();
      const res = await client.get<CartDto>('/cart/me', {
        headers: { 'X-User-Id': String(userId) },
      });
      return res.data;
    },
    enabled: userId > 0,
  });
}

export function useAddCartItem(userId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: AddCartItemBody) => {
      const client = getBeClient();
      const res = await client.post<CartDto>('/cart/items', body, {
        headers: { 'X-User-Id': String(userId) },
      });
      return res.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: goodsQueryKeys.cart() });
    },
  });
}

export function useUpdateCartItem(userId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ itemId, quantity }: { itemId: number; quantity: number }) => {
      const client = getBeClient();
      const res = await client.patch<CartDto>(
        `/cart/items/${itemId}`,
        { quantity } satisfies UpdateCartItemBody,
        { headers: { 'X-User-Id': String(userId) } }
      );
      return res.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: goodsQueryKeys.cart() });
    },
  });
}

export function useRemoveCartItem(userId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (itemId: number) => {
      const client = getBeClient();
      const res = await client.delete<CartDto>(`/cart/items/${itemId}`, {
        headers: { 'X-User-Id': String(userId) },
      });
      return res.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: goodsQueryKeys.cart() });
    },
  });
}

export function useCreateGoodsOrder(userId: number) {
  return useMutation({
    mutationFn: async ({
      body,
      idempotencyKey,
    }: {
      body: CreateGoodsOrderBody;
      idempotencyKey: string;
    }) => {
      const client = getBeClient();
      const res = await client.post<GoodsOrderCreatedDto>('/goods-orders', body, {
        headers: {
          'X-User-Id': String(userId),
          'Idempotency-Key': idempotencyKey,
        },
      });
      return res.data;
    },
  });
}

/** userId를 Zustand에서 읽어오는 편의 훅. X-User-Id 임시 패턴에서 사용. */
export function useCurrentUserId(): number {
  // 임시 구현: accessToken에서 userId 파싱이 필요하지만 AUTH-04 전까지는 -1 반환.
  // 현재 BE가 테스트 환경에서 고정 userId(예: 1)로 동작하므로 개발 시에는 1을 반환.
  const accessToken = useAuthStore((s) => s.accessToken);
  if (!accessToken) return 0;
  // TODO(AUTH-04): JWT decode로 교체
  return 1;
}

/** X-User-Id 헤더용 — getUserIdHeader export (하위 호환) */
export { getUserIdHeader };
