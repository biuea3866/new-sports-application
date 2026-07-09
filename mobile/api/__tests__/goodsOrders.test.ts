/**
 * U-01: getGoodsOrderDetail은 GET /goods-orders/{id}로 주문 상세를 반환한다(주문상세 Option A)
 * U-02: 응답에 paymentId·paymentStatus가 포함되고 items에 productName이 없다(백엔드 실제 계약)
 * U-03: 존재하지 않는 주문(404)은 예외로 전파된다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import { getGoodsOrderDetail } from '../goodsOrders';
import type { GoodsOrderDetailResponse } from '../types';

jest.mock('../be-client', () => {
  const actual = jest.requireActual<typeof import('../be-client')>('../be-client');
  return { ...actual, getBeClient: jest.fn() };
});

import { getBeClient } from '../be-client';

const getBeClientMock = getBeClient as jest.MockedFunction<typeof getBeClient>;

describe('goodsOrders API — getGoodsOrderDetail', () => {
  const client = createBeClient('http://localhost:8080');
  const mock = new MockAdapter(client);

  beforeEach(() => getBeClientMock.mockReturnValue(client));
  afterEach(() => mock.reset());

  const mockDetail: GoodsOrderDetailResponse = {
    id: 5,
    userId: 1,
    status: 'PAID',
    totalAmount: '10000',
    paymentId: 300,
    paymentStatus: 'PAID',
    items: [{ id: 1, productId: 88, quantity: 1, unitPrice: '10000', subtotal: '10000' }],
  };

  describe('U-01', () => {
    it('GET /goods-orders/5 호출 시 주문 상세를 반환한다', async () => {
      mock.onGet('/goods-orders/5').reply(200, mockDetail);

      const res = await getGoodsOrderDetail(5);

      expect(res.id).toBe(5);
      expect(res.items).toHaveLength(1);
    });
  });

  describe('U-02', () => {
    it('paymentId·paymentStatus가 응답에 포함된다(백엔드 실제 계약)', async () => {
      mock.onGet('/goods-orders/5').reply(200, mockDetail);

      const res = await getGoodsOrderDetail(5);

      expect(res.paymentId).toBe(300);
      expect(res.paymentStatus).toBe('PAID');
      expect(res.items[0]).not.toHaveProperty('productName');
    });
  });

  describe('U-03', () => {
    it('존재하지 않는 주문(404)은 예외로 전파된다', async () => {
      mock.onGet('/goods-orders/999').reply(404, { message: 'Not found' });

      await expect(getGoodsOrderDetail(999)).rejects.toThrow();
    });
  });
});
