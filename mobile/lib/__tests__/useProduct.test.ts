/**
 * U-01: GET /products/{id} 성공 시 상품 상세를 반환한다
 * U-02: 존재하지 않는 상품 id 호출 시 404 에러가 발생한다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../../api/be-client';
import type { ProductDetailResponse } from '../../api/types';

// expo-secure-store, expo-router는 jest.setup.ts의 global mock에서 처리

describe('Product API', () => {
  const client = createBeClient('http://localhost:8080');
  const mock = new MockAdapter(client);

  afterEach(() => mock.reset());

  const mockProduct: ProductDetailResponse = {
    id: 1,
    name: '테니스 라켓',
    category: 'EQUIPMENT',
    price: '150000',
    description: '고품질 테니스 라켓입니다.',
    imageUrl: 'https://example.com/racket.jpg',
    status: 'ACTIVE',
    stockQuantity: 10,
  };

  describe('U-01: getProduct', () => {
    it('GET /products/1 호출 시 상품 상세를 반환한다', async () => {
      mock.onGet('/products/1').reply(200, mockProduct);

      const res = await client.get<ProductDetailResponse>('/products/1');

      expect(res.data.id).toBe(1);
      expect(res.data.name).toBe('테니스 라켓');
      expect(res.data.price).toBe('150000');
      expect(res.data.stockQuantity).toBe(10);
    });
  });

  describe('U-02: 없는 상품 조회', () => {
    it('GET /products/999 호출 시 404 에러가 발생한다', async () => {
      mock.onGet('/products/999').reply(404, { message: 'Product not found' });

      await expect(client.get('/products/999')).rejects.toThrow();
    });
  });
});
