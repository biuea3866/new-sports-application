/**
 * U-01: orderType·status·page·size가 요청 쿼리 파라미터로 전달된다
 * U-02: 옵션 파라미터 미지정 시 params에서 생략된다
 * U-03: 응답이 OrderHistoryResponse(항목에 title 포함)로 그대로 반환된다
 * U-04: 인증 토큰 없이 호출해 401을 받으면 예외가 호출부로 전파된다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import type { OrderHistoryResponse } from '../order-history-types';
import { getOrderHistory } from '../orderHistory';

jest.mock('../be-client', () => {
  const actual = jest.requireActual('../be-client');
  return {
    ...actual,
    getBeClient: jest.fn(),
  };
});

import { getBeClient } from '../be-client';

const getBeClientMock = getBeClient as jest.MockedFunction<typeof getBeClient>;

describe('orderHistory API', () => {
  const client = createBeClient('http://localhost:8080');
  const mock = new MockAdapter(client);

  beforeEach(() => {
    getBeClientMock.mockReturnValue(client);
  });

  afterEach(() => mock.reset());

  const mockResponse: OrderHistoryResponse = {
    items: [
      {
        orderType: 'BOOKING',
        sourceId: 42,
        title: '강남 풋살장 예약',
        status: 'PAID',
        paymentId: 4821,
        detailPath: '/bookings/42',
        createdAt: '2026-07-05T14:00:00Z',
      },
    ],
    page: 0,
    size: 20,
    failedDomains: [],
  };

  describe('U-01: 옵션·필수 파라미터 전달', () => {
    it('orderType·status·page·size가 요청 쿼리 파라미터로 전달된다', async () => {
      mock.onGet('/api/orders').reply(200, mockResponse);

      await getOrderHistory({ orderType: 'BOOKING', status: 'PAID', page: 0, size: 20 });

      expect(mock.history.get).toHaveLength(1);
      expect(mock.history.get[0].params).toEqual({
        orderType: 'BOOKING',
        status: 'PAID',
        page: 0,
        size: 20,
      });
    });
  });

  describe('U-02: 옵션 파라미터 미지정', () => {
    it('orderType·status 미지정 시 params에서 생략된다', async () => {
      mock.onGet('/api/orders').reply(200, mockResponse);

      await getOrderHistory({ page: 0, size: 20 });

      expect(mock.history.get).toHaveLength(1);
      expect(mock.history.get[0].params).toEqual({ page: 0, size: 20 });
    });
  });

  describe('U-03: 응답 그대로 반환', () => {
    it('응답이 OrderHistoryResponse(항목에 title 포함)로 그대로 반환된다', async () => {
      mock.onGet('/api/orders').reply(200, mockResponse);

      const result = await getOrderHistory({ page: 0, size: 20 });

      expect(result.items).toHaveLength(1);
      expect(result.items[0].title).toBe('강남 풋살장 예약');
      expect(result.items[0].orderType).toBe('BOOKING');
      expect(result.items[0].paymentId).toBe(4821);
      expect(result.failedDomains).toEqual([]);
    });
  });

  describe('U-04: 인증 없이 401', () => {
    it('인증 토큰 없이 호출해 401을 받으면 예외가 호출부로 전파된다', async () => {
      mock.onGet('/api/orders').reply(401, { message: 'Unauthorized' });

      await expect(getOrderHistory({ page: 0, size: 20 })).rejects.toThrow();
    });
  });
});
