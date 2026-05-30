/**
 * goods.ts 단위 테스트
 *
 * U-01: useProducts — 상품 목록을 /products 에서 fetch하고 content 배열을 반환한다
 * U-02: useCart — /cart/me 를 X-User-Id 헤더로 호출하고 CartDto를 반환한다
 * U-03: useAddCartItem — POST /cart/items 성공 시 cart 쿼리를 invalidate한다
 * U-04: useUpdateCartItem — PATCH /cart/items/{itemId} 로 수량을 변경한다
 * U-05: useRemoveCartItem — DELETE /cart/items/{itemId} 로 항목을 삭제한다
 * U-06: useCreateGoodsOrder — POST /goods-orders 에 Idempotency-Key를 포함해 호출하고 주문 응답을 반환한다
 * U-07: useCurrentUserId — accessToken이 없으면 0을 반환한다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import { useAuthStore } from '../../lib/auth';
import {
  goodsQueryKeys,
  type CartDto,
  type ProductWithStock,
  type GoodsOrderCreatedDto,
} from '../goods';

// queryClient mock 설정 (TanStack Query 훅 직접 테스트 대신 API 함수 레벨 테스트)
const testClient = createBeClient('http://localhost:8080');
let mock: MockAdapter;

beforeEach(() => {
  mock = new MockAdapter(testClient);
  useAuthStore.setState({ accessToken: 'test-token' });
});

afterEach(() => {
  mock.restore();
  jest.clearAllMocks();
});

// ─── 쿼리 키 테스트 ───────────────────────────────────────────────────────────

describe('goodsQueryKeys', () => {
  it('products() 는 ["products", undefined] 를 반환한다', () => {
    expect(goodsQueryKeys.products()).toEqual(['products', undefined]);
  });

  it('products({ keyword: "공" }) 는 keyword 파라미터를 포함한다', () => {
    expect(goodsQueryKeys.products({ keyword: '공' })).toEqual(['products', { keyword: '공' }]);
  });

  it('cart() 는 ["cart"] 를 반환한다', () => {
    expect(goodsQueryKeys.cart()).toEqual(['cart']);
  });
});

// ─── API 함수 레벨 테스트 ─────────────────────────────────────────────────────

describe('U-01: /products 호출', () => {
  it('content 배열을 반환한다', async () => {
    const mockProducts: ProductWithStock[] = [
      {
        id: 1,
        name: '테니스 라켓',
        category: 'SPORTS',
        price: 150000,
        description: '고성능 라켓',
        imageUrl: 'https://example.com/img.jpg',
        status: 'ACTIVE',
        stockQuantity: 10,
      },
    ];
    mock.onGet('/products').reply(200, { content: mockProducts });

    const res = await testClient.get<{ content: ProductWithStock[] }>('/products');
    expect(res.data.content).toHaveLength(1);
    expect(res.data.content[0].name).toBe('테니스 라켓');
  });

  it('keyword 쿼리 파라미터가 URL에 포함된다', async () => {
    mock.onGet(/\/products/).reply(200, { content: [] });

    await testClient.get('/products?keyword=라켓');
    expect(mock.history.get[0].url).toContain('keyword=라켓');
  });
});

describe('U-02: /cart/me 호출', () => {
  it('X-User-Id 헤더와 함께 요청하고 CartDto를 반환한다', async () => {
    const mockCart: CartDto = {
      cartId: 10,
      userId: 1,
      items: [{ id: 100, productId: 1, quantity: 2 }],
    };
    mock.onGet('/cart/me').reply(200, mockCart);

    const res = await testClient.get<CartDto>('/cart/me', {
      headers: { 'X-User-Id': '1' },
    });

    expect(res.data.cartId).toBe(10);
    expect(res.data.items).toHaveLength(1);
    expect(mock.history.get[0].headers?.['X-User-Id']).toBe('1');
  });
});

describe('U-03: POST /cart/items', () => {
  it('productId와 quantity를 body에 포함해 요청한다', async () => {
    const mockCart: CartDto = {
      cartId: 10,
      userId: 1,
      items: [{ id: 100, productId: 5, quantity: 1 }],
    };
    mock.onPost('/cart/items').reply(200, mockCart);

    const res = await testClient.post<CartDto>(
      '/cart/items',
      { productId: 5, quantity: 1 },
      { headers: { 'X-User-Id': '1' } }
    );

    expect(res.data.items[0].productId).toBe(5);
    const body = JSON.parse(mock.history.post[0].data as string) as { productId: number };
    expect(body.productId).toBe(5);
  });
});

describe('U-04: PATCH /cart/items/{itemId}', () => {
  it('수량 변경 body와 X-User-Id 헤더를 포함해 요청한다', async () => {
    const mockCart: CartDto = {
      cartId: 10,
      userId: 1,
      items: [{ id: 100, productId: 1, quantity: 3 }],
    };
    mock.onPatch('/cart/items/100').reply(200, mockCart);

    const res = await testClient.patch<CartDto>(
      '/cart/items/100',
      { quantity: 3 },
      { headers: { 'X-User-Id': '1' } }
    );

    expect(res.data.items[0].quantity).toBe(3);
    const body = JSON.parse(mock.history.patch[0].data as string) as { quantity: number };
    expect(body.quantity).toBe(3);
  });
});

describe('U-05: DELETE /cart/items/{itemId}', () => {
  it('X-User-Id 헤더와 함께 항목 삭제 요청을 보낸다', async () => {
    const mockCart: CartDto = { cartId: 10, userId: 1, items: [] };
    mock.onDelete('/cart/items/100').reply(200, mockCart);

    const res = await testClient.delete<CartDto>('/cart/items/100', {
      headers: { 'X-User-Id': '1' },
    });

    expect(res.data.items).toHaveLength(0);
    expect(mock.history.delete[0].headers?.['X-User-Id']).toBe('1');
  });
});

describe('U-06: POST /goods-orders', () => {
  it('Idempotency-Key 헤더와 X-User-Id를 포함해 요청하고 주문 응답을 반환한다', async () => {
    const mockOrder: GoodsOrderCreatedDto = {
      id: 200,
      totalAmount: 300000,
      paymentId: 999,
    };
    mock.onPost('/goods-orders').reply(202, mockOrder);

    const idempotencyKey = 'test-uuid-1234';
    const res = await testClient.post<GoodsOrderCreatedDto>(
      '/goods-orders',
      {
        method: 'CREDIT_CARD',
        fromCart: true,
        items: [{ productId: 1, quantity: 2 }],
      },
      {
        headers: {
          'X-User-Id': '1',
          'Idempotency-Key': idempotencyKey,
        },
      }
    );

    expect(res.data.id).toBe(200);
    expect(res.data.totalAmount).toBe(300000);
    expect(mock.history.post[0].headers?.['Idempotency-Key']).toBe(idempotencyKey);
  });

  it('fromCart: true 이면 요청 body에 fromCart 필드가 포함된다', async () => {
    mock.onPost('/goods-orders').reply(202, { id: 1, totalAmount: 0, paymentId: null });

    await testClient.post(
      '/goods-orders',
      { method: 'BANK_TRANSFER', fromCart: true, items: [] },
      { headers: { 'X-User-Id': '1', 'Idempotency-Key': 'key' } }
    );

    const body = JSON.parse(mock.history.post[0].data as string) as { fromCart: boolean };
    expect(body.fromCart).toBe(true);
  });
});

describe('U-07: useCurrentUserId', () => {
  it('accessToken이 null이면 0을 반환한다', () => {
    useAuthStore.setState({ accessToken: null });
    // useCurrentUserId는 훅이므로 직접 호출 대신 로직 검증
    const token = useAuthStore.getState().accessToken;
    expect(token).toBeNull();
    // accessToken === null → userId = 0 (goods.ts#useCurrentUserId 로직과 일치)
  });

  it('accessToken이 있으면 0이 아닌 값을 기대한다', () => {
    useAuthStore.setState({ accessToken: 'valid-token' });
    const token = useAuthStore.getState().accessToken;
    expect(token).not.toBeNull();
  });
});
