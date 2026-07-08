/**
 * U-01: GET /products 성공 시 상품 목록을 반환한다
 * U-02: GET /products?category=EQUIPMENT 호출 시 카테고리 파라미터가 포함된다
 * U-03: GET /products/{id} 성공 시 상품 상세를 반환한다
 * U-04: 존재하지 않는 상품 id 호출 시 404 에러가 발생한다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import type { ProductDetailResponse, ProductListResponse } from '../types';

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

  const mockListResponse: ProductListResponse = {
    content: [
      {
        id: 1,
        name: '테니스 라켓',
        category: 'EQUIPMENT',
        price: '150000',
        imageUrl: 'https://example.com/racket.jpg',
        status: 'ACTIVE',
        stockQuantity: 10,
      },
    ],
    totalElements: 1,
    totalPages: 1,
    page: 0,
    size: 20,
  };

  describe('U-01: getProducts — 전체 목록', () => {
    it('GET /products?page=0&size=20 호출 시 상품 목록을 반환한다', async () => {
      mock.onGet('/products').reply(200, mockListResponse);

      const res = await client.get<ProductListResponse>('/products', {
        params: { page: 0, size: 20 },
      });

      expect(res.data.content).toHaveLength(1);
      expect(res.data.content[0].id).toBe(1);
      expect(res.data.totalPages).toBe(1);
    });
  });

  describe('U-02: getProducts — 카테고리 필터', () => {
    it('GET /products?category=EQUIPMENT 호출 시 카테고리 파라미터가 포함된다', async () => {
      mock
        .onGet('/products', { params: { page: 0, size: 20, category: 'EQUIPMENT' } })
        .reply(200, mockListResponse);

      const res = await client.get<ProductListResponse>('/products', {
        params: { page: 0, size: 20, category: 'EQUIPMENT' },
      });

      expect(res.data.content[0].category).toBe('EQUIPMENT');
    });
  });

  describe('U-03: getProduct', () => {
    it('GET /products/1 호출 시 상품 상세를 반환한다', async () => {
      mock.onGet('/products/1').reply(200, mockProduct);

      const res = await client.get<ProductDetailResponse>('/products/1');

      expect(res.data.id).toBe(1);
      expect(res.data.name).toBe('테니스 라켓');
      expect(res.data.price).toBe('150000');
      expect(res.data.stockQuantity).toBe(10);
    });
  });

  describe('U-04: 없는 상품 조회', () => {
    it('GET /products/999 호출 시 404 에러가 발생한다', async () => {
      mock.onGet('/products/999').reply(404, { message: 'Product not found' });

      await expect(client.get('/products/999')).rejects.toThrow();
    });
  });
});
